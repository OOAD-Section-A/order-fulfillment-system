# Order Fulfillment Subsystem

This subsystem is an Order Fulfillment module for the OOAD Java project.
It integrates with the shared database subsystem using the provided standalone JAR.

## Features

- Order capture and centralization
- Inventory promising using warehouse stock records
- Simple order routing and allocation
- Order validation before persistence
- Picking task creation for warehouse execution
- Packing and dispatch staging- live database order processing when orders exist in the shared schema
## Integration with the database subsystem

This project uses `com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade` and `com.jackfruit.scm.database.adapter.OrderFulfillmentAdapter`.
The module persists orders through `OrdersSubsystemFacade` and creates fulfillment records through `OrderFulfillmentAdapter`.

## Integration with the exception handling subsystem

The subsystem integrates with the exception handler subsystem for automatic error logging and notifications.
When exceptions occur during order processing, packing, or batch operations, they are automatically logged using `ExceptionHandlingSubsystemFacade.logException()`.
This provides centralized exception management across the supply chain system.

## Setup

1. Place the shared JARs in the local `lib/` folder:

   ```text
   lib/database-module-1.0.0-SNAPSHOT-standalone.jar
   lib/scm-exception-handler-v3.jar
   lib/scm-exception-viewer-gui.jar
   ```

2. Set your database environment variables:

   ```bash
   export DB_URL="jdbc:mysql://localhost:3306/OOAD"
   export DB_USERNAME="your_user"
   export DB_PASSWORD="your_password"
   ```

3. Run the application (processes pending orders from DB):

   ```bash
   mvn compile exec:java
   ```

4. Run tests:

   ```bash
   mvn test
   ```

## Project structure

- `src/main/java/com/jackfruit/orderfulfillment/OrderFulfillmentApplication.java` – entry point
- `OrderFulfillmentService` – orchestrates order capture, ATP/GTP, routing, fulfillment creation, and packing
- `OrderValidationService` – validates order details before processing
- `OrderRequest` and `OrderItemRequest` – domain models for incoming orders

## Notes

- The example uses MySQL Connector/J as the JDBC driver.
- The database module bootstraps schema based on `schema.sql` found in the packaged JAR.
- If you want a Maven-native dependency, install the module JAR into your local Maven repository and replace the `system` dependency with a normal dependency.
