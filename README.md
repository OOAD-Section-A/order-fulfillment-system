# Order Fulfillment Subsystem

A complete Java-based Order Fulfillment module for the Supply Chain Management (SCM) system. This subsystem handles order capture, validation, inventory allocation, routing, and fulfillment orchestration with comprehensive exception handling and database persistence.

## Core Features

- **Order Capture & Centralization**: Accepts orders from diverse channels and stores them in a unified format
- **Inventory Promising (ATP/GTP)**: Real-time availability checks against warehouse stock before order confirmation
- **Intelligent Order Routing & Allocation**: Dynamically selects the optimal fulfillment warehouse to minimize costs and transit time
- **Order Validation & Fraud Detection**: Validates customer details and order structure before processing
- **Picking & Packing Orchestration**: Generates picking tasks and packing details for warehouse execution
- **Real-Time Tracking & Communication**: Integrates with staging and dispatch systems for logistics visibility
- **Batch Order Processing**: Processes pending orders from the database for automated fulfillment workflows
- **Exception Management & Logging**: Comprehensive error handling with centralized exception tracking

## Architecture

### Integration with the Database Subsystem

The Order Fulfillment subsystem integrates with the shared database layer through two primary facades:

- **SupplyChainDatabaseFacade**: Provides access to orders, warehouse stock, and exception logging
- **OrderFulfillmentAdapter**: Handles creation and retrieval of fulfillment records, packing details, and staging/dispatch data

Key database interactions:
- Reads/writes orders and order items via `OrdersSubsystemFacade`
- Queries real-time warehouse stock via `WarehouseSubsystemFacade`
- Creates fulfillment records, packing details, and staging dispatch records via `OrderFulfillmentAdapter`
- Logs all exceptions to the centralized exception table via `ExceptionHandlingSubsystemFacade`

### Exception Handling Architecture

The Order Fulfillment subsystem uses a dual-layer exception handling model:

- **SCM Exception Layer**: Builds `SCMException` objects using `SCMExceptionFactory` and dispatches them through `SCMExceptionHandler.INSTANCE.handle()` for centralized processing
- **Database Persistence Layer**: Converts `SCMException` to `SubsystemException` and persists to the database via `ExceptionHandlingSubsystemFacade.logException()`

Supported severity levels: `MAJOR` (critical failures), `MINOR` (non-critical issues), `WARNING` (advisory events)

Exception logging occurs automatically when:
- Order validation fails (ORDER_PROCESSING_FAILED - severity MAJOR)
- Packing or dispatch operations fail (PACKING_DISPATCH_FAILED - severity MINOR)
- Batch processing encounters errors (BATCH_PROCESSING_FAILED - severity MAJOR)

See `INTEGRATION.md` for details on integrating with other subsystems.

## Setup & Installation

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- MySQL 5.7+ (or compatible database)
- Local lib folder containing required JARs

### Step 1: Prepare JAR Dependencies

Place the following JARs in the `lib/` directory:

```
order-fulfillment-subsystem/lib/
├── database-module-1.0.0-SNAPSHOT-standalone.jar
├── scm-exception-handler-v3.jar
└── scm-exception-viewer-gui.jar
```

### Step 2: Configure Database Connection

Set environment variables:

```bash
export DB_URL="jdbc:mysql://localhost:3306/OOAD"
export DB_USERNAME="root"
export DB_PASSWORD="your_password"
```

Alternatively, edit `src/main/resources/database.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/OOAD
db.user=root
db.password=your_password
```

### Step 3: Build & Test

Compile the project:

```bash
mvn clean compile
```

Run all tests:

```bash
mvn test
```

Run the application (processes pending orders):

```bash
mvn compile exec:java
```

## Project Structure

```
order-fulfillment-subsystem/
├── src/
│   ├── main/
│   │   ├── java/com/jackfruit/orderfulfillment/
│   │   │   ├── OrderFulfillmentApplication.java      # Entry point
│   │   │   ├── model/
│   │   │   │   ├── OrderRequest.java                 # Order request record
│   │   │   │   └── OrderItemRequest.java             # Order item details
│   │   │   └── service/
│   │   │       ├── OrderFulfillmentService.java      # Core fulfillment logic
│   │   │       ├── OrderValidationService.java       # Order validation rules
│   │   │       └── OrderFulfillmentExceptionLogger.java  # Exception handling helper
│   │   └── resources/
│   │       └── database.properties
│   └── test/
│       └── java/com/jackfruit/orderfulfillment/
│           ├── OrderFulfillmentServiceTest.java      # Service tests & exception coverage
│           └── OrderValidationServiceTest.java       # Validation tests
├── lib/                                               # System-scope JAR dependencies
├── pom.xml                                            # Maven configuration
└── README.md                                          # This file
```

## Core Services

### OrderFulfillmentService

Main orchestrator for the fulfillment workflow:

- `processNewOrder(OrderRequest)`: Validates, persists, and fulfills a new order
- `fulfillOrderForRequest(OrderRequest)`: Creates fulfillment records and pick tasks
- `createPackingAndDispatch(String, String)`: Generates packing details and staging dispatch
- `processPendingOrdersFromDatabase()`: Batch processes orders from the database
- `listFulfillmentOrders()`: Retrieves all active fulfillment records

### OrderValidationService

Ensures order integrity before processing:

- `validate(OrderRequest)`: Checks for null/blank fields and required items
- Throws `IllegalArgumentException` on validation failure

### OrderFulfillmentExceptionLogger

Centralizes exception creation and logging:

- `buildScmException()`: Creates SCM exception objects
- `convertToSubsystemException()`: Transforms SCM exceptions to database format
- `logException()`: Orchestrates exception handling and persistence

## Testing

The subsystem includes 12 unit tests covering:

- Valid order processing workflows
- Invalid order rejection (null/blank fields, missing items)
- Database batch processing
- SCM exception creation and conversion
- Exception persistence and severity mapping

Run tests with:

```bash
mvn test
```

Test results are written to:
```
target/surefire-reports/
```

## Configuration Notes

- The project uses Maven's `system` scope for local JAR dependencies to keep the build self-contained
- Database schema is auto-initialized by the database module on first connection
- Exception IDs (1, 2, 3) are predefined for order fulfillment operations
- Timestamps are UTC-based using Java's LocalDateTime

## Troubleshooting

**Issue**: "Cannot find symbol: variable MEDIUM" during build

**Solution**: Severity enum in `scm-exception-handler-v3.jar` only supports `MAJOR`, `MINOR`, and `WARNING`. Update code to use these values.

**Issue**: "Data truncation: Data too long for column 'subsystem'"

**Solution**: The subsystem name exceeds the database column size. Verify that the `subsystem` column in the exception table supports at least 50 characters.

**Issue**: Order not found during packing

**Solution**: Ensure the order was successfully persisted before calling `createPackingAndDispatch()`. Check database connection and exception logs.

## Integration Roadmap

The Order Fulfillment subsystem is designed to integrate with the following SCM modules:

- **Inventory Management**: Stock level verification and reservation
- **Warehouse Management**: Pick task creation and fulfillment coordination
- **Real-Time Delivery Monitoring**: Shipment tracking and proof of delivery
- **Transport & Logistics**: Carrier selection and route optimization
- **Reporting & Analytics**: KPI tracking for fulfillment performance
- **Exception Handling**: Centralized error logging and notifications

See `INTEGRATION.md` for implementation details.

## Dependencies

- **MySQL Connector/J**: Database connectivity
- **JUnit 5 (Jupiter)**: Unit testing framework
- **SupplyChainDatabase**: Shared database module (bundled JAR)
- **SCM Exception Handler**: Centralized exception management (bundled JAR)

## Future Enhancements

- Multi-warehouse fulfillment strategies (split shipments)
- Advanced inventory allocation algorithms (min-cost routing)
- Fulfillment analytics dashboard
- Integration with shipping carriers (FedEx, UPS, DHL)
- Customer self-service order tracking portal
- Returns and reverse logistics support
- AI-driven demand forecasting integration

## License & Support

This subsystem is part of the OOAD Supply Chain Management project.
For support or questions, refer to the project documentation or contact the development team.
