package com.jackfruit.orderfulfillment.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CommissionEvent(
        String fulfillmentId,
        String orderId,
        String agentId,
        String agentName,
        String customerId,
        BigDecimal totalSalesAmount,
        String currency,
        LocalDateTime orderDate,
        LocalDateTime deliveryDate,
        String invoiceNumber,
        String salesChannel,
        String paymentStatus,
        String eventType,
        LocalDateTime timestamp
) {
}