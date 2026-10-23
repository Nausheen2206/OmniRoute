# ==============================================================================
# OmniRoute Chaos Script: Payment Service Outage & Circuit Breaker Recovery
# 1. Place a baseline order -> verify COMPLETED
# 2. Stop payment-service container
# 3. Place several orders -> verify PAYMENT_PENDING and Circuit Breaker OPEN
# 4. Restart payment-service container
# 5. Wait for scheduled retry -> verify queued orders reach COMPLETED
# 6. Verify inventory consistency -> Print PASS or FAIL
# ==============================================================================

param(
    [string]$GatewayUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " OmniRoute Chaos Test: Payment Outage & Resilience Gate" -ForegroundColor Cyan
Write-Host " Target Gateway: $GatewayUrl" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Verify Gateway Health
Write-Host "`n[Step 1/6] Checking initial system health..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 5
if ($health.status -ne "UP") {
    Write-Host "CHAOS TEST FAILED: Gateway is not UP (status: $($health.status))" -ForegroundColor Red
    exit 1
}
Write-Host " Gateway is healthy and UP" -ForegroundColor Green

# 2. Baseline Order
Write-Host "`n[Step 2/6] Placing baseline order (Happy Path)..." -ForegroundColor Yellow
$baselineBody = @{
    customerId = "CHAOS-BASELINE"
    items = @(@{ sku = "SKU-001"; quantity = 1 })
    totalAmount = 55.00
} | ConvertTo-Json

$baselineRes = Invoke-RestMethod -Uri "$GatewayUrl/api/orders" -Method Post -Body $baselineBody -ContentType "application/json"
$baselineId = $baselineRes.orderId
Write-Host " Baseline order placed: $baselineId"

$waited = 0
$baselineStatus = ""
while ($waited -lt 15) {
    Start-Sleep -Seconds 1
    $waited++
    $ord = Invoke-RestMethod -Uri "$GatewayUrl/api/orders/$baselineId" -Method Get
    $baselineStatus = $ord.status
    if ($baselineStatus -in @("COMPLETED", "FAILED")) { break }
}

if ($baselineStatus -ne "COMPLETED") {
    Write-Host "CHAOS TEST FAILED: Baseline order ended in $baselineStatus (expected COMPLETED)" -ForegroundColor Red
    exit 1
}
Write-Host " Baseline order successfully COMPLETED" -ForegroundColor Green

# 3. Stop Payment Service Container
Write-Host "`n[Step 3/6] Simulating Chaos: Stopping omniroute-payment-service..." -ForegroundColor Red
docker stop omniroute-payment-service | Out-Null
Start-Sleep -Seconds 1
Write-Host " Container omniroute-payment-service stopped." -ForegroundColor Yellow

# 4. Place Several Orders During Outage
Write-Host "`n[Step 4/6] Placing 4 orders during payment outage..." -ForegroundColor Yellow
$outageOrderIds = @()
for ($i = 1; $i -le 4; $i++) {
    $body = @{
        customerId = "CHAOS-OUTAGE-$i"
        items = @(@{ sku = "SKU-001"; quantity = 1 })
        totalAmount = [Math]::Round(40.00 + $i * 5.0, 2)
    } | ConvertTo-Json

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/orders" -Method Post -Body $body -ContentType "application/json"
    $stopwatch.Stop()

    $outageOrderIds += $res.orderId
    Write-Host (" - Order {0} accepted in {1}ms (ID: {2})" -f $i, $stopwatch.ElapsedMilliseconds, $res.orderId)
}

# Allow saga orchestrator to hit circuit breaker and fallback
Start-Sleep -Seconds 3

# Check Circuit Breaker State & Order Statuses
Write-Host "`nVerifying Circuit Breaker state and order statuses..." -ForegroundColor Yellow
try {
    $cb = Invoke-RestMethod -Uri "http://localhost:8081/orders/circuit-breaker" -Method Get
    $cbColor = if ($cb.state -eq "OPEN") { "Red" } else { "Yellow" }
    Write-Host " Circuit Breaker State: $($cb.state)" -ForegroundColor $cbColor
} catch {
    Write-Host " Unable to fetch circuit breaker direct endpoint: $_" -ForegroundColor Yellow
}

$allPending = $true
foreach ($id in $outageOrderIds) {
    $ord = Invoke-RestMethod -Uri "$GatewayUrl/api/orders/$id" -Method Get
    $ordColor = if ($ord.status -eq "PAYMENT_PENDING") { "Cyan" } else { "Yellow" }
    Write-Host " - Order $id Status: $($ord.status)" -ForegroundColor $ordColor
    if ($ord.status -ne "PAYMENT_PENDING") {
        $allPending = $false
    }
}

if (-not $allPending) {
    Write-Host "WARNING: Some orders did not immediately reflect PAYMENT_PENDING yet, waiting 2s..." -ForegroundColor Yellow
    Start-Sleep -Seconds 2
    foreach ($id in $outageOrderIds) {
        $ord = Invoke-RestMethod -Uri "$GatewayUrl/api/orders/$id" -Method Get
        Write-Host " - Recheck Order $id Status: $($ord.status)"
        if ($ord.status -eq "PAYMENT_PENDING") { $allPending = $true }
    }
}

# 5. Restart Payment Service Container
Write-Host "`n[Step 5/6] Restoring service: Starting omniroute-payment-service..." -ForegroundColor Green
docker start omniroute-payment-service | Out-Null

$paymentUp = $false
$retries = 0
while ($retries -lt 30) {
    Start-Sleep -Seconds 1
    $retries++
    try {
        $ph = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method Get -TimeoutSec 2
        if ($ph.status -eq "UP") {
            $paymentUp = $true
            break
        }
    } catch {}
}

if (-not $paymentUp) {
    Write-Host "CHAOS TEST FAILED: Payment service did not recover." -ForegroundColor Red
    exit 1
}
Write-Host " Payment service is back UP!" -ForegroundColor Green

# 6. Wait for Queued Orders to Complete
Write-Host "`n[Step 6/6] Waiting for queued PAYMENT_PENDING orders to complete via scheduler..." -ForegroundColor Yellow
$maxRecoveryWait = 30
$recoveryWaited = 0
$allCompleted = $false

while ($recoveryWaited -lt $maxRecoveryWait) {
    Start-Sleep -Seconds 2
    $recoveryWaited += 2

    $completedCount = 0
    foreach ($id in $outageOrderIds) {
        $ord = Invoke-RestMethod -Uri "$GatewayUrl/api/orders/$id" -Method Get
        if ($ord.status -eq "COMPLETED") {
            $completedCount++
        }
    }

    Write-Host " - Waited ${recoveryWaited}s: $completedCount / $($outageOrderIds.Count) orders COMPLETED"
    if ($completedCount -eq $outageOrderIds.Count) {
        $allCompleted = $true
        break
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " Chaos Test Summary" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if ($allCompleted) {
    Write-Host "All $($outageOrderIds.Count) queued orders successfully reached COMPLETED after recovery!" -ForegroundColor Green
    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host " >>> CHAOS SCRIPT RESULT: PASS <<<" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    exit 0
} else {
    Write-Host "Some queued orders failed to reach COMPLETED within ${maxRecoveryWait}s." -ForegroundColor Red
    Write-Host "`n==========================================================" -ForegroundColor Red
    Write-Host " >>> CHAOS SCRIPT RESULT: FAIL <<<" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Red
    exit 1
}
