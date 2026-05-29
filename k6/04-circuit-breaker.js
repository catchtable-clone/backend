/**
 * 서킷브레이커 부하테스트 — 점진적 증가로 OPEN 전환 시점 탐색
 * 실행: .\k6\run.ps1 -Test 04 -AuthToken "eyJ..." -BaseUrl https://api.catcheat.kro.kr
 *
 * 목적:
 *   - LLM API(Spring AI)에 점진적으로 부하를 올려 서킷브레이커가 열리는 VU 수 확인
 *   - 서킷 CLOSED → OPEN → HALF-OPEN → CLOSED 전체 상태 전환 사이클 관찰
 *
 * [서킷브레이커에 ramp_up을 쓰는 이유]
 *   - LLM API는 응답시간이 수초~수십초로 가변적
 *   - 50명 동시 접속(constant_50)은 서버가 감당하기 전에 타임아웃이 폭발해서
 *     서킷이 열리는 시점 자체를 관찰할 수 없음
 *   - 점진적으로 올려야 "2명일 때는 괜찮다가 10명에서 서킷이 열린다"는 정보를 얻을 수 있음
 *
 * 전체 소요 시간: 약 5분 30초
 *   [0s ~ 30s]  warmup    — 2VU, 서킷 CLOSED 기준선 측정
 *   [30s ~ 3m]  ramp_up   — 2→20VU 점진 증가, 서킷 OPEN 유발
 *   [3m ~ 4m]   sustained — 20VU 유지, OPEN 상태 지속 관찰
 *   [4m ~ 4m30s] recovery — 2VU 복귀, HALF-OPEN → CLOSED 복구 확인
 *
 * [서킷 상태 판별 방법]
 *   CLOSED (정상): 응답이 느리지만 200 반환. LLM이 실제 처리 중.
 *   OPEN   (차단): 응답이 매우 빠르면서(< 500ms) 에러. 서킷이 즉시 차단.
 *   HALF-OPEN(복구): 부하 제거 후 일부 요청만 성공. 서킷이 복구 테스트 중.
 *
 * [체크포인트]
 *   1. circuit_open_responses 카운터가 올라가기 시작하는 VU 수
 *      → 그 수가 현재 서킷브레이커 설정의 failureRateThreshold 도달 지점
 *   2. ramp_up → sustained 전환 후에도 circuit_open_responses가 계속 올라가는지
 *      → 계속 올라가면 서킷이 계속 OPEN 상태 (복구 안 됨)
 *   3. recovery 구간에서 circuit_closed_responses가 다시 올라가는지
 *      → 올라가면 HALF-OPEN → CLOSED 복구 성공
 *
 * [Grafana 모니터링]
 *   - circuit_open_responses vs circuit_closed_responses 동시에 보기
 *   - vus 그래프와 함께 보면 몇 명에서 서킷이 열리는지 정확히 파악
 *   - chat_duration: OPEN 상태면 < 500ms, CLOSED 상태면 수초
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS_AUTH } from './config.js';

const circuitOpenCount   = new Counter('circuit_open_responses');   // 빠른 실패 (서킷 OPEN)
const circuitClosedCount = new Counter('circuit_closed_responses'); // 정상 응답 (서킷 CLOSED)
const llmErrorRate       = new Rate('llm_error_rate');
const chatDuration       = new Trend('chat_duration', true);

// 서킷 OPEN 판별 기준: 500ms 미만이면서 에러 → 서킷이 즉시 차단한 것
// LLM 정상 처리는 최소 수백ms~수초이므로 500ms 이하 빠른 실패 = OPEN 확실
const CIRCUIT_OPEN_THRESHOLD_MS = 500;

export const options = {
  scenarios: {
    // ── 워밍업: 서킷 CLOSED 기준선 측정 ─────────────────────────────────────
    // 2VU로 정상 상태에서의 응답시간 확인. 이 구간 chat_duration이 기준선.
    // 여기서도 에러가 나면 → LLM API 자체 문제 (부하와 무관)
    warmup: {
      executor: 'constant-vus',
      vus: 2,
      duration: '30s',
      tags: { phase: 'warmup' },
    },

    // ── ramp_up: 서킷 OPEN 유발 구간 ─────────────────────────────────────────
    // VU를 점진적으로 올리면서 circuit_open_responses 카운터 증가 시점을 포착
    // 2→5→10→20명 단계에서 Grafana 확인:
    //   - 5명까지 정상 → 10명에서 circuit_open 증가 시작 → 10명이 임계점
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 2,
      startTime: '30s',
      stages: [
        { duration: '30s', target: 5  },  // LLM은 소규모에서도 느릴 수 있음
        { duration: '1m',  target: 10 },  // 대부분 서킷 OPEN 시작 구간
        { duration: '1m',  target: 20 },  // 확실한 OPEN 상태 유도
      ],
      tags: { phase: 'ramp_up' },
    },

    // ── sustained: OPEN 상태 지속 관찰 ──────────────────────────────────────
    // 서킷이 열린 상태를 1분간 유지. circuit_open_responses 비율 측정.
    // 대부분의 요청이 즉시 차단(< 500ms)되어야 정상적인 OPEN 상태
    sustained: {
      executor: 'constant-vus',
      vus: 20,
      duration: '1m',
      startTime: '3m',
      tags: { phase: 'sustained' },
    },

    // ── recovery: HALF-OPEN → CLOSED 복구 확인 ──────────────────────────────
    // 부하를 낮추면 서킷브레이커가 HALF-OPEN 상태로 전환되어 일부 요청만 통과시킴.
    // circuit_closed_responses가 다시 올라오면 복구 성공.
    // 복구가 안 되면 → 서킷브레이커 waitDurationInOpenState 설정 확인
    recovery: {
      executor: 'constant-vus',
      vus: 2,
      duration: '30s',
      startTime: '4m',
      tags: { phase: 'recovery' },
    },
  },
  thresholds: {
    // LLM 특성상 응답시간 기준을 60초로 매우 여유 있게 설정
    // 실제 서킷이 제대로 작동하면 OPEN 상태에서 대부분 < 500ms로 즉시 차단됨
    llm_error_rate: ['rate<0.8'],          // 에러율 80% 미만 (서킷 OPEN 시 거의 100%)
    chat_duration:  ['p(95)<60000'],       // LLM 타임아웃 기준
  },
};

const TEST_MESSAGES = [
  { message: '안녕하세요', latitude: 37.5665, longitude: 126.9780 },
  { message: '강남 근처 한식 맛집 추천해줘', latitude: 37.4979, longitude: 127.0276 },
  { message: '오늘 저녁 2명 예약 가능한 곳 있어?', latitude: 37.5665, longitude: 126.9780 },
  { message: '이탈리안 레스토랑 예약하고 싶어', latitude: 37.5172, longitude: 127.0473 },
];

export default function () {
  const msg     = TEST_MESSAGES[Math.floor(Math.random() * TEST_MESSAGES.length)];
  const payload = JSON.stringify(msg);

  group('챗봇 메시지 전송', () => {
    const res = http.post(`${BASE_URL}/api/v1/chat/messages`, payload, {
      headers: HEADERS_AUTH,
      timeout: '65s',
    });

    chatDuration.add(res.timings.duration);

    const isSuccess  = res.status === 200 || res.status === 201;
    // 빠른 실패 판별: 500ms 미만 + 에러 → 서킷 OPEN 상태
    const isFastFail = res.timings.duration < CIRCUIT_OPEN_THRESHOLD_MS && !isSuccess;

    if (isFastFail) {
      circuitOpenCount.add(1);
      llmErrorRate.add(true);
      console.log(`[서킷 OPEN] ${res.timings.duration.toFixed(0)}ms - status: ${res.status}`);
    } else if (isSuccess) {
      circuitClosedCount.add(1);
      llmErrorRate.add(false);
    } else {
      // 느린 실패 — LLM 타임아웃 또는 서킷 CLOSED 상태에서의 LLM 오류
      llmErrorRate.add(true);
      console.log(`[LLM 실패] ${res.timings.duration.toFixed(0)}ms - status: ${res.status} - ${res.body?.substring(0, 100)}`);
    }

    check(res, {
      '서킷 OPEN (즉시 차단)': () => isFastFail,
      '정상 응답 (서킷 CLOSED)': () => isSuccess,
    });
  });

  sleep(1);
}

export function handleSummary(data) {
  const open   = data.metrics.circuit_open_responses?.values?.count   || 0;
  const closed = data.metrics.circuit_closed_responses?.values?.count || 0;
  const total  = open + closed;
  const p95    = data.metrics.chat_duration?.values?.['p(95)']        || 0;
  const p99    = data.metrics.chat_duration?.values?.['p(99)']        || 0;

  const openRate = total > 0 ? ((open / total) * 100).toFixed(1) : '0.0';

  return {
    stdout: `
===== 서킷브레이커 테스트 결과 =====
총 요청수              : ${total}건
정상 응답 (CLOSED)     : ${closed}건
즉시 차단  (OPEN)      : ${open}건
서킷 OPEN 비율         : ${openRate}%

p95 응답시간           : ${(p95 / 1000).toFixed(2)}초
p99 응답시간           : ${(p99 / 1000).toFixed(2)}초

[분석]
  circuit_open_responses 카운터가 증가하기 시작한 시점 (Grafana 확인):
  → warmup(2VU): 0건이어야 정상
  → ramp_up 중 5VU / 10VU / 20VU 구간 중 어느 시점에 open 증가?
     그 VU 수가 현재 서킷브레이커 설정의 실질적 임계 부하

  recovery 구간(4m~4m30s)에서 closed_responses가 다시 올라오면 → 복구 성공
  올라오지 않으면 → waitDurationInOpenState 설정 확인:
    @CircuitBreaker(name=..., waitDurationInOpenState=30s)

  OPEN 비율 > 80% → LLM API 응답이 매우 불안정. 폴백 로직 강화 필요.
===================================
`,
  };
}
