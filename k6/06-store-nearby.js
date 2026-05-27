/**
 * [단일 API] PostGIS 지리 쿼리 부하테스트
 * 실행: .\k6\run.ps1 -Test 06 -BaseUrl https://api.catcheat.kro.kr
 *
 * 목적:
 *   - GET /stores/nearby   (ST_DistanceSphere 거리 계산 + 정렬)
 *   - GET /stores/in-bounds (지도 bounding box 조회)
 *   두 API가 PostGIS 지리 쿼리를 사용하는 병목 지점
 *
 * 이전 테스트 결과: 50VU에서 nearby p95=10.33s, in-bounds p95=8.36s
 * → GIST 공간 인덱스 미적용 의심 → 이 테스트로 수치 재확인
 *
 * 기대 결과:
 *   - GIST 인덱스 적용 후: p95 < 1s
 *   - 미적용 시: 50VU 이상에서 타임아웃 발생 확인
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON } from './config.js';

const nearbyDuration   = new Trend('nearby_duration', true);
const inBoundsDuration = new Trend('in_bounds_duration', true);
const timeoutCount     = new Counter('geo_timeout_count');
const errorRate        = new Rate('geo_error_rate');

// 서울 주요 지역 좌표 — 다양한 지점에서 조회하여 캐시 히트 방지
const LOCATIONS = [
  { name: '강남',   lat: 37.4979, lng: 127.0276, minLat: 37.49, maxLat: 37.52, minLng: 127.01, maxLng: 127.05 },
  { name: '홍대',   lat: 37.5563, lng: 126.9236, minLat: 37.54, maxLat: 37.57, minLng: 126.91, maxLng: 126.94 },
  { name: '명동',   lat: 37.5636, lng: 126.9869, minLat: 37.55, maxLat: 37.58, minLng: 126.97, maxLng: 127.00 },
  { name: '이태원', lat: 37.5344, lng: 126.9997, minLat: 37.52, maxLat: 37.55, minLng: 126.98, maxLng: 127.02 },
  { name: '성수',   lat: 37.5446, lng: 127.0556, minLat: 37.53, maxLat: 37.56, minLng: 127.04, maxLng: 127.07 },
  { name: '종로',   lat: 37.5704, lng: 126.9910, minLat: 37.56, maxLat: 37.59, minLng: 126.97, maxLng: 127.01 },
];

export const options = {
  scenarios: {
    // 1단계: 소부하 — 기준선 측정 (인덱스 있어도 없어도 여기선 빠름)
    baseline: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      tags: { phase: 'baseline' },
    },
    // 2단계: 중부하 — 인덱스 없으면 이 구간부터 급격히 느려짐
    medium_load: {
      executor: 'constant-vus',
      vus: 30,
      duration: '1m',
      startTime: '35s',
      tags: { phase: 'medium_load' },
    },
    // 3단계: 고부하 — 이전 테스트에서 타임아웃 발생한 구간 (50VU)
    high_load: {
      executor: 'constant-vus',
      vus: 50,
      duration: '1m',
      startTime: '2m',
      tags: { phase: 'high_load' },
    },
  },
  thresholds: {
    nearby_duration:    ['p(95)<2000'],  // 2초 기준 (PostGIS 특성 고려)
    in_bounds_duration: ['p(95)<2000'],
    geo_error_rate:     ['rate<0.05'],
    http_req_failed:    ['rate<0.05'],
  },
};

export default function () {
  // 랜덤 위치 선택 — 매번 다른 좌표로 DB 풀스캔 유도
  const loc = LOCATIONS[Math.floor(Math.random() * LOCATIONS.length)];
  // 좌표에 미세한 오프셋 추가 → 캐시 효과 없애고 실제 쿼리 유도
  const latOffset = (Math.random() - 0.5) * 0.005;
  const lngOffset = (Math.random() - 0.5) * 0.005;
  const lat = (loc.lat + latOffset).toFixed(6);
  const lng = (loc.lng + lngOffset).toFixed(6);

  group(`근처 매장 조회 (PostGIS nearby) - ${loc.name}`, () => {
    const res = http.get(
      `${BASE_URL}/api/v1/stores/nearby?latitude=${lat}&longitude=${lng}&page=0&size=10`,
      { headers: HEADERS_JSON, timeout: '15s' },
    );

    nearbyDuration.add(res.timings.duration);

    // 15초 이상 걸리면 타임아웃으로 카운트
    if (res.timings.duration > 15000 || res.status === 0) {
      timeoutCount.add(1);
    }

    const ok = check(res, {
      'nearby 200': (r) => r.status === 200,
      'nearby 15s 이내': (r) => r.timings.duration < 15000,
    });
    errorRate.add(!ok);
  });

  group(`지도 영역 조회 (in-bounds) - ${loc.name}`, () => {
    const url =
      `${BASE_URL}/api/v1/stores/in-bounds` +
      `?minLat=${loc.minLat}&maxLat=${loc.maxLat}` +
      `&minLng=${loc.minLng}&maxLng=${loc.maxLng}` +
      `&centerLat=${lat}&centerLng=${lng}&limit=50`;

    const res = http.get(url, { headers: HEADERS_JSON, timeout: '15s' });
    inBoundsDuration.add(res.timings.duration);

    if (res.timings.duration > 15000 || res.status === 0) {
      timeoutCount.add(1);
    }

    check(res, {
      'in-bounds 200': (r) => r.status === 200,
      'in-bounds 15s 이내': (r) => r.timings.duration < 15000,
    });
  });

  sleep(1);
}

export function handleSummary(data) {
  const p50Nearby   = data.metrics.nearby_duration?.values?.['p(50)']    || 0;
  const p95Nearby   = data.metrics.nearby_duration?.values?.['p(95)']    || 0;
  const p99Nearby   = data.metrics.nearby_duration?.values?.['p(99)']    || 0;
  const p95Bounds   = data.metrics.in_bounds_duration?.values?.['p(95)'] || 0;
  const p99Bounds   = data.metrics.in_bounds_duration?.values?.['p(99)'] || 0;
  const timeouts    = data.metrics.geo_timeout_count?.values?.count       || 0;
  const totalReqs   = data.metrics.http_reqs?.values?.count               || 0;

  return {
    stdout: `
===== PostGIS 지리 쿼리 단일 API 부하테스트 결과 =====
총 요청수              : ${totalReqs}
타임아웃 횟수          : ${timeouts}

[GET /stores/nearby]
p50 응답시간           : ${p50Nearby.toFixed(0)}ms
p95 응답시간           : ${p95Nearby.toFixed(0)}ms
p99 응답시간           : ${p99Nearby.toFixed(0)}ms

[GET /stores/in-bounds]
p95 응답시간           : ${p95Bounds.toFixed(0)}ms
p99 응답시간           : ${p99Bounds.toFixed(0)}ms

[분석]
- p95 > 2s 또는 타임아웃 존재 → GIST 공간 인덱스 미적용 가능성 높음
  확인 쿼리: SELECT indexname FROM pg_indexes WHERE tablename = 'stores';
  적용 예시: CREATE INDEX idx_stores_location ON stores USING GIST(location);
- p95 < 500ms → 인덱스 정상 적용 상태
======================================================
`,
  };
}
