/**
 * 서킷브레이커 부하테스트
 * 실행: k6 run --out experimental-prometheus-rw -e AUTH_TOKEN=<JWT> -e BASE_URL=https://api.catcheat.kro.kr k6/04-circuit-breaker.js
 *
 * 목적:
 *   - LLM API(Spring AI)에 동시 요청을 쏴서 서킷브레이커가 열리는 시점 확인
 *   - 서킷 CLOSED → OPEN → HALF-OPEN 상태 전환 관찰
 *
 * 서킷브레이커 상태 판별:
 *   - 응답 느림 + 성공/실패   → CLOSED (정상 흐름, LLM 호출 중)
 *   - 응답 빠름 + 에러        → OPEN   (서킷 차단, 즉시 실패 반환)
 *   - 응답 일부 성공          → HALF-OPEN (복구 테스트 중)
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS_AUTH } from './config.js';

const circuitOpenCount   = new Counter('circuit_open_responses');   // 빠른 실패 (서킷 OPEN)
const circuitClosedCount = new Counter('circuit_closed_responses'); // 정상 응답 (서킷 CLOSED)
const llmErrorRate       = new Rate('llm_error_rate');
const chatDuration       = new Trend('chat_duration', true);

// 서킷 OPEN 판별 기준: 500ms 미만이면서 에러 → 즉시 차단된 것
const CIRCUIT_OPEN_THRESHOLD_MS = 500;

export const options = {
  scenarios: {
    // 1단계: 워밍업 (서킷 CLOSED 기준선 확인)
    warmup: {
      executor: 'constant-vus',
      vus: 2,
      duration: '30s',
      tags: { phase: 'warmup' },
    },
    // 2단계: 부하 증가 → 서킷 OPEN 유도
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 2,
      stages: [
        { duration: '30s', target: 5  },
        { duration: '1m',  target: 10 },
        { duration: '1m',  target: 20 },
      ],
      startTime: '30s',
      tags: { phase: 'ramp_up' },
    },
    // 3단계: 고부하 유지 → 서킷 OPEN 상태 관찰
    sustained: {
      executor: 'constant-vus',
      vus: 20,
      duration: '1m',
      startTime: '3m',
      tags: { phase: 'sustained' },
    },
    // 4단계: 부하 제거 → HALF-OPEN 전환 관찰
    recovery: {
      executor: 'constant-vus',
      vus: 2,
      duration: '30s',
      startTime: '4m',
      tags: { phase: 'recovery' },
    },
  },
  thresholds: {
    llm_error_rate:  ['rate<0.8'],          // 에러율 80% 미만 (서킷 완전 차단 시 100%)
    chat_duration:   ['p(95)<60000'],       // LLM 특성상 타임아웃 기준 60초
  },
};

// 테스트용 메시지 목록 (다양한 복잡도)
const TEST_MESSAGES = [
  { message: '안녕하세요', latitude: 37.5665, longitude: 126.9780 },
  { message: '강남 근처 한식 맛집 추천해줘', latitude: 37.4979, longitude: 127.0276 },
  { message: '오늘 저녁 2명 예약 가능한 곳 있어?', latitude: 37.5665, longitude: 126.9780 },
  { message: '이탈리안 레스토랑 예약하고 싶어', latitude: 37.5172, longitude: 127.0473 },
];

export default function () {
  const msg = TEST_MESSAGES[Math.floor(Math.random() * TEST_MESSAGES.length)];
  const payload = JSON.stringify(msg);

  group('챗봇 메시지 전송', () => {
    const res = http.post(`${BASE_URL}/api/v1/chat/messages`, payload, {
      headers: HEADERS_AUTH,
      timeout: 65000,  // LLM 응답 최대 대기 (65초)
    });

    chatDuration.add(res.timings.duration);

    const isSuccess = res.status === 200 || res.status === 201;
    const isFastFail = res.timings.duration < CIRCUIT_OPEN_THRESHOLD_MS && !isSuccess;

    if (isFastFail) {
      // 빠른 실패 = 서킷 OPEN 상태
      circuitOpenCount.add(1);
      llmErrorRate.add(true);
      console.log(`[서킷 OPEN] ${res.timings.duration.toFixed(0)}ms - status: ${res.status}`);
    } else if (isSuccess) {
      circuitClosedCount.add(1);
      llmErrorRate.add(false);
    } else {
      // 느린 실패 (LLM 타임아웃 등)
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

  return {
    stdout: `
===== 서킷브레이커 테스트 결과 =====
총 요청수              : ${total}
정상 응답 (CLOSED)     : ${closed}
즉시 차단  (OPEN)      : ${open}
서킷 OPEN 비율         : ${total > 0 ? ((open / total) * 100).toFixed(1) : 0}%

p95 응답시간           : ${(p95 / 1000).toFixed(2)}초
p99 응답시간           : ${(p99 / 1000).toFixed(2)}초

[분석]
- "즉시 차단" 수가 늘어나는 시점 = 서킷 OPEN 전환 시점
- 부하 제거 후 정상 응답 돌아오면 HALF-OPEN → CLOSED 복구 확인
=====================================
`,
  };
}
