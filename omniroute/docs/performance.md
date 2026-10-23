# OmniRoute Performance & Load Test Report

## Executive Summary
This report documents the performance, latency characteristics, and data consistency of **OmniRoute** under a sustained 50 virtual user (VU) distributed load test for 60 seconds using pinned k6 (`grafana/k6:0.54.0`). The test exercises the complete asynchronous event-driven Saga orchestration across the Spring Cloud Gateway, Kafka cluster, Order Orchestrator, Warehouse Service (pessimistic row locking), Payment Service (with chaos failure injection), and Fleet Routing Service.

---

## 1. Test Configuration

| Parameter | Value |
|---|---|
| **Virtual Users (VUs)** | 50 concurrent virtual users |
| **Duration** | 60 seconds (sustained) |
| **Tool** | `grafana/k6:0.54.0` in Docker (`omniroute_omniroute-net`) |
| **Runner** | `loadtest/run.ps1` (automated health, k6 execution, Saga settlement polling, SQL invariant verification) |
| **Target Gateway** | `http://omniroute-gateway:8080/api/orders` |
| **Workload Mix** | **85% Normal Orders**: random SKU (`SKU-001` - `SKU-010`), quantity 1–3, price $20–$180<br>**15% Forced Failures**: total amount exactly `$13.13` (deterministic payment decline triggering compensating saga) |
| **Host Environment** | Windows 11 x64, Docker Desktop 4.55.0, 16 vCPUs |

---

## 2. Ingress & HTTP Gateway Metrics

Measured by k6 across **3,697 total requests**:

| Metric | Target SLA | Measured Value | Status |
|---|---|---|---|
| **Total HTTP Submissions** | > 2,000 requests | **3,697 requests** | PASS |
| **Submission Throughput** | > 30 req/sec | **65.26 req/sec** | PASS |
| **HTTP Success Rate (202 Accepted)** | > 99.0% | **100.00%** (0 errors) | PASS |
| **Latency Average** | < 100 ms | **16.30 ms** | PASS |
| **Latency Median (p50)** | < 50 ms | **11.58 ms** | PASS |
| **Latency p90** | < 100 ms | **19.15 ms** | PASS |
| **Latency p95** | < 200 ms | **25.03 ms** | PASS |
| **Latency p99** | < 500 ms | **236.50 ms** | PASS |
| **Latency Max** | — | **306.95 ms** | PASS |

---

## 3. End-to-End Saga Orchestration & Resilience

After k6 submission ceased, the runner monitored the Postgres database (`orders_db`) every 2 seconds until all in-flight Sagas settled to terminal states (`COMPLETED` or `FAILED`).

| Saga Metric | Expected | Measured |
|---|---|---|
| **Total Sagas Orchestrated** | 3,697 | **3,697** |
| **Sagas Settled to Terminal State** | 100% | **100.00% (3,697 / 3,697)** |
| **Completed Orders (Happy Path)** | ~85% | **3,103 orders (83.93%)** |
| **Failed / Compensated Orders ($13.13)** | ~15% | **594 orders (16.07%)** |
| **Failure Rate Precision** | Target ~15% | **16.07% (delta: +1.07%)** |
| **Saga Settlement Time** | < 60s | **26.0 seconds** |
| **Sagas In-Flight After Settlement** | 0 | **0** |

---

## 4. Stock Invariant Verification

**Invariant Rule**: For every SKU:
$$\text{Initial Available Stock} - \text{Final Available Stock} = \sum_{\text{COMPLETED}} \text{Quantity}$$
Failed orders ($13.13 payment failure) lock inventory during the saga but release it back to the warehouse during saga compensation (`ReleaseInventory` -> `InventoryReleased`).

```
SKU        | Initial  | Consumed   | Final    | Expected   | Status      
------------------------------------------------------------------------
SKU-001    |     1000 |        617 |      383 |        383 | CONSISTENT  
SKU-002    |     1000 |        670 |      330 |        330 | CONSISTENT  
SKU-003    |     1000 |        684 |      316 |        316 | CONSISTENT  
SKU-004    |     1000 |        597 |      403 |        403 | CONSISTENT  
SKU-005    |     1000 |        605 |      395 |        395 | CONSISTENT  
SKU-006    |     1000 |        586 |      414 |        414 | CONSISTENT  
SKU-007    |     1000 |        632 |      368 |        368 | CONSISTENT  
SKU-008    |     1000 |        621 |      379 |        379 | CONSISTENT  
SKU-009    |     1000 |        620 |      380 |        380 | CONSISTENT  
SKU-010    |     1000 |        578 |      422 |        422 | CONSISTENT  
```

**Result: 10/10 SKUs CONSISTENT (0 stock leaks, 0 ghost reservations).**

---

## 5. Architectural Observations & Bottleneck Analysis

1. **Ingress Throughput (Spring Cloud Gateway)**:
   - Handled 65.26 req/s with a p50 latency of 11.58ms and p95 of 25.03ms. Non-blocking Netty gateway layer introduced negligible overhead.
2. **Postgres Pessimistic Row Locking (`SELECT ... FOR UPDATE`)**:
   - `WarehouseService` sorts items by SKU alphabetically before acquiring pessimistic locks. Even under 50 concurrent VUs hammering the same 10 SKUs, **zero deadlocks** were detected.
3. **Fleet Routing Capacity & Turnaround**:
   - Simulated 200 telemetry trucks. With turnaround configured to 2s, maximum allocation throughput is $\frac{200}{2} = 100$ orders/sec, which comfortably outpaced the 65 req/s order ingress, allowing the entire 3,697-order queue to clear within 26 seconds.
4. **Resilience & Compensation Accuracy**:
   - 594 orders forced to fail with $13.13 were processed, intercepted by payment rejection, and reliably compensated via warehouse inventory release without a single lost reservation.
