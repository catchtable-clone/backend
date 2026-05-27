export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 인증이 필요한 테스트 실행 시: k6 run -e AUTH_TOKEN=<JWT토큰> script.js
export const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

// 예약 동시성 테스트용 — 같은 시간대에 몰아야 의미있음
// k6 run -e REMAIN_ID=<실제ID> 02-reservation-concurrency.js
export const REMAIN_ID = __ENV.REMAIN_ID || '1';

export const HEADERS_JSON = {
  'Content-Type': 'application/json',
};

export const HEADERS_AUTH = {
  'Content-Type': 'application/json',
  Authorization: `Bearer ${AUTH_TOKEN}`,
};

// 공통 성능 기준선 — 이 수치를 넘으면 테스트 실패 처리
export const THRESHOLDS = {
  http_req_failed: ['rate<0.05'],          // 에러율 5% 미만
  http_req_duration: ['p(95)<1000'],       // p95 1초 미만
};
