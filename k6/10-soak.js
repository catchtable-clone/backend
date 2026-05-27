/**
 * [내구성] Soak 테스트 (장시간 안정성)
 * 실행: .\k6\run.ps1 -Test 10 -AuthToken "eyJ..." -BaseUrl https://api.catcheat.kro.kr
 *
 * 목적:
 *   - 낮은 VU(10명)를 30분간 유지하면서 서버 안정성 확인
 *   - 스파이크가 아닌 지속 트래픽에서의 문제 탐지:
 *       1. 메모리 누수 (JVM 힙 점진적 증가)
 *       2. DB 커넥션풀 고갈 (HikariCP activeConnections 포화)
 *       3. Redis 커넥션 누수 (Lettuce 커넥션 증가)
 *       4. 응답시간 점진적 증가 (시간이 갈수록 느려지면 누수 의심)
 *
 * 모니터링 포인트 (Grafana에서 확인):
 *   - jvm_memory_used_bytes (Heap 사용량 우상향 여부)
 *   - hikaricp_connections_active (커넥션 포화 여부)
 *   - http_req_duration p95 추이 (시간에 따른 변화)
 *
 * 기대 결과:
 *   - 30분 내내 p95 < 1s 유지
 *   - 에러율 0% 유지
 *   - JVM 힙 사용량 안정적 (GC 후 회복)
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON, HEADERS_AUTH } from './config.js';

const soakDuration = new Trend('soak_response_duration', true);
const errorRate    = new Rate('soak_error_rate');
// 시간대별 에러 추적 — 후반부에 에러가 늘면 누수 의심
const lateErrors   = new Counter('soak_late_errors');

// 테스트할 매장 ID 목록
const STORE_IDS = [1, 2, 3, 4, 5];

// 테스트 시작 시간 (후반부 에러 감지용)
const START_TIME = Date.now();
// 20분 이후를 "후반부"로 정의
const LATE_PHASE_MS = 20 * 60 * 1000;

export const options = {
  scenarios: {
    // 3분 워밍업 후 10VU 유지 → 30분 장시간 운영
    soak: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '3m',  target: 10 },  // 천천히 올려서 안정화
        { duration: '25m', target: 10 },  // 25분 유지 (핵심 구간)
        { duration: '2m',  target: 0  },  // 점진적 종료
      ],
    },
  },
  thresholds: {
    soak_response_duration: ['p(95)<1000', 'p(99)<2000'],  // 장시간에도 1s 유지
    soak_error_rate:        ['rate<0.005'],                 // 에러율 0.5% 미만
    http_req_failed:        ['rate<0.005'],
  },
};

export default function () {
  const storeId = STORE_IDS[Math.floor(Math.random() * STORE_IDS.length)];
  const today   = new Date().toISOString().split('T')[0];
  const isLatePhase = (Date.now() - START_TIME) > LATE_PHASE_MS;

  // 일반 사용자 행동 믹스 — 읽기 위주로 구성 (쓰기는 10% 확률)
  // 읽기 비율 높게: 실제 서비스에서 조회가 훨씬 많음
  const rand = Math.random();

  if (rand < 0.30) {
    // 30%: 매장 목록 조회
    group('매장 목록', () => {
      const res = http.get(`${BASE_URL}/api/v1/stores?page=0&size=10`, { headers: HEADERS_JSON });
      soakDuration.add(res.timings.duration);
      const ok = check(res, { '목록 200': (r) => r.status === 200 });
      errorRate.add(!ok);
      if (!ok && isLatePhase) lateErrors.add(1); // 후반부 에러 별도 카운트
    });
  } else if (rand < 0.55) {
    // 25%: 인기 매장 조회
    group('인기 매장', () => {
      const res = http.get(`${BASE_URL}/api/v1/stores/popular?limit=10`, { headers: HEADERS_JSON });
      soakDuration.add(res.timings.duration);
      const ok = check(res, { '인기 200': (r) => r.status === 200 });
      errorRate.add(!ok);
      if (!ok && isLatePhase) lateErrors.add(1);
    });
  } else if (rand < 0.75) {
    // 20%: 매장 상세 조회
    group('매장 상세', () => {
      const res = http.get(`${BASE_URL}/api/v1/stores/${storeId}`, { headers: HEADERS_JSON });
      soakDuration.add(res.timings.duration);
      const ok = check(res, { '상세 200': (r) => r.status === 200 });
      errorRate.add(!ok);
      if (!ok && isLatePhase) lateErrors.add(1);
    });
  } else if (rand < 0.90) {
    // 15%: 잔여석 조회 (예약 전 가장 많이 호출)
    group('잔여석 조회', () => {
      const res = http.get(
        `${BASE_URL}/api/v1/remains?storeId=${storeId}&date=${today}`,
        { headers: HEADERS_JSON },
      );
      soakDuration.add(res.timings.duration);
      const ok = check(res, { '잔여석 200': (r) => r.status === 200 });
      errorRate.add(!ok);
      if (!ok && isLatePhase) lateErrors.add(1);
    });
  } else {
    // 10%: 인증이 필요한 API (내 예약 조회)
    // AUTH_TOKEN이 없으면 401이 반환되지만 서버 부하는 발생함
    group('내 예약 조회 (인증)', () => {
      const res = http.get(`${BASE_URL}/api/v1/reservations/me`, { headers: HEADERS_AUTH });
      soakDuration.add(res.timings.duration);
      // 401은 토큰 미설정 시 정상 — 500만 에러로 처리
      const ok = check(res, { '인증 API 5xx 없음': (r) => r.status < 500 });
      errorRate.add(!ok);
      if (!ok && isLatePhase) lateErrors.add(1);
    });
  }

  // 실제 사용자 행동 패턴 — 페이지 사이 2~5초 대기
  sleep(2 + Math.random() * 3);
}

export function handleSummary(data) {
  const p50       = data.metrics.soak_response_duration?.values?.['p(50)'] || 0;
  const p95       = data.metrics.soak_response_duration?.values?.['p(95)'] || 0;
  const p99       = data.metrics.soak_response_duration?.values?.['p(99)'] || 0;
  const max       = data.metrics.soak_response_duration?.values?.max       || 0;
  const errR      = data.metrics.soak_error_rate?.values?.rate             || 0;
  const lateErr   = data.metrics.soak_late_errors?.values?.count           || 0;
  const totalReqs = data.metrics.http_reqs?.values?.count                  || 0;
  const duration  = data.metrics.http_req_duration?.values?.avg            || 0;

  return {
    stdout: `
===== Soak 테스트 (장시간 안정성) 결과 =====
총 요청수              : ${totalReqs}
에러율                 : ${(errR * 100).toFixed(3)}%
후반부(20분~) 에러     : ${lateErr}건

p50 응답시간           : ${p50.toFixed(0)}ms
p95 응답시간           : ${p95.toFixed(0)}ms
p99 응답시간           : ${p99.toFixed(0)}ms
최대 응답시간          : ${max.toFixed(0)}ms
평균 응답시간          : ${duration.toFixed(0)}ms

[분석]
- 후반부 에러 > 0 → 메모리 누수 또는 커넥션풀 고갈 의심
  Grafana에서 확인:
    1. jvm_memory_used_bytes{area="heap"} → 우상향이면 힙 누수
    2. hikaricp_connections_active → 포화 여부
    3. http_req_duration p95 추이 → 시간에 따라 증가하면 누수

- p95 처음과 끝이 크게 다르면 → 워밍업 후 응답시간 변화 원인 분석 필요
- 에러율 0%, p95 일정 → 안정적인 서버 상태
===========================================
`,
  };
}
