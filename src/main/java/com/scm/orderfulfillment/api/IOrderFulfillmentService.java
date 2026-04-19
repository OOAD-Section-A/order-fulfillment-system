package com.scm.orderfulfillment.api;

import java.util.List;
import com.scm.orderfulfillment.model.Order;
import com.ramennoodles.delivery.model.*;

public interface IOrderFulfillmentService {

    Order getOrderDetails(String orderId);

    List<Order> getDispatchedOrders();

    void notifyOrderPickedUp(String orderId, String riderId);

    void notifyOrderDelivered(String orderId, PODRecord pod);

    void notifyDeliveryFailed(String orderId, String reason);

    Customer getCustomerForOrder(String orderId);
}