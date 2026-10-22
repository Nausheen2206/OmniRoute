# verify-observability.ps1
# Script to verify Phase 1 Observability: Prometheus targets, custom metrics, and Jaeger distributed traces

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " OmniRoute Phase 1 Observability Verification" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Check Prometheus Targets
Write-Host "`n[1/4] Verifying Prometheus Targets..." -ForegroundColor Yellow
try {
    $targetsResponse = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/targets" -Method Get -TimeoutSec 5
    $active = $targetsResponse.data.activeTargets
    foreach ($t in $active) {
        $job = $t.labels.job
        $health = $t.health
        $url = $t.scrapeUrl
        Write-Host " - Target: $job ($url) -> $health" -ForegroundColor Green
    }
} catch {
    Write-Host " Failed to query Prometheus targets: $_" -ForegroundColor Red
}

# 2. Check Custom Prometheus Metrics
Write-Host "`n[2/4] Querying Custom Metrics from Prometheus..." -ForegroundColor Yellow
$metricQueries = @(
    "omniroute_orders_total",
    "omniroute_orders_completed_total",
    "omniroute_orders_failed_total",
    "omniroute_orders_active",
    "omniroute_warehouse_reservations_total",
    "omniroute_warehouse_reservations_released_total",
    "omniroute_payment_charges_total",
    "omniroute_payment_failures_total",
    "omniroute_fleet_trucks_tracked",
    "omniroute_fleet_assignments_made_total",
    "omniroute_simulator_events_sent_total",
    "resilience4j_circuitbreaker_state"
)

foreach ($metric in $metricQueries) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/query?query=$metric" -Method Get -TimeoutSec 5
        $count = $res.data.result.Count
        if ($count -gt 0) {
            $sample = $res.data.result[0].value[1]
            Write-Host " Metric '$metric': FOUND ($count series, sample val: $sample)" -ForegroundColor Green
        } else {
            Write-Host " Metric '$metric': 0 series found" -ForegroundColor Yellow
        }
    } catch {
        Write-Host " Metric '$metric': Error querying: $_" -ForegroundColor Red
    }
}

# 3. Check Jaeger Distributed Traces
Write-Host "`n[3/4] Verifying Distributed Tracing in Jaeger..." -ForegroundColor Yellow
try {
    $servicesRes = Invoke-RestMethod -Uri "http://localhost:16686/api/services" -Method Get -TimeoutSec 5
    Write-Host " Registered Services in Jaeger: $($servicesRes.data -join ', ')" -ForegroundColor Green

    # Find a trace for order creation and saga orchestration
    $tracesRes = Invoke-RestMethod -Uri "http://localhost:16686/api/traces?service=order-service&operation=http%20post%20/orders&limit=5" -Method Get -TimeoutSec 5
    $bestTrace = $null
    $maxServices = 0

    if ($tracesRes.data.Count -gt 0) {
        $bestTrace = $tracesRes.data[0]
    }

    if ($bestTrace) {
        $traceId = $bestTrace.traceID
        Write-Host " Found Multi-Service Distributed Trace: $traceId" -ForegroundColor Green
        Write-Host " Total Spans: $($bestTrace.spans.Count)" -ForegroundColor Green
        
        $svcNames = @()
        foreach ($procKey in $bestTrace.processes.PSObject.Properties.Name) {
            $svcNames += $bestTrace.processes.$procKey.serviceName
        }
        $uniqueSvcs = $svcNames | Select-Object -Unique
        Write-Host " Participating Services ($($uniqueSvcs.Count)): $($uniqueSvcs -join ' -> ')" -ForegroundColor Cyan
        
        Write-Host " Trace Span Breakdown:" -ForegroundColor DarkCyan
        foreach ($s in ($bestTrace.spans | Select-Object -First 12)) {
            $proc = $bestTrace.processes.($s.processID)
            $durationMs = [math]::Round($s.duration / 1000.0, 2)
            Write-Host ("   [{0, -20}] {1, -35} ({2} ms)" -f $proc.serviceName, $s.operationName, $durationMs) -ForegroundColor Gray
        }
    } else {
        Write-Host " No traces found for order-service yet." -ForegroundColor Yellow
    }
} catch {
    Write-Host " Failed to query Jaeger: $_" -ForegroundColor Red
}

# 4. Check Grafana Dashboard
Write-Host "`n[4/4] Verifying Grafana Dashboard Provisioning..." -ForegroundColor Yellow
try {
    $grafanaRes = Invoke-RestMethod -Uri "http://localhost:3000/api/search?type=dash-db" -Headers @{ Authorization = "Basic YWRtaW46YWRtaW4=" } -Method Get -TimeoutSec 5
    foreach ($d in $grafanaRes) {
        Write-Host " Dashboard: '$($d.title)' (uid: $($d.uid), url: $($d.url))" -ForegroundColor Green
    }
} catch {
    Write-Host " Failed to query Grafana: $_" -ForegroundColor Red
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " Observability Verification Complete!" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
