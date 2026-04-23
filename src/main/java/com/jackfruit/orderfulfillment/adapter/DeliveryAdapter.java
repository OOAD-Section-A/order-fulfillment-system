package com.jackfruit.orderfulfillment.adapter;

import com.jackfruit.orderfulfillment.integration.DeliveryService;
import com.jackfruit.scm.database.adapter.OrderFulfillmentAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.OrderFulfillmentModels;
import com.jackfruit.scm.database.model.WarehouseModels;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Adapter that bridges DeliveryService interface with the DB team's warehouse and fulfillment facades.
 * Handles picking, packing, staging dispatch, and shipment tracking.
 */
public class DeliveryAdapter implements DeliveryService {

    private final SupplyChainDatabaseFacade facade;
    private final OrderFulfillmentAdapter fulfillmentAdapter;

    public DeliveryAdapter(SupplyChainDatabaseFacade facade) {
        this.facade = facade;
        this.fulfillmentAdapter = new OrderFulfillmentAdapter(facade);
    }

    @Override
    public String createShipment(String orderId) {
        String trackingId = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        System.out.println("[DeliveryAdapter] Shipment created: " + trackingId + " for order " + orderId);
        return trackingId;
    }

    @Override
    public void createPickTask(String orderId, String productId, int quantity, String warehouse) {
        try {
            WarehouseModels.PickTask pickTask = new WarehouseModels.PickTask(
                    "PICK-" + UUID.randomUUID(),
                    orderId,
                    "WAREHOUSE_PICKER_01",
                    productId,
                    quantity,
                    "PENDING"
            );
            facade.warehouse().createPickTask(pickTask);
            System.out.println("[DeliveryAdapter] Pick task created for order " + orderId);
        } catch (Exception e) {
            System.err.println("[DeliveryAdapter] Pick task issue: " + e.getMessage());
        }
    }

    @Override
    public void createPackingDetail(String fulfillmentId) {
        try {
            OrderFulfillmentModels.PackingDetail packingDetail = new OrderFulfillmentModels.PackingDetail(
                    "PACK-" + UUID.randomUUID(),
                    fulfillmentId,
                    "STANDARD_BOX",
                    "warehouse-robot-01",
                    LocalDateTime.now(),
                    BigDecimal.valueOf(5.4)
            );
            fulfillmentAdapter.createPackingDetail(packingDetail);
            System.out.println("[DeliveryAdapter] Packing detail created for fulfillment " + fulfillmentId);
        } catch (Exception e) {
            System.err.println("[DeliveryAdapter] Packing issue: " + e.getMessage());
        }
    }

    @Override
    public void createStagingDispatch(String orderId) {
        try {
            WarehouseModels.StagingDispatch dispatch = new WarehouseModels.StagingDispatch(
                    UUID.randomUUID().toString(),
                    "DOCK-01",
                    orderId,
                    LocalDateTime.now(),
                    "STAGED"
            );
            facade.warehouse().createStagingDispatch(dispatch);
            System.out.println("[DeliveryAdapter] Staging dispatch created for order " + orderId);
        } catch (Exception e) {
            System.err.println("[DeliveryAdapter] Staging dispatch issue: " + e.getMessage());
        }
    }

    @Override
    public String getTrackingStatus(String trackingId) {
        // Future: integrate with Real-Time Delivery Monitoring subsystem
        return "IN_TRANSIT";
    }
}