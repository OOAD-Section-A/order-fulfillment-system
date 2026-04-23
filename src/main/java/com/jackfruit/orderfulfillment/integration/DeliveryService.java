package com.jackfruit.orderfulfillment.integration;

/**
 * Interface for delivery/logistics operations — abstracts picking, packing, staging, and shipment.
 */
public interface DeliveryService {

    String createShipment(String orderId);

    void createPickTask(String orderId, String productId, int quantity, String warehouse);

    void createPackingDetail(String fulfillmentId);

    void createStagingDispatch(String orderId);

    String getTrackingStatus(String trackingId);
}