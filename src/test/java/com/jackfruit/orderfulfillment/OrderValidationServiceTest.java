package com.jackfruit.orderfulfillment;

import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.orderfulfillment.service.OrderValidationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OrderValidationServiceTest {

    private final OrderValidationService validationService = new OrderValidationService();

    @Test
    public void testValidateValidOrder() {
        OrderRequest validRequest = buildValidOrderRequest();
        assertDoesNotThrow(() -> validationService.validate(validRequest));
    }

    @Test
    public void testValidateOrderWithNullOrderId() {
        OrderRequest invalidRequest = new OrderRequest(
                null,
                "CUST-001",
                "Customer",
                "Address",
                "Phone",
                "ECOMMERCE",
                "PAYMENT",
                "AUTHORIZED",
                List.of(new OrderItemRequest("ITEM-001", "PRD-001", 1, BigDecimal.ONE)),
                LocalDateTime.now()
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(invalidRequest));
    }

    @Test
    public void testValidateOrderWithBlankOrderId() {
        OrderRequest invalidRequest = new OrderRequest(
                "",
                "CUST-001",
                "Customer",
                "Address",
                "Phone",
                "ECOMMERCE",
                "PAYMENT",
                "AUTHORIZED",
                List.of(new OrderItemRequest("ITEM-001", "PRD-001", 1, BigDecimal.ONE)),
                LocalDateTime.now()
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(invalidRequest));
    }

    @Test
    public void testValidateOrderWithNullShippingAddress() {
        OrderRequest invalidRequest = new OrderRequest(
                "ORD-001",
                "CUST-001",
                "Customer",
                null,
                "Phone",
                "ECOMMERCE",
                "PAYMENT",
                "AUTHORIZED",
                List.of(new OrderItemRequest("ITEM-001", "PRD-001", 1, BigDecimal.ONE)),
                LocalDateTime.now()
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(invalidRequest));
    }

    @Test
    public void testValidateOrderWithEmptyItems() {
        OrderRequest invalidRequest = new OrderRequest(
                "ORD-001",
                "CUST-001",
                "Customer",
                "Address",
                "Phone",
                "ECOMMERCE",
                "PAYMENT",
                "AUTHORIZED",
                List.of(),
                LocalDateTime.now()
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(invalidRequest));
    }

    private OrderRequest buildValidOrderRequest() {
        return new OrderRequest(
                "ORD-TEST-001",
                "CUST-TEST-001",
                "Test Customer",
                "123 Test Street, Test City, TC 12345",
                "+1-123-456-7890",
                "ECOMMERCE",
                "TEST-PAYMENT-123",
                "AUTHORIZED",
                List.of(
                        new OrderItemRequest("ITEM-TEST-001", "PRD-TEST-001", 2, new BigDecimal("10.00")),
                        new OrderItemRequest("ITEM-TEST-002", "PRD-TEST-002", 1, new BigDecimal("5.50"))
                ),
                LocalDateTime.now()
        );
    }
}
