/**
 * 빈자리 구독 (Vacancy) 부하테스트 - Redis SADD/SMEMBERS 응답시간 측정
 * 실행: k6 run -e PHASE=<subscribe|smembers|ttl> ... 11-vacancy-test.js
 *
 * 시험 절차 (원본 스펙):
 *   1) 100명 구독자를 SADD 후 SMEMBERS 평균 응답시간 측정
 *   2) 시간대 + 1시간 후 키가 자동 삭제되는지 확인
 *
 * 측정 경로:
 *   - SADD     : POST /api/v1/vacancy 를 통해 백엔드 내부에서 호출 (실제 운영 경로)
 *                -> backend/src/main/java/com/catchtable/vacancy/service/VacancyService.java:56
 *   - SMEMBERS : 백엔드가 HTTP 로 노출하지 않음 (Kafka Consumer 내부 호출).
 *                k6/experimental/redis 모듈로 Redis 에 직접 붙어서 측정.
 *                -> backend/src/main/java/com/catchtable/notification/service/NotificationKafkaConsumer.java:155
 *   - TTL      : k6/experimental/redis 의 TTL/EXISTS 로 직접 확인.
 *
 * Redis 키 포맷 (백엔드 generateRedisKey 기준):
 *   wait:store:{storeId}:{remainDate}:{remainTime}
 *   예) wait:store:1:2026-05-29:18:00
 *
 * =============================================================================
 * 실행 환경 - 1안 vs 2안
 * =============================================================================
 *   본 시나리오는 두 가지 실행 환경을 지원한다. 명령 예시는 각 PHASE 안에서
 *   [1안] [2안] 으로 구분해 표기.
 *
 *   [1안] EC2 안에서 k6 실행 (권장)
 *     - URL          = http://localhost:8080   (백엔드와 같은 호스트)
 *     - REDIS_ADDR   = catchtable-redis:6379   (docker network 컨테이너명)
 *     - 필요 작업    : EC2 접속 (SSH 또는 AWS SSM) + k6 설치
 *     - 운영 구성    : 변경 없음
 *     - 측정 정확도  : 높음 (RTT 거의 0)
 *     - PHASE 2/3 는 docker network `catchtable-net` 에 붙어야 하므로
 *       `docker run --network catchtable-net grafana/k6 run -` 방식 사용.
 *
 *   [2안] 로컬에서 k6 실행
 *     - URL          = https://<운영도메인>
 *     - REDIS_ADDR   = <ec2-public-ip>:6379
 *     - 관리자 작업  : docker-compose.prod.yml 의 redis 섹션 expose -> ports
 *                      변경 + 컨테이너 재시작 + 보안그룹 6379 허용
 *     - 운영 구성    : 변경 (테스트 종료 후 원복 필수)
 *     - 측정 정확도  : 로컬 ↔ EC2 RTT 노이즈 섞임
 *
 * =============================================================================
 * PHASE 1: 100명 구독 (SADD 부하 생성)
 * =============================================================================
 *
 * [사전 준비]
 *   1. 시드 유저 100명 + 토큰 100개 생성 (backend 디렉토리에서)
 *      ./gradlew test \
 *        --tests "com.catchtable.loadtest.VacancyLoadTestTokenGenerator.generateTokens" \
 *        -DrunLoadTokenGen=true
 *      ( -DrunLoadTokenGen=true 가드가 있어 일반 ./gradlew test 시 자동 스킵됨 )
 *
 *   2. 테스트 대상 StoreRemain 준비
 *      - remainTeam = 0 인 (잔여 0) StoreRemain 의 id 가 필요하다.
 *      - DB 에서 미리 하나 골라두고, 그 row 의 remainDate / remainTime 으로 Redis 키를 만든다.
 *
 * [실행]
 *   [1안] EC2 안에서 (Bash)
 *     k6 run -e PHASE=subscribe \
 *       -e URL=http://localhost:8080 \
 *       -e TOKENS=$(cat build/load-test-tokens.csv) \
 *       -e REMAIN_ID=<id> -e N=100 \
 *       k6/11-vacancy-test.js
 *
 *   [2안] 로컬에서 (PowerShell)
 *     $tokens = Get-Content backend\build\load-test-tokens.csv -Raw
 *     k6 run -e PHASE=subscribe `
 *       -e URL=https://<운영도메인> `
 *       -e TOKENS=$tokens `
 *       -e REMAIN_ID=<id> -e N=100 `
 *       backend\k6\11-vacancy-test.js
 *
 *   [2안] 로컬에서 (Bash)
 *     k6 run -e PHASE=subscribe \
 *       -e URL=https://<운영도메인> \
 *       -e TOKENS=$(cat backend/build/load-test-tokens.csv) \
 *       -e REMAIN_ID=<id> -e N=100 \
 *       backend/k6/11-vacancy-test.js
 *
 * =============================================================================
 * PHASE 2: SMEMBERS 평균 응답시간 측정
 * =============================================================================
 *
 * [사전 준비]
 *   1. PHASE 1 이 끝나서 Redis 키에 100명이 들어있어야 한다.
 *   2. 백엔드 로그에서 Redis 키를 확인한다.
 *      예) [빈자리 알림 등록] Redis SADD 완료: key=wait:store:1:2026-05-29:18:00, userId=...
 *
 * [실행]
 *   [1안] EC2 안에서 (docker 로 k6 컨테이너를 catchtable-net 에 붙임)
 *     docker run --rm -i --network catchtable-net \
 *       -e PHASE=smembers \
 *       -e REDIS_ADDR=catchtable-redis:6379 \
 *       -e REDIS_KEY=wait:store:1:2026-05-29:18:00 \
 *       -e N=1000 \
 *       grafana/k6 run - < k6/11-vacancy-test.js
 *
 *   [2안] 로컬에서
 *     k6 run -e PHASE=smembers \
 *       -e REDIS_ADDR=<ec2-public-ip>:6379 \
 *       -e REDIS_KEY=wait:store:1:2026-05-29:18:00 \
 *       -e N=1000 \
 *       backend/k6/11-vacancy-test.js
 *
 *   (Redis 에 패스워드가 걸려있다면 -e REDIS_PASSWORD=... 추가. 현재 운영은 미설정)
 *   N = SMEMBERS 호출 반복 횟수 (1000회 정도면 분포가 안정됨).
 *   결과: smembers_duration_ms 의 avg / p95 / p99 가 평균 응답시간.
 *
 * =============================================================================
 * PHASE 3: TTL 자동 삭제 확인
 * =============================================================================
 *
 * [실행 - PHASE 1 직후]
 *   [1안] EC2 안에서
 *     docker run --rm -i --network catchtable-net \
 *       -e PHASE=ttl \
 *       -e REDIS_ADDR=catchtable-redis:6379 \
 *       -e REDIS_KEY=wait:store:1:2026-05-29:18:00 \
 *       grafana/k6 run - < k6/11-vacancy-test.js
 *
 *   [2안] 로컬에서
 *     k6 run -e PHASE=ttl \
 *       -e REDIS_ADDR=<ec2-public-ip>:6379 \
 *       -e REDIS_KEY=wait:store:1:2026-05-29:18:00 \
 *       backend/k6/11-vacancy-test.js
 *
 *   확인 사항:
 *     - exists == 1
 *     - ttl 초 단위 값이 (remainDate+remainTime+1h - now) 와 일치
 *
 * [실행 - 만료 시각 (remainDate+remainTime+1h) 이후]
 *   동일 명령 다시 실행.
 *   확인: exists == 0  (자동 삭제 성공)
 *
 * =============================================================================
 * 테스트 종료 후 정리 (필수)
 * =============================================================================
 *   - 시드 데이터 정리:
 *       DELETE FROM vacancy WHERE user_id IN (SELECT id FROM users WHERE google_id LIKE 'loadtest-google-%');
 *       DELETE FROM users  WHERE google_id LIKE 'loadtest-google-%';
 *   - Redis 잔존 키 정리:
 *       redis-cli --scan --pattern 'wait:store:*' | xargs redis-cli DEL
 */

import http from 'k6/http';
// k6 v2.0+에서 'k6/experimental/redis'가 'k6/x/redis'로 이전됨 (자동 extension 빌드 — 첫 실행 시 ~15초 소요).
import redis from 'k6/x/redis';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.URL || 'http://localhost:8080';
const PHASE = __ENV.PHASE || 'subscribe';
const N = Number(__ENV.N || 100);

// PHASE=subscribe 용
const TOKENS = (__ENV.TOKENS ? __ENV.TOKENS.split(',') : []).filter(Boolean);
const REMAIN_ID = Number(__ENV.REMAIN_ID || 0);

// PHASE=smembers / ttl 용
const REDIS_ADDR = __ENV.REDIS_ADDR || 'localhost:6379';
const REDIS_KEY = __ENV.REDIS_KEY || '';
const REDIS_PASSWORD = __ENV.REDIS_PASSWORD || '';

// 메트릭
const status2xx = new Counter('status_2xx');
const status4xx = new Counter('status_4xx');
const status5xx = new Counter('status_5xx');
const subscribeDuration = new Trend('subscribe_duration_ms', true);
const smembersDuration = new Trend('smembers_duration_ms', true);
const smembersSize = new Trend('smembers_set_size');

// Redis 클라이언트 (smembers / ttl phase 에서만 사용)
// URL 형식: redis://[:password@]host:port
// subscribe phase 에서는 Redis 접근이 불필요하므로 조건부 초기화
//   (Redis 접근 불가 환경에서도 subscribe phase 단독 실행 가능하게 함)
const REDIS_URL = (() => {
    const auth = REDIS_PASSWORD ? `:${encodeURIComponent(REDIS_PASSWORD)}@` : '';
    return `redis://${auth}${REDIS_ADDR}`;
})();
const redisClient = (PHASE === 'smembers' || PHASE === 'ttl') ? new redis.Client(REDIS_URL) : null;

export const options = (() => {
    if (PHASE === 'subscribe') {
        return { vus: N, iterations: N };
    }
    if (PHASE === 'smembers') {
        // 단일 VU 가 N 번 반복 - SMEMBERS 자체 응답시간 분포만 본다.
        // 동시성 부하를 보려면 vus 를 늘릴 것.
        return { vus: 1, iterations: N };
    }
    if (PHASE === 'ttl') {
        return { vus: 1, iterations: 1 };
    }
    throw new Error(`Unknown PHASE: ${PHASE}`);
})();

// ---- PHASE 1: subscribe ----
function subscribe() {
    if (TOKENS.length === 0) throw new Error('TOKENS env required');
    if (!REMAIN_ID) throw new Error('REMAIN_ID env required');

    const vu = __VU - 1;
    const token = TOKENS[vu % TOKENS.length];

    const res = http.post(
        `${BASE_URL}/api/v1/vacancy`,
        JSON.stringify({ remainId: parseInt(REMAIN_ID, 10) }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
            tags: { endpoint: 'subscribe' },
        }
    );

    subscribeDuration.add(res.timings.duration);
    if (res.status >= 200 && res.status < 300) status2xx.add(1);
    else if (res.status >= 400 && res.status < 500) status4xx.add(1);
    else if (res.status >= 500) status5xx.add(1);

    check(res, {
        'subscribe 2xx': (r) => r.status >= 200 && r.status < 300,
    });
}

// ---- PHASE 2: smembers ----
async function smembers() {
    if (!REDIS_KEY) throw new Error('REDIS_KEY env required');

    // k6 v2 런타임에 performance 객체가 없어서 Date.now() 사용.
    // 밀리초 정밀도라 sub-ms 호출은 0 으로 잡혀 분포가 약간 왜곡될 수 있다.
    const start = Date.now();
    const members = await redisClient.smembers(REDIS_KEY);
    const duration = Date.now() - start;

    smembersDuration.add(duration);
    smembersSize.add(members.length);
}

// ---- PHASE 3: ttl ----
async function ttl() {
    if (!REDIS_KEY) throw new Error('REDIS_KEY env required');

    const exists = await redisClient.exists(REDIS_KEY);
    const ttlSec = await redisClient.ttl(REDIS_KEY);

    console.log(`[TTL CHECK] key=${REDIS_KEY}`);
    console.log(`  exists = ${exists}   (1 = 키 존재, 0 = 자동 삭제됨)`);
    console.log(`  ttl    = ${ttlSec}s  (-1 = TTL 없음, -2 = 키 없음)`);

    check(null, {
        'key state observed': () => true,
    });
}

export default async function () {
    if (PHASE === 'subscribe') subscribe();
    else if (PHASE === 'smembers') await smembers();
    else if (PHASE === 'ttl') await ttl();
}
