package com.jackfruit.orderfulfillment.model;

import java.time.LocalDateTime;
import java.util.List;

public record OrderRequest(
        String orderId,
        String customerId,
        String customerName,
        String shippingAddress,
        String contactNumber,
        String salesChannel,
        String paymentMethod,
        String paymentStatus,
        List<OrderItemRequest> items,
        LocalDateTime orderDate,
        String agentId,
        String agentName
) {
}
