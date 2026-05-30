/**
 * [단일 API] 쿠폰 선착순 발급 동시성 부하테스트
 *
 * 실행:
 *   # 단일 토큰 (검증 의미 반감 — 첫 1건만 성공, 나머지는 "이미 발급" 에러)
 *   .\k6\run.ps1 -Test 08 -AuthToken "eyJ..." -CouponTemplateId 1
 *
 *   # 다중 토큰 (권장 — 비관적 락 정확히 검증)
 *   ./k6/run.sh -t 08 -c 1 -T build/tokens-1000.csv
 *
 * 목적:
 *   - POST /coupons/{templateId}/issue 에 100명 동시 요청
 *   - 비관적 락(SELECT FOR UPDATE)으로 초과 발급 방지 검증
 *   - 재고 소진 후 올바른 에러 응답 확인
 *
 * 기대 결과 (다중 토큰 기준):
 *   - 쿠폰 재고(remainCount) 수만큼만 성공 (201)
 *   - 초과 요청은 400/409 에러 (COUPON_EXHAUSTED 등)
 *   - 성공 수가 재고를 초과하면 비관적 락 오작동 → 버그
 *
 * 토큰 풀 발급 (다중 토큰 사용 시):
 *   cd backend
 *   ./gradlew test \
 *     --tests "com.catchtable.loadtest.VacancyLoadTestTokenGenerator.generateTokens" \
 *     -DrunLoadTokenGen=true
 *   # → build/load-test-tokens.csv 에 1000개 토큰 콤마구분 출력
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS_JSON, HEADERS_AUTH, AUTH_TOKEN, requireAuthToken } from './config.js';

// 빈 AUTH_TOKEN + 빈 TOKENS 환경변수 → 즉시 fail-fast (사일런트 401 방지)
export function setup() {
  const hasMulti = (__ENV.TOKENS || '').split(',').filter(Boolean).length > 1;
  if (!AUTH_TOKEN && !hasMulti) {
    requireAuthToken('08');
  }
}

// 발급할 쿠폰 템플릿 ID — run.ps1 -CouponTemplateId 로 주입
const TEMPLATE_ID = __ENV.COUPON_TEMPLATE_ID || '1';

// 다중 토큰 풀 (선택) — CSV 형식, 단일 토큰만 있으면 AUTH_TOKEN 사용 (검증 의미 반감)
const TOKENS = (__ENV.TOKENS ? __ENV.TOKENS.split(',') : []).filter(Boolean);
const USE_MULTI_TOKENS = TOKENS.length > 1;

if (!USE_MULTI_TOKENS) {
  console.warn(
    '[WARN] 단일 토큰 모드 — 첫 1건 외 나머지는 "이미 발급" 에러로 처리됩니다.\n' +
    '       비관적 락 검증을 정확히 하려면 TOKENS 환경변수 (CSV)로 다중 토큰을 주입하세요.\n' +
    '       예: TOKENS=$(cat build/load-test-tokens.csv) k6 run ...'
  );
}

const issuedCount    = new Counter('coupon_issued');      // 발급 성공
const exhaustedCount = new Counter('coupon_exhausted');   // 재고 소진
const duplicateCount = new Counter('coupon_duplicate');   // 중복 발급 시도
const issueDuration  = new Trend('coupon_issue_duration', true);
const errorRate      = new Rate('coupon_error_rate');

export const options = {
  scenarios: {
    // 선착순 스파이크: 5초 만에 50명 동시 요청
    spike: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 60,
      maxVUs: 80,
      stages: [
        { duration: '5s',  target: 50 },  // 5초 안에 초당 50 요청 — 선착순 이벤트 재현
        { duration: '20s', target: 50 },  // 20초 유지 (재고 소진 시점 확인)
        { duration: '5s',  target: 0  },
      ],
    },
  },
  thresholds: {
    coupon_issue_duration: ['p(95)<2000'],  // 비관적 락 대기 포함 2초 기준
    coupon_error_rate:     ['rate<0.01'],   // 5xx 에러만 실패 (재고 소진은 정상)
    http_req_failed:       ['rate<0.01'],
  },
};

export default function () {
  // 다중 토큰 모드면 VU별로 다른 토큰 사용 → 사용자당 1건 발급 제약을 우회해 비관적 락만 검증
  const headers = USE_MULTI_TOKENS
    ? { ...HEADERS_JSON, Authorization: `Bearer ${TOKENS[(__VU - 1) % TOKENS.length]}` }
    : HEADERS_AUTH;

  group('쿠폰 발급 (선착순 동시성)', () => {
    const res = http.post(
      `${BASE_URL}/api/v1/coupons/${TEMPLATE_ID}/issue`,
      null, // body 없음 — POST 요청만으로 발급
      { headers },
    );

    issueDuration.add(res.timings.duration);

    if (res.status === 201 || res.status === 200) {
      // 발급 성공
      issuedCount.add(1);
      errorRate.add(false);
      check(res, { '쿠폰 발급 성공': (r) => r.status === 201 || r.status === 200 });
    } else if (res.status === 400 || res.status === 409) {
      const body = (() => { try { return res.json(); } catch { return {}; } })();
      const code = body?.code || body?.errorCode || '';

      if (code.includes('EXHAUSTED') || code.includes('SOLD_OUT') || code.includes('OUT_OF_STOCK')) {
        // 재고 소진 — 정상 동작
        exhaustedCount.add(1);
        errorRate.add(false);
        check(res, { '재고 소진 (정상)': () => true });
      } else if (code.includes('DUPLICATE') || code.includes('ALREADY')) {
        // 동일 사용자 중복 발급 시도 — 정상 동작
        duplicateCount.add(1);
        errorRate.add(false);
        check(res, { '중복 발급 방지 (정상)': () => true });
      } else {
        // 알 수 없는 4xx
        errorRate.add(true);
        console.warn(`[알수없는 4xx] status=${res.status} code=${code}`);
      }
    } else {
      // 5xx — 비정상
      errorRate.add(true);
      check(res, { '5xx 에러 없음': (r) => r.status < 500 });
      console.error(`[5xx 에러] status=${res.status} body=${res.body?.substring(0, 150)}`);
    }
  });

  sleep(0.1); // 짧은 sleep — 선착순 이벤트 패턴 재현
}

export function handleSummary(data) {
  const issued     = data.metrics.coupon_issued?.values?.count     || 0;
  const exhausted  = data.metrics.coupon_exhausted?.values?.count  || 0;
  const duplicate  = data.metrics.coupon_duplicate?.values?.count  || 0;
  const total      = issued + exhausted + duplicate;
  const p95        = data.metrics.coupon_issue_duration?.values?.['p(95)'] || 0;
  const p99        = data.metrics.coupon_issue_duration?.values?.['p(99)'] || 0;

  return {
    stdout: `
===== 쿠폰 선착순 발급 동시성 테스트 결과 =====
총 요청수              : ${total}
발급 성공              : ${issued}
재고 소진 (정상)       : ${exhausted}
중복 발급 방지 (정상)  : ${duplicate}

p95 응답시간           : ${p95.toFixed(0)}ms
p99 응답시간           : ${p99.toFixed(0)}ms

[검증] 발급 성공 수가 실제 재고를 초과하지 않는지 DB 확인:
SELECT issued_count, remain_count FROM coupon_templates WHERE id = ${TEMPLATE_ID};

[분석]
- 발급 성공 수 <= 초기 재고 수 → 비관적 락 정상 동작
- 발급 성공 수 > 초기 재고 수 → 락 오작동 → 버그
- p95 > 2s → 비관적 락 대기로 인한 병목 → 재고 소진 방식 재검토 필요
===============================================
`,
  };
}
