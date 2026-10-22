# ==============================================================================
# OmniRoute Demo & Invariant Consistency Verification Script (PowerShell)
# Generates 100 orders (including random $13.13 payment failure injections),
# monitors Sagas until quiescent, and verifies inventory invariants.
# ==============================================================================

param(
    [string]$GatewayUrl = "http://localhost:8080",
    [int]$TotalOrders = 100
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting OmniRoute Load & Consistency Demo ($TotalOrders Orders)" -ForegroundColor Cyan
Write-Host " Target Gateway: $GatewayUrl" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Healthcheck
Write-Host "[1/4] Checking Gateway health..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 5
    Write-Host "Gateway status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Gateway at $GatewayUrl is unreachable. Ensure docker compose up -d is running." -ForegroundColor Red
    exit 1
}

# 2. Track initial state
Write-Host "[2/4] Fetching initial warehouse stock..." -ForegroundColor Yellow
$initialStock = Invoke-RestMethod -Uri "$GatewayUrl/api/inventory" -Method Get

$skus = @("SKU-001", "SKU-002", "SKU-003", "SKU-004", "SKU-005")
$rng = [System.Random]::new()

# 3. Disseminate orders
Write-Host "[3/4] Dispatching $TotalOrders orders with simulated payment failure mix..." -ForegroundColor Yellow

for ($i = 1; $i -le $TotalOrders; $i++) {
    $sku = $skus[$rng.Next($skus.Count)]
    $qty = $rng.Next(1, 5)
    
    # 10% chance of $13.13 forced failure
    if ($rng.Next(10) -eq 0) {
        $amount = 13.13
    } else {
        $amount = [Math]::Round(($rng.NextDouble() * 150 + 30), 2)
    }

    $body = @{
        customerId = "DEMO-USER-$i"
        items = @(
            @{
                sku = $sku
                quantity = $qty
            }
        )
        totalAmount = $amount
    } | ConvertTo-Json -Depth 5

    try {
        $res = Invoke-RestMethod -Uri "$GatewayUrl/api/orders" -Method Post -Body $body -ContentType "application/json"
    } catch {
        Write-Warning "Failed placing order $i: $_"
    }

    if ($i % 20 -eq 0) {
        Write-Host " - Dispatched $i / $TotalOrders orders..."
    }
}

Write-Host "All $TotalOrders orders dispatched! Waiting for Saga state settlement..." -ForegroundColor Yellow

# 4. Wait for settling
$maxWaitSeconds = 60
$waited = 0
$settled = $false
$orders = @()

while ($waited -lt $maxWaitSeconds) {
    Start-Sleep -Seconds 2
    $waited += 2
    
    $orders = Invoke-RestMethod -Uri "$GatewayUrl/api/orders" -Method Get
    $inProgress = $orders | Where-Object { $_.status -notin @("COMPLETED", "FAILED") }
    
    Write-Host "Wait time: ${waited}s | In-progress Sagas: $($inProgress.Count)"
    if ($inProgress.Count -eq 0) {
        $settled = $true
        break
    }
}

$completed = $orders | Where-Object { $_.status -eq "COMPLETED" }
$failed = $orders | Where-Object { $_.status -eq "FAILED" }

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Order Processing Summary" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Completed Orders : $($completed.Count)" -ForegroundColor Green
Write-Host "Failed Orders    : $($failed.Count) (Compensated)" -ForegroundColor Red

# 5. Invariant Consistency Check
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Invariant Verification: Initial - Completed_Orders = Current" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$currentInventory = Invoke-RestMethod -Uri "$GatewayUrl/api/inventory" -Method Get

# Calculate completed order item consumption per SKU
$completedConsumption = @{}
foreach ($ord in $completed) {
    foreach ($item in $ord.items) {
        if (-not $completedConsumption.ContainsKey($item.sku)) {
            $completedConsumption[$item.sku] = 0
        }
        $completedConsumption[$item.sku] += $item.quantity
    }
}

$allConsistent = $true
Write-Host ("{0,-10} | {1,-8} | {2,-10} | {3,-10} | {4,-12}" -f "SKU", "Initial", "Consumed", "Available", "Status")
Write-Host ("-" * 60)

foreach ($inv in $currentInventory) {
    $sku = $inv.sku
    $initial = 1000
    $consumed = if ($completedConsumption.ContainsKey($sku)) { $completedConsumption[$sku] } else { 0 }
    $expected = $initial - $consumed
    $actual = $inv.availableQty

    $status = if ($actual -eq $expected) { "CONSISTENT" } else { "VIOLATED" }
    if ($status -eq "VIOLATED") { $allConsistent = $false }

    $color = if ($status -eq "CONSISTENT") { "Green" } else { "Red" }
    Write-Host ("{0,-10} | {1,-8} | {2,-10} | {3,-10} | {4,-12}" -f $sku, $initial, $consumed, $actual, $status) -ForegroundColor $color
}

Write-Host ""
if ($allConsistent) {
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host " >>> DEMO RESULT: PASS <<<" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    exit 0
} else {
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host " >>> DEMO RESULT: FAIL <<<" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Red
    exit 1
}
