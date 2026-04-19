package com.scm.orderfulfillment.orchestrator;

import com.scm.exceptions.subsystems.OrderFulfilmentSubsystem;
import com.scm.exceptions.Severity;

import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;

// Real-time delivery
import com.ramennoodles.delivery.facade.DeliveryMonitoringFacade;
import com.ramennoodles.delivery.enums.DeliveryEventType;
import com.ramennoodles.delivery.model.PODRecord;

public class OrderFulfillmentOrchestrator {

    private final OrderFulfilmentSubsystem exceptions = OrderFulfilmentSubsystem.INSTANCE;
    private final SupplyChainDatabaseFacade db;

    private DeliveryMonitoringFacade deliverySystem;

    public OrderFulfillmentOrchestrator(SupplyChainDatabaseFacade db) {
        this.db = db;
        this.deliverySystem = new DeliveryMonitoringFacade();
        subscribeToEvents();
    }

    // ================= REAL-TIME EVENTS =================
    private void subscribeToEvents() {
        try {
            deliverySystem.subscribeToEvents(
                    DeliveryEventType.ORDER_DELIVERED,
                    (eventType, data) -> {
                        String orderId = (String) data.get("orderId");
                        PODRecord pod = (PODRecord) data.get("pod");
                        notifyOrderDelivered(orderId, pod);
                    }
            );
        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("DeliveryMonitoring", e.getMessage());
            return;
        }
    }

    // ================= MAIN FLOW =================
    public void process(String fulfillmentId) {

        // ================= FETCH =================
        var fulfillment = db.orders().getFulfillmentOrder(fulfillmentId);

        if (fulfillment == null) {
            exceptions.onInvalidOrderId(fulfillmentId);
            return;
        }

        String orderId = fulfillment.order_id();
        String productId = fulfillment.product_id();
        int quantity = fulfillment.quantity();
        String warehouseId = fulfillment.warehouse_id();

        // ================= BASIC VALIDATIONS =================
        if (fulfillment.shipping_address() == null) {
            exceptions.onInvalidShippingAddress(orderId, null);
            return;
        }

        if (!fulfillment.payment_status()) {
            exceptions.onPaymentNotConfirmed(orderId, "NOT CONFIRMED");
            return;
        }

        // ================= PRODUCT =================
        var product = db.inventory().getProduct(productId);

        // ================= PRICING =================
        try {
            db.pricing().getActivePrice(product.sku());
        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("Pricing", e.getMessage());
            return;
        }

        // ================= INVENTORY =================
        try {
            var stock = db.inventory().getStockLevel(productId);

            if (stock.available_stock_qty() < quantity) {
                exceptions.onSystemIntegrationFailure("Inventory", "Insufficient stock");
                return;
            }

            db.inventory().reserveStock(productId, quantity, orderId);

        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("Inventory", e.getMessage());
            return;
        }

        // ================= LEDGER =================
        try {
            db.inventory().recordLedgerEntry(orderId, productId, quantity);
        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("DoubleEntryStock", e.getMessage());
            return;
        }

        // ================= WAREHOUSE =================
        try {
            var warehouse = db.warehouse().getWarehouse(warehouseId);

            db.warehouse().createPickTask(orderId, productId, quantity);

        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("Warehouse", e.getMessage());
            return;
        }

        // ================= STOCK MOVEMENT =================
        try {
            db.warehouse().recordStockMovement(productId, quantity, "OUTBOUND");
        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("WarehouseMovement", e.getMessage());
            return;
        }

        // ================= RFID =================
        try {
            var rfidEvent = db.barcodeTracking()
                    .getLatestEventForProduct(productId);

            if (rfidEvent == null || !"OK".equals(rfidEvent.status())) {
                exceptions.onSystemIntegrationFailure("RFID", "RFID scan failed");
                return;
            }

        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("RFID", e.getMessage());
            return;
        }

        // ================= PACKING =================
        try {
            db.packaging().createPackagingJob(
                    orderId,
                    quantity,
                    fulfillment.total_amount()
            );

            db.orders().updatePackingStatus(fulfillmentId, "PACKED");

        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("Packing", e.getMessage());
            return;
        }

        // ================= LOGISTICS =================
        String shipmentId;

        try {
            var warehouse = db.warehouse().getWarehouse(warehouseId);

            shipmentId = db.logistics().createShipment(
                    orderId,
                    warehouse.warehouse_name(),
                    fulfillment.shipping_address(),
                    quantity
            );

            db.orders().updateShipmentId(fulfillmentId, shipmentId);

        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("Logistics", e.getMessage());
            return;
        }

        // ================= DELIVERY =================
        try {
            db.delivery().createDeliveryOrder(
                    orderId,
                    fulfillment.customer_id(),
                    fulfillment.shipping_address(),
                    warehouseId
            );

            db.orders().updateShippingStatus(fulfillmentId, "SHIPPED");

        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("DeliveryOrders", e.getMessage());
            return;
        }

        // ================= REAL-TIME DELIVERY =================
        try {
            var warehouse = db.warehouse().getWarehouse(warehouseId);

            deliverySystem.createAndInitializeDelivery(
                    fulfillment.customer_id(),
                    warehouse.warehouse_name(),
                    fulfillment.shipping_address(),
                    null,
                    null
            );

        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("RealTimeDelivery", e.getMessage());
            return;
        }

        // ================= DB TRACKING =================
        try {
            db.deliveryTracking().createTrackingRoute(
                    orderId,
                    fulfillment.customer_id(),
                    fulfillment.shipping_address()
            );
        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("Tracking", e.getMessage());
            return;
        }

        // ================= REPORTING =================
        try {
            db.reporting().logOrderFulfillment(
                    orderId,
                    quantity,
                    0
            );
        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("Reporting", e.getMessage());
            return;
        }
    }

    // ================= CALLBACK =================
    private void notifyOrderDelivered(String orderId, PODRecord pod) {
        try {
            db.orders().updateShippingStatus(orderId, "DELIVERED");
        } catch (Exception e) {
            exceptions.onSystemIntegrationFailure("DeliveryUpdate", e.getMessage());
            return;
        }
    }
}