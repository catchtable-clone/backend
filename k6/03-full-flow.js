/**
 * 실제 사용자 플로우 부하테스트
 * 실행: k6 run -e AUTH_TOKEN=<JWT> k6/03-full-flow.js
 *
 * 시나리오: 홈 진입 → 매장 검색 → 상세 조회 → 좌석 확인
 * (결제가 필요한 예약 확정은 외부 API 의존이라 포함하지 않음)
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON, HEADERS_AUTH, THRESHOLDS } from './config.js';

const flowDuration = new Trend('full_flow_duration', true);

export const options = {
  scenarios: {
    // 일반 트래픽 — 점진적 증가
    normal_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m',  target: 30 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    ...THRESHOLDS,
    full_flow_duration: ['p(95)<5000'],   // 전체 플로우 5초 이내
  },
};

const SEOUL = { lat: 37.5665, lng: 126.9780 };
const STORE_IDS = [1, 2, 3, 4, 5];

export default function () {
  const start = Date.now();
  const storeId = STORE_IDS[Math.floor(Math.random() * STORE_IDS.length)];
  const today = new Date().toISOString().split('T')[0];

  group('1. 홈 - 인기 매장 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/popular?limit=10`, {
      headers: HEADERS_JSON,
    });
    check(res, { '인기 매장 200': (r) => r.status === 200 });
    sleep(0.5);
  });

  group('2. 카테고리/지역 필터 검색', () => {
    const categories = ['한식', '일식', '중식', '양식'];
    const category = categories[Math.floor(Math.random() * categories.length)];
    const res = http.get(
      `${BASE_URL}/api/v1/stores?category=${encodeURIComponent(category)}&page=0&size=10`,
      { headers: HEADERS_JSON },
    );
    check(res, { '필터 검색 200': (r) => r.status === 200 });
    sleep(0.5);
  });

  group('3. 지도 화면 - 영역 내 매장 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/stores/in-bounds` +
        `?minLat=37.49&maxLat=37.54&minLng=127.01&maxLng=127.07` +
        `&centerLat=${SEOUL.lat}&centerLng=${SEOUL.lng}&limit=100`,
      { headers: HEADERS_JSON },
    );
    check(res, { '지도 200': (r) => r.status === 200 });
    sleep(0.3);
  });

  group('4. 매장 상세 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/${storeId}`, {
      headers: HEADERS_JSON,
    });
    check(res, { '상세 200': (r) => r.status === 200 });
    sleep(1);
  });

  group('5. 메뉴 + 리뷰 조회 (동시)', () => {
    const responses = http.batch([
      ['GET', `${BASE_URL}/api/v1/stores/${storeId}/menu`, null, { headers: HEADERS_JSON }],
      ['GET', `${BASE_URL}/api/v1/reviews/store/${storeId}`, null, { headers: HEADERS_JSON }],
    ]);
    check(responses[0], { '메뉴 200': (r) => r.status === 200 });
    check(responses[1], { '리뷰 200': (r) => r.status === 200 });
    sleep(0.5);
  });

  group('6. 예약 가능 시간 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/remains?storeId=${storeId}&date=${today}`,
      { headers: HEADERS_JSON },
    );
    check(res, { '좌석 200': (r) => r.status === 200 });
    sleep(0.5);
  });

  // 로그인 유저만: 북마크 폴더 조회
  if (HEADERS_AUTH.Authorization !== 'Bearer ') {
    group('7. 북마크 폴더 조회 (로그인)', () => {
      const res = http.get(`${BASE_URL}/api/v1/bookmark-folders`, {
        headers: HEADERS_AUTH,
      });
      check(res, { '북마크 200': (r) => r.status === 200 });
    });
  }

  flowDuration.add(Date.now() - start);
  sleep(1);
}
