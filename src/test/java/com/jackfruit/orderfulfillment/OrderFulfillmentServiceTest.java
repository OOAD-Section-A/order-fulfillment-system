package com.jackfruit.orderfulfillment;

import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.orderfulfillment.service.OrderFulfillmentService;
import com.jackfruit.scm.database.adapter.OrderFulfillmentAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class OrderFulfillmentServiceTest {

    private OrderFulfillmentService service;

    @BeforeEach
    public void setUp() {
        // Note: In a real test, you would use mocks or an in-memory database
        // For now, this assumes a test database is set up
        SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade();
        OrderFulfillmentAdapter adapter = new OrderFulfillmentAdapter(facade);
        service = new OrderFulfillmentService(facade, adapter);
    }

    @Test
    public void testProcessNewOrder() {
        OrderRequest request = buildDemoOrderRequest();
        assertDoesNotThrow(() -> {
            var fulfillmentOrder = service.processNewOrder(request);
            service.createPackingAndDispatch(request.orderId(), fulfillmentOrder.fulfillmentId());
        });
    }

    @Test
    public void testProcessPendingOrdersFromDatabase() {
        long ts = System.currentTimeMillis();
        String pendingOrderId = "ORD-DB-TEST-" + ts;
        insertPendingOrderWithItems(pendingOrderId, List.of(
                new OrderItemRequest("DBITEM-" + ts + "-1", "PRD-DB-001", 1, new BigDecimal("12.34")),
                new OrderItemRequest("DBITEM-" + ts + "-2", "PRD-DB-002", 2, new BigDecimal("5.50"))
        ));

        assertDoesNotThrow(() -> service.processPendingOrdersFromDatabase());
    }

    @Test
    public void testListFulfillmentOrders() {
        assertDoesNotThrow(() -> {
            List<?> orders = service.listFulfillmentOrders();
            // Add more assertions as needed
        });
    }

    private OrderRequest buildDemoOrderRequest() {
        long ts = System.currentTimeMillis();
        return new OrderRequest(
                "ORD-TEST-" + ts,
                "CUST-TEST-001",
                "Test Customer",
                "123 Test Street, Test City, TC 12345",
                "+1-123-456-7890",
                "ECOMMERCE",
                "TEST-PAYMENT-123",
                "AUTHORIZED",
                List.of(
                        new OrderItemRequest("ITEM-TEST-" + ts + "-1", "PRD-TEST-001", 2, new BigDecimal("10.00")),
                        new OrderItemRequest("ITEM-TEST-" + ts + "-2", "PRD-TEST-002", 1, new BigDecimal("5.50"))
                ),
                LocalDateTime.now()
        );
    }

    private void insertPendingOrderWithItems(String orderId, List<OrderItemRequest> items) {
        String dbUrl = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/OOAD");
        String dbUser = System.getenv().getOrDefault("DB_USERNAME", "root");
        String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "root1234");

        String insertOrder = "INSERT INTO orders (order_id, customer_id, order_status, order_date, total_amount, payment_status, sales_channel) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String insertItem = "INSERT INTO order_items (order_item_id, order_id, product_id, ordered_quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement orderStmt = conn.prepareStatement(insertOrder);
             PreparedStatement itemStmt = conn.prepareStatement(insertItem)) {

            BigDecimal total = items.stream()
                    .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            orderStmt.setString(1, orderId);
            orderStmt.setString(2, "CUST-DB-TEST");
            orderStmt.setString(3, "RECEIVED");
            orderStmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            orderStmt.setBigDecimal(5, total);
            orderStmt.setString(6, "AUTHORIZED");
            orderStmt.setString(7, "ECOMMERCE");
            orderStmt.executeUpdate();

            for (OrderItemRequest item : items) {
                itemStmt.setString(1, item.orderItemId());
                itemStmt.setString(2, orderId);
                itemStmt.setString(3, item.productId());
                itemStmt.setInt(4, item.quantity());
                itemStmt.setBigDecimal(5, item.unitPrice());
                itemStmt.setBigDecimal(6, item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
                itemStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to seed pending order with items", e);
        }
    }
}
