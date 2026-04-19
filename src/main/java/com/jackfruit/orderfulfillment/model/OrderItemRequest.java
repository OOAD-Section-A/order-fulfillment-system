package com.jackfruit.orderfulfillment.model;

import java.math.BigDecimal;

public record OrderItemRequest(
        String orderItemId,
        String productId,
        int quantity,
        BigDecimal unitPrice
) {
}
