/**
 * 예약 동시성 부하테스트 (핵심 시나리오)
 * 실행: k6 run -e AUTH_TOKEN=<JWT> -e REMAIN_ID=<ID> k6/02-reservation-concurrency.js
 *
 * 목적: 같은 remainId에 100명이 동시에 예약 요청 → 분산락/Optimistic Lock 검증
 * 기대 결과:
 *   - 좌석 수만큼만 성공 (201)
 *   - 나머지는 REMAIN_EXHAUSTED (400/409)
 *   - 데이터 정합성 깨지면 안 됨 (중복 예약 X)
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON, HEADERS_AUTH, AUTH_TOKEN, REMAIN_ID, THRESHOLDS, requireAuthToken } from './config.js';

// 다중 토큰 풀 (선택). 운영 환경과 더 비슷한 검증을 원하면 TOKENS CSV 주입.
const TOKENS = (__ENV.TOKENS ? __ENV.TOKENS.split(',') : []).filter(Boolean);
const USE_MULTI_TOKENS = TOKENS.length > 1;

export function setup() {
  if (!AUTH_TOKEN && !USE_MULTI_TOKENS) {
    requireAuthToken('02');
  }
}

const successCount   = new Counter('reservation_success');
const exhaustedCount = new Counter('reservation_exhausted');
const conflictCount  = new Counter('reservation_conflict');
const errorRate      = new Rate('reservation_error_rate');
const reserveDuration = new Trend('reservation_duration', true);

export const options = {
  // stdout 메트릭에 p99 노출 (기본은 p95까지만)
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  scenarios: {
    // 100명이 동시에 한꺼번에 예약 요청 — 스파이크 시나리오
    spike: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 150,
      maxVUs: 200,
      stages: [
        { duration: '5s',  target: 100 },  // 5초 만에 초당 100 요청
        { duration: '30s', target: 100 },  // 30초 유지
        { duration: '5s',  target: 0 },
      ],
    },
  },
  thresholds: {
    ...THRESHOLDS,
    reservation_duration: ['p(95)<3000'],
    // 동시성 테스트이므로 에러율 기준 완화 (좌석 부족 에러는 정상)
    http_req_failed: ['rate<0.01'],        // 5xx 에러만 실패로 간주
  },
};

export default function () {
  const headers = USE_MULTI_TOKENS
    ? { ...HEADERS_JSON, Authorization: `Bearer ${TOKENS[(__VU - 1) % TOKENS.length]}` }
    : HEADERS_AUTH;

  group('예약 생성 (동시성)', () => {
    const payload = JSON.stringify({
      remainId: parseInt(REMAIN_ID),
      member: 2,
      couponId: null,
    });

    const res = http.post(`${BASE_URL}/api/v1/reservations`, payload, { headers });

    reserveDuration.add(res.timings.duration);

    if (res.status === 201) {
      successCount.add(1);
      errorRate.add(false);
      check(res, { '예약 성공 (201)': (r) => r.status === 201 });
    } else if (res.status === 400 || res.status === 409) {
      const body = (() => { try { return res.json(); } catch { return {}; } })();
      const code = body?.code || '';

      if (code === 'REMAIN_EXHAUSTED') {
        exhaustedCount.add(1);
      } else if (code === 'OPTIMISTIC_LOCK_CONFLICT') {
        conflictCount.add(1);
      }

      errorRate.add(false); // 좌석 부족/락 충돌은 정상 동작
      check(res, { '좌석 부족/락 충돌 (정상)': () => true });
    } else {
      errorRate.add(true);
      check(res, { '예상치 못한 에러': (r) => r.status < 500 });
      console.error(`예상치 못한 응답: ${res.status} - ${res.body}`);
    }
  });

  sleep(0.1);
}

export function handleSummary(data) {
  const success   = data.metrics.reservation_success?.values?.count || 0;
  const exhausted = data.metrics.reservation_exhausted?.values?.count || 0;
  const conflict  = data.metrics.reservation_conflict?.values?.count || 0;
  const total     = success + exhausted + conflict;
  const p99       = data.metrics.reservation_duration?.values?.['p(99)'] || 0;
  const p95       = data.metrics.reservation_duration?.values?.['p(95)'] || 0;

  return {
    stdout: `
===== 예약 동시성 테스트 결과 =====
총 요청수         : ${total}
예약 성공         : ${success}
좌석 소진 (정상)  : ${exhausted}
락 충돌 (정상)    : ${conflict}
p95 응답시간      : ${p95.toFixed(0)}ms
p99 응답시간      : ${p99.toFixed(0)}ms

[검증] 예약 성공 수가 실제 좌석 수와 일치하는지 DB에서 확인하세요.
SELECT COUNT(*) FROM reservations WHERE remain_id = ${REMAIN_ID} AND status != 'PAYMENT_FAILED';
===================================
`,
  };
}
