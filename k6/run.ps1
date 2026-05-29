# k6 부하테스트 실행 스크립트
#
# 사용법:
#   .\k6\run.ps1 -Test 01                              # 기본 (운영서버 + 서버 Grafana)
#   .\k6\run.ps1 -Test 02 -AuthToken "eyJ..." -RemainId 5
#   .\k6\run.ps1 -Test 01 -BaseUrl http://localhost:8080  # 로컬 서버 테스트
#
# 테스트 번호 목록:
#   01 - 매장 전체 탐색 플로우 (constant_50 + ramp_up)
#   02 - 예약 동시성 (분산락 + 낙관적락 검증)
#   03 - 전체 사용자 플로우 (constant_50 + ramp_up)
#   04 - AI 챗봇 서킷브레이커 (ramp_up)
#   05 - [단일] 매장 목록 조회 (constant_50 + ramp_up)
#   06 - [단일] PostGIS 지리 쿼리 (constant_50 + ramp_up)
#   07 - [단일] 잔여석 조회 (constant_50 + ramp_up)
#   08 - [단일] 쿠폰 선착순 발급 동시성
#   10 - [내구성] Soak 테스트 (10VU × 30분)

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('01', '02', '03', '04', '05', '06', '07', '08', '10')]
    [string]$Test,

    [string]$AuthToken        = "",
    [string]$RemainId         = "1",
    [string]$CouponTemplateId = "1",
    [string]$BaseUrl          = "https://api.catcheat.kro.kr",
    [string]$PrometheusUrl    = "http://54.116.98.27:9090/api/v1/write",
    [string]$GrafanaUrl       = "http://54.116.98.27:3001"
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
}

$scriptName = $scriptMap[$Test]

Write-Host ""
Write-Host "===== k6 부하테스트 시작 =====" -ForegroundColor Cyan
Write-Host "테스트    : $Test"
Write-Host "스크립트  : $scriptName"
Write-Host "대상 서버 : $BaseUrl"
Write-Host "Grafana   : $GrafanaUrl (실시간 모니터링)"
Write-Host ""

$env:K6_PROMETHEUS_RW_SERVER_URL = $PrometheusUrl

k6 run `
    --out "experimental-prometheus-rw" `
    -e "BASE_URL=$BaseUrl" `
    -e "AUTH_TOKEN=$AuthToken" `
    -e "REMAIN_ID=$RemainId" `
    -e "COUPON_TEMPLATE_ID=$CouponTemplateId" `
    $scriptName
