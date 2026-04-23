package com.jackfruit.orderfulfillment.service;

import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Order validation and fraud detection service.
 * Validates customer details, shipping addresses, payment, and order structure.
 */
public class OrderValidationService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\-\\s]{7,15}$");

    public void validate(OrderRequest request) {
        Objects.requireNonNull(request, "Order request must not be null");

        // Order ID validation
        if (request.orderId() == null || request.orderId().isBlank()) {
            throw new IllegalArgumentException("Order ID is required");
        }

        // Customer validation
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        // Shipping address validation (fraud detection: reject obviously invalid addresses)
        if (request.shippingAddress() == null || request.shippingAddress().isBlank()) {
            throw new IllegalArgumentException("Shipping address is required");
        }
        if (request.shippingAddress().length() < 3) {
            throw new IllegalArgumentException("Shipping address too short — possible fraud");
        }

        // Contact number validation
        if (request.contactNumber() != null && !request.contactNumber().isBlank()) {
            if (!PHONE_PATTERN.matcher(request.contactNumber()).matches()) {
                throw new IllegalArgumentException("Invalid contact number format");
            }
        }

        // Payment validation
        if (request.paymentStatus() == null || request.paymentStatus().isBlank()) {
            throw new IllegalArgumentException("Payment status is required");
        }
        if ("DECLINED".equalsIgnoreCase(request.paymentStatus())
                || "FAILED".equalsIgnoreCase(request.paymentStatus())) {
            throw new IllegalArgumentException("Payment declined — cannot process order");
        }

        // Items validation
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // Individual item validation
        for (OrderItemRequest item : request.items()) {
            if (item.productId() == null || item.productId().isBlank()) {
                throw new IllegalArgumentException("Product ID is required for all items");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Item quantity must be positive: " + item.productId());
            }
            if (item.unitPrice() == null || item.unitPrice().signum() <= 0) {
                throw new IllegalArgumentException("Unit price must be positive: " + item.productId());
            }
        }
    }
}
