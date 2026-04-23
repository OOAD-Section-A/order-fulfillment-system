package com.jackfruit.orderfulfillment.adapter;

import com.jackfruit.orderfulfillment.model.OrderRequest;

import com.ramennoodles.delivery.integration.IOrderFulfillmentService;
import com.ramennoodles.delivery.model.Coordinate;
import com.ramennoodles.delivery.model.Customer;
import com.ramennoodles.delivery.model.Order;
import com.ramennoodles.delivery.model.PODRecord;
import com.ramennoodles.delivery.observer.DeliveryEventType;
import com.ramennoodles.delivery.observer.DeliveryEventListener;
import com.ramennoodles.delivery.enums.OrderStatus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter bridging Order Fulfillment subsystem to Real-Time Delivery Monitoring subsystem.
 *
 * Implements IOrderFulfillmentService (their interface) so their system can query our data.
 * Also wraps DeliveryMonitoringFacadeDB so our system can push fulfilled orders to them.
 *
 * NOTE: Their facade class (DeliveryMonitoringFacadeDB) is compiled with Java 24,
 * while our project compiles with Java 21. We use reflection to instantiate it,
 * keeping all interfaces and models (Java 17 compatible) as direct imports.
 *
 * Integration pattern:
 *   Order Fulfillment → (push) → Delivery Monitoring (via createDelivery)
 *   Delivery Monitoring → (pull) → Order Fulfillment (via IOrderFulfillmentService)
 */
public class DeliveryMonitoringAdapter implements IOrderFulfillmentService {

    private Object deliveryFacade;  // DeliveryMonitoringFacadeDB instance (loaded via reflection)
    private boolean connected = false;

    // Local cache of orders we've dispatched (for their pull queries)
    private final Map<String, OrderRequest> dispatchedOrders = new ConcurrentHashMap<>();

    public DeliveryMonitoringAdapter() {
        try {
            // Use reflection because their facade is compiled with Java 24 (class file v68)
            // while we compile with Java 21 (v65). At runtime with JDK 24 this works fine.
            Class<?> facadeClass = Class.forName("com.ramennoodles.delivery.facade.DeliveryMonitoringFacadeDB");
            deliveryFacade = facadeClass.getDeclaredConstructor().newInstance();
            connected = true;
            System.out.println("[DeliveryMonitoringAdapter] Connected to Real-Time Delivery Monitoring system");
        } catch (Throwable t) {
            // Catches UnsupportedClassVersionError (Java 24 JAR on JDK 21) and any other errors
            System.out.println("[DeliveryMonitoringAdapter] Delivery Monitoring not available: " + t.getMessage());
            System.out.println("[DeliveryMonitoringAdapter] Running in standalone mode — integration will activate with JDK 24");
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    // ======================== OUR SYSTEM → THEIR SYSTEM (Push) ========================

    /**
     * Called after order fulfillment is complete.
     * Pushes the fulfilled order to the Delivery Monitoring system for live tracking.
     */
    public String handoffToDeliveryMonitoring(OrderRequest request) {
        if (!connected) {
            System.out.println("[DeliveryMonitoringAdapter] Skipping handoff — monitoring system not connected");
            return null;
        }

        try {
            // Register the customer in their system
            Method registerCustomer = deliveryFacade.getClass().getMethod(
                    "registerCustomer", String.class, String.class, String.class);
            Customer customer = (Customer) registerCustomer.invoke(deliveryFacade,
                    request.customerName(),
                    request.customerId() + "@email.com",
                    request.contactNumber()
            );

            // Create delivery with coordinates
            Coordinate pickup = new Coordinate(12.9352, 77.6245);    // Warehouse
            Coordinate dropoff = new Coordinate(12.9716, 77.5946);   // Customer

            Method createDelivery = deliveryFacade.getClass().getMethod(
                    "createAndInitializeDelivery",
                    String.class, String.class, String.class,
                    Coordinate.class, Coordinate.class);
            Order deliveryOrder = (Order) createDelivery.invoke(deliveryFacade,
                    customer.getCustomerId(),
                    "Warehouse Central",
                    request.shippingAddress(),
                    pickup, dropoff
            );

            dispatchedOrders.put(deliveryOrder.getOrderId(), request);

            System.out.println("[DeliveryMonitoringAdapter] Order handed off for delivery: "
                    + deliveryOrder.getOrderId());
            return deliveryOrder.getOrderId();

        } catch (Exception e) {
            System.err.println("[DeliveryMonitoringAdapter] Handoff failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the live tracking URL for a delivery order.
     */
    public String getTrackingURL(String deliveryOrderId) {
        if (!connected) return "Tracking unavailable (monitoring system not connected)";
        try {
            Method getTracking = deliveryFacade.getClass().getMethod("getTrackingURL", String.class);
            return (String) getTracking.invoke(deliveryFacade, deliveryOrderId);
        } catch (Exception e) {
            return "Tracking unavailable";
        }
    }

    /**
     * Gets the current delivery status from their system.
     */
    public String getLiveDeliveryStatus(String deliveryOrderId) {
        if (!connected) return "Status unavailable";
        try {
            Method getStatus = deliveryFacade.getClass().getMethod("getOrderStatus", String.class);
            Object status = getStatus.invoke(deliveryFacade, deliveryOrderId);
            return status != null ? status.toString() : "UNKNOWN";
        } catch (Exception e) {
            return "Status unavailable";
        }
    }

    /**
     * Subscribes to delivery events from their system (Observer pattern).
     */
    public void subscribeToDeliveryEvents(DeliveryEventType eventType, DeliveryEventListener listener) {
        if (!connected) return;
        try {
            Method subscribe = deliveryFacade.getClass().getMethod(
                    "subscribeToEvents", DeliveryEventType.class, DeliveryEventListener.class);
            subscribe.invoke(deliveryFacade, eventType, listener);
        } catch (Exception e) {
            System.err.println("[DeliveryMonitoringAdapter] Event subscription failed: " + e.getMessage());
        }
    }

    // ======================== THEIR SYSTEM → OUR SYSTEM (Pull via IOrderFulfillmentService) ========================

    @Override
    public Order getOrderDetails(String orderId) {
        OrderRequest request = dispatchedOrders.get(orderId);
        if (request == null) return null;

        Order order = Order.create(
                request.customerId(),
                "Warehouse Central",
                request.shippingAddress(),
                new Coordinate(12.9352, 77.6245),
                new Coordinate(12.9716, 77.5946)
        );
        order.setOrderId(orderId);
        return order;
    }

    @Override
    public List<Order> getDispatchedOrders() {
        List<Order> orders = new ArrayList<>();
        for (Map.Entry<String, OrderRequest> entry : dispatchedOrders.entrySet()) {
            Order order = getOrderDetails(entry.getKey());
            if (order != null) orders.add(order);
        }
        return orders;
    }

    @Override
    public void notifyOrderPickedUp(String orderId, String riderId) {
        System.out.println("[DeliveryMonitoringAdapter] Order " + orderId
                + " picked up by rider " + riderId);
    }

    @Override
    public void notifyOrderDelivered(String orderId, PODRecord pod) {
        System.out.println("[DeliveryMonitoringAdapter] Order " + orderId
                + " delivered! POD: " + pod.getPodId()
                + " | Signature: " + pod.getSignatureUrl());
        dispatchedOrders.remove(orderId);
    }

    @Override
    public void notifyDeliveryFailed(String orderId, String reason) {
        System.out.println("[DeliveryMonitoringAdapter] Delivery FAILED for order " + orderId
                + " — Reason: " + reason);
    }

    @Override
    public Customer getCustomerForOrder(String orderId) {
        OrderRequest request = dispatchedOrders.get(orderId);
        if (request == null) return null;

        return Customer.createProfile(
                request.customerName(),
                request.customerId() + "@email.com",
                request.contactNumber()
        );
    }
}
