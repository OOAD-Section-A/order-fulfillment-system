package com.jackfruit.orderfulfillment;

import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.orderfulfillment.service.OrderFulfillmentService;

import com.jackfruit.orderfulfillment.adapter.InventoryAdapter;
import com.jackfruit.orderfulfillment.adapter.DatabaseAdapter;
import com.jackfruit.orderfulfillment.adapter.DeliveryAdapter;
import com.jackfruit.orderfulfillment.adapter.ExceptionAdapter;
import com.jackfruit.orderfulfillment.adapter.ReturnAdapter;
import com.jackfruit.orderfulfillment.adapter.DeliveryMonitoringAdapter;

import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;

import com.ramennoodles.delivery.observer.DeliveryEventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entry point for the Order Fulfillment Subsystem.
 *
 * Architecture: Adapter Pattern + Dependency Injection
 *   - All adapters implement interfaces (InventoryService, OrderRepository, etc.)
 *   - Service layer depends ONLY on interfaces (loosely coupled)
 *   - Adapters bridge to DB team's SupplyChainDatabaseFacade (integration)
 *
 * Integrations:
 *   1. Database Team     → SupplyChainDatabaseFacade (via all adapters)
 *   2. Delivery Monitoring → DeliveryMonitoringFacadeDB (via DeliveryMonitoringAdapter)
 */
public class OrderFulfillmentApplication {

    public static void main(String[] args) {

        System.out.println("========================================");
        System.out.println(" Order Fulfillment Subsystem — Starting ");
        System.out.println("========================================");

        try (SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade()) {

            // ============ DEPENDENCY INJECTION (Adapter Pattern) ============

            OrderFulfillmentService service = new OrderFulfillmentService(
                    new InventoryAdapter(facade),     // Implements InventoryService
                    new DatabaseAdapter(facade),      // Implements OrderRepository
                    new DeliveryAdapter(facade),      // Implements DeliveryService
                    new ExceptionAdapter(facade),     // Implements ExceptionService
                    new ReturnAdapter(facade)          // Implements ReturnService
            );

            // ============ INTEGRATION: Real-Time Delivery Monitoring ============

            DeliveryMonitoringAdapter deliveryMonitoring = new DeliveryMonitoringAdapter();

            // Subscribe to delivery events (Observer pattern from their system)
            deliveryMonitoring.subscribeToDeliveryEvents(
                    DeliveryEventType.ORDER_DELIVERED,
                    (eventType, data) -> {
                        String orderId = (String) data.get("orderId");
                        System.out.println("[Integration] Delivery confirmed by monitoring system: " + orderId);
                    }
            );

            deliveryMonitoring.subscribeToDeliveryEvents(
                    DeliveryEventType.STATUS_CHANGED,
                    (eventType, data) -> {
                        System.out.println("[Integration] Delivery status update: " + data);
                    }
            );

            // ============ 1. ORDER CAPTURE & PROCESSING ============

            System.out.println("\n--- 1. Order Capture & Processing ---");

            OrderRequest orderRequest = new OrderRequest(
                    "ORD-" + System.currentTimeMillis(),
                    "CUST-001",
                    "John Doe",
                    "42 MG Road, Bangalore 560001",
                    "9876543210",
                    "WEB",
                    "CREDIT_CARD",
                    "AUTHORIZED",
                    List.of(
                            new OrderItemRequest("ITEM-1", "PROD-001", 2, new BigDecimal("250.00")),
                            new OrderItemRequest("ITEM-2", "PROD-002", 1, new BigDecimal("500.00"))
                    ),
                    LocalDateTime.now(),
                    "AGENT-001",
                    "Agent Smith"
            );

            String fulfillmentId = service.processNewOrder(orderRequest);
            System.out.println("Fulfillment ID: " + fulfillmentId);

            // ============ 2. HAND OFF TO DELIVERY MONITORING ============

            System.out.println("\n--- 2. Delivery Monitoring Integration ---");

            if (fulfillmentId != null && !fulfillmentId.startsWith("BACKORDER")) {
                String deliveryOrderId = deliveryMonitoring.handoffToDeliveryMonitoring(orderRequest);

                if (deliveryOrderId != null) {
                    // Get live tracking URL from their system
                    String trackingUrl = deliveryMonitoring.getTrackingURL(deliveryOrderId);
                    System.out.println("[Integration] Live Tracking URL: " + trackingUrl);

                    // Get delivery status from their system
                    String deliveryStatus = deliveryMonitoring.getLiveDeliveryStatus(deliveryOrderId);
                    System.out.println("[Integration] Delivery Status: " + deliveryStatus);
                }
            } else {
                System.out.println("[Integration] Order is backordered — delivery handoff deferred");
            }

            // ============ 3. BATCH PROCESSING ============

            System.out.println("\n--- 3. Batch Processing Pending Orders ---");
            service.processPendingOrdersFromDatabase();

            // ============ 4. LIST FULFILLMENT ORDERS ============

            System.out.println("\n--- 4. Active Fulfillment Orders ---");
            List<String> fulfillmentOrders = service.listFulfillmentOrders();
            fulfillmentOrders.forEach(id -> System.out.println("  → " + id));

            // ============ 5. RETURNS & REVERSE LOGISTICS ============

            System.out.println("\n--- 5. Returns & Reverse Logistics ---");

            String returnId = service.initiateReturn(orderRequest.orderId(), "Item not as described");
            System.out.println("Return ID: " + returnId);

            String result = service.processReturn(returnId, "LIKE_NEW", "WH-CENTRAL-01");
            System.out.println("Return result: " + result);

            String returnStatus = service.getReturnStatus(returnId);
            System.out.println("Return status: " + returnStatus);

            // ============ COMPLETE ============

            System.out.println("\n========================================");
            System.out.println(" Order Fulfillment Completed Successfully ");
            System.out.println("========================================");
            System.out.println("Integrations Active:");
            System.out.println("  ✓ Database Team (SupplyChainDatabaseFacade)");
            System.out.println("  ✓ Delivery Monitoring (DeliveryMonitoringFacadeDB)");

        } catch (Exception e) {
            System.err.println("Order fulfillment subsystem failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}