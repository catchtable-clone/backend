/**
 * [단일 API] 잔여석 조회 부하테스트 — 이벤트 스파이크 vs 점진적 증가
 * 실행: .\k6\run.ps1 -Test 07
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

const remainsDuration = new Trend('remains_duration', true);
const spikeDuration   = new Trend('remains_spike_duration', true);
const rampDuration    = new Trend('remains_ramp_duration', true);
const errorRate       = new Rate('remains_error_rate');

const STORE_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

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
    //   잔여석은 단순 인덱스 조회라 점프 후에도 p95 300ms 이내가 정상
    //   넘으면 → (store_id, remain_date) 복합 인덱스 미적용 또는 Row Lock 경합
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
    // 목적: p95가 VU 수와 비례해서 증가하는지 확인
    // 인덱스가 제대로 걸리면 VU 수와 무관하게 p95가 일정해야 함
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
    'remains_duration':       ['p(95)<300', 'p(99)<500'],
    'remains_spike_duration': ['p(95)<400'],
    'remains_ramp_duration':  ['p(95)<300'],
    'remains_error_rate':     ['rate<0.01'],
    'http_req_failed':        ['rate<0.01'],
  },
};

export default function () {
  const isSpike   = exec.scenario.name === 'event_spike';
  const storeId   = STORE_IDS[Math.floor(Math.random() * STORE_IDS.length)];
  // +9h: 서버가 UTC로 실행될 경우 자정~오전9시 사이에 날짜가 하루 틀어지는 것 방지
  const today     = new Date(Date.now() + 9 * 60 * 60 * 1000);
  const dayOffset = Math.floor(Math.random() * 7);
  today.setDate(today.getDate() + dayOffset);
  const date = today.toISOString().split('T')[0];

  group('잔여석 조회', () => {
    const res = http.get(
      `${BASE_URL}/api/v1/remains?storeId=${storeId}&date=${date}`,
      { headers: HEADERS_JSON },
    );
    remainsDuration.add(res.timings.duration);
    isSpike ? spikeDuration.add(res.timings.duration) : rampDuration.add(res.timings.duration);

    const ok = check(res, {
      '잔여석 200': (r) => r.status === 200,
      '응답 200ms 이내': (r) => r.timings.duration < 200,
    });
    errorRate.add(!ok);
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
