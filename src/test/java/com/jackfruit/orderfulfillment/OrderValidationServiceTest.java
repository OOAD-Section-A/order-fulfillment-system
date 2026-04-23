package com.jackfruit.orderfulfillment;

import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.orderfulfillment.service.OrderValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderValidationService.
 * Tests validation rules and fraud detection logic.
 */
public class OrderValidationServiceTest {

    private OrderValidationService validationService;

    @BeforeEach
    public void setUp() {
        validationService = new OrderValidationService();
    }

    @Test
    public void testValidOrderPasses() {
        OrderRequest request = buildValidRequest();
        assertDoesNotThrow(() -> validationService.validate(request));
    }

    @Test
    public void testNullRequestThrows() {
        assertThrows(NullPointerException.class, () -> validationService.validate(null));
    }

    @Test
    public void testBlankOrderIdThrows() {
        OrderRequest request = new OrderRequest(
                "", "CUST-001", "John", "123 Street", "9876543210",
                "WEB", "CREDIT_CARD", "AUTHORIZED",
                List.of(new OrderItemRequest("I1", "P1", 1, new BigDecimal("10"))),
                LocalDateTime.now(), "A1", "Agent"
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(request));
    }

    @Test
    public void testBlankShippingAddressThrows() {
        OrderRequest request = new OrderRequest(
                "ORD-001", "CUST-001", "John", "", "9876543210",
                "WEB", "CREDIT_CARD", "AUTHORIZED",
                List.of(new OrderItemRequest("I1", "P1", 1, new BigDecimal("10"))),
                LocalDateTime.now(), "A1", "Agent"
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(request));
    }

    @Test
    public void testEmptyItemsThrows() {
        OrderRequest request = new OrderRequest(
                "ORD-001", "CUST-001", "John", "123 Street", "9876543210",
                "WEB", "CREDIT_CARD", "AUTHORIZED",
                List.of(),
                LocalDateTime.now(), "A1", "Agent"
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(request));
    }

    @Test
    public void testDeclinedPaymentThrows() {
        OrderRequest request = new OrderRequest(
                "ORD-001", "CUST-001", "John", "123 Street", "9876543210",
                "WEB", "CREDIT_CARD", "DECLINED",
                List.of(new OrderItemRequest("I1", "P1", 1, new BigDecimal("10"))),
                LocalDateTime.now(), "A1", "Agent"
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(request));
    }

    @Test
    public void testZeroQuantityThrows() {
        OrderRequest request = new OrderRequest(
                "ORD-001", "CUST-001", "John", "123 Street", "9876543210",
                "WEB", "CREDIT_CARD", "AUTHORIZED",
                List.of(new OrderItemRequest("I1", "P1", 0, new BigDecimal("10"))),
                LocalDateTime.now(), "A1", "Agent"
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(request));
    }

    @Test
    public void testShortAddressFraudDetection() {
        OrderRequest request = new OrderRequest(
                "ORD-001", "CUST-001", "John", "AB", "9876543210",
                "WEB", "CREDIT_CARD", "AUTHORIZED",
                List.of(new OrderItemRequest("I1", "P1", 1, new BigDecimal("10"))),
                LocalDateTime.now(), "A1", "Agent"
        );
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(request));
    }

    private OrderRequest buildValidRequest() {
        return new OrderRequest(
                "ORD-VALID-001", "CUST-001", "John Doe",
                "42 MG Road, Bangalore", "9876543210",
                "WEB", "CREDIT_CARD", "AUTHORIZED",
                List.of(new OrderItemRequest("ITEM-1", "PROD-1", 2, new BigDecimal("100.00"))),
                LocalDateTime.now(), "AGENT-001", "Agent Smith"
        );
    }
}
