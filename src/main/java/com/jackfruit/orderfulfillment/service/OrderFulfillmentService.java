package com.jackfruit.orderfulfillment.service;

import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.scm.database.adapter.OrderFulfillmentAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.Order;
import com.jackfruit.scm.database.model.OrderItem;
import com.jackfruit.orderfulfillment.client.CommissionWebhookClient;
import com.jackfruit.orderfulfillment.model.CommissionEvent;
import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.scm.database.adapter.OrderFulfillmentAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.Order;
import com.jackfruit.scm.database.model.OrderItem;
import com.jackfruit.scm.database.model.OrderFulfillmentModels;
import com.jackfruit.scm.database.model.WarehouseModels.StockRecord;
import com.scm.core.Severity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrderFulfillmentService {
    private final SupplyChainDatabaseFacade facade;
    private final OrderFulfillmentAdapter fulfillmentAdapter;
    private final CommissionWebhookClient commissionWebhookClient;

    public OrderFulfillmentService(SupplyChainDatabaseFacade facade, OrderFulfillmentAdapter fulfillmentAdapter) {
        this.facade = facade;
        this.fulfillmentAdapter = fulfillmentAdapter;
        // TODO: Load webhook URL from configuration
        this.commissionWebhookClient = new CommissionWebhookClient("https://commission-system.example.com/api/v1/commission-events");
    }

    public OrderFulfillmentModels.FulfillmentOrder processNewOrder(OrderRequest request) {
        try {
            new OrderValidationService().validate(request);

            Order order = new Order(
                    request.orderId(),
                    request.customerId(),
                    "RECEIVED",
                    request.orderDate(),
                    calculateTotal(request.items()),
                    request.paymentStatus(),
                    request.salesChannel()
            );
            facade.orders().createOrder(order);

            for (OrderItemRequest itemRequest : request.items()) {
                OrderItem orderItem = new OrderItem(
                        itemRequest.orderItemId(),
                        request.orderId(),
                        itemRequest.productId(),
                        itemRequest.quantity(),
                        itemRequest.unitPrice(),
                        itemRequest.unitPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()))
                );
                facade.orders().addOrderItem(orderItem);
            }

            return fulfillOrderForRequest(request);
        } catch (Exception e) {
            String errorMessage = "Failed to process new order: " + request.orderId() + " - " + e.getMessage();
            OrderFulfillmentExceptionLogger.logException(facade, 2, "ORDER_PROCESSING_FAILED", errorMessage, Severity.MAJOR);
            throw e;
        }
    }

    private OrderFulfillmentModels.FulfillmentOrder fulfillOrderForRequest(OrderRequest request) {
        new OrderValidationService().validate(request);

        String selectedWarehouse = selectBestWarehouse(request);
        OrderFulfillmentModels.FulfillmentOrder fulfillmentOrder = createFulfillmentOrder(request, selectedWarehouse);
        fulfillmentAdapter.createFulfillmentOrder(fulfillmentOrder);

        createPickTask(request, selectedWarehouse);
        return fulfillmentOrder;
    }

    public void createPackingAndDispatch(String orderId, String fulfillmentId) {
        try {
            Optional<Order> existing = facade.orders().getOrder(orderId);
            if (existing.isEmpty()) {
                throw new IllegalStateException("Order not found for packing: " + orderId);
            }

            OrderFulfillmentModels.PackingDetail packingDetail = new OrderFulfillmentModels.PackingDetail(
                    "PACK-" + UUID.randomUUID(),
                    fulfillmentId,
                    "STANDARD_BOX",
                    "warehouse-robot-01",
                    LocalDateTime.now(),
                    BigDecimal.valueOf(5.4)
            );
            fulfillmentAdapter.createPackingDetail(packingDetail);

            facade.warehouse().createStagingDispatch(new com.jackfruit.scm.database.model.WarehouseModels.StagingDispatch(
                    UUID.randomUUID().toString(),
                    "DOCK-01",
                    orderId,
                    LocalDateTime.now(),
                    "STAGED"
            ));
        } catch (Exception e) {
            String errorMessage = "Failed to create packing and dispatch for order: " + orderId + " - " + e.getMessage();
            OrderFulfillmentExceptionLogger.logException(facade, 1, "PACKING_DISPATCH_FAILED", errorMessage, Severity.MINOR);
            throw e;
        }
    }

    public void processPendingOrdersFromDatabase() {
        try {
            Set<String> existingFulfillmentOrderIds = fulfillmentAdapter.listFulfillmentOrders().stream()
                    .map(OrderFulfillmentModels.FulfillmentOrder::orderId)
                    .collect(Collectors.toSet());

            List<Order> orders = facade.orders().listOrders();
            for (Order order : orders) {
                if (!isPending(order) || existingFulfillmentOrderIds.contains(order.getOrderId())) {
                    continue;
                }

                List<OrderItem> orderItems = facade.orders().listOrderItems(order.getOrderId());
                if (orderItems.isEmpty()) {
                    continue;
                }
                OrderRequest request = buildOrderRequestFromDb(order, orderItems);
                OrderFulfillmentModels.FulfillmentOrder fulfillmentOrder = fulfillOrderForRequest(request);
                createPackingAndDispatch(order.getOrderId(), fulfillmentOrder.fulfillmentId());
                // Trigger commission webhook for delivered orders
                triggerCommissionWebhook(fulfillmentOrder, request);
            }
        } catch (Exception e) {
            String errorMessage = "Failed to process pending orders from database - " + e.getMessage();
            OrderFulfillmentExceptionLogger.logException(facade, 3, "BATCH_PROCESSING_FAILED", errorMessage, Severity.MAJOR);
            throw e;
        }
    }

    public List<OrderFulfillmentModels.FulfillmentOrder> listFulfillmentOrders() {
        return fulfillmentAdapter.listFulfillmentOrders();
    }

    private String selectBestWarehouse(OrderRequest request) {
        List<StockRecord> stockRecords = facade.warehouse().listStockRecords();
        return request.items().stream()
                .map(OrderItemRequest::productId)
                .filter(pid -> stockRecords.stream().anyMatch(r -> r.productId().equals(pid) && r.quantity() >= 1))
                .findFirst()
                .map(record -> stockRecords.stream()
                        .filter(r -> r.productId().equals(record) && r.quantity() > 0)
                        .findFirst()
                        .map(StockRecord::binId)
                        .orElse("DEFAULT-WH"))
                .orElse("BACKORDER-WH");
    }

    private OrderFulfillmentModels.FulfillmentOrder createFulfillmentOrder(OrderRequest request, String assignedWarehouse) {
        return new OrderFulfillmentModels.FulfillmentOrder(
                "FULFILL-" + UUID.randomUUID(),
                request.orderId(),
                request.customerId(),
                request.items().get(0).productId(),
                request.items().get(0).quantity(),
                "CONFIRMED",
                request.orderDate(),
                calculateTotal(request.items()),
                request.customerName(),
                request.shippingAddress(),
                request.contactNumber(),
                "PAY-" + UUID.randomUUID(),
                request.paymentStatus(),
                request.paymentMethod(),
                0,
                0,
                assignedWarehouse,
                "RACK-A1",
                "PENDING",
                "PENDING",
                null,
                "NOT_SHIPPED",
                "NOT_ASSIGNED",
                null,
                LocalDate.now().plusDays(3),
                "IN_PROGRESS",
                "WAREHOUSE_TEAM",
                LocalDateTime.now(),
                "Leave at front door",
                0,
                "OPEN",
                "NONE",
                null,
                assignedWarehouse,
                "HIGH",
                LocalDateTime.now()
        );
    }

    private OrderRequest buildOrderRequestFromDb(Order order, List<OrderItem> orderItems) {
        return new OrderRequest(
                order.getOrderId(),
                order.getCustomerId(),
                "Customer-" + order.getCustomerId(),
                "UNKNOWN",
                "UNKNOWN",
                order.getSalesChannel(),
                "UNKNOWN",
                order.getPaymentStatus(),
                orderItems.stream()
                        .map(item -> new OrderItemRequest(
                                item.getOrderItemId(),
                                item.getProductId(),
                                item.getOrderedQuantity(),
                                item.getUnitPrice()
                        ))
                        .collect(Collectors.toList()),
                order.getOrderDate(),
                "UNKNOWN", // agentId
                "Unknown Agent" // agentName
        );
    }

    private boolean isPending(Order order) {
        return "RECEIVED".equalsIgnoreCase(order.getOrderStatus()) || "NEW".equalsIgnoreCase(order.getOrderStatus());
    }

    private void createPickTask(OrderRequest request, String assignedWarehouse) {
        facade.warehouse().createPickTask(new com.jackfruit.scm.database.model.WarehouseModels.PickTask(
                "PICK-" + UUID.randomUUID(),
                request.orderId(),
                "WAREHOUSE_PICKER_01",
                request.items().get(0).productId(),
                request.items().get(0).quantity(),
                "PENDING"
        ));
    }

    private BigDecimal calculateTotal(List<OrderItemRequest> items) {
        return items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void triggerCommissionWebhook(OrderFulfillmentModels.FulfillmentOrder fulfillmentOrder, OrderRequest request) {
        try {
            CommissionEvent event = new CommissionEvent(
                    fulfillmentOrder.fulfillmentId(),
                    fulfillmentOrder.orderId(),
                    request.agentId() != null ? request.agentId() : "UNKNOWN_AGENT",
                    request.agentName() != null ? request.agentName() : "Unknown Agent",
                    fulfillmentOrder.customerId(),
                    fulfillmentOrder.totalAmount(),
                    "USD", // Default currency
                    fulfillmentOrder.orderDate(),
                    LocalDateTime.now(), // deliveryDate - using current time as approximation
                    "INV-" + fulfillmentOrder.fulfillmentId(),
                    request.salesChannel(),
                    fulfillmentOrder.paymentStatus(),
                    "ORDER_DELIVERED",
                    LocalDateTime.now()
            );
            commissionWebhookClient.sendCommissionEvent(event, facade);
        } catch (Exception e) {
            // Log webhook failure but don't fail the main process
            OrderFulfillmentExceptionLogger.logException(
                    facade, 6, "COMMISSION_WEBHOOK_TRIGGER_FAILED",
                    "Failed to trigger commission webhook: " + e.getMessage(), Severity.MINOR
            );
        }
    }
}
