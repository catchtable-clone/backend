/**
 * [단일 API] 매장 목록 조회 부하테스트 — 이벤트 스파이크 vs 점진적 증가
 * 실행: .\k6\run.ps1 -Test 05
 *
 * 전체 소요 시간: 약 5분
 *   [0s ~ ~2m50s] event_spike — 워밍업 후 50명 즉시 점프
 *   [3m ~ ~5m]    ramp_up     — 0→50명 단축 단계 (10→30→50, 임계점 1차 탐색)
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
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {

    // SCENARIO A: 이벤트 스파이크 — 50명 즉시 점프로 이벤트 오픈 순간 재현 (패턴 근거는 01-store-browse.js 헤더)
    // 체크: 점프 직후 p95 급등 = DB 커넥션풀 부족 또는 Specification 쿼리 Full Scan
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

    // SCENARIO B: 점진적 증가 — 임계점 1차 탐색 (몇 명부터 store_list_duration p95가 800ms를 넘는가)
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
    'store_list_duration':    ['p(95)<1200'],
    'store_popular_duration': ['p(95)<800'],
    'list_spike_duration':    ['p(95)<1500'],
    'list_ramp_duration':     ['p(95)<1200'],
    'store_list_error_rate':  ['rate<0.01'],
    'http_req_failed':        ['rate<0.01'],
  },
};

const CATEGORIES = ['KOREAN', 'JAPANESE', 'CHINESE', 'WESTERN', 'CAFE'];
const DISTRICTS  = ['GANGNAM', 'MAPO', 'JONGNO', 'YONGSAN', 'SEONGDONG'];

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
      if (Math.random() > 0.5) url += `&district=${encodeURIComponent(randomDistrict)}`;
    }
    const res = http.get(url, { headers: HEADERS_JSON });
    listDuration.add(res.timings.duration);
    record(res.timings.duration);
    const ok = check(res, {
      '목록 조회 200': (r) => r.status === 200,
      '응답 body 존재': (r) => r.body && r.body.length > 0,
    });
    if (!ok) console.log(`[ERROR] status=${res.status} url=${url} body=${res.body?.substring(0, 200)}`);
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
