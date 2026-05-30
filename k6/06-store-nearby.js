/**
 * [단일 API] PostGIS 지리 쿼리 부하테스트 — 이벤트 스파이크 vs 점진적 증가
 * 실행: .\k6\run.ps1 -Test 06
 *
 * 이전 테스트 결과: 50VU에서 nearby p95=10.33s, in-bounds p95=8.36s
 * → GIST 공간 인덱스 미적용 의심 → 이 테스트로 수치 재확인
 *
 * 전체 소요 시간: 약 9분
 *   [0s ~ ~2m50s] event_spike — 워밍업 후 50명 즉시 점프
 *   [3m20s ~ ~9m] ramp_up    — 0→50명 단계적 증가
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import exec from 'k6/execution';
import { Trend, Rate, Counter } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON } from './config.js';

const nearbyDuration   = new Trend('nearby_duration', true);
const inBoundsDuration = new Trend('in_bounds_duration', true);
const spikeDuration    = new Trend('geo_spike_duration', true);
const rampDuration     = new Trend('geo_ramp_duration', true);
const timeoutCount     = new Counter('geo_timeout_count');
const errorRate        = new Rate('geo_error_rate');

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

    // ── SCENARIO A: 이벤트 스파이크 ────────────────────────────────────────
    // 실제 상황: 오전 10시 이벤트 오픈, 좌석 공개처럼 특정 시각에 트래픽이 몰리는 상황
    //
    // [워밍업을 추가한 이유]
    // 실제 서비스에서 이벤트가 오픈되는 시점의 서버는 이미 운영 중인 상태.
    // JVM JIT 컴파일과 DB 커넥션풀이 확보된 상태에서 갑자기 50명이 몰리는 것이 현실적.
    // 워밍업 없이 바로 50명을 붙이면 JVM 콜드 스타트 비용이 포함되어
    // 실제 이벤트 상황의 성능과 다른 결과가 나옴.
    //
    // [ramping-vus를 쓴 이유]
    // warmup(10명) + constant(50명)을 별도 시나리오로 돌리면 동시 실행되어
    // 실제로는 10 + 50 = 60명이 되는 문제가 생김.
    // ramping-vus 하나로 합쳐야 정확히 50명으로 제어 가능.
    //
    // [체크포인트]
    //   GIST 인덱스 없으면 점프 직후 타임아웃 폭발 → timeoutCount 급증
    //   GIST 인덱스 있으면 50명에서도 p95 < 2s 유지
    event_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },  // 워밍업: 평상시 트래픽 재현
        { duration: '5s',  target: 50 },  // 이벤트 오픈: 5초 안에 50명으로 점프
        { duration: '2m',  target: 50 },  // 피크 유지
        { duration: '15s', target: 0  },
      ],
      tags: { scenario: 'event_spike' },
    },

    // ── SCENARIO B: 점진적 증가 ────────────────────────────────────────────
    // 목적: 몇 명부터 타임아웃이 발생하는지 → 인덱스 없는 서버의 한계 탐색
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '3m20s',
      stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 20 },
        { duration: '30s', target: 35 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 0  },
      ],
      tags: { scenario: 'ramp_up' },
    },
  },
  thresholds: {
    'nearby_duration':    ['p(95)<2000'],
    'in_bounds_duration': ['p(95)<2000'],
    'geo_spike_duration': ['p(95)<3000'],
    'geo_ramp_duration':  ['p(95)<2000'],
    'geo_error_rate':     ['rate<0.05'],
    'http_req_failed':    ['rate<0.05'],
  },
};

export default function () {
  const isSpike = exec.scenario.name === 'event_spike';
  const loc     = LOCATIONS[Math.floor(Math.random() * LOCATIONS.length)];
  const lat     = (loc.lat + (Math.random() - 0.5) * 0.005).toFixed(6);
  const lng     = (loc.lng + (Math.random() - 0.5) * 0.005).toFixed(6);

  function record(duration) {
    isSpike ? spikeDuration.add(duration) : rampDuration.add(duration);
  }

  group(`근처 매장 조회 (nearby) - ${loc.name}`, () => {
    const res = http.get(
      `${BASE_URL}/api/v1/stores/nearby?latitude=${lat}&longitude=${lng}&page=0&size=10`,
      { headers: HEADERS_JSON, timeout: '15s' },
    );
    nearbyDuration.add(res.timings.duration);
    record(res.timings.duration);
    if (res.timings.duration >= 15000 || res.status === 0) timeoutCount.add(1);
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
    record(res.timings.duration);
    if (res.timings.duration >= 15000 || res.status === 0) timeoutCount.add(1);
    check(res, {
      'in-bounds 200': (r) => r.status === 200,
      'in-bounds 15s 이내': (r) => r.timings.duration < 15000,
    });
  });

  sleep(1);
}

export function handleSummary(data) {
  const s_p95     = data.metrics.geo_spike_duration?.values?.['p(95)']  || 0;
  const s_p99     = data.metrics.geo_spike_duration?.values?.['p(99)']  || 0;
  const r_p95     = data.metrics.geo_ramp_duration?.values?.['p(95)']   || 0;
  const r_p99     = data.metrics.geo_ramp_duration?.values?.['p(99)']   || 0;
  const p95Nearby = data.metrics.nearby_duration?.values?.['p(95)']     || 0;
  const p95Bounds = data.metrics.in_bounds_duration?.values?.['p(95)']  || 0;
  const timeouts  = data.metrics.geo_timeout_count?.values?.count       || 0;
  const totalReqs = data.metrics.http_reqs?.values?.count               || 0;

  const indexStatus = timeouts > 0
    ? `⚠  타임아웃 ${timeouts}건 → GIST 인덱스 미적용 가능성`
    : `✓  타임아웃 0건 → GIST 인덱스 정상`;

  return {
    stdout: `
===== PostGIS 지리 쿼리 부하테스트 결과 =====
총 요청수              : ${totalReqs.toLocaleString()}건
타임아웃 횟수          : ${timeouts}건

[SCENARIO A] 이벤트 스파이크 (워밍업 10명 → 50명 점프)
  p95 : ${s_p95.toFixed(0)}ms  /  p99 : ${s_p99.toFixed(0)}ms

[SCENARIO B] 점진적 증가 (0→50명)
  p95 : ${r_p95.toFixed(0)}ms  /  p99 : ${r_p99.toFixed(0)}ms

[API별 p95]
  GET /stores/nearby    : ${p95Nearby.toFixed(0)}ms  (기준: 2000ms)
  GET /stores/in-bounds : ${p95Bounds.toFixed(0)}ms  (기준: 2000ms)

[GIST 인덱스 진단] ${indexStatus}
=============================================
`,
  };
}
