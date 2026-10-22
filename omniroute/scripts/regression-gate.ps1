# ==============================================================================
# OmniRoute Automated Regression Gate
# 1. Verify health of all services
# 2. Test $120.00 SKU-001 qty 2 -> COMPLETED and stock drops by 2
# 3. Test $13.13 SKU-001 qty 1 -> FAILED and stock restored
# 4. Run 20-order burst with stock invariant verification
# ==============================================================================

param(
    [string]$GatewayUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Running OmniRoute Regression Gate Against $GatewayUrl" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Healthcheck
Write-Host "`n[Gate 1/4] Checking Service Health..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 5
if ($health.status -ne "UP") {
    Write-Host "GATE FAILED: Gateway is not UP (status: $($health.status))" -ForegroundColor Red
    exit 1
}
Write-Host " Gateway Health: UP" -ForegroundColor Green

# 2. Test Order $120.00 (SKU-001 qty 2) -> COMPLETED
Write-Host "`n[Gate 2/4] Testing Happy Path Order (SKU-001 x 2, $120.00)..." -ForegroundColor Yellow
$invBefore = Invoke-RestMethod -Uri "$GatewayUrl/api/inventory" -Method Get
$sku1Before = ($invBefore | Where-Object { $_.sku -eq "SKU-001" }).availableQty

$body120 = @{
    customerId = "REG-GATE-HAPPY"
    items = @(@{ sku = "SKU-001"; quantity = 2 })
    totalAmount = 120.00
} | ConvertTo-Json

$res120 = Invoke-RestMethod -Uri "$GatewayUrl/api/orders" -Method Post -Body $body120 -ContentType "application/json"
$orderId120 = $res120.orderId

$waited = 0
$status120 = ""
while ($waited -lt 15) {
    Start-Sleep -Seconds 1
    $waited++
    $ord = Invoke-RestMethod -Uri "$GatewayUrl/api/orders/$orderId120" -Method Get
    $status120 = $ord.status
    if ($status120 -in @("COMPLETED", "FAILED")) { break }
}

$invAfter = Invoke-RestMethod -Uri "$GatewayUrl/api/inventory" -Method Get
$sku1After = ($invAfter | Where-Object { $_.sku -eq "SKU-001" }).availableQty

if ($status120 -ne "COMPLETED" -or $sku1After -ne ($sku1Before - 2)) {
    Write-Host "GATE FAILED: Happy path failed. Status: $status120 (expected COMPLETED), Stock: $sku1After (expected $($sku1Before - 2))" -ForegroundColor Red
    exit 1
}
Write-Host " Happy path PASS: Order $orderId120 reached COMPLETED and SKU-001 dropped by 2 ($sku1Before -> $sku1After)" -ForegroundColor Green

# 3. Test Order $13.13 (SKU-001 qty 1) -> FAILED & Stock Restored
Write-Host "`n[Gate 3/4] Testing Forced Payment Failure & Compensation ($13.13)..." -ForegroundColor Yellow
$invBeforeFail = Invoke-RestMethod -Uri "$GatewayUrl/api/inventory" -Method Get
$sku1BeforeFail = ($invBeforeFail | Where-Object { $_.sku -eq "SKU-001" }).availableQty

$body1313 = @{
    customerId = "REG-GATE-FAIL"
    items = @(@{ sku = "SKU-001"; quantity = 1 })
    totalAmount = 13.13
} | ConvertTo-Json

$res1313 = Invoke-RestMethod -Uri "$GatewayUrl/api/orders" -Method Post -Body $body1313 -ContentType "application/json"
$orderId1313 = $res1313.orderId

$waited = 0
$status1313 = ""
while ($waited -lt 15) {
    Start-Sleep -Seconds 1
    $waited++
    $ord = Invoke-RestMethod -Uri "$GatewayUrl/api/orders/$orderId1313" -Method Get
    $status1313 = $ord.status
    if ($status1313 -in @("COMPLETED", "FAILED")) { break }
}

$invAfterFail = Invoke-RestMethod -Uri "$GatewayUrl/api/inventory" -Method Get
$sku1AfterFail = ($invAfterFail | Where-Object { $_.sku -eq "SKU-001" }).availableQty

if ($status1313 -ne "FAILED" -or $sku1AfterFail -ne $sku1BeforeFail) {
    Write-Host "GATE FAILED: Compensation failed. Status: $status1313 (expected FAILED), Stock: $sku1AfterFail (expected $sku1BeforeFail)" -ForegroundColor Red
    exit 1
}
Write-Host " Compensation PASS: Order $orderId1313 reached FAILED and stock restored to $sku1AfterFail" -ForegroundColor Green

# 4. 20-order burst
Write-Host "`n[Gate 4/4] Testing 20-Order Burst and Stock Invariant..." -ForegroundColor Yellow
$burstScript = Join-Path $PSScriptRoot "demo.ps1"
& powershell -File $burstScript -GatewayUrl $GatewayUrl -TotalOrders 20
if ($LASTEXITCODE -ne 0) {
    Write-Host "GATE FAILED: 20-order burst failed." -ForegroundColor Red
    exit 1
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " ALL REGRESSION GATES PASSED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
exit 0
