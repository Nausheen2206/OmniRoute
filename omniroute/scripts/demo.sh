#!/usr/bin/env bash
# ==============================================================================
# OmniRoute Demo & Invariant Consistency Verification Script
# Generates 100 orders (including random $13.13 payment failure injections),
# monitors Sagas until quiescent, and verifies inventory invariants.
# ==============================================================================

set -e

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TOTAL_ORDERS=100
SKUS=("SKU-001" "SKU-002" "SKU-003" "SKU-004" "SKU-005")

echo "=========================================================="
echo " Starting OmniRoute Load & Consistency Demo (100 Orders)"
echo " Target Gateway: $GATEWAY_URL"
echo "=========================================================="

# 1. Healthcheck
echo "[1/4] Checking Gateway health..."
if ! curl -sf "$GATEWAY_URL/actuator/health" > /dev/null 2>&1; then
    echo "ERROR: Gateway at $GATEWAY_URL is unreachable. Please start docker-compose up -d first."
    exit 1
fi
echo "Gateway is HEALTHY."

# 2. Record initial stock
echo "[2/4] Fetching initial warehouse stock..."
INITIAL_STOCK_JSON=$(curl -s "$GATEWAY_URL/api/inventory")

# 3. Disseminate 100 orders
echo "[3/4] Dispatching $TOTAL_ORDERS orders (with ~10% forced payment failures for compensation)..."
declare -A PLACED_ORDERS

for i in $(seq 1 $TOTAL_ORDERS); do
    SKU=${SKUS[$((RANDOM % ${#SKUS[@]}))]}
    QTY=$(( (RANDOM % 4) + 1 ))
    
    # 10% chance of $13.13 forced failure
    if [ $((RANDOM % 10)) -eq 0 ]; then
        AMT="13.13"
    else
        AMT="$(( (RANDOM % 150) + 30 )).00"
    fi
    
    PAYLOAD=$(cat <<EOF
{
  "customerId": "DEMO-USER-$i",
  "items": [{"sku": "$SKU", "quantity": $QTY}],
  "totalAmount": $AMT
}
EOF
)
    RESP=$(curl -s -X POST "$GATEWAY_URL/api/orders" \
         -H "Content-Type: application/json" \
         -d "$PAYLOAD")
    
    if [ $((i % 20)) -eq 0 ]; then
        echo " - Dispatched $i / $TOTAL_ORDERS orders..."
    fi
done

echo "All $TOTAL_ORDERS orders dispatched! Waiting for Saga state settlement..."

# 4. Wait for settling
SETTLED=false
MAX_WAIT_SECONDS=40
WAITED=0

while [ $WAITED -lt $MAX_WAIT_SECONDS ]; do
    sleep 2
    WAITED=$((WAITED + 2))
    
    ORDERS_JSON=$(curl -s "$GATEWAY_URL/api/orders")
    
    # Check if any orders remain in intermediate states
    IN_PROGRESS=$(echo "$ORDERS_JSON" | grep -o '"status":"[^"]*"' | grep -v 'COMPLETED' | grep -v 'FAILED' | wc -l || true)
    
    echo "Wait time: ${WAITED}s | Active in-progress Sagas: $IN_PROGRESS"
    if [ "$IN_PROGRESS" -eq 0 ]; then
        SETTLED=true
        break
    fi
done

echo "=========================================================="
echo " Order Processing Summary"
echo "=========================================================="

COMPLETED_COUNT=$(echo "$ORDERS_JSON" | grep -o '"status":"COMPLETED"' | wc -l || true)
FAILED_COUNT=$(echo "$ORDERS_JSON" | grep -o '"status":"FAILED"' | wc -l || true)
echo "Completed Orders : $COMPLETED_COUNT"
echo "Failed Orders    : $FAILED_COUNT (Compensated)"

echo ""
echo "=========================================================="
echo " Invariant Verification: Initial - Completed_Orders = Current"
echo "=========================================================="

# Fetch current inventory
CURRENT_INVENTORY=$(curl -s "$GATEWAY_URL/api/inventory")

# Evaluate consistency per SKU using python or node if available
CONSISTENCY_CHECK=$(python3 -c "
import sys, json

try:
    orders = json.loads('''$ORDERS_JSON''')
    inventory = json.loads('''$CURRENT_INVENTORY''')
except Exception as e:
    sys.exit(1)

# Sum completed quantities per SKU
completed_usage = {}
for o in orders:
    if o.get('status') == 'COMPLETED':
        for it in o.get('items', []):
            sku = it.get('sku')
            qty = it.get('quantity', 0)
            completed_usage[sku] = completed_usage.get(sku, 0) + qty

all_pass = True
print(f'{\"SKU\":<10} | {\"Initial\":<8} | {\"Consumed\":<10} | {\"Available\":<10} | {\"Status\":<10}')
print('-' * 55)

for inv in inventory:
    sku = inv.get('sku')
    avail = inv.get('availableQty', 0)
    initial = 1000
    consumed = completed_usage.get(sku, 0)
    expected = initial - consumed
    
    status = 'CONSISTENT' if avail == expected else 'VIOLATED'
    if avail != expected:
        all_pass = False
    print(f'{sku:<10} | {initial:<8} | {consumed:<10} | {avail:<10} | {status:<10}')

if all_pass:
    print('\nRESULT: PASS')
    sys.exit(0)
else:
    print('\nRESULT: FAIL')
    sys.exit(1)
" || echo "FAIL")

echo "$CONSISTENCY_CHECK"

if echo "$CONSISTENCY_CHECK" | grep -q "RESULT: PASS"; then
    echo "=========================================================="
    echo " >>> DEMO RESULT: PASS <<<"
    echo "=========================================================="
    exit 0
else
    echo "=========================================================="
    echo " >>> DEMO RESULT: FAIL <<<"
    echo "=========================================================="
    exit 1
fi
