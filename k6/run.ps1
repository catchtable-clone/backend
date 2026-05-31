# k6 부하테스트 실행 스크립트
#
# 사용법:
#   .\k6\run.ps1 -Test 01                              # 기본 (로컬 서버)
#   .\k6\run.ps1 -Test 02 -AuthToken "eyJ..." -RemainId 5
#   .\k6\run.ps1 -Test 01 -BaseUrl http://localhost:8080  # 로컬 서버 테스트
#
# 테스트 번호 목록:
#   01 - 매장 전체 탐색 플로우 (event_spike + ramp_up)
#   02 - 예약 동시성 (분산락 + 낙관적락 검증)
#   03 - 전체 사용자 플로우 (event_spike + ramp_up)
#   04 - AI 챗봇 서킷브레이커 (step: flood + recover) ⚠ Gemini 비용 — MAX_ITER_PER_VU 가드 권장
#   05 - [단일] 매장 목록 조회 (event_spike + ramp_up)
#   06 - [단일] PostGIS 지리 쿼리 (event_spike + ramp_up)
#   07 - [단일] 잔여석 조회 (event_spike + ramp_up)
#   08 - [단일] 쿠폰 선착순 발급 동시성
#   10 - [내구성] Soak 테스트 (10VU × 30분)
#   11 - 빈자리 SADD/SMEMBERS — PHASE 인자 별도 (11-vacancy-test.js 헤더 참고)

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('01', '02', '03', '04', '05', '06', '07', '08', '10', '11')]
    [string]$Test,

    [string]$AuthToken        = "",
    [string]$RemainId         = "1",
    [string]$CouponTemplateId = "1",
    # 기본값을 로컬로 — prod 도메인에 실수로 부하 쏘는 사고 방지.
    # prod 또는 staging 대상 시 명시적으로 -BaseUrl 지정.
    [string]$BaseUrl          = "http://localhost:8080",
    # remote_write 미지정 시 콘솔만 — 사용자가 의식적으로 지정해야 prod Prometheus로 전송.
    [string]$PrometheusUrl    = "",
    [string]$GrafanaUrl       = "http://localhost:3001"
)

$scriptMap = @{
    '01' = 'k6/01-store-browse.js'
    '02' = 'k6/02-reservation-concurrency.js'
    '03' = 'k6/03-full-flow.js'
    '04' = 'k6/04-circuit-breaker.js'
    '05' = 'k6/05-store-list.js'
    '06' = 'k6/06-store-nearby.js'
    '07' = 'k6/07-remains.js'
    '08' = 'k6/08-coupon-issue.js'
    '10' = 'k6/10-soak.js'
    '11' = 'k6/11-vacancy-test.js'   # PHASE 인자 별도 필요 — README 참고
}

# prod 도메인 감지 시 경고
if ($BaseUrl -match "catcheat\.kro\.kr|api\.catchtable") {
    Write-Host ""
    Write-Host "⚠️  prod 도메인 감지: $BaseUrl" -ForegroundColor Yellow
    Write-Host "    5초 후 실행됩니다. 취소하려면 Ctrl+C." -ForegroundColor Yellow
    Start-Sleep -Seconds 5
}

$scriptName = $scriptMap[$Test]

Write-Host ""
Write-Host "===== k6 부하테스트 시작 =====" -ForegroundColor Cyan
Write-Host "테스트    : $Test"
Write-Host "스크립트  : $scriptName"
Write-Host "대상 서버 : $BaseUrl"
Write-Host "Grafana   : $GrafanaUrl (실시간 모니터링)"
Write-Host ""

# k6 v2.0+ 호환 — 기본은 p99만 노출하므로 p50/p90/p95도 명시 push
# 대시보드 Row A/B의 백분위 패널이 작동하려면 필수
$env:K6_PROMETHEUS_RW_TREND_STATS = 'p(50),p(90),p(95),p(99),min,max,avg'

# PrometheusUrl 지정 시에만 remote_write 사용. 미지정 시 콘솔 출력만.
if ($PrometheusUrl) {
    $env:K6_PROMETHEUS_RW_SERVER_URL = $PrometheusUrl
    k6 run `
        --out "experimental-prometheus-rw" `
        -e "BASE_URL=$BaseUrl" `
        -e "AUTH_TOKEN=$AuthToken" `
        -e "REMAIN_ID=$RemainId" `
        -e "COUPON_TEMPLATE_ID=$CouponTemplateId" `
        $scriptName
} else {
    Write-Host "[info] PrometheusUrl 미지정 — 콘솔 출력만 (Grafana 연동 안 함)" -ForegroundColor Cyan
    k6 run `
        -e "BASE_URL=$BaseUrl" `
        -e "AUTH_TOKEN=$AuthToken" `
        -e "REMAIN_ID=$RemainId" `
        -e "COUPON_TEMPLATE_ID=$CouponTemplateId" `
        $scriptName
}
