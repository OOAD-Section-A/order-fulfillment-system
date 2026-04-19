package com.scm.orderfulfillment.api;

import com.scm.orderfulfillment.model.Order;

public interface OrderFulfillmentAPI {

    void createOrder(Order order);

    void cancelOrder(String orderId);

    void getOrderStatus(String orderId);

    void processReturn(String orderId);
}