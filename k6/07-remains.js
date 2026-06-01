/**
 * [단일 API] 잔여석 조회 부하테스트 — 이벤트 스파이크 vs 점진적 증가
 * 실행: .\k6\run.ps1 -Test 07
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

const remainsDuration = new Trend('remains_duration', true);
const spikeDuration   = new Trend('remains_spike_duration', true);
const rampDuration    = new Trend('remains_ramp_duration', true);
const errorRate       = new Rate('remains_error_rate');

const STORE_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

const REMAINS_SLA_MS = Number(__ENV.REMAINS_SLA_MS || 400);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {

    // SCENARIO A: 이벤트 스파이크 — 50명 즉시 점프로 이벤트 오픈 순간 재현 (패턴 근거는 01-store-browse.js 헤더)
    // 체크: 점프 후 p95 > 300ms = (store_id, remain_date) 복합 인덱스 미적용 또는 Row Lock 경합
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

    // SCENARIO B: 점진적 증가 — p95가 VU 수와 비례해 증가하는가 (인덱스 OK면 VU와 무관하게 일정해야 함)
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
    // REMAINS_SLA_MS와 정합: 기본 400ms → p95 SLA, p99는 2배 마진
    'remains_duration':       [`p(95)<${REMAINS_SLA_MS}`, `p(99)<${REMAINS_SLA_MS * 2}`],
    'remains_spike_duration': [`p(95)<${REMAINS_SLA_MS + 200}`],
    'remains_ramp_duration':  [`p(95)<${REMAINS_SLA_MS}`],
    'remains_error_rate':     ['rate<0.01'],
    'http_req_failed':        ['rate<0.01'],
  },
};

export default function () {
  const isSpike   = exec.scenario.name === 'event_spike';
  const storeId   = STORE_IDS[Math.floor(Math.random() * STORE_IDS.length)];
  // +9h: 서버가 UTC로 실행될 경우 자정~오전9시 사이에 날짜가 하루 틀어지는 것 방지
  const dayOffset = Math.floor(Math.random() * 7) * 24 * 60 * 60 * 1000;
  const date = new Date(Date.now() + 9 * 60 * 60 * 1000 + dayOffset).toISOString().split('T')[0];

  group('잔여석 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/remains?storeId=${storeId}&date=${date}`,
      { headers: HEADERS_JSON },
    );
    remainsDuration.add(res.timings.duration);
    isSpike ? spikeDuration.add(res.timings.duration) : rampDuration.add(res.timings.duration);

    // HTTP 성공 여부만 errorRate에 반영 — SLA 초과는 에러가 아닌 별도 지표로 관찰
    const ok = check(res, { '잔여석 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    // SLA 체크: threshold 위반 여부는 remains_duration p95/p99로 판단
    check(res, { [`응답 ${REMAINS_SLA_MS}ms 이내`]: (r) => r.timings.duration < REMAINS_SLA_MS });
  });

  sleep(0.5);
}

export function handleSummary(data) {
  const s_p95  = data.metrics.remains_spike_duration?.values?.['p(95)'] || 0;
  const s_p99  = data.metrics.remains_spike_duration?.values?.['p(99)'] || 0;
  const r_p95  = data.metrics.remains_ramp_duration?.values?.['p(95)']  || 0;
  const r_p99  = data.metrics.remains_ramp_duration?.values?.['p(99)']  || 0;
  const p95    = data.metrics.remains_duration?.values?.['p(95)']       || 0;
  const p99    = data.metrics.remains_duration?.values?.['p(99)']       || 0;
  const reqs   = data.metrics.http_reqs?.values?.count                  || 0;
  const rps    = data.metrics.http_reqs?.values?.rate                   || 0;
  const errR   = data.metrics.remains_error_rate?.values?.rate          || 0;

  const indexNote = p95 < 300
    ? `✓  p95 ${p95.toFixed(0)}ms → 복합 인덱스 정상`
    : `⚠  p95 ${p95.toFixed(0)}ms → 인덱스 미적용 또는 Row Lock 경합 의심`;

  return {
    stdout: `
===== 잔여석 조회 단일 API 부하테스트 결과 =====
총 요청수              : ${reqs.toLocaleString()}건
최대 RPS               : ${rps.toFixed(1)} req/s
에러율                 : ${(errR * 100).toFixed(3)}%

[SCENARIO A] 이벤트 스파이크 (워밍업 10명 → 50명 점프)
  p95 : ${s_p95.toFixed(0)}ms  /  p99 : ${s_p99.toFixed(0)}ms

[SCENARIO B] 점진적 증가 (0→50명)
  p95 : ${r_p95.toFixed(0)}ms  /  p99 : ${r_p99.toFixed(0)}ms

[전체 p95 : ${p95.toFixed(0)}ms  /  p99 : ${p99.toFixed(0)}ms]

[인덱스 진단] ${indexNote}
================================================
`,
  };
}
