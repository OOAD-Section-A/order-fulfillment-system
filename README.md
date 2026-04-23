# Order Fulfillment Subsystem | Team VERTEX (#17)
> **OOAD Lab Project | Supply Chain Management | Section A**

A production-ready Order Fulfillment Subsystem built with **Adapter Pattern + Dependency Injection** architecture, fully integrated with the SCM Database Module and Real-Time Delivery Monitoring system.

---

## ✨ Key Features

| # | Feature | Status |
|---|---------|--------|
| 1 | Order Capture & Centralization (WEB, MOBILE, EDI, POS) | ✅ |
| 2 | Inventory Promising (ATP/GTP) | ✅ |
| 3 | Intelligent Order Routing & Allocation | ✅ |
| 4 | Order Validation & Fraud Detection | ✅ |
| 5 | Picking & Packing Orchestration | ✅ |
| 6 | Shipping & Carrier Management | ✅ |
| 7 | Real-Time Tracking & Communication | ✅ |
| 8 | Returns & Reverse Logistics | ✅ |
| 9 | Exception & Backorder Management | ✅ |

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│              OrderFulfillmentService                │
│         (depends ONLY on interfaces)                │
├──────────┬──────────┬──────────┬──────────┬─────────┤
│Inventory │  Order   │ Delivery │Exception │ Return  │
│ Service  │Repository│ Service  │ Service  │ Service │
│(interface)│(interface)│(interface)│(interface)│(interface)│
├──────────┼──────────┼──────────┼──────────┼─────────┤
│Inventory │ Database │ Delivery │Exception │ Return  │
│ Adapter  │ Adapter  │ Adapter  │ Adapter  │ Adapter │
├──────────┴──────────┴──────────┴──────────┴─────────┤
│         SupplyChainDatabaseFacade (DB Team)         │
└─────────────────────────────────────────────────────┘
                        +
┌─────────────────────────────────────────────────────┐
│        DeliveryMonitoringAdapter                    │
│  implements IOrderFulfillmentService (their API)    │
├─────────────────────────────────────────────────────┤
│    DeliveryMonitoringFacadeDB (Ramen Noodles)       │
└─────────────────────────────────────────────────────┘
```

### Design Patterns
- **Adapter Pattern** — Bridges interfaces to DB team's facade
- **Dependency Injection** — All services injected via constructors
- **Interface Segregation** — 5 focused interfaces instead of 1 monolith
- **Observer Pattern** — Event subscription with Delivery Monitoring
- **Facade Pattern** — Single entry point via `OrderFulfillmentService`

## 📦 Project Structure

```
src/main/java/com/jackfruit/orderfulfillment/
├── adapter/                     # Adapter implementations
│   ├── DatabaseAdapter.java     # OrderRepository → DB Facade
│   ├── InventoryAdapter.java    # InventoryService → DB Facade
│   ├── DeliveryAdapter.java     # DeliveryService → DB Facade
│   ├── ExceptionAdapter.java    # ExceptionService → DB Facade
│   ├── ReturnAdapter.java       # ReturnService → DB Facade
│   └── DeliveryMonitoringAdapter.java  # Integration with Team #9
├── integration/                 # Interface contracts
│   ├── InventoryService.java
│   ├── OrderRepository.java
│   ├── DeliveryService.java
│   ├── ExceptionService.java
│   └── ReturnService.java
├── model/                       # Domain models
│   ├── OrderRequest.java
│   ├── OrderItemRequest.java
│   └── FulfillmentRecord.java
├── service/                     # Business logic
│   ├── OrderFulfillmentService.java
│   ├── OrderFulfillmentExceptionLogger.java
│   └── OrderValidationService.java
├── client/
│   └── CommissionWebhookClient.java  # Commission subsystem integration
└── OrderFulfillmentApplication.java  # Entry point

src/test/java/com/jackfruit/orderfulfillment/
├── OrderFulfillmentServiceTest.java  # 16 tests with in-memory stubs
└── OrderValidationServiceTest.java   # 8 validation tests

lib/
├── database-module-1.0.0-SNAPSHOT-standalone.jar  # DB Team
├── scm-exception-handler-v3.jar                   # Exception Handler
├── delivery-monitoring-1.0.0.jar                  # Ramen Noodles (Team #9)
└── ...
```

## 🔗 Integrations

### 1. Database Team (SCM Database Module)
- **JAR**: `lib/database-module-1.0.0-SNAPSHOT-standalone.jar`
- **Usage**: All 5 adapters wrap `SupplyChainDatabaseFacade`
- **Auto exception handling**: DB team's facade handles exceptions internally

### 2. Real-Time Delivery Monitoring (Team Ramen Noodles #9)
- **JAR**: `lib/delivery-monitoring-1.0.0.jar`
- **We implement**: `IOrderFulfillmentService` (their interface for pulling our data)
- **We consume**: `DeliveryMonitoringFacadeDB` (their facade for pushing orders)
- **Events**: Subscribe to `ORDER_DELIVERED`, `STATUS_CHANGED` via Observer pattern

### 3. Commission Tracking (Webhook)
- **Protocol**: HTTP POST to commission subsystem endpoint
- **Resilience**: Non-blocking with connection/read timeouts
- **Graceful fallback**: Logs to console if endpoint is unreachable

## 🚀 Quick Start

### Prerequisites
- Java Development Kit (JDK) 21+
- Apache Maven 3.9+
- MySQL Database (optional — tests work without it)

### Build & Test
```bash
mvn clean test
```
**Expected output**: `Tests run: 24, Failures: 0, Errors: 0`

### Run the Application
```bash
mvn clean compile
java -cp "target/classes;lib/*" com.jackfruit.orderfulfillment.OrderFulfillmentApplication
```

> **Note**: Requires MySQL with the OOAD database for full operation. The test suite runs entirely in-memory using stub adapters.

## 🧪 Test Suite (24 Tests)

All tests use **in-memory stub implementations** — no database required!

| Test Class | Tests | What It Covers |
|-----------|-------|----------------|
| `OrderFulfillmentServiceTest` | 16 | Order processing, backorders, validation, batch processing, returns |
| `OrderValidationServiceTest` | 8 | Address, payment, phone, fraud detection validation |

```bash
mvn clean test
# Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
```

## 📋 For Partner Teams — Integration Guide

**See [`integration.md`](integration.md) for the full integration guide.**

Quick overview:
1. Download our source or clone this repo
2. Our system exposes clean interfaces under `com.jackfruit.orderfulfillment.integration.*`
3. Use `OrderFulfillmentService` with your own adapter implementations
4. Subscribe to fulfillment events for real-time updates

## 👥 Team Information

| Team Name | Team Number | Subsystem |
|-----------|------------|-----------|
| **VERTEX** | #17 | Order Fulfillment (#5) |

## 📄 License

This project is part of the OOAD Section A Lab Project — Supply Chain Management.

---
**Last Updated**: 2026-04-24  
**Version**: 2.0.0  
**Java**: 21+  
**Build**: Maven  
**Tests**: 24/24 passing ✅  
**Status**: ✅ Production Ready, Integration Complete
