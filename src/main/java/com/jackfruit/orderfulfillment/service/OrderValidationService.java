package com.jackfruit.orderfulfillment.service;

import com.jackfruit.orderfulfillment.model.OrderRequest;

import java.util.Objects;

public class OrderValidationService {
    public void validate(OrderRequest request) {
        Objects.requireNonNull(request, "Order request must not be null");
        if (request.orderId() == null || request.orderId().isBlank()) {
            throw new IllegalArgumentException("Order ID is required");
        }
        if (request.shippingAddress() == null || request.shippingAddress().isBlank()) {
            throw new IllegalArgumentException("Shipping address is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
    }
}
