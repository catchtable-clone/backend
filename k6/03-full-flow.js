/**
 * 실제 사용자 플로우 부하테스트 — 이벤트 스파이크 vs 점진적 증가
 * 실행: .\k6\run.ps1 -Test 03 -AuthToken "eyJ..."
 *
 * 시나리오: 홈 진입 → 매장 검색 → 상세 조회 → 메뉴/리뷰 확인 → 좌석 확인
 *
 * 전체 소요 시간: 약 9분
 *   [0s ~ ~2m50s] event_spike — 워밍업 후 50명 즉시 점프
 *   [3m20s ~ ~9m] ramp_up    — 0→50명 단계적 증가
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import exec from 'k6/execution';
import { Trend, Rate } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON, HEADERS_AUTH, AUTH_TOKEN, THRESHOLDS } from './config.js';

const flowDuration = new Trend('full_flow_duration', true);

// 시나리오별 메트릭
const spikeDuration = new Trend('flow_spike_duration', true);
const rampDuration  = new Trend('flow_ramp_duration', true);

// Step별 Trend — 어느 단계에서 병목이 발생하는지 파악
const stepDurations = {
  popular: new Trend('flow_step_popular', true),
  search:  new Trend('flow_step_search', true),
  mapView: new Trend('flow_step_map', true),
  detail:  new Trend('flow_step_detail', true),
  batch:   new Trend('flow_step_batch', true),
  remains: new Trend('flow_step_remains', true),
};

const errorRate = new Rate('flow_error_rate');

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
    //   점프 직후(30~35s) Step별 p95 중 어느 step이 먼저 터지는지 확인
    //   → 가장 먼저 급등하는 step = 이벤트 상황에서의 첫 번째 병목
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
    // 목적: 몇 명부터 어느 step이 먼저 나빠지는지 임계점 탐색
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
    ...THRESHOLDS,
    'full_flow_duration':  ['p(95)<5000'],
    'flow_spike_duration': ['p(95)<6000'],
    'flow_ramp_duration':  ['p(95)<5000'],
    'flow_step_detail':    ['p(95)<1000'],
    'flow_step_batch':     ['p(95)<1500'],
    'flow_step_remains':   ['p(95)<600'],
    'flow_error_rate':     ['rate<0.01'],
  },
};

const SEOUL      = { lat: 37.5665, lng: 126.9780 };
const STORE_IDS  = [1, 2, 3, 4, 5];
const CATEGORIES = ['KOREAN', 'JAPANESE', 'CHINESE', 'WESTERN', 'MEAT', 'DESSERT'];

export default function () {
  const isSpike = exec.scenario.name === 'event_spike';
  const start   = Date.now();
  const storeId = STORE_IDS[Math.floor(Math.random() * STORE_IDS.length)];
  // +9h: 서버가 UTC로 실행될 경우 자정~오전9시 사이에 날짜가 하루 틀어지는 것 방지
  const today = new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString().split('T')[0];

  function record(duration) {
    isSpike ? spikeDuration.add(duration) : rampDuration.add(duration);
  }

  group('1. 홈 - 인기 매장 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/popular?limit=10`, { headers: HEADERS_JSON });
    const ok  = check(res, { '인기 매장 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    stepDurations.popular.add(res.timings.duration);
    record(res.timings.duration);
    sleep(0.5);
  });

  group('2. 카테고리/지역 필터 검색', () => {
    const category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
    const res = http.get(
      `${BASE_URL}/api/v1/stores?category=${category}&page=0&size=10`,
      { headers: HEADERS_JSON },
    );
    const ok = check(res, { '필터 검색 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    stepDurations.search.add(res.timings.duration);
    record(res.timings.duration);
    sleep(0.5);
  });

  group('3. 지도 화면 - 영역 내 매장 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/stores/in-bounds` +
        `?minLat=37.49&maxLat=37.54&minLng=127.01&maxLng=127.07` +
        `&centerLat=${SEOUL.lat}&centerLng=${SEOUL.lng}&limit=100`,
      { headers: HEADERS_JSON },
    );
    const ok = check(res, { '지도 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    stepDurations.mapView.add(res.timings.duration);
    record(res.timings.duration);
    sleep(0.3);
  });

  group('4. 매장 상세 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/${storeId}`, { headers: HEADERS_JSON });
    const ok  = check(res, { '상세 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    stepDurations.detail.add(res.timings.duration);
    record(res.timings.duration);
    sleep(1);
  });

  group('5. 메뉴 + 리뷰 조회 (동시)', () => {
    const responses = http.batch([
      ['GET', `${BASE_URL}/api/v1/stores/${storeId}/menu`, null, { headers: HEADERS_JSON }],
      ['GET', `${BASE_URL}/api/v1/reviews/store/${storeId}`, null, { headers: HEADERS_JSON }],
    ]);
    const ok = check(responses[0], { '메뉴 200': (r) => r.status === 200 }) &&
               check(responses[1], { '리뷰 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    stepDurations.batch.add(Math.max(responses[0].timings.duration, responses[1].timings.duration));
    record(Math.max(responses[0].timings.duration, responses[1].timings.duration));
    sleep(0.5);
  });

  group('6. 예약 가능 시간 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/remains?storeId=${storeId}&date=${today}`,
      { headers: HEADERS_JSON },
    );
    const ok = check(res, { '좌석 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    stepDurations.remains.add(res.timings.duration);
    record(res.timings.duration);
    sleep(0.5);
  });

  // AUTH_TOKEN 있을 때만 로그인 step 실행 (config.js v1.4: 빈 토큰이면 Authorization 헤더 자체 제거)
  if (AUTH_TOKEN) {
    group('7. 북마크 폴더 조회 (로그인)', () => {
      const res = http.get(`${BASE_URL}/api/v1/bookmark-folders`, { headers: HEADERS_AUTH });
      const ok  = check(res, { '북마크 200': (r) => r.status === 200 });
      errorRate.add(!ok);
      record(res.timings.duration);
    });
  }

  flowDuration.add(Date.now() - start);
  sleep(1);
}

export function handleSummary(data) {
  const s_p95    = data.metrics.flow_spike_duration?.values?.['p(95)'] || 0;
  const s_p99    = data.metrics.flow_spike_duration?.values?.['p(99)'] || 0;
  const r_p95    = data.metrics.flow_ramp_duration?.values?.['p(95)']  || 0;
  const r_p99    = data.metrics.flow_ramp_duration?.values?.['p(99)']  || 0;
  const flow_p95 = data.metrics.full_flow_duration?.values?.['p(95)']  || 0;

  const steps = [
    { name: '1. 인기 매장', p95: data.metrics.flow_step_popular?.values?.['p(95)'] || 0 },
    { name: '2. 필터 검색', p95: data.metrics.flow_step_search?.values?.['p(95)']  || 0 },
    { name: '3. 지도 조회', p95: data.metrics.flow_step_map?.values?.['p(95)']     || 0 },
    { name: '4. 매장 상세', p95: data.metrics.flow_step_detail?.values?.['p(95)']  || 0 },
    { name: '5. 메뉴+리뷰', p95: data.metrics.flow_step_batch?.values?.['p(95)']   || 0 },
    { name: '6. 잔여석',    p95: data.metrics.flow_step_remains?.values?.['p(95)'] || 0 },
  ];
  const bottleneck = steps.reduce((a, b) => a.p95 > b.p95 ? a : b);
  const totalReqs  = data.metrics.http_reqs?.values?.count      || 0;
  const errorR     = data.metrics.flow_error_rate?.values?.rate || 0;

  return {
    stdout: `
===== 전체 사용자 플로우 부하테스트 결과 =====
총 요청수              : ${totalReqs.toLocaleString()}건
에러율                 : ${(errorR * 100).toFixed(3)}%
전체 플로우 p95        : ${flow_p95.toFixed(0)}ms

[SCENARIO A] 이벤트 스파이크 (워밍업 10명 → 50명 점프)
  p95 : ${s_p95.toFixed(0)}ms  /  p99 : ${s_p99.toFixed(0)}ms

[SCENARIO B] 점진적 증가 (0→50명)
  p95 : ${r_p95.toFixed(0)}ms  /  p99 : ${r_p99.toFixed(0)}ms

[Step별 p95]
${steps.map(s => `  ${s.name.padEnd(12)}: ${s.p95.toFixed(0)}ms`).join('\n')}

[병목 1순위] → ${bottleneck.name} (p95: ${bottleneck.p95.toFixed(0)}ms)
=============================================
`,
  };
}
