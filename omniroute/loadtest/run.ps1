# ==============================================================================
# OmniRoute Performance & Load Test Runner
# Orchestrates k6 in Docker (pinned grafana/k6:0.54.0) with 50 VUs for 60s
# Verifies Saga settlement, stock invariants, and failure compensation
# ==============================================================================

param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$K6Image = "grafana/k6:0.54.0"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " OmniRoute 50-VU Load Test & Invariant Verification Runner" -ForegroundColor Cyan
Write-Host " Target Gateway: $GatewayUrl" -ForegroundColor Cyan
Write-Host " k6 Docker Image: $K6Image" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Health Check
Write-Host "`n[Step 1/6] Checking system health..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 5
    if ($health.status -ne "UP") {
        Write-Host "LOAD TEST ABORTED: Gateway status is $($health.status)" -ForegroundColor Red
        exit 1
    }
    Write-Host " Gateway is healthy (UP)." -ForegroundColor Green
} catch {
    Write-Host "LOAD TEST ABORTED: Unable to connect to Gateway at ${GatewayUrl}: $_" -ForegroundColor Red
    exit 1
}

# 2. Record Initial Warehouse Stock
Write-Host "`n[Step 2/6] Recording initial warehouse stock..." -ForegroundColor Yellow
$initialInventoryList = Invoke-RestMethod -Uri "$GatewayUrl/api/inventory" -Method Get
$initialStock = @{}
foreach ($item in $initialInventoryList) {
    $initialStock[$item.sku] = [int]$item.availableQty
    Write-Host ("  - {0,-10}: {1,6} available" -f $item.sku, $item.availableQty)
}

# Clean any prior summary.json
$summaryPath = Join-Path $PSScriptRoot "summary.json"
if (Test-Path $summaryPath) {
    Remove-Item $summaryPath -Force
}

$RunId = (Get-Date -Format "yyyyMMdd-HHmmss")
Write-Host " Test Run ID: $RunId" -ForegroundColor Cyan

# 3. Execute k6 Load Test in Docker
Write-Host "`n[Step 3/6] Launching k6 container (50 VUs, 60s duration, 85/15 mix)..." -ForegroundColor Yellow
$dockerPath = (Resolve-Path $PSScriptRoot).Path.Replace('\', '/')

$k6Cmd = @(
    "run", "--rm",
    "--network", "omniroute_omniroute-net",
    "-v", "${dockerPath}:/scripts",
    "-e", "TARGET_URL=http://omniroute-gateway:8080",
    "-e", "TEST_RUN_ID=$RunId",
    $K6Image,
    "run", "/scripts/orders.js",
    "--summary-export=/scripts/summary.json"
)

$k6Start = [System.Diagnostics.Stopwatch]::StartNew()
& docker $k6Cmd
$k6Exit = $LASTEXITCODE
$k6Start.Stop()

$k6ExitColor = if ($k6Exit -eq 0) { "Green" } else { "Yellow" }
Write-Host " k6 execution finished in $($k6Start.Elapsed.TotalSeconds.ToString('F1'))s (Exit code: $k6Exit)" -ForegroundColor $k6ExitColor

# 4. Wait for Saga Orchestrator to Settle
Write-Host "`n[Step 4/6] Waiting for Saga orchestrator to settle all orders..." -ForegroundColor Yellow
$maxSettleSeconds = 90
$settleWaited = 0
$settled = $false

while ($settleWaited -lt $maxSettleSeconds) {
    Start-Sleep -Seconds 2
    $settleWaited += 2

    $pendingCountStr = docker exec omniroute-postgres psql -U postgres -d orders_db -t -A -c "SELECT count(*) FROM orders WHERE customer_id LIKE 'CUST-k6-$RunId-%' AND status NOT IN ('COMPLETED', 'FAILED');"
    $pendingCount = 0
    if ($pendingCountStr) {
        [int]::TryParse($pendingCountStr.Trim(), [ref]$pendingCount) | Out-Null
    }

    Write-Host ("  Waited {0,2}s: In-progress Sagas remaining: {1}" -f $settleWaited, $pendingCount)
    if ($pendingCount -eq 0) {
        $settled = $true
        break
    }
}

if (-not $settled) {
    Write-Host "WARNING: Sagas did not fully settle within ${maxSettleSeconds}s!" -ForegroundColor Red
} else {
    Write-Host " All Sagas have reached terminal state (COMPLETED or FAILED)!" -ForegroundColor Green
}

# 5. Fetch Final Warehouse Stock & Query Postgres Metrics
Write-Host "`n[Step 5/6] Verifying order statistics and database invariants..." -ForegroundColor Yellow
$finalInventoryList = Invoke-RestMethod -Uri "$GatewayUrl/api/inventory" -Method Get
$finalStock = @{}
foreach ($item in $finalInventoryList) {
    $finalStock[$item.sku] = [int]$item.availableQty
}

# Total orders placed by k6
$totalOrdersStr = docker exec omniroute-postgres psql -U postgres -d orders_db -t -A -c "SELECT count(*) FROM orders WHERE customer_id LIKE 'CUST-k6-$RunId-%';"
$completedOrdersStr = docker exec omniroute-postgres psql -U postgres -d orders_db -t -A -c "SELECT count(*) FROM orders WHERE customer_id LIKE 'CUST-k6-$RunId-%' AND status = 'COMPLETED';"
$failedOrdersStr = docker exec omniroute-postgres psql -U postgres -d orders_db -t -A -c "SELECT count(*) FROM orders WHERE customer_id LIKE 'CUST-k6-$RunId-%' AND status = 'FAILED';"

$totalOrders = [int]$totalOrdersStr.Trim()
$completedOrders = [int]$completedOrdersStr.Trim()
$failedOrders = [int]$failedOrdersStr.Trim()

$actualFailRate = if ($totalOrders -gt 0) { [Math]::Round(($failedOrders / $totalOrders) * 100, 2) } else { 0 }

# Query consumed items per SKU for completed orders
$consumptionRaw = docker exec omniroute-postgres psql -U postgres -d orders_db -t -A -F "," -c "SELECT item->>'sku' AS sku, SUM((item->>'quantity')::int) AS qty FROM orders, json_array_elements(items_json::json) AS item WHERE status = 'COMPLETED' AND customer_id LIKE 'CUST-k6-$RunId-%' GROUP BY 1;"

$consumedPerSku = @{}
foreach ($line in ($consumptionRaw -split "`r?`n")) {
    $parts = $line.Trim().Split(',')
    if ($parts.Count -eq 2 -and $parts[0]) {
        $consumedPerSku[$parts[0].Trim()] = [int]$parts[1].Trim()
    }
}

# 6. Parse k6 Summary Metrics
$summaryData = $null
if (Test-Path $summaryPath) {
    try {
        $summaryData = Get-Content $summaryPath -Raw | ConvertFrom-Json
    } catch {}
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " OmniRoute Load Test & Performance Results" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if ($summaryData -and $summaryData.metrics) {
    $dur = $summaryData.metrics.http_req_duration
    $reqs = $summaryData.metrics.http_reqs
    $p99Val = if ($dur.PSObject.Properties['p(99)']) { $dur.'p(99)' } else { $dur.'p(95)' }
    Write-Host ("Total HTTP Submissions : {0}" -f $reqs.count)
    Write-Host ("Submission Throughput  : {0:F2} req/sec" -f $reqs.rate)
    Write-Host ("HTTP Latency Avg       : {0:F2} ms" -f $dur.avg)
    Write-Host ("HTTP Latency Med (p50) : {0:F2} ms" -f $dur.med)
    Write-Host ("HTTP Latency p90       : {0:F2} ms" -f $dur.'p(90)')
    Write-Host ("HTTP Latency p95       : {0:F2} ms" -f $dur.'p(95)')
    Write-Host ("HTTP Latency p99       : {0:F2} ms" -f $p99Val)
    Write-Host ("HTTP Latency Max       : {0:F2} ms" -f $dur.max)
}

Write-Host "`n--- Saga Order Processing ---" -ForegroundColor Cyan
Write-Host ("Total k6 Orders Placed : {0}" -f $totalOrders)
Write-Host ("Completed Orders       : {0}" -f $completedOrders) -ForegroundColor Green
Write-Host ("Failed / Compensated   : {0}" -f $failedOrders) -ForegroundColor Red
Write-Host ("Measured Failure Rate  : {0}% (Target: ~15%)" -f $actualFailRate)
Write-Host ("Saga Settlement Time   : {0}s" -f $settleWaited)

Write-Host "`n--- Stock Invariant Verification (Initial - Final == Consumed) ---" -ForegroundColor Cyan
Write-Host ("{0,-10} | {1,-8} | {2,-10} | {3,-8} | {4,-10} | {5,-12}" -f "SKU", "Initial", "Consumed", "Final", "Expected", "Status")
Write-Host ("-" * 72)

$allConsistent = $true
foreach ($sku in ($initialStock.Keys | Sort-Object)) {
    $init = $initialStock[$sku]
    $fin = $finalStock[$sku]
    $consumed = if ($consumedPerSku.ContainsKey($sku)) { $consumedPerSku[$sku] } else { 0 }
    $expectedFinal = $init - $consumed
    $isConsistent = ($fin -eq $expectedFinal)

    if (-not $isConsistent) {
        $allConsistent = $false
    }

    $statusStr = if ($isConsistent) { "CONSISTENT" } else { "VIOLATED" }
    $statusColor = if ($isConsistent) { "Green" } else { "Red" }

    Write-Host ("{0,-10} | {1,8} | {2,10} | {3,8} | {4,10} | " -f $sku, $init, $consumed, $fin, $expectedFinal) -NoNewline
    Write-Host ("{0,-12}" -f $statusStr) -ForegroundColor $statusColor
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
if ($allConsistent -and $settled -and ($totalOrders -gt 0)) {
    Write-Host " >>> LOAD TEST & INVARIANT VERIFICATION: PASS <<<" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host " >>> LOAD TEST & INVARIANT VERIFICATION: FAIL <<<" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Cyan
    exit 1
}
