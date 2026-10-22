CREATE TABLE IF NOT EXISTS inventory (
    sku VARCHAR(100) PRIMARY KEY,
    available_qty INT NOT NULL CHECK (available_qty >= 0)
);

CREATE TABLE IF NOT EXISTS reservations (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    qty INT NOT NULL,
    status VARCHAR(20) NOT NULL, -- ACTIVE, RELEASED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reservations_order_sku UNIQUE (order_id, sku)
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(100) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reservations_order_id ON reservations(order_id);

-- Seed initial inventory with at least 10 SKUs having 1000 units each
INSERT INTO inventory (sku, available_qty) VALUES
    ('SKU-001', 1000),
    ('SKU-002', 1000),
    ('SKU-003', 1000),
    ('SKU-004', 1000),
    ('SKU-005', 1000),
    ('SKU-006', 1000),
    ('SKU-007', 1000),
    ('SKU-008', 1000),
    ('SKU-009', 1000),
    ('SKU-010', 1000)
ON CONFLICT (sku) DO NOTHING;
