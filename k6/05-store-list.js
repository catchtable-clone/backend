/**
 * [단일 API] 매장 목록 조회 부하테스트 — 이벤트 스파이크 vs 점진적 증가
 * 실행: .\k6\run.ps1 -Test 05
 *
 * 전체 소요 시간: 약 9분
 *   [0s ~ ~2m50s] event_spike — 워밍업 후 50명 즉시 점프
 *   [3m20s ~ ~9m] ramp_up    — 0→50명 단계적 증가
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import exec from 'k6/execution';
import { Trend, Rate } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON } from './config.js';

const listDuration    = new Trend('store_list_duration', true);
const popularDuration = new Trend('store_popular_duration', true);
const spikeDuration   = new Trend('list_spike_duration', true);
const rampDuration    = new Trend('list_ramp_duration', true);
const errorRate       = new Rate('store_list_error_rate');

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
    //   인덱스가 제대로 걸려 있으면 점프 직후에도 p95가 크게 안 올라야 함
    //   급등하면 → DB 커넥션풀 부족 또는 Specification 쿼리 Full Scan
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
    // 목적: 몇 명부터 store_list_duration p95가 800ms를 넘는지 임계점 탐색
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
    'store_list_duration':    ['p(95)<800'],
    'store_popular_duration': ['p(95)<500'],
    'list_spike_duration':    ['p(95)<1200'],
    'list_ramp_duration':     ['p(95)<800'],
    'store_list_error_rate':  ['rate<0.01'],
    'http_req_failed':        ['rate<0.01'],
  },
};

const CATEGORIES = ['KOREAN', 'JAPANESE', 'CHINESE', 'WESTERN', 'CAFE'];
const DISTRICTS  = ['강남구', '마포구', '종로구', '용산구', '성동구'];

export default function () {
  const isSpike        = exec.scenario.name === 'event_spike';
  const randomCategory = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
  const randomDistrict = DISTRICTS[Math.floor(Math.random() * DISTRICTS.length)];
  const randomPage     = Math.floor(Math.random() * 5);
  const useFilter      = Math.random() > 0.5;

  function record(duration) {
    isSpike ? spikeDuration.add(duration) : rampDuration.add(duration);
  }

  group('매장 목록 조회 (필터 조합)', () => {
    let url = `${BASE_URL}/api/v1/stores?page=${randomPage}&size=10`;
    if (useFilter) {
      if (Math.random() > 0.5) url += `&category=${randomCategory}`;
      if (Math.random() > 0.5) url += `&district=${randomDistrict}`;
    }
    const res = http.get(url, { headers: HEADERS_JSON });
    listDuration.add(res.timings.duration);
    record(res.timings.duration);
    const ok = check(res, {
      '목록 조회 200': (r) => r.status === 200,
      '응답 body 존재': (r) => r.body && r.body.length > 0,
    });
    errorRate.add(!ok);
  });

  group('인기 매장 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/popular?limit=10`, { headers: HEADERS_JSON });
    popularDuration.add(res.timings.duration);
    record(res.timings.duration);
    check(res, { '인기 매장 200': (r) => r.status === 200 });
  });

  sleep(1);
}

export function handleSummary(data) {
  const s_p95      = data.metrics.list_spike_duration?.values?.['p(95)']       || 0;
  const s_p99      = data.metrics.list_spike_duration?.values?.['p(99)']       || 0;
  const r_p95      = data.metrics.list_ramp_duration?.values?.['p(95)']        || 0;
  const r_p99      = data.metrics.list_ramp_duration?.values?.['p(99)']        || 0;
  const p95List    = data.metrics.store_list_duration?.values?.['p(95)']       || 0;
  const p99List    = data.metrics.store_list_duration?.values?.['p(99)']       || 0;
  const p95Popular = data.metrics.store_popular_duration?.values?.['p(95)']    || 0;
  const totalReqs  = data.metrics.http_reqs?.values?.count                     || 0;
  const errorR     = data.metrics.store_list_error_rate?.values?.rate          || 0;

  return {
    stdout: `
===== 매장 목록 단일 API 부하테스트 결과 =====
총 요청수              : ${totalReqs.toLocaleString()}건
에러율                 : ${(errorR * 100).toFixed(2)}%

[SCENARIO A] 이벤트 스파이크 (워밍업 10명 → 50명 점프)
  p95 : ${s_p95.toFixed(0)}ms  /  p99 : ${s_p99.toFixed(0)}ms

[SCENARIO B] 점진적 증가 (0→50명)
  p95 : ${r_p95.toFixed(0)}ms  /  p99 : ${r_p99.toFixed(0)}ms

[API별 p95]
  GET /stores (필터 조합) : ${p95List.toFixed(0)}ms  (기준: 800ms)
  GET /stores (p99)       : ${p99List.toFixed(0)}ms
  GET /stores/popular     : ${p95Popular.toFixed(0)}ms  (기준: 500ms)

[분석]
  p95 > 800ms → popularity 정렬 컬럼 복합 인덱스 검토
  p95 > 1s    → Specification 동적 쿼리 실행계획 확인: EXPLAIN ANALYZE
==============================================
`,
  };
}
