/**
 * [단일 API] 매장 목록 조회 부하테스트
 * 실행: .\k6\run.ps1 -Test 05 -BaseUrl https://api.catcheat.kro.kr
 *
 * 목적:
 *   - GET /stores (필터 조합) 단일 API에 집중 부하
 *   - Specification 기반 동적 필터링 + 페이지네이션 성능 확인
 *   - GET /stores/popular (인기 매장 Top10) 함께 측정
 *
 * 기대 결과:
 *   - p95 < 500ms (인덱스 정상 활용 시)
 *   - p95 > 1s 이면 popularity 정렬 컬럼 인덱스 검토 필요
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON } from './config.js';

const listDuration    = new Trend('store_list_duration', true);
const popularDuration = new Trend('store_popular_duration', true);
const errorRate       = new Rate('store_list_error_rate');

// 테스트할 카테고리 필터 목록 — 실제 DB에 있는 값으로 교체
const CATEGORIES = ['KOREAN', 'JAPANESE', 'CHINESE', 'WESTERN', 'CAFE'];
// 테스트할 지역(district) 필터 목록
const DISTRICTS = ['강남구', '마포구', '종로구', '용산구', '성동구'];

export const options = {
  scenarios: {
    // 1단계: 워밍업 (JVM JIT, DB 커넥션풀 안정화)
    warmup: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
      tags: { phase: 'warmup' },
    },
    // 2단계: 단계적 부하 증가 → 50VU까지
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 5,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '30s', target: 35 },
        { duration: '1m',  target: 50 },
        { duration: '1m',  target: 50 }, // 50VU 유지
        { duration: '30s', target: 0  },
      ],
      startTime: '30s',
      tags: { phase: 'ramp_up' },
    },
  },
  thresholds: {
    store_list_duration:    ['p(95)<800'],   // 목록 조회 p95 800ms 기준
    store_popular_duration: ['p(95)<500'],   // 인기 매장은 단순 정렬이라 500ms 기준
    store_list_error_rate:  ['rate<0.01'],   // 에러율 1% 미만
    http_req_failed:        ['rate<0.01'],
  },
};

export default function () {
  // 랜덤 필터 조합 — 실제 사용자가 다양한 필터로 조회하는 상황 재현
  const randomCategory = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
  const randomDistrict = DISTRICTS[Math.floor(Math.random() * DISTRICTS.length)];
  const randomPage     = Math.floor(Math.random() * 5); // 0~4 페이지
  const useFilter      = Math.random() > 0.5;           // 50% 확률로 필터 적용

  group('매장 목록 조회 (필터 조합)', () => {
    let url = `${BASE_URL}/api/v1/stores?page=${randomPage}&size=10`;
    if (useFilter) {
      // 필터 조합을 랜덤하게 — 카테고리만, 지역만, 둘 다 섞어서 테스트
      if (Math.random() > 0.5) url += `&category=${randomCategory}`;
      if (Math.random() > 0.5) url += `&district=${randomDistrict}`;
    }

    const res = http.get(url, { headers: HEADERS_JSON });
    listDuration.add(res.timings.duration);

    const ok = check(res, {
      '목록 조회 200': (r) => r.status === 200,
      '응답 body 존재': (r) => r.body && r.body.length > 0,
    });
    errorRate.add(!ok);
  });

  group('인기 매장 조회', () => {
    const res = http.get(`${BASE_URL}/api/v1/stores/popular?limit=10`, { headers: HEADERS_JSON });
    popularDuration.add(res.timings.duration);

    check(res, {
      '인기 매장 200': (r) => r.status === 200,
    });
  });

  sleep(1);
}

export function handleSummary(data) {
  const p95List    = data.metrics.store_list_duration?.values?.['p(95)']    || 0;
  const p99List    = data.metrics.store_list_duration?.values?.['p(99)']    || 0;
  const p95Popular = data.metrics.store_popular_duration?.values?.['p(95)'] || 0;
  const totalReqs  = data.metrics.http_reqs?.values?.count                  || 0;
  const errorR     = data.metrics.store_list_error_rate?.values?.rate       || 0;

  return {
    stdout: `
===== 매장 목록 조회 단일 API 부하테스트 결과 =====
총 요청수              : ${totalReqs}
에러율                 : ${(errorR * 100).toFixed(2)}%

[GET /stores]
p95 응답시간           : ${p95List.toFixed(0)}ms
p99 응답시간           : ${p99List.toFixed(0)}ms

[GET /stores/popular]
p95 응답시간           : ${p95Popular.toFixed(0)}ms

[분석]
- p95 > 800ms → popularity 정렬 컬럼(star, reviewCount, bookmarkCount)에 복합 인덱스 검토
- p95 > 1s   → Specification 동적 쿼리 실행계획 확인 (EXPLAIN ANALYZE)
=================================================
`,
  };
}
