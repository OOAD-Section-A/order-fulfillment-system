package com.jackfruit.orderfulfillment.adapter;

import com.jackfruit.orderfulfillment.integration.OrderRepository;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.orderfulfillment.model.OrderItemRequest;

import com.jackfruit.scm.database.adapter.OrderFulfillmentAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.Order;
import com.jackfruit.scm.database.model.OrderItem;
import com.jackfruit.scm.database.model.OrderFulfillmentModels;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter that bridges OrderRepository interface with the DB team's facade and fulfillment adapter.
 * Handles all order persistence and fulfillment record management.
 */
public class DatabaseAdapter implements OrderRepository {

    private final SupplyChainDatabaseFacade facade;
    private final OrderFulfillmentAdapter fulfillmentAdapter;

    public DatabaseAdapter(SupplyChainDatabaseFacade facade) {
        this.facade = facade;
        this.fulfillmentAdapter = new OrderFulfillmentAdapter(facade);
    }

    @Override
    public void saveOrder(OrderRequest request) {
        try {
            BigDecimal totalAmount = request.items().stream()
                    .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Order order = new Order(
                    request.orderId(),
                    request.customerId(),
                    "RECEIVED",
                    request.orderDate(),
                    totalAmount,
                    request.paymentStatus(),
                    request.salesChannel());

            // Skip if order already exists (prevents DUPLICATE_PRIMARY_KEY from DB facade)
            if (orderExists(request.orderId())) {
                System.out.println("[DatabaseAdapter] Order already exists, skipping: " + request.orderId());
                return;
            }

            facade.orders().createOrder(order);

            for (OrderItemRequest item : request.items()) {
                OrderItem orderItem = new OrderItem(
                        item.orderItemId(),
                        request.orderId(),
                        item.productId(),
                        item.quantity(),
                        item.unitPrice(),
                        item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
                facade.orders().addOrderItem(orderItem);
            }

            System.out.println("[DatabaseAdapter] Order saved: " + request.orderId());

        } catch (Exception e) {
            // Log but don't crash — the DB team's facade already handles the popup
            System.err.println("[DatabaseAdapter] Order save issue: " + e.getMessage());
        }
    }

    @Override
    public boolean orderExists(String orderId) {
        try {
            Optional<Order> order = facade.orders().getOrder(orderId);
            return order.isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<OrderRequest> listPendingOrders() {
        try {
            List<Order> orders = facade.orders().listOrders();
            Set<String> fulfilledOrderIds = fulfillmentAdapter.listFulfillmentOrders().stream()
                    .map(OrderFulfillmentModels.FulfillmentOrder::orderId)
                    .collect(Collectors.toSet());

            return orders.stream()
                    .filter(o -> isPending(o) && !fulfilledOrderIds.contains(o.getOrderId()))
                    .map(this::convertToOrderRequest)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to list pending orders: " + e.getMessage(), e);
        }
    }

    @Override
    public void createFulfillmentRecord(String fulfillmentId, String orderId, OrderRequest request, String warehouse) {
        try {
            // Skip if fulfillment already exists for this order
            if (isFulfillmentExists(orderId)) {
                System.out.println("[DatabaseAdapter] Fulfillment already exists for order " + orderId + ", skipping.");
                return;
            }

            BigDecimal totalAmount = request.items().stream()
                    .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            OrderFulfillmentModels.FulfillmentOrder fulfillmentOrder = new OrderFulfillmentModels.FulfillmentOrder(
                    fulfillmentId,
                    orderId,
                    request.customerId(),
                    request.items().get(0).productId(),
                    request.items().get(0).quantity(),
                    "CONFIRMED",
                    request.orderDate(),
                    totalAmount,
                    request.customerName(),
                    request.shippingAddress(),
                    request.contactNumber(),
                    "PAY-" + UUID.randomUUID(),
                    request.paymentStatus(),
                    request.paymentMethod(),
                    0,
                    0,
                    warehouse,
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
                    warehouse,
                    "HIGH",
                    LocalDateTime.now()
            );

            fulfillmentAdapter.createFulfillmentOrder(fulfillmentOrder);
            System.out.println("[DatabaseAdapter] Fulfillment record created: " + fulfillmentId);

        } catch (Exception e) {
            System.err.println("[DatabaseAdapter] Fulfillment record issue: " + e.getMessage());
        }
    }

    @Override
    public List<String> listFulfillmentOrderIds() {
        try {
            return fulfillmentAdapter.listFulfillmentOrders().stream()
                    .map(OrderFulfillmentModels.FulfillmentOrder::fulfillmentId)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to list fulfillment orders: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isFulfillmentExists(String orderId) {
        try {
            return fulfillmentAdapter.listFulfillmentOrders().stream()
                    .anyMatch(fo -> fo.orderId().equals(orderId));
        } catch (Exception e) {
            return false;
        }
    }

    // ================= HELPER METHODS =================

    private boolean isPending(Order order) {
        return "RECEIVED".equalsIgnoreCase(order.getOrderStatus())
                || "NEW".equalsIgnoreCase(order.getOrderStatus());
    }

    private OrderRequest convertToOrderRequest(Order order) {
        try {
            List<OrderItem> orderItems = facade.orders().listOrderItems(order.getOrderId());
            List<OrderItemRequest> items = orderItems.stream()
                    .map(item -> new OrderItemRequest(
                            item.getOrderItemId(),
                            item.getProductId(),
                            item.getOrderedQuantity(),
                            item.getUnitPrice()))
                    .collect(Collectors.toList());

            return new OrderRequest(
                    order.getOrderId(),
                    order.getCustomerId(),
                    "Customer-" + order.getCustomerId(),
                    "UNKNOWN",
                    "UNKNOWN",
                    order.getSalesChannel(),
                    "UNKNOWN",
                    order.getPaymentStatus(),
                    items,
                    order.getOrderDate(),
                    "UNKNOWN",
                    "Unknown Agent");
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert order: " + e.getMessage(), e);
        }
    }
}