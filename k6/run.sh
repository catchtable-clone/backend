#!/usr/bin/env bash
# k6 부하테스트 실행 스크립트 (bash, run.ps1과 동일 인터페이스)
#
# 사용법:
#   ./k6/run.sh -t 01                              # 기본 (운영서버 + 서버 Grafana)
#   ./k6/run.sh -t 02 -a "eyJ..." -r 5             # 예약 동시성
#   ./k6/run.sh -t 08 -a "eyJ..." -c 1             # 쿠폰 선착순
#   ./k6/run.sh -t 01 -b http://localhost:8080     # 로컬 서버
#
# 테스트 번호 목록:
#   01 - 매장 전체 탐색 플로우 (event_spike + ramp_up)
#   02 - 예약 동시성 (분산락 + 낙관적락 검증)
#   03 - 전체 사용자 플로우 (event_spike + ramp_up)
#   04 - AI 챗봇 서킷브레이커 (ramp_up) ⚠ Gemini 비용 발생 — 4-MAX_REQUESTS 가드 사용 권장
#   05 - [단일] 매장 목록 조회
#   06 - [단일] PostGIS 지리 쿼리
#   07 - [단일] 잔여석 조회
#   08 - [단일] 쿠폰 선착순 발급 동시성 — 다중 토큰 CSV 권장 (-T 파일)
#   10 - [내구성] Soak 테스트 (10VU × 30분)
#   11 - 빈자리 SADD/SMEMBERS — 직접 11-vacancy-test.js 명령 참고 (PHASE 인자 별도)

set -euo pipefail

# ── 기본값 ──────────────────────────────────────────────────────────────────
TEST=""
AUTH_TOKEN=""
REMAIN_ID="1"
COUPON_TEMPLATE_ID="1"
# 기본값을 로컬로 — prod에 실수로 부하 쏘는 사고 방지. 명시적 -b 옵션으로 prod/staging 지정.
BASE_URL="http://localhost:8080"
# remote_write 미지정 시 콘솔만. -p로 명시적 지정 시에만 Grafana 전송.
PROMETHEUS_URL=""
GRAFANA_URL="http://localhost:3001"
TOKENS_FILE=""

usage() {
    cat <<EOF
Usage: $0 -t <테스트번호> [옵션]

옵션:
  -t  테스트 번호 (01..08, 10, 11) — 필수
      11: PHASE 인자 별도 필요 — 11-vacancy-test.js 헤더 주석 참고
  -a  AUTH_TOKEN (JWT)
  -r  REMAIN_ID (시나리오 02)
  -c  COUPON_TEMPLATE_ID (시나리오 08)
  -b  BASE_URL (기본: $BASE_URL) — prod 도메인 사용 시 5초 경고
  -p  PROMETHEUS_URL — 미지정 시 콘솔 출력만 (Grafana 연동 안 함)
  -T  TOKENS CSV 파일 경로 (시나리오 08 다중 토큰)
  -h  도움말
EOF
    exit 1
}

# ── 인자 파싱 ───────────────────────────────────────────────────────────────
while getopts "t:a:r:c:b:p:T:h" opt; do
    case $opt in
        t) TEST="$OPTARG" ;;
        a) AUTH_TOKEN="$OPTARG" ;;
        r) REMAIN_ID="$OPTARG" ;;
        c) COUPON_TEMPLATE_ID="$OPTARG" ;;
        b) BASE_URL="$OPTARG" ;;
        p) PROMETHEUS_URL="$OPTARG" ;;
        T) TOKENS_FILE="$OPTARG" ;;
        h|*) usage ;;
    esac
done

[[ -z "$TEST" ]] && usage

# ── 스크립트 매핑 ────────────────────────────────────────────────────────────
# macOS 기본 bash (3.2)는 `declare -A` 미지원이라 case 문으로 처리. 11은 PHASE 인자 별도 필요.
case "$TEST" in
    01) SCRIPT="k6/01-store-browse.js" ;;
    02) SCRIPT="k6/02-reservation-concurrency.js" ;;
    03) SCRIPT="k6/03-full-flow.js" ;;
    04) SCRIPT="k6/04-circuit-breaker.js" ;;
    05) SCRIPT="k6/05-store-list.js" ;;
    06) SCRIPT="k6/06-store-nearby.js" ;;
    07) SCRIPT="k6/07-remains.js" ;;
    08) SCRIPT="k6/08-coupon-issue.js" ;;
    10) SCRIPT="k6/10-soak.js" ;;
    11) SCRIPT="k6/11-vacancy-test.js" ;;
    *)  echo "[error] 알 수 없는 테스트 번호: $TEST" >&2; usage ;;
esac

# ── prod 도메인 감지 시 경고 ────────────────────────────────────────────────
if [[ "$BASE_URL" =~ catcheat\.kro\.kr|api\.catchtable ]]; then
    echo ""
    echo "⚠️  prod 도메인 감지: $BASE_URL"
    echo "    5초 후 실행됩니다. 취소하려면 Ctrl+C."
    sleep 5
fi

# ── 출력 ───────────────────────────────────────────────────────────────────
echo ""
echo "===== k6 부하테스트 시작 ====="
echo "테스트    : $TEST"
echo "스크립트  : $SCRIPT"
echo "대상 서버 : $BASE_URL"
if [[ -n "$PROMETHEUS_URL" ]]; then
    echo "Prometheus: $PROMETHEUS_URL"
    echo "Grafana   : $GRAFANA_URL (실시간 모니터링)"
else
    echo "Prometheus: (미지정 — 콘솔 출력만, Grafana 연동 안 함)"
fi
echo ""

# ── 환경변수 ───────────────────────────────────────────────────────────────
# k6 v2.0+ 호환 — 기본은 p99만 노출하므로 p50/p90/p95도 명시 push (대시보드 백분위 패널 필수)
export K6_PROMETHEUS_RW_TREND_STATS='p(50),p(90),p(95),p(99),min,max,avg'

# ── 시나리오 08 다중 토큰 (선택) ─────────────────────────────────────────────
TOKENS_ENV=""
if [[ -n "$TOKENS_FILE" ]]; then
    if [[ ! -f "$TOKENS_FILE" ]]; then
        echo "[error] 토큰 파일이 없습니다: $TOKENS_FILE" >&2
        exit 1
    fi
    TOKENS_ENV=$(cat "$TOKENS_FILE")
    echo "[info] 다중 토큰 로드: $(echo "$TOKENS_ENV" | tr ',' '\n' | wc -l | tr -d ' ')개"
fi

# ── 실행 ───────────────────────────────────────────────────────────────────
if [[ -n "$PROMETHEUS_URL" ]]; then
    export K6_PROMETHEUS_RW_SERVER_URL="$PROMETHEUS_URL"
    k6 run \
        --out "experimental-prometheus-rw" \
        -e "BASE_URL=$BASE_URL" \
        -e "AUTH_TOKEN=$AUTH_TOKEN" \
        -e "REMAIN_ID=$REMAIN_ID" \
        -e "COUPON_TEMPLATE_ID=$COUPON_TEMPLATE_ID" \
        ${TOKENS_ENV:+-e "TOKENS=$TOKENS_ENV"} \
        "$SCRIPT"
else
    k6 run \
        -e "BASE_URL=$BASE_URL" \
        -e "AUTH_TOKEN=$AUTH_TOKEN" \
        -e "REMAIN_ID=$REMAIN_ID" \
        -e "COUPON_TEMPLATE_ID=$COUPON_TEMPLATE_ID" \
        ${TOKENS_ENV:+-e "TOKENS=$TOKENS_ENV"} \
        "$SCRIPT"
fi
