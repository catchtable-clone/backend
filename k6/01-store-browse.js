/**
 * 매장 조회 부하테스트
 * 실행: k6 run k6/01-store-browse.js
 *
 * 테스트 항목:
 *   - GET /stores          (목록 + 필터)
 *   - GET /stores/popular  (인기 매장)
 *   - GET /stores/nearby   (PostGIS 거리 계산)
 *   - GET /stores/in-bounds (지도 영역 조회)
 *   - GET /stores/{id}     (상세)
 *   - GET /remains         (시간대별 좌석)
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON, THRESHOLDS } from './config.js';

const nearbyDuration = new Trend('nearby_duration', true);
const inBoundsDuration = new Trend('in_bounds_duration', true);

export const options = {
  scenarios: {
    // 단계적으로 부하 증가 → 유지 → 감소
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },   // 워밍업
        { duration: '30s', target: 30 },   // 1단계
        { duration: '1m',  target: 50 },   // 2단계
        { duration: '1m',  target: 50 },   // 50명 유지
        { duration: '30s', target: 0 },    // 종료
      ],
    },
  },
  thresholds: {
    ...THRESHOLDS,
    nearby_duration: ['p(95)<2000'],      // PostGIS 쿼리는 2초 기준
    in_bounds_duration: ['p(95)<2000'],
  },
};

// 서울 중심 좌표
const SEOUL = { lat: 37.5665, lng: 126.9780 };

// 테스트할 매장 ID 목록 (실제 DB에 있는 ID로 교체)
const STORE_IDS = [1, 2, 3, 4, 5];

export default function () {
  const storeId = STORE_IDS[Math.floor(Math.random() * STORE_IDS.length)];
  const today = new Date().toISOString().split('T')[0];

  group('매장 목록 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores?page=0&size=10`, { headers: HEADERS_JSON });
    check(res, { '목록 200': (r) => r.status === 200 });
  });

  group('인기 매장 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/popular?limit=10`, { headers: HEADERS_JSON });
    check(res, { '인기 200': (r) => r.status === 200 });
  });

  group('근처 매장 조회 (PostGIS)', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/stores/nearby?latitude=${SEOUL.lat}&longitude=${SEOUL.lng}&page=0&size=10`,
      { headers: HEADERS_JSON },
    );
    check(res, { '근처 200': (r) => r.status === 200 });
    nearbyDuration.add(res.timings.duration);
  });

  group('지도 영역 매장 조회 (in-bounds)', () => {
    // 서울 강남 일대 bounding box
    const res = http.get(
      `${BASE_URL}/api/v1/stores/in-bounds` +
        `?minLat=37.49&maxLat=37.54&minLng=127.01&maxLng=127.07` +
        `&centerLat=${SEOUL.lat}&centerLng=${SEOUL.lng}&limit=100`,
      { headers: HEADERS_JSON },
    );
    check(res, { 'in-bounds 200': (r) => r.status === 200 });
    inBoundsDuration.add(res.timings.duration);
  });

  group('매장 상세 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/${storeId}`, { headers: HEADERS_JSON });
    check(res, { '상세 200': (r) => r.status === 200 });
  });

  group('시간대별 좌석 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/remains?storeId=${storeId}&date=${today}`,
      { headers: HEADERS_JSON },
    );
    check(res, { '좌석 200': (r) => r.status === 200 });
  });

  sleep(1);
}
