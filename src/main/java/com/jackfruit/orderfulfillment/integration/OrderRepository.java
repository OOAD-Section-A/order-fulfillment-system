package com.jackfruit.orderfulfillment.integration;

import com.jackfruit.orderfulfillment.model.OrderRequest;

import java.util.List;

/**
 * Interface for order persistence — abstracts database CRUD for orders and fulfillment records.
 */
public interface OrderRepository {

    void saveOrder(OrderRequest request);

    boolean orderExists(String orderId);

    List<OrderRequest> listPendingOrders();

    void createFulfillmentRecord(String fulfillmentId, String orderId, OrderRequest request, String warehouse);

    List<String> listFulfillmentOrderIds();

    boolean isFulfillmentExists(String orderId);
}