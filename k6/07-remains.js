/**
 * [단일 API] 잔여석 조회 부하테스트
 * 실행: .\k6\run.ps1 -Test 07 -BaseUrl https://api.catcheat.kro.kr
 *
 * 목적:
 *   - GET /remains?storeId={id}&date={date} 단일 API 집중 부하
 *   - 예약 전 반드시 호출되는 API → 실제 트래픽에서 가장 빈번하게 호출됨
 *   - (store_id, remain_date) 복합 인덱스 효과 검증
 *
 * 기대 결과:
 *   - p95 < 200ms (인덱스 정상 활용 시, 단순 조회 쿼리)
 *   - 300VU에서도 안정적으로 처리 가능해야 함
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON } from './config.js';

const remainsDuration = new Trend('remains_duration', true);
const errorRate       = new Rate('remains_error_rate');

// 테스트할 매장 ID 목록 — 실제 DB에 존재하는 ID로 교체
const STORE_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

export const options = {
  scenarios: {
    // 최대 50VU
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },  // 워밍업
        { duration: '30s', target: 30 },
        { duration: '1m',  target: 50 },  // 최대 50VU
        { duration: '1m',  target: 50 },  // 유지
        { duration: '30s', target: 0  },  // 종료
      ],
    },
  },
  thresholds: {
    remains_duration: ['p(95)<300', 'p(99)<500'],  // 단순 인덱스 조회라 300ms 기준
    remains_error_rate: ['rate<0.01'],
    http_req_failed:    ['rate<0.01'],
  },
};

export default function () {
  const storeId = STORE_IDS[Math.floor(Math.random() * STORE_IDS.length)];

  // 오늘 포함 향후 7일 중 랜덤 날짜 — 캐시 히트 방지 + 실제 사용 패턴 재현
  const today     = new Date();
  const dayOffset = Math.floor(Math.random() * 7); // 0~6일 후
  today.setDate(today.getDate() + dayOffset);
  const date = today.toISOString().split('T')[0];

  group('잔여석 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/remains?storeId=${storeId}&date=${date}`,
      { headers: HEADERS_JSON },
    );

    remainsDuration.add(res.timings.duration);

    const ok = check(res, {
      '잔여석 200': (r) => r.status === 200,
      '응답 200ms 이내': (r) => r.timings.duration < 200,
    });
    errorRate.add(!ok);
  });

  sleep(0.5); // 잔여석은 짧은 sleep — 실제 클릭 패턴 반영
}

export function handleSummary(data) {
  const p50  = data.metrics.remains_duration?.values?.['p(50)'] || 0;
  const p95  = data.metrics.remains_duration?.values?.['p(95)'] || 0;
  const p99  = data.metrics.remains_duration?.values?.['p(99)'] || 0;
  const max  = data.metrics.remains_duration?.values?.max       || 0;
  const reqs = data.metrics.http_reqs?.values?.count            || 0;
  const rps  = data.metrics.http_reqs?.values?.rate             || 0;

  return {
    stdout: `
===== 잔여석 조회 단일 API 부하테스트 결과 =====
총 요청수              : ${reqs}
최대 RPS               : ${rps.toFixed(1)} req/s

p50 응답시간           : ${p50.toFixed(0)}ms
p95 응답시간           : ${p95.toFixed(0)}ms
p99 응답시간           : ${p99.toFixed(0)}ms
최대 응답시간          : ${max.toFixed(0)}ms

[분석]
- p95 < 200ms → 정상 (인덱스 활용 중)
- p95 200~500ms → 쿼리 실행계획 확인 권장
  EXPLAIN ANALYZE SELECT * FROM store_remains WHERE store_id=? AND remain_date=?;
- p95 > 500ms → (store_id, remain_date) 복합 인덱스 누락 가능성
================================================
`,
  };
}
