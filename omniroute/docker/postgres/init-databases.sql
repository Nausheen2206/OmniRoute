-- PostgreSQL database initialization script for OmniRoute microservices
SELECT 'CREATE DATABASE orders_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'orders_db')\gexec
SELECT 'CREATE DATABASE payments_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payments_db')\gexec
SELECT 'CREATE DATABASE warehouse_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'warehouse_db')\gexec

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE orders_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE payments_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE warehouse_db TO postgres;
