# k6 부하테스트 실행 스크립트
#
# 사용법:
#   .\k6\run.ps1 -Test 01                                           # 인증 불필요
#   .\k6\run.ps1 -Test 02 -AuthToken "eyJ..." -RemainId 5          # 예약 동시성
#   .\k6\run.ps1 -Test 04 -AuthToken "eyJ..."                      # 서킷브레이커
#   .\k6\run.ps1 -Test 05 -BaseUrl https://api.catcheat.kro.kr     # 운영서버 목록 부하
#   .\k6\run.ps1 -Test 06 -BaseUrl https://api.catcheat.kro.kr     # PostGIS 지리 쿼리
#   .\k6\run.ps1 -Test 07 -BaseUrl https://api.catcheat.kro.kr     # 잔여석 조회
#   .\k6\run.ps1 -Test 08 -AuthToken "eyJ..." -CouponTemplateId 1  # 쿠폰 선착순 발급
#   .\k6\run.ps1 -Test 10 -AuthToken "eyJ..." -BaseUrl https://api.catcheat.kro.kr  # Soak
#
# 테스트 번호 목록:
#   01 - 매장 전체 탐색 플로우 (6개 API 조합)
#   02 - 예약 동시성 (분산락 + 낙관적락 검증)
#   03 - 전체 사용자 플로우 (home → 예약)
#   04 - AI 챗봇 서킷브레이커
#   05 - [단일] 매장 목록 조회 (필터 조합, 최대 200VU)
#   06 - [단일] PostGIS 지리 쿼리 (nearby + in-bounds, 최대 150VU)
#   07 - [단일] 잔여석 조회 (최대 300VU)
#   08 - [단일] 쿠폰 선착순 발급 동시성
#   10 - [내구성] Soak 테스트 (10VU × 30분)

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('01', '02', '03', '04', '05', '06', '07', '08', '10')]
    [string]$Test,

    [string]$AuthToken        = "",
    [string]$RemainId         = "1",
    [string]$CouponTemplateId = "1",
    [string]$BaseUrl          = "http://localhost:8080",
    [string]$PrometheusUrl    = "http://localhost:9091/api/v1/write"
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
Write-Host "Grafana   : http://localhost:3002 (실시간 모니터링)"
Write-Host ""

$env:K6_PROMETHEUS_RW_SERVER_URL = $PrometheusUrl

k6 run `
    --out "experimental-prometheus-rw" `
    -e "BASE_URL=$BaseUrl" `
    -e "AUTH_TOKEN=$AuthToken" `
    -e "REMAIN_ID=$RemainId" `
    -e "COUPON_TEMPLATE_ID=$CouponTemplateId" `
    $scriptName
