-- ==============================================================================
-- OmniRoute Stock Consistency Verification Script
-- Invariant: Initial Stock (1000) - Active/Held Stock = Current Available Stock
-- Must return 0 inconsistent rows for PASS
-- ==============================================================================

-- Connect to warehouse_db
\c warehouse_db;

WITH expected_vs_actual AS (
    SELECT 
        i.sku,
        1000 AS initial_qty,
        COALESCE(SUM(CASE WHEN r.status = 'ACTIVE' THEN r.qty ELSE 0 END), 0) AS reserved_qty,
        i.available_qty,
        (1000 - COALESCE(SUM(CASE WHEN r.status = 'ACTIVE' THEN r.qty ELSE 0 END), 0)) AS expected_available_qty,
        CASE 
            WHEN i.available_qty = (1000 - COALESCE(SUM(CASE WHEN r.status = 'ACTIVE' THEN r.qty ELSE 0 END), 0)) 
            THEN 'CONSISTENT' 
            ELSE 'INCONSISTENT' 
        END AS consistency_status
    FROM inventory i
    LEFT JOIN reservations r ON i.sku = r.sku
    GROUP BY i.sku, i.available_qty
)
SELECT 
    sku,
    initial_qty,
    reserved_qty,
    available_qty,
    expected_available_qty,
    consistency_status
FROM expected_vs_actual
ORDER BY sku;

-- Inconsistency Check: Count any violated rows
SELECT 
    CASE 
        WHEN COUNT(*) = 0 THEN 'PASS: All inventory stocks are perfectly consistent!'
        ELSE 'FAIL: Inconsistency detected in ' || COUNT(*) || ' SKUs!'
    END AS verification_result
FROM (
    SELECT 
        i.sku
    FROM inventory i
    LEFT JOIN reservations r ON i.sku = r.sku
    GROUP BY i.sku, i.available_qty
    HAVING i.available_qty <> (1000 - COALESCE(SUM(CASE WHEN r.status = 'ACTIVE' THEN r.qty ELSE 0 END), 0))
) violated_records;
