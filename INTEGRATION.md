# Order Fulfillment System — Partner Integration Guide
## Team VERTEX (#17) | Subsystem #5

---

## 📋 Integration Overview

This guide provides step-by-step instructions for partner teams to integrate with our **Order Fulfillment Subsystem**. Our system handles the complete order lifecycle — from capture to fulfillment, including ATP checks, intelligent routing, picking/packing, shipping, and returns.

### Partner Teams We Integrate With

| # | Team | Subsystem | Integration Type |
|---|------|-----------|-----------------|
| 1 | **Database Team** | SCM Database Module | Bidirectional (via `SupplyChainDatabaseFacade`) |
| 2 | **Ramen Noodles (#9)** | Real-Time Delivery Monitoring | Bidirectional (we push orders, they push delivery events) |
| 3 | **Commission Team** | Multi-Tier Commission Tracking | Outbound webhook (HTTP POST) |

---

## 🔌 How to Integrate With Us

### Step 1: Clone Our Repository
```bash
git clone https://github.com/OOAD-Section-A/order-fulfillment-system.git
cd order-fulfillment-system
```

### Step 2: Build the Project
```bash
mvn clean compile
```

### Step 3: Import Our Interfaces
```java
import com.jackfruit.orderfulfillment.integration.InventoryService;
import com.jackfruit.orderfulfillment.integration.OrderRepository;
import com.jackfruit.orderfulfillment.integration.DeliveryService;
import com.jackfruit.orderfulfillment.integration.ExceptionService;
import com.jackfruit.orderfulfillment.integration.ReturnService;
```

### Step 4: Use Our Service
```java
import com.jackfruit.orderfulfillment.service.OrderFulfillmentService;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.orderfulfillment.model.OrderItemRequest;

// Create the service with your adapters (or use ours)
OrderFulfillmentService service = new OrderFulfillmentService(
    yourInventoryAdapter,   // implements InventoryService
    yourOrderRepository,    // implements OrderRepository
    yourDeliveryAdapter,    // implements DeliveryService
    yourExceptionAdapter,   // implements ExceptionService
    yourReturnAdapter       // implements ReturnService
);

// Process an order
OrderRequest order = new OrderRequest(
    "ORD-001", "CUST-001", "John Doe",
    "123 Main St", "9876543210", "WEB",
    "CREDIT_CARD", "AUTHORIZED",
    List.of(new OrderItemRequest("ITEM-1", "PROD-001", 2, new BigDecimal("250.00"))),
    LocalDateTime.now(), "AGENT-001", "Agent Smith"
);

String fulfillmentId = service.processNewOrder(order);
```

---

## 📡 Available Interfaces

### InventoryService
```java
public interface InventoryService {
    boolean checkStock(String productId, int quantity);
    void reserveStock(String productId, int quantity);
    String getEstimatedDelivery(String productId, int quantity);
    String selectBestWarehouse(String productId, int quantity);
}
```

### OrderRepository
```java
public interface OrderRepository {
    void saveOrder(OrderRequest request);
    boolean orderExists(String orderId);
    List<OrderRequest> listPendingOrders();
    void createFulfillmentRecord(String fulfillmentId, String orderId, OrderRequest request, String warehouse);
    List<String> listFulfillmentOrderIds();
    boolean isFulfillmentExists(String orderId);
}
```

### DeliveryService
```java
public interface DeliveryService {
    String createShipment(String orderId);
    void createPickTask(String orderId, String productId, int quantity, String warehouse);
    void createPackingDetail(String fulfillmentId);
    void createStagingDispatch(String orderId);
    String getTrackingStatus(String trackingId);
}
```

### ExceptionService
```java
public interface ExceptionService {
    void logException(String module, String message, String severity);
}
```

### ReturnService
```java
public interface ReturnService {
    String initiateReturn(String orderId, String reason);
    String generateReturnLabel(String returnId);
    boolean inspectReturnedItem(String returnId, String condition);
    void restockItem(String returnId, String warehouseId);
    String getReturnStatus(String returnId);
}
```

---

## 🤝 Integration with Real-Time Delivery Monitoring (Team #9)

### What We Provide (IOrderFulfillmentService)
We implement their `IOrderFulfillmentService` interface so they can pull data from us:

```java
Order getOrderDetails(String orderId);
List<Order> getDispatchedOrders();
void notifyOrderPickedUp(String orderId, String riderId);
void notifyOrderDelivered(String orderId, PODRecord pod);
void notifyDeliveryFailed(String orderId, String reason);
Customer getCustomerForOrder(String orderId);
```

### What We Consume (DeliveryMonitoringFacadeDB)
After fulfilling an order, we hand it off to their system:

```java
DeliveryMonitoringAdapter deliveryMonitoring = new DeliveryMonitoringAdapter();

// Push fulfilled order to delivery monitoring
String deliveryOrderId = deliveryMonitoring.handoffToDeliveryMonitoring(orderRequest);

// Get live tracking
String trackingUrl = deliveryMonitoring.getTrackingURL(deliveryOrderId);

// Subscribe to delivery events (Observer pattern)
deliveryMonitoring.subscribeToDeliveryEvents(
    DeliveryEventType.ORDER_DELIVERED,
    (eventType, data) -> {
        System.out.println("Order delivered: " + data.get("orderId"));
    }
);
```

---

## 🔗 Integration with Commission Tracking

We fire webhook events to the Commission Tracking subsystem after each successful fulfillment:

```
POST https://commission-system.example.com/api/v1/commission-events
Body: { "fulfillmentId": "...", "orderId": "...", "agentId": "...", "totalAmount": ... }
```

**To connect**: Update the webhook URL in `OrderFulfillmentService.java`.

---

## 🧪 Testing Your Integration

```bash
mvn clean test
# Tests run: 24, Failures: 0, Errors: 0
```

### Example: Creating a Stub Adapter (No Database Needed!)
```java
static class StubInventoryService implements InventoryService {
    boolean stockAvailable = true;
    public boolean checkStock(String productId, int qty) { return stockAvailable; }
    public void reserveStock(String productId, int qty) { }
    public String getEstimatedDelivery(String productId, int qty) { return "2-3 days"; }
    public String selectBestWarehouse(String productId, int qty) { return "WH-01"; }
}
```

---

## ⚠️ Requirements

| Requirement | Value |
|------------|-------|
| Java | JDK 21+ (JDK 24 for delivery monitoring) |
| Build | Apache Maven 3.9+ |
| Database | MySQL with OOAD schema (optional for testing) |
| Dependencies | All JARs in `lib/` directory |

---

**Last Updated**: 2026-04-24 | **Version**: 2.0.0 | **Team**: VERTEX (#17) | **Status**: ✅ Ready for Partner Integration
