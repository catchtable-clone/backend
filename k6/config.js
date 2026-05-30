export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 인증이 필요한 테스트 실행 시: k6 run -e AUTH_TOKEN=<JWT토큰> script.js
export const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

// 예약 동시성 테스트용 — 같은 시간대에 몰아야 의미있음
// k6 run -e REMAIN_ID=<실제ID> 02-reservation-concurrency.js
export const REMAIN_ID = __ENV.REMAIN_ID || '1';

export const HEADERS_JSON = {
  'Content-Type': 'application/json',
};

// 빈 토큰이면 Authorization 헤더 자체를 제거 (사일런트 401 방지)
export const HEADERS_AUTH = AUTH_TOKEN
  ? { 'Content-Type': 'application/json', Authorization: `Bearer ${AUTH_TOKEN}` }
  : { 'Content-Type': 'application/json' };

export function requireAuthToken(scenarioId) {
  if (!AUTH_TOKEN) {
    throw new Error(
      `[FATAL] 시나리오 ${scenarioId}는 AUTH_TOKEN 필수입니다. ` +
      `예: ./k6/run.sh -t ${scenarioId} -a "eyJ..." 또는 -e AUTH_TOKEN=<JWT>`
    );
  }
}

// 공통 기준선. 시나리오별 override는 각 스크립트의 thresholds에서.
export const THRESHOLDS = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<1000'],
};
