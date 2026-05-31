/**
 * 서킷브레이커 풀 사이클 시연 — 무료 quota로 CLOSED → OPEN → HALF_OPEN → CLOSED 전체 검증
 * 실행: ./k6/run.sh -t 04 -a "eyJ..." -u https://api.catcheat.kro.kr
 *
 * ⚠️ ⚠️ ⚠️  비용 경고  ⚠️ ⚠️ ⚠️
 *   이 시나리오는 실제 Gemini API를 호출합니다.
 *   CB OPEN 후엔 호출이 Gemini까지 안 가므로 실제 quota 출혈은 약 ~18건 수준이지만,
 *   부하 시점에 다른 사용자가 챗봇을 쓰면 같은 API key 공유로 영향받습니다.
 *
 *   안전 가드:
 *     1. MAX_ITER_PER_VU 환경변수로 VU당 요청 상한 (기본 50)
 *        ⚠️ k6 VU는 isolated VM이라 이 가드는 "VU별" 상한임
 *           글로벌 최대 = MAX_ITER_PER_VU × 시나리오 max VU 수(1)
 *     2. 일일 메시지 제한(100회/사용자) — 단일 토큰 ~33건 소모, 한도 내
 *     3. mock LLM 사용 권장 — application.yml에서 spring.ai.* 를 mock provider로 변경 후 테스트
 *
 * [원리]
 *   CB OPEN 후엔 호출이 Gemini까지 안 가므로 quota 출혈이 0.
 *   "분당 15회 키 단위 제한"만 살짝 초과시키면 ~40s 시점에 OPEN 트리거됨.
 *   부하 중지 후 30s 대기로 자동 HALF_OPEN 전환, 다시 1분 더 대기해 Gemini quota
 *   회복 후 3회 시도로 CLOSED 복귀 확인.
 *
 * [타임라인 — 약 2분 15초]
 *   0~60s     flood   : constant-arrival-rate 30/min — 응답 시간(LLM 평균 6s) 무관하게 정확히 분당 30회 발사.
 *                       VU 풀(10명)에서 2초마다 한 명씩 발사. 첫 15회 통과, 다음 15회 429.
 *                       sliding window 10건 중 5건 429 누적 시 ~40s에 OPEN.
 *   60~120s   휴지    : 호출 없음. 70s쯤 자동 HALF_OPEN 전환되어 대기.
 *                       Gemini 1분 rolling quota 회복 (마지막 호출 60s + 60s = 120s).
 *   120~135s  recover : 3회만 천천히 발사. quota 회복돼 모두 200 → CLOSED 복귀.
 *
 * [설계 변경 이력]
 *   v1: VU 1 + sleep(2s) — LLM 응답 6s에 가로막혀 실제 분당 7~9회만 발사되어 CB OPEN 미도달.
 *   v2: constant-arrival-rate로 변경 (현재). 발사 페이스가 LLM 응답 시간과 분리됨.
 *
 * [서킷 상태 판별 방법]
 *   CLOSED (정상): 응답이 느리지만 200 반환. LLM이 실제 처리 중.
 *   OPEN   (차단): 응답이 매우 빠르면서(< 500ms) 에러. 서킷이 즉시 차단.
 *   HALF-OPEN(복구): 부하 제거 후 일부 요청만 성공. 서킷이 복구 테스트 중.
 *
 * [Grafana CB 패널 (수정한 PromQL)]
 *   0=closed / 1=half_open / 2=open
 *     0~40s   : 0 (closed)
 *    ~40s     : 2 (open) 점프
 *    ~70s     : 1 (half_open) 자동 전환
 *    ~120s+   : 0 (closed) 복귀
 *   chat_duration: open 구간만 < 500ms (즉시 차단)
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS_AUTH, requireAuthToken } from './config.js';

export function setup() {
  requireAuthToken('04');
}

const circuitOpenCount   = new Counter('circuit_open_responses');   // CB OPEN fallback (status 200 + "점검 중" body)
const circuitClosedCount = new Counter('circuit_closed_responses'); // 정상 LLM 응답
const bulkheadCount      = new Counter('bulkhead_fallback');        // Bulkhead Full fallback
const rateLimitCount     = new Counter('rate_limit_fallback');      // 429 Rate Limit fallback
const timeoutCount       = new Counter('timeout_fallback');         // Timeout fallback
const llmErrorRate       = new Rate('llm_error_rate');
const chatDuration       = new Trend('chat_duration', true);
const skippedSafeguard   = new Counter('skipped_by_safeguard');

// 백엔드 ChatbotService의 fallback 메시지 키워드 — buildFallbackMessage()와 catch 블록 기준
// CB OPEN/Bulkhead Full은 status 200 + fallback 문자열로 응답되어 status code만으론 구분 불가
const FALLBACK_PATTERNS = {
  cbOpen:    '일시적으로 점검 중',  // CallNotPermittedException
  bulkhead:  '많은 요청이 몰려',    // BulkheadFullException
  rateLimit: '요청 한도에 도달',    // CHAT_AI_RATE_LIMIT (429)
  timeout:   '응답이 지연',         // CHAT_AI_TIMEOUT
};

// 안전 가드: VU당 최대 요청 수 — 예상 호출수(~33)보다 약간 큰 50으로 기본값
const MAX_ITER_PER_VU = Number(__ENV.MAX_ITER_PER_VU || 50);
const MAX_VUS_IN_SCENARIO = 1;
let vuIterCount = 0;

export const options = {
  // stdout 메트릭에 p99 노출 (기본은 avg/min/med/max/p(90)/p(95)만)
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  scenarios: {
    // ── flood: 분당 30회로 Gemini 15 RPM 초과 → CB OPEN 유도 ─────────────────
    // constant-arrival-rate — 응답 시간 무관하게 정확히 30회/분 강제 발사
    // VU 풀(10명)에서 2초마다 한 명씩 발사, LLM 응답 평균 6s라도 페이스 유지
    flood: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1m',
      duration: '60s',
      preAllocatedVUs: 10,
      maxVUs: 20,
      exec: 'flood',
      tags: { phase: 'flood' },
    },
    // ── recover: 60s 휴지 후 3건만 발사 → HALF_OPEN → CLOSED 복귀 확인 ───────
    recover: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3,
      maxDuration: '30s',
      startTime: '120s',
      exec: 'recover',
      tags: { phase: 'recover' },
    },
  },
  thresholds: {
    llm_error_rate: ['rate<0.8'],
    chat_duration:  ['p(95)<60000'],
  },
};

const TEST_MESSAGES = [
  { message: '안녕하세요', latitude: 37.5665, longitude: 126.9780 },
  { message: '강남 근처 한식 맛집 추천해줘', latitude: 37.4979, longitude: 127.0276 },
  { message: '오늘 저녁 2명 예약 가능한 곳 있어?', latitude: 37.5665, longitude: 126.9780 },
  { message: '이탈리안 레스토랑 예약하고 싶어', latitude: 37.5172, longitude: 127.0473 },
];

function callChat(phase) {
  vuIterCount++;
  if (MAX_ITER_PER_VU > 0 && vuIterCount > MAX_ITER_PER_VU) {
    skippedSafeguard.add(1);
    return;
  }

  const msg     = TEST_MESSAGES[Math.floor(Math.random() * TEST_MESSAGES.length)];
  const payload = JSON.stringify(msg);

  const res = http.post(`${BASE_URL}/api/v1/chat/messages`, payload, {
    headers: HEADERS_AUTH,
    timeout: '65s',
  });

  chatDuration.add(res.timings.duration);

  const body      = res.body || '';
  const isSuccess = res.status === 200 || res.status === 201;
  // ChatbotService는 CB/Bulkhead/Rate/Timeout 모두 status 200 + fallback 문자열로 응답함.
  // 따라서 status code만으론 구분 불가 → 응답 body의 fallback 키워드로 분류.
  const isCbOpen    = body.includes(FALLBACK_PATTERNS.cbOpen);
  const isBulkhead  = body.includes(FALLBACK_PATTERNS.bulkhead);
  const isRateLimit = body.includes(FALLBACK_PATTERNS.rateLimit);
  const isTimeout   = body.includes(FALLBACK_PATTERNS.timeout);
  const isFallback  = isCbOpen || isBulkhead || isRateLimit || isTimeout;

  let label;
  if (isCbOpen) {
    circuitOpenCount.add(1);
    llmErrorRate.add(true);
    label = 'CB-OPEN';
  } else if (isBulkhead) {
    bulkheadCount.add(1);
    llmErrorRate.add(true);
    label = 'BULKHEAD';
  } else if (isRateLimit) {
    rateLimitCount.add(1);
    llmErrorRate.add(true);
    label = 'RATE-LIMIT';
  } else if (isTimeout) {
    timeoutCount.add(1);
    llmErrorRate.add(true);
    label = 'TIMEOUT';
  } else if (isSuccess) {
    circuitClosedCount.add(1);
    llmErrorRate.add(false);
    label = 'OK';
  } else {
    llmErrorRate.add(true);
    label = `FAIL-${res.status}`;
  }
  console.log(`[${phase} ${label}] ${res.timings.duration.toFixed(0)}ms status=${res.status}`);

  check(res, {
    'CB OPEN fallback 감지': () => isCbOpen,
    '정상 LLM 응답':         () => isSuccess && !isFallback,
  });
}

export function flood() {
  // arrival-rate executor가 발사 페이스를 제어하므로 sleep 불필요
  callChat('flood');
}

export function recover() {
  callChat('recover');
  sleep(3);
}

export function handleSummary(data) {
  const open      = data.metrics.circuit_open_responses?.values?.count   || 0;
  const closed    = data.metrics.circuit_closed_responses?.values?.count || 0;
  const bulkhead  = data.metrics.bulkhead_fallback?.values?.count        || 0;
  const rateLimit = data.metrics.rate_limit_fallback?.values?.count      || 0;
  const timeout   = data.metrics.timeout_fallback?.values?.count         || 0;
  const skipped   = data.metrics.skipped_by_safeguard?.values?.count     || 0;
  const total     = open + closed + bulkhead + rateLimit + timeout;
  const p95       = data.metrics.chat_duration?.values?.['p(95)']        || 0;
  const p99       = data.metrics.chat_duration?.values?.['p(99)']        || 0;

  const openRate = total > 0 ? ((open / total) * 100).toFixed(1) : '0.0';
  const globalEstimate = MAX_ITER_PER_VU * MAX_VUS_IN_SCENARIO;
  const safeguardNote = skipped > 0
    ? `\n[안전 가드] ${skipped}건이 MAX_ITER_PER_VU(=${MAX_ITER_PER_VU}, 글로벌 추정 ${globalEstimate}건) 도달로 차단됨`
    : `\n[안전 가드] 작동 가능: VU당 최대 ${MAX_ITER_PER_VU}건, 글로벌 추정 최대 ${globalEstimate}건`;

  const cbVerdict      = open >= 5  ? '✅ OPEN 트리거 확인 (fallback body 감지)' : '⚠️  OPEN 미관측 — Grafana CB 상태 패널과 교차 확인';
  const recoverVerdict = closed >= 17 ? '✅ CLOSED 복귀 확인' : '⚠️  recover 단계 정상응답 부족 — quota 회복 시간 또는 부하 잔류 확인';

  return {
    stdout: `
===== 서킷브레이커 풀 사이클 시연 결과 =====
총 요청수              : ${total}건
정상 LLM 응답          : ${closed}건   (Gemini 통과)
CB OPEN fallback       : ${open}건    ← "일시적으로 점검 중"
Bulkhead Full fallback : ${bulkhead}건 ← "많은 요청이 몰려"
Rate Limit fallback    : ${rateLimit}건 ← "요청 한도에 도달"
Timeout fallback       : ${timeout}건 ← "응답이 지연"
서킷 OPEN 비율         : ${openRate}%${safeguardNote}

p95 응답시간           : ${(p95 / 1000).toFixed(2)}초
p99 응답시간           : ${(p99 / 1000).toFixed(2)}초

[판정]
  ${cbVerdict}
  ${recoverVerdict}

[Grafana CB 패널 (0=closed / 1=half_open / 2=open)]
  0~40s   → 0
  ~40s    → 2  (OPEN 점프)
  ~70s    → 1  (HALF_OPEN 자동 전환)
  ~120s+  → 0  (CLOSED 복귀)
============================================
`,
  };
}
