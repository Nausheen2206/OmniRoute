# Test Phase 2 Happy Path
$gatewayUrl = "http://localhost:8080"

Write-Host "=== Phase 2: Happy Path Verification ===" -ForegroundColor Cyan

# 1. Check initial inventory
Write-Host "1. Checking initial inventory for SKU-001..." -ForegroundColor Yellow
$invBefore = Invoke-RestMethod -Uri "$gatewayUrl/api/inventory/SKU-001" -Method Get
Write-Host "Initial available quantity for SKU-001: $($invBefore.availableQty)" -ForegroundColor Green

# 2. Place an order
$orderBody = @{
    customerId = "cust-phase2-happy"
    items = @(
        @{
            sku = "SKU-001"
            quantity = 5
        }
    )
    totalAmount = 100.00
} | ConvertTo-Json

Write-Host "2. Placing order for 5 units of SKU-001..." -ForegroundColor Yellow
$postResponse = Invoke-RestMethod -Uri "$gatewayUrl/api/orders" -Method Post -Body $orderBody -ContentType "application/json"
$orderId = $postResponse.orderId
Write-Host "Order placed! Order ID: $orderId, Initial Status: $($postResponse.status)" -ForegroundColor Green

# 3. Poll until PAYMENT_COMPLETED
Write-Host "3. Polling order status until PAYMENT_COMPLETED..." -ForegroundColor Yellow
$maxAttempts = 15
$attempt = 0
$finalOrder = $null

while ($attempt -lt $maxAttempts) {
    Start-Sleep -Seconds 1
    $attempt++
    $finalOrder = Invoke-RestMethod -Uri "$gatewayUrl/api/orders/$orderId" -Method Get
    Write-Host "Attempt $attempt : Status = $($finalOrder.status)"
    if ($finalOrder.status -eq "PAYMENT_COMPLETED") {
        Write-Host "SUCCESS: Order reached PAYMENT_COMPLETED!" -ForegroundColor Green
        break
    }
}

# 4. Check inventory after
Write-Host "4. Checking updated inventory for SKU-001..." -ForegroundColor Yellow
$invAfter = Invoke-RestMethod -Uri "$gatewayUrl/api/inventory/SKU-001" -Method Get
Write-Host "Updated available quantity for SKU-001: $($invAfter.availableQty)" -ForegroundColor Green

# 5. Check Saga history
Write-Host "5. Saga Step History:" -ForegroundColor Yellow
$finalOrder.sagaHistory | ForEach-Object {
    Write-Host " - Step: $($_.step) | EventId: $($_.eventId) | Time: $($_.createdAt)"
}

if ($invAfter.availableQty -eq ($invBefore.availableQty - 5) -and $finalOrder.status -eq "PAYMENT_COMPLETED") {
    Write-Host "=== PHASE 2 VERIFICATION PASSED ===" -ForegroundColor Green
} else {
    Write-Host "=== PHASE 2 VERIFICATION FAILED ===" -ForegroundColor Red
}
