package com.scm.orderfulfillment.service;

import com.scm.orderfulfillment.model.Order;
import com.scm.exceptions.subsystems.OrderFulfilmentSubsystem;

public class OrderValidationService {

    private final OrderFulfilmentSubsystem exceptions = OrderFulfilmentSubsystem.INSTANCE;

    public boolean validate(Order o) {

        if (o.orderId == null || o.orderId.isEmpty()) {
            exceptions.onInvalidOrderId(o.orderId);
            return false;
        }

        if (o.address == null || o.address.isEmpty()) {
            exceptions.onInvalidShippingAddress(o.orderId, o.address);
            return false;
        }

        if (!o.paymentStatus) {
            exceptions.onPaymentNotConfirmed(o.orderId, "FAILED");
            return false;
        }

        return true;
    }
}