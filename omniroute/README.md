# OmniRoute: Event-Driven Distributed Order Fulfillment & Fleet Routing

OmniRoute is an event-driven, microservices-based logistics and order fulfillment platform built with **Java 21**, **Spring Boot 3.3**, **Apache Kafka (KRaft)**, **PostgreSQL 16**, and a real-time **JavaFX 21** desktop client. It coordinates distributed transactions via the **Saga Pattern (Orchestration)**, incorporates automated compensations and watchdogs, and dynamically assigns vehicles using real-time GPS telemetry and Haversine geodesic routing.

---

## 1. System Architecture

```mermaid
flowchart TD
    subgraph ClientLayer["Presentation & Client Layer"]
        UI["JavaFX Desktop Client (Port 8080 Target)"]
        CLI["Demo Load Scripts (demo.sh / demo.ps1)"]
    end

    subgraph GatewayLayer["API Gateway (Port 8080)"]
        GW["Spring Cloud Gateway\n(CORS, Path Rewriting, Resilience)"]
    end

    subgraph Microservices["Backend Services"]
        OS["Order Service\n(Port 8081)\nSaga Orchestrator & State Machine"]
        WS["Warehouse Service\n(Port 8083)\nPessimistic Locking & Reservations"]
        PS["Payment Service\n(Port 8082)\nFailure Simulation & Authorizations"]
        FS["Fleet Routing Service\n(Port 8084)\nGeodesic Engine & In-memory Tracking"]
        TS["Telemetry Simulator\n(Port 8085)\n200 GPS Virtual Threads"]
    end

    subgraph Messaging["Kafka Event Bus (KRaft)"]
        TOPIC_ORDERS["omniroute.orders.events"]
        TOPIC_PAY_CMD["omniroute.payment.commands"]
        TOPIC_PAY_EVT["omniroute.payment.events"]
        TOPIC_WH_CMD["omniroute.warehouse.commands"]
        TOPIC_WH_EVT["omniroute.warehouse.events"]
        TOPIC_FLEET_GPS["omniroute.fleet.gps"]
    end

    subgraph Storage["PostgreSQL Multi-Database"]
        DB_ORDERS[("orders_db")]
        DB_PAY[("payments_db")]
        DB_WH[("warehouse_db")]
    end

    UI -->|REST /api/**| GW
    CLI -->|REST /api/**| GW

    GW -->|/orders/**| OS
    GW -->|/inventory/**| WS
    GW -->|/fleet/**| FS

    OS -->|JDBC / Flyway| DB_ORDERS
    PS -->|JDBC / Flyway| DB_PAY
    WS -->|JDBC / Flyway| DB_WH

    OS -->|OrderCreated, OrderAllocated| TOPIC_ORDERS
    OS -->|PaymentRequested, RefundPayment| TOPIC_PAY_CMD
    OS -->|ReleaseInventory| TOPIC_WH_CMD

    WS -->|InventoryLocked, InventoryLockFailed, InventoryReleased| TOPIC_WH_EVT
    PS -->|PaymentCompleted, PaymentFailed, PaymentRefunded| TOPIC_PAY_EVT
    TS -->|TruckPositionUpdated (500ms)| TOPIC_FLEET_GPS
    FS -->|TruckAssigned| TOPIC_ORDERS

    TOPIC_ORDERS --> OS
    TOPIC_WH_EVT --> OS
    TOPIC_PAY_EVT --> OS
    TOPIC_FLEET_GPS --> FS
```

---

## 2. Kafka Topic Reference

| Topic Name | Producer(s) | Consumer(s) | Payload Events | Purpose |
|---|---|---|---|---|
| `omniroute.orders.events` | `order-service`, `fleet-routing` | `warehouse-service`, `fleet-routing`, `order-service` | `OrderCreated`, `OrderAllocated`, `TruckAssigned` | Saga initialization, dispatch trigger, and finalization |
| `omniroute.warehouse.commands` | `order-service` | `warehouse-service` | `ReleaseInventory` | Compensation command to release reserved stock |
| `omniroute.warehouse.events` | `warehouse-service` | `order-service` | `InventoryLocked`, `InventoryLockFailed`, `InventoryReleased` | Inventory reservation and release confirmations |
| `omniroute.payment.commands` | `order-service` | `payment-service` | `PaymentRequested`, `RefundPayment` | Payment authorization & refund requests |
| `omniroute.payment.events` | `payment-service` | `order-service` | `PaymentCompleted`, `PaymentFailed`, `PaymentRefunded` | Payment status updates and failure notices |
| `omniroute.fleet.gps` | `telemetry-simulator` | `fleet-routing` | `TruckPositionUpdated` | High-throughput GPS updates (200 vehicles @ 500ms) |

Each topic is configured with **3 partitions**, a replication factor of 1, and an associated Dead Letter Topic (`<topic>.DLT`) for poison pill handling.

---

## 3. Distributed Saga Flows

### A. Happy Path Workflow (Order Completion)

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Desktop Client / API
    participant OS as Order Service (Orchestrator)
    participant WS as Warehouse Service
    participant PS as Payment Service
    participant FS as Fleet Routing Service

    Customer->>OS: POST /api/orders (Create Order)
    Note over OS: Status: PENDING<br/>Save order & log step
    OS->>WS: Publish OrderCreated
    Note over WS: Lock stock via Pessimistic Lock<br/>Create ACTIVE reservation
    WS-->>OS: Publish InventoryLocked
    Note over OS: Status: INVENTORY_LOCKED
    OS->>PS: Publish PaymentRequested
    Note over PS: Process charge & check rules
    PS-->>OS: Publish PaymentCompleted
    Note over OS: Status: PAYMENT_COMPLETED
    OS->>FS: Publish OrderAllocated
    Note over FS: Haversine distance search<br/>Pick nearest idle truck
    FS-->>OS: Publish TruckAssigned
    Note over OS: Status: COMPLETED<br/>Saga Successful
```

### B. Failure & Compensation Workflow (e.g., Payment Failure or $13.13 trigger)

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Desktop Client / API
    participant OS as Order Service (Orchestrator)
    participant WS as Warehouse Service
    participant PS as Payment Service

    Customer->>OS: POST /api/orders (Amount: $13.13)
    OS->>WS: Publish OrderCreated
    WS-->>OS: Publish InventoryLocked
    Note over OS: Status: INVENTORY_LOCKED
    OS->>PS: Publish PaymentRequested
    Note over PS: Injected failure ($13.13 rule)
    PS-->>OS: Publish PaymentFailed (Reason: Simulated failure)
    Note over OS: Status: COMPENSATING
    OS->>WS: Publish ReleaseInventory
    Note over WS: Restore available_qty<br/>Mark reservation RELEASED
    WS-->>OS: Publish InventoryReleased
    Note over OS: Status: FAILED<br/>Compensation Finished
```

---

## 4. Order State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING: Order Placed
    PENDING --> INVENTORY_LOCKED: InventoryLocked
    PENDING --> FAILED: InventoryLockFailed

    INVENTORY_LOCKED --> PAYMENT_COMPLETED: PaymentCompleted
    INVENTORY_LOCKED --> PAYMENT_PENDING: Circuit Breaker Fallback
    INVENTORY_LOCKED --> COMPENSATING: PaymentFailed / Timeout
    INVENTORY_LOCKED --> FAILED: Immediate Failure

    PAYMENT_PENDING --> PAYMENT_COMPLETED: Async Payment Resolved
    PAYMENT_PENDING --> COMPENSATING: Payment Failed / Expired
    PAYMENT_PENDING --> FAILED: Explicit Rejection

    PAYMENT_COMPLETED --> COMPLETED: TruckAssigned
    PAYMENT_COMPLETED --> COMPENSATING: Watchdog Dispatch Timeout

    COMPENSATING --> FAILED: InventoryReleased / Refunded
    COMPLETED --> [*]
    FAILED --> [*]
```

### State Transition Validation Table

| Current State | Allowed Next States | Trigger Event |
|---|---|---|
| `PENDING` | `INVENTORY_LOCKED`, `FAILED` | `InventoryLocked`, `InventoryLockFailed` |
| `INVENTORY_LOCKED` | `PAYMENT_COMPLETED`, `PAYMENT_PENDING`, `COMPENSATING`, `FAILED` | `PaymentCompleted`, Circuit breaker open, `PaymentFailed` |
| `PAYMENT_PENDING` | `PAYMENT_COMPLETED`, `COMPENSATING`, `FAILED` | Payment resolution or fallback timeout |
| `PAYMENT_COMPLETED` | `COMPLETED`, `COMPENSATING`, `FAILED` | `TruckAssigned`, or fleet dispatch timeout |
| `COMPENSATING` | `FAILED` | `InventoryReleased`, `PaymentRefunded` |
| `COMPLETED` | *Terminal State (None)* | N/A |
| `FAILED` | *Terminal State (None)* | N/A |

---

## 5. Architectural & Design Decisions

### 1. Why the Saga Pattern?
In a distributed microservice architecture, multi-database transactions across PostgreSQL instances cannot rely on two-phase commit (2PC) without introducing severe performance bottlenecks, blocking locks, and single-points-of-failure. The Saga pattern provides **eventual consistency** by breaking transactions into localized sub-transactions paired with explicit compensating actions.

### 2. Why Orchestration over Choreography?
* **Single Source of Truth**: The `order-service` functions as the Saga Orchestrator, maintaining a complete state machine and an immutable `saga_log` of every transition and event ID.
* **Inspectability & Monitoring**: Sagas can be inspected via REST APIs in real time, enabling the desktop UI to display granular timelines.
* **Reduced Cyclomatic Coupling**: Services only need to know how to perform their specific domain tasks and reply with standard events, preventing circular event cascades.

### 3. Idempotency & Exactly-Once Semantics
Network retries and Kafka consumer rebalances inevitably deliver duplicate messages. To guarantee safety:
* Every service checks incoming `eventId` against a dedicated `processed_events` table within the same ACID transaction before taking action.
* Duplicate events are logged as warnings and safely skipped without re-decrementing stock, charging payments, or re-running dispatches.

### 4. Transaction Order vs. Transactional Outbox
Currently, services commit the local DB transaction first, and then publish to Kafka. If the process terminates in the microsecond between DB commit and Kafka dispatch, the event could be lost.
* **Current Mitigation**: The `OrderWatchdogService` periodically audits orders stuck in transitional states (`INVENTORY_LOCKED`, `PAYMENT_COMPLETED`) beyond a threshold and initiates recovery.
* **Production Recommendation**: The **Transactional Outbox Pattern** with Debezium CDC (Change Data Capture) will be the future enhancement to make DB commits and message publication atomic.

---

## 6. How to Run

### Prerequisites
* **Java 21** (JDK 21+)
* **Maven 3.9+**
* **Docker Desktop** (or Docker Engine + Compose)

### A. Start the Entire Backend with Docker Compose
From the `omniroute` folder:
```powershell
cd omniroute
docker compose up --build -d
```
This launches:
* **Kafka** (`localhost:29092` host, `kafka:9092` internal)
* **Kafka UI** (`http://localhost:8090`)
* **PostgreSQL** (`localhost:5432`) with `orders_db`, `payments_db`, `warehouse_db`
* **Order Service** (`http://localhost:8081`, Actuator: `/actuator/prometheus`)
* **Payment Service** (`http://localhost:8082`, Actuator: `/actuator/prometheus`)
* **Warehouse Service** (`http://localhost:8083`, Actuator: `/actuator/prometheus`)
* **Fleet Routing Service** (`http://localhost:8084`, Actuator: `/actuator/prometheus`)
* **Telemetry Simulator** (`http://localhost:8085`, Actuator: `/actuator/prometheus`)
* **API Gateway** (`http://localhost:8080`, Actuator: `/actuator/prometheus`)
* **Prometheus** (`http://localhost:9090` - 2s scrape interval for all 6 services)
* **Grafana** (`http://localhost:3000` - user: `admin`, pass: `admin`, auto-provisioned dashboards)
* **Jaeger Tracing** (`http://localhost:16686` - end-to-end distributed traces across HTTP & Kafka)

### B. Observability & Tracing Verification
OmniRoute features end-to-end observability with Prometheus, Grafana, and Jaeger:

1. **Grafana Dashboards**: Access [http://localhost:3000](http://localhost:3000) to view the provisioned `OmniRoute - Observability & Distributed Transactions` dashboard:
   * Orders per second & throughput
   * Saga success rate vs. failure rate
   * End-to-end P95 saga duration histogram
   * Kafka consumer lag per consumer group
   * Circuit breaker state gauge
   * Real-time fleet vehicles tracked & routing latency
2. **Jaeger Distributed Tracing**: Access [http://localhost:16686](http://localhost:16686) to inspect traces across all 5 participating services (`gateway` -> `order-service` -> `warehouse-service` -> `payment-service` -> `fleet-routing`). Trace context (`traceparent`, `tracestate`) is automatically propagated across Kafka headers by Spring Kafka + OpenTelemetry. Every saga step injects `orderId`, `traceId`, and `spanId` into MDC logging.
3. **Automated Observability Verification Script**:
```powershell
powershell -File .\scripts\verify-observability.ps1
```

### C. Launch the JavaFX Desktop Client
The desktop client runs on the host machine and communicates directly with the Gateway:
```powershell
cd omniroute
mvn -pl desktop-client javafx:run
```
* **Orders Tab**: Real-time order placement, live table, Saga audit timeline, and demo buttons.
* **Fleet Tab**: Real-time radar Canvas tracking all 200 trucks moving live at 500ms intervals with dispatch lines.
* **Inventory Tab**: Live warehouse stock tracking with availability ratio indicators.

### D. Run Regression Gate & Automated Tests
To run the automated regression gate checking service health, $120 happy path, $13.13 forced failure compensation, and the 20-order burst inventory consistency invariant:
```powershell
powershell -File .\scripts\regression-gate.ps1
```

---

## 7. Demo & Consistency Verification

To run an automated test that places **100 random orders** (with 10% injected payment failures) and mathematically verifies that `Initial_Stock (1000) - Completed_Order_Items == Current_Stock`:

#### On Windows (PowerShell):
```powershell
.\scripts\demo.ps1
```

#### On Linux / macOS / Git Bash:
```bash
./scripts/demo.sh
```

#### SQL Direct Query:
```powershell
docker exec -i omniroute-postgres psql -U postgres -d warehouse_db < omniroute/scripts/verify-consistency.sql
```

