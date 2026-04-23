package com.jackfruit.orderfulfillment.service;

import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.orderfulfillment.model.CommissionEvent;
import com.jackfruit.orderfulfillment.client.CommissionWebhookClient;

import com.jackfruit.orderfulfillment.integration.InventoryService;
import com.jackfruit.orderfulfillment.integration.OrderRepository;
import com.jackfruit.orderfulfillment.integration.DeliveryService;
import com.jackfruit.orderfulfillment.integration.ExceptionService;
import com.jackfruit.orderfulfillment.integration.ReturnService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Core orchestrator for the order fulfillment workflow.
 * Uses Dependency Injection via interfaces — no direct database coupling.
 *
 * Fulfillment pipeline:
 *   Order Capture → Validation → ATP Check → Routing → Persist
 *   → Pick Task → Packing → Staging Dispatch → Commission Webhook
 */
public class OrderFulfillmentService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final DeliveryService deliveryService;
    private final ExceptionService exceptionService;
    private final ReturnService returnService;
    private final CommissionWebhookClient commissionWebhookClient;

    /**
     * Constructor-based Dependency Injection (DI).
     * All dependencies are injected as interfaces — Open/Closed Principle.
     */
    public OrderFulfillmentService(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            DeliveryService deliveryService,
            ExceptionService exceptionService,
            ReturnService returnService) {

        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.deliveryService = deliveryService;
        this.exceptionService = exceptionService;
        this.returnService = returnService;
        this.commissionWebhookClient =
                new CommissionWebhookClient("https://commission-system.example.com/api/v1/commission-events");
    }

    // ======================== 1. ORDER CAPTURE & PROCESSING ========================

    /**
     * Processes a new order through the full fulfillment pipeline.
     * Steps: Validate → ATP check → Route → Persist → Pick → Pack → Stage → Ship
     */
    public String processNewOrder(OrderRequest request) {
        try {
            // Step 1: Order Validation & Fraud Detection
            new OrderValidationService().validate(request);
            System.out.println("[Fulfillment] Order validated: " + request.orderId());

            // Step 2: ATP/GTP — Inventory Promising
            boolean allInStock = true;
            for (OrderItemRequest item : request.items()) {
                boolean available = inventoryService.checkStock(item.productId(), item.quantity());
                if (!available) {
                    System.out.println("[Fulfillment] Stock unavailable for " + item.productId() + " → BACKORDER");
                    handleBackorder(request, item);
                    allInStock = false;
                } else {
                    inventoryService.reserveStock(item.productId(), item.quantity());
                    String eta = inventoryService.getEstimatedDelivery(item.productId(), item.quantity());
                    System.out.println("[Fulfillment] ETA for " + item.productId() + ": " + eta);
                }
            }

            // If no items are in stock, save order as BACKORDERED and return
            if (!allInStock) {
                orderRepository.saveOrder(request);
                String fulfillmentId = "BACKORDER-" + UUID.randomUUID();
                System.out.println("[Fulfillment] Order " + request.orderId() + " saved as BACKORDERED: " + fulfillmentId);
                return fulfillmentId;
            }

            // Step 3: Intelligent Order Routing — select best warehouse
            String selectedWarehouse = inventoryService.selectBestWarehouse(
                    request.items().get(0).productId(),
                    request.items().get(0).quantity());
            System.out.println("[Fulfillment] Routed to warehouse: " + selectedWarehouse);

            // Step 4: Order Persistence
            orderRepository.saveOrder(request);

            // Step 5: Create Fulfillment Record
            String fulfillmentId = "FULFILL-" + UUID.randomUUID();
            orderRepository.createFulfillmentRecord(fulfillmentId, request.orderId(), request, selectedWarehouse);
            System.out.println("[Fulfillment] Fulfillment record created: " + fulfillmentId);

            // Step 6: Picking & Packing Orchestration
            for (OrderItemRequest item : request.items()) {
                deliveryService.createPickTask(request.orderId(), item.productId(), item.quantity(), selectedWarehouse);
            }

            // Step 7: Packing & Staging Dispatch
            createPackingAndDispatch(request.orderId(), fulfillmentId);

            // Step 8: Shipping & Carrier Management
            String trackingId = deliveryService.createShipment(request.orderId());
            System.out.println("[Fulfillment] Tracking: " + trackingId);

            // Step 9: Real-Time Tracking & Communication
            System.out.println("[Fulfillment] Customer notification: Order " + request.orderId()
                    + " confirmed, tracking " + trackingId);

            // Step 10: Commission Webhook (integration with Commission Tracking subsystem)
            triggerCommissionWebhook(fulfillmentId, request);

            System.out.println("[Fulfillment] Order " + request.orderId() + " fulfilled successfully!");
            return fulfillmentId;

        } catch (Exception e) {
            String errorMessage = "Failed to process order: " + request.orderId() + " - " + e.getMessage();
            exceptionService.logException("ORDER_FULFILLMENT", errorMessage, "MAJOR");
            throw e;
        }
    }

    // ======================== 2. PACKING & DISPATCH ========================

    /**
     * Creates packing details and staging dispatch records for a fulfilled order.
     */
    public void createPackingAndDispatch(String orderId, String fulfillmentId) {
        try {
            if (!orderRepository.orderExists(orderId)) {
                throw new IllegalStateException("Order not found for packing: " + orderId);
            }

            deliveryService.createPackingDetail(fulfillmentId);
            deliveryService.createStagingDispatch(orderId);

            System.out.println("[Fulfillment] Packing and dispatch completed for order: " + orderId);

        } catch (Exception e) {
            String errorMessage = "Failed to create packing and dispatch for order: " + orderId + " - " + e.getMessage();
            exceptionService.logException("PACKING", errorMessage, "MINOR");
            throw e;
        }
    }

    // ======================== 3. BATCH ORDER PROCESSING ========================

    /**
     * Processes all pending orders from the database.
     * Retrieves unprocessed orders and runs them through the fulfillment pipeline.
     */
    public void processPendingOrdersFromDatabase() {
        try {
            List<OrderRequest> pendingOrders = orderRepository.listPendingOrders();
            System.out.println("[Fulfillment] Found " + pendingOrders.size() + " pending orders");

            int processed = 0;
            int failed = 0;

            for (OrderRequest pendingOrder : pendingOrders) {
                if (pendingOrder.items() == null || pendingOrder.items().isEmpty()) {
                    System.out.println("[Fulfillment] Skipping order " + pendingOrder.orderId() + " — no items");
                    continue;
                }

                try {
                    // Route the order
                    String warehouse = inventoryService.selectBestWarehouse(
                            pendingOrder.items().get(0).productId(),
                            pendingOrder.items().get(0).quantity());

                    // Create fulfillment
                    String fulfillmentId = "FULFILL-" + UUID.randomUUID();
                    orderRepository.createFulfillmentRecord(fulfillmentId, pendingOrder.orderId(), pendingOrder, warehouse);

                    // Create pick tasks
                    for (OrderItemRequest item : pendingOrder.items()) {
                        deliveryService.createPickTask(pendingOrder.orderId(), item.productId(), item.quantity(), warehouse);
                    }

                    // Pack and dispatch
                    createPackingAndDispatch(pendingOrder.orderId(), fulfillmentId);

                    // Commission
                    triggerCommissionWebhook(fulfillmentId, pendingOrder);

                    processed++;
                    System.out.println("[Fulfillment] Batch: processed order " + pendingOrder.orderId());

                } catch (Exception e) {
                    failed++;
                    System.err.println("[Fulfillment] Batch: failed order " + pendingOrder.orderId() + " — " + e.getMessage());
                }
            }

            System.out.println("[Fulfillment] Batch complete: " + processed + " processed, " + failed + " failed");

        } catch (Exception e) {
            String errorMessage = "Failed to process pending orders from database - " + e.getMessage();
            exceptionService.logException("BATCH_PROCESSING", errorMessage, "MAJOR");
            throw e;
        }
    }

    // ======================== 4. QUERY METHODS ========================

    /**
     * Lists all existing fulfillment order IDs.
     */
    public List<String> listFulfillmentOrders() {
        return orderRepository.listFulfillmentOrderIds();
    }

    /**
     * Gets the tracking status for a shipment.
     */
    public String getTrackingStatus(String trackingId) {
        return deliveryService.getTrackingStatus(trackingId);
    }

    // ======================== 5. BACKORDER MANAGEMENT ========================

    /**
     * Handles backorder situations when stock is unavailable.
     * Logs the exception and marks the order for future processing.
     */
    private void handleBackorder(OrderRequest request, OrderItemRequest item) {
        String message = String.format(
                "Backorder created: Order %s, Product %s, Qty %d — insufficient stock",
                request.orderId(), item.productId(), item.quantity());
        // Backorders are normal business flow — log to console, not as system exception
        System.out.println("[Fulfillment] " + message);
    }

    // ======================== 7. RETURNS & REVERSE LOGISTICS ========================

    /**
     * Initiates a return for a fulfilled order.
     * Generates a return label and provides customer with tracking info.
     */
    public String initiateReturn(String orderId, String reason) {
        try {
            String returnId = returnService.initiateReturn(orderId, reason);
            String labelTracking = returnService.generateReturnLabel(returnId);
            System.out.println("[Returns] Return " + returnId + " initiated for order " + orderId
                    + " — Label: " + labelTracking);
            return returnId;
        } catch (Exception e) {
            exceptionService.logException("RETURNS", "Failed to initiate return for " + orderId + ": " + e.getMessage(), "MAJOR");
            throw e;
        }
    }

    /**
     * Processes a returned item: inspects condition and restocks if it passes quality check.
     * Items in LIKE_NEW or GOOD condition are restocked; damaged items are flagged.
     */
    public String processReturn(String returnId, String condition, String warehouseId) {
        try {
            boolean passesInspection = returnService.inspectReturnedItem(returnId, condition);

            if (passesInspection) {
                returnService.restockItem(returnId, warehouseId);
                System.out.println("[Returns] Return " + returnId + " restocked at " + warehouseId);
                return "RESTOCKED";
            } else {
                System.out.println("[Returns] Return " + returnId + " failed inspection (" + condition + ") — not restockable");
                return "INSPECTION_FAILED";
            }
        } catch (Exception e) {
            exceptionService.logException("RETURNS", "Failed to process return " + returnId + ": " + e.getMessage(), "MINOR");
            throw e;
        }
    }

    /**
     * Gets the current status of a return request.
     */
    public String getReturnStatus(String returnId) {
        return returnService.getReturnStatus(returnId);
    }

    // ======================== 6. HELPER METHODS ========================

    private BigDecimal calculateTotal(List<OrderItemRequest> items) {
        return items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Triggers commission webhook to the Commission Tracking subsystem.
     * Integration point: Order Fulfillment → Multi-Tier Commission Tracking.
     */
    private void triggerCommissionWebhook(String fulfillmentId, OrderRequest request) {
        try {
            CommissionEvent event = new CommissionEvent(
                    fulfillmentId,
                    request.orderId(),
                    request.agentId() != null ? request.agentId() : "UNKNOWN_AGENT",
                    request.agentName() != null ? request.agentName() : "Unknown Agent",
                    request.customerId(),
                    calculateTotal(request.items()),
                    "USD",
                    request.orderDate(),
                    LocalDateTime.now(),
                    "INV-" + fulfillmentId,
                    request.salesChannel(),
                    request.paymentStatus(),
                    "ORDER_DELIVERED",
                    LocalDateTime.now()
            );

            commissionWebhookClient.sendCommissionEvent(event, null);
            System.out.println("[Fulfillment] Commission webhook sent for " + fulfillmentId);

        } catch (Exception e) {
            // Webhook failures are non-critical — log to console only
            System.out.println("[Fulfillment] Commission webhook skipped (not configured): " + e.getMessage());
        }
    }
}