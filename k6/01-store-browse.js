/**
 * 매장 조회 부하테스트 — 이벤트 스파이크 vs 점진적 증가
 * 실행: .\k6\run.ps1 -Test 01
 *
 * 테스트 항목:
 *   - GET /stores          (목록 + 필터)
 *   - GET /stores/popular  (인기 매장)
 *   - GET /stores/nearby   (PostGIS 거리 계산)
 *   - GET /stores/in-bounds (지도 영역 조회)
 *   - GET /stores/{id}     (상세)
 *   - GET /remains         (시간대별 좌석)
 *
 * 전체 소요 시간: 약 5분
 *   [0s ~ ~2m50s] event_spike — 워밍업 후 50명 즉시 점프
 *   [3m ~ ~5m]    ramp_up     — 0→50명 단축 단계 (10→30→50, 임계점 1차 탐색)
 *
 * [두 시나리오 비교 포인트]
 *   - nearby / in-bounds: PostGIS 쿼리는 GIST 인덱스가 없으면 spike 구간에서 타임아웃
 *   - 인기 매장 조회: Redis 캐시 히트율이 높으면 두 시나리오 모두 빠름 (차이 없음)
 *   - event_spike 점프 직후(30~35s) p95가 급등하면 → 서버가 스파이크를 못 버팀
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import exec from 'k6/execution';
import { Trend, Rate } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON, THRESHOLDS } from './config.js';

const nearbyDuration   = new Trend('browse_nearby_duration', true);
const inBoundsDuration = new Trend('browse_in_bounds_duration', true);

// 시나리오별 메트릭 — event_spike vs ramp_up 응답시간 분포 비교
const spikeDuration = new Trend('browse_spike_duration', true);
const rampDuration  = new Trend('browse_ramp_duration', true);

const errorRate = new Rate('browse_error_rate');

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
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
    // [단계별 의미]
    //   30s: 10명 유지 → 서버가 이미 운영 중인 상태 재현 (JIT 워밍업, 커넥션풀 확보)
    //    5s: 10→50명 점프 → 이벤트 오픈 순간 재현
    //    2m: 50명 유지 → 피크 타임 지속 상황에서의 안정성 확인
    //
    // [체크포인트]
    //   점프 직후(30~35s) 구간 p95가 급등하는지 Grafana에서 확인
    //   → 급등하면 서버가 순간 스파이크를 못 버팀 (커넥션풀 또는 스레드풀 부족)
    event_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },  // 워밍업: 평상시 트래픽 재현
        { duration: '5s',  target: 50 },  // 이벤트 오픈: 5초 안에 50명으로 점프
        { duration: '2m',  target: 50 },  // 피크 유지
        { duration: '15s', target: 0  },  // 종료
      ],
      tags: { scenario: 'event_spike' },
    },

    // ── SCENARIO B: 점진적 증가 ────────────────────────────────────────────
    // 목적: 몇 명부터 응답시간이 꺾이는지 임계점 탐색
    // event_spike와 달리 서버가 단계적으로 적응하면서 50명에 도달
    // 체크: 10→30→50 각 단계에서 nearby p95 변화량 확인
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '3m',
      stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 30 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 0  },
      ],
      tags: { scenario: 'ramp_up' },
    },
  },
  thresholds: {
    ...THRESHOLDS,
    'browse_nearby_duration':    ['p(95)<2000'],
    'browse_in_bounds_duration': ['p(95)<2000'],
    'browse_spike_duration':     ['p(95)<1500'],
    'browse_ramp_duration':      ['p(95)<1000'],
    'browse_error_rate':         ['rate<0.01'],
  },
};

const SEOUL     = { lat: 37.5665, lng: 126.9780 };
const STORE_IDS = [1, 2, 3, 4, 5];

export default function () {
  const isSpike = exec.scenario.name === 'event_spike';
  const storeId = STORE_IDS[Math.floor(Math.random() * STORE_IDS.length)];
  // +9h: 서버가 UTC로 실행될 경우 자정~오전9시 사이에 날짜가 하루 틀어지는 것 방지
  const today = new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString().split('T')[0];

  function record(duration) {
    isSpike ? spikeDuration.add(duration) : rampDuration.add(duration);
  }

  group('매장 목록 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores?page=0&size=10`, { headers: HEADERS_JSON });
    const ok  = check(res, { '목록 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    record(res.timings.duration);
  });

  group('인기 매장 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/popular?limit=10`, { headers: HEADERS_JSON });
    const ok  = check(res, { '인기 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    record(res.timings.duration);
  });

  group('근처 매장 조회 (PostGIS nearby)', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/stores/nearby?latitude=${SEOUL.lat}&longitude=${SEOUL.lng}&page=0&size=10`,
      { headers: HEADERS_JSON },
    );
    const ok = check(res, { '근처 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    nearbyDuration.add(res.timings.duration);
    record(res.timings.duration);
  });

  group('지도 영역 매장 조회 (in-bounds)', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/stores/in-bounds` +
        `?minLat=37.49&maxLat=37.54&minLng=127.01&maxLng=127.07` +
        `&centerLat=${SEOUL.lat}&centerLng=${SEOUL.lng}&limit=100`,
      { headers: HEADERS_JSON },
    );
    const ok = check(res, { 'in-bounds 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    inBoundsDuration.add(res.timings.duration);
    record(res.timings.duration);
  });

  group('매장 상세 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/${storeId}`, { headers: HEADERS_JSON });
    const ok  = check(res, { '상세 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    record(res.timings.duration);
  });

  group('시간대별 좌석 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/remains?storeId=${storeId}&date=${today}`,
      { headers: HEADERS_JSON },
    );
    const ok = check(res, { '좌석 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    record(res.timings.duration);
  });

  sleep(1);
}

export function handleSummary(data) {
  const s_p95     = data.metrics.browse_spike_duration?.values?.['p(95)']     || 0;
  const s_p99     = data.metrics.browse_spike_duration?.values?.['p(99)']     || 0;
  const r_p95     = data.metrics.browse_ramp_duration?.values?.['p(95)']      || 0;
  const r_p99     = data.metrics.browse_ramp_duration?.values?.['p(99)']      || 0;
  const n_p95     = data.metrics.browse_nearby_duration?.values?.['p(95)']    || 0;
  const b_p95     = data.metrics.browse_in_bounds_duration?.values?.['p(95)'] || 0;
  const totalReqs = data.metrics.http_reqs?.values?.count                     || 0;
  const errorR    = data.metrics.browse_error_rate?.values?.rate              || 0;

  const diff      = s_p95 - r_p95;
  const spikeNote = diff > 200
    ? `⚠  스파이크 구간이 ${diff.toFixed(0)}ms 더 느림 → 커넥션풀/스레드풀 점검 필요`
    : `✓  스파이크 안정적 처리. 차이 ${diff.toFixed(0)}ms`;

  return {
    stdout: `
===== 매장 조회 부하테스트 결과 — 시나리오 비교 =====
총 요청수              : ${totalReqs.toLocaleString()}건
에러율                 : ${(errorR * 100).toFixed(3)}%

[SCENARIO A] 이벤트 스파이크 (워밍업 10명 → 50명 점프)
  p95 : ${s_p95.toFixed(0)}ms  /  p99 : ${s_p99.toFixed(0)}ms

[SCENARIO B] 점진적 증가 (0→50명)
  p95 : ${r_p95.toFixed(0)}ms  /  p99 : ${r_p99.toFixed(0)}ms

[스파이크 분석] ${spikeNote}

[API별 p95]
  nearby (PostGIS)     : ${n_p95.toFixed(0)}ms  (기준: 2000ms)
  in-bounds (PostGIS)  : ${b_p95.toFixed(0)}ms  (기준: 2000ms)
====================================================
`,
  };
}
