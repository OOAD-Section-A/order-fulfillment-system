package com.jackfruit.orderfulfillment;

import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.orderfulfillment.service.OrderFulfillmentExceptionLogger;
import com.jackfruit.orderfulfillment.service.OrderFulfillmentService;

import com.jackfruit.orderfulfillment.integration.InventoryService;
import com.jackfruit.orderfulfillment.integration.OrderRepository;
import com.jackfruit.orderfulfillment.integration.DeliveryService;
import com.jackfruit.orderfulfillment.integration.ExceptionService;
import com.jackfruit.orderfulfillment.integration.ReturnService;

import com.scm.core.SCMException;
import com.scm.core.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OrderFulfillmentService.
 *
 * KEY OOAD BENEFIT: Because we use Dependency Injection with interfaces,
 * we can swap real DB adapters with in-memory stubs for testing.
 * This is IMPOSSIBLE with the tightly-coupled GitHub version.
 */
public class OrderFulfillmentServiceTest {

    private OrderFulfillmentService service;
    private StubOrderRepository stubOrderRepo;
    private StubInventoryService stubInventory;
    private StubDeliveryService stubDelivery;
    private StubExceptionService stubExceptions;
    private StubReturnService stubReturns;

    @BeforeEach
    public void setUp() {
        stubInventory = new StubInventoryService();
        stubOrderRepo = new StubOrderRepository();
        stubDelivery = new StubDeliveryService();
        stubExceptions = new StubExceptionService();
        stubReturns = new StubReturnService();

        service = new OrderFulfillmentService(
                stubInventory,
                stubOrderRepo,
                stubDelivery,
                stubExceptions,
                stubReturns
        );
    }

    // ================= HAPPY PATH TESTS =================

    @Test
    public void testProcessNewOrder_Success() {
        OrderRequest request = buildDemoOrderRequest();
        String fulfillmentId = service.processNewOrder(request);

        assertNotNull(fulfillmentId);
        assertTrue(fulfillmentId.startsWith("FULFILL-"));
        assertTrue(stubOrderRepo.savedOrders.contains(request.orderId()));
        assertTrue(stubDelivery.pickTasksCreated > 0);
    }

    @Test
    public void testProcessNewOrder_CreatesPickTasks() {
        OrderRequest request = buildDemoOrderRequest();
        service.processNewOrder(request);

        assertEquals(2, stubDelivery.pickTasksCreated); // 2 items
    }

    @Test
    public void testProcessNewOrder_CreatesPackingAndDispatch() {
        OrderRequest request = buildDemoOrderRequest();
        service.processNewOrder(request);

        assertTrue(stubDelivery.packingCreated);
        assertTrue(stubDelivery.stagingCreated);
    }

    // ================= BACKORDER TESTS =================

    @Test
    public void testProcessNewOrder_BackorderWhenNoStock() {
        stubInventory.stockAvailable = false;
        OrderRequest request = buildDemoOrderRequest();

        String fulfillmentId = service.processNewOrder(request);
        assertNotNull(fulfillmentId);
        assertTrue(fulfillmentId.startsWith("BACKORDER-"));
    }

    // ================= VALIDATION TESTS =================

    @Test
    public void testProcessNewOrder_RejectsEmptyItems() {
        OrderRequest invalidRequest = new OrderRequest(
                "ORD-INVALID-001", "CUST-001", "Test Customer",
                "123 Test Street", "9876543210", "WEB",
                "CREDIT_CARD", "AUTHORIZED",
                List.of(),
                LocalDateTime.now(), "AGENT-001", "Test Agent"
        );

        assertThrows(IllegalArgumentException.class, () -> service.processNewOrder(invalidRequest));
    }

    @Test
    public void testProcessNewOrder_RejectsNullOrderId() {
        OrderRequest invalidRequest = new OrderRequest(
                null, "CUST-001", "Test Customer",
                "123 Test Street", "9876543210", "WEB",
                "CREDIT_CARD", "AUTHORIZED",
                List.of(new OrderItemRequest("I1", "P1", 1, new BigDecimal("10"))),
                LocalDateTime.now(), "AGENT-001", "Test Agent"
        );

        assertThrows(IllegalArgumentException.class, () -> service.processNewOrder(invalidRequest));
    }

    @Test
    public void testProcessNewOrder_RejectsDeclinedPayment() {
        OrderRequest invalidRequest = new OrderRequest(
                "ORD-DECLINED-001", "CUST-001", "Test Customer",
                "123 Test Street", "9876543210", "WEB",
                "CREDIT_CARD", "DECLINED",
                List.of(new OrderItemRequest("I1", "P1", 1, new BigDecimal("10"))),
                LocalDateTime.now(), "AGENT-001", "Test Agent"
        );

        assertThrows(IllegalArgumentException.class, () -> service.processNewOrder(invalidRequest));
    }

    // ================= EXCEPTION TESTS =================

    @Test
    public void testBuildScmException() {
        SCMException scmException = OrderFulfillmentExceptionLogger.buildScmException(
                99, "TEST_EXCEPTION", "Test error message", Severity.MINOR
        );

        assertEquals(99, scmException.getExceptionId());
        assertEquals("TEST_EXCEPTION", scmException.getExceptionName());
        assertEquals("ORDER_FULFILLMENT", scmException.getSubsystem());
        assertEquals(Severity.MINOR, scmException.getSeverity());
    }

    @Test
    public void testScmExceptionMajorSeverity() {
        SCMException scmException = OrderFulfillmentExceptionLogger.buildScmException(
                42, "FACTORY_TEST", "Factory unit test", Severity.MAJOR
        );

        assertEquals(Severity.MAJOR, scmException.getSeverity());
    }

    // ================= BATCH PROCESSING TESTS =================

    @Test
    public void testProcessPendingOrders_Empty() {
        assertDoesNotThrow(() -> service.processPendingOrdersFromDatabase());
    }

    @Test
    public void testProcessPendingOrders_WithOrders() {
        stubOrderRepo.pendingOrders.add(new OrderRequest(
                "ORD-PENDING-001", "CUST-002", "Jane Doe",
                "456 Test Ave", "1234567890", "MOBILE",
                "UPI", "AUTHORIZED",
                List.of(new OrderItemRequest("I1", "P1", 1, new BigDecimal("99.99"))),
                LocalDateTime.now(), "AGENT-002", "Agent Jane"
        ));

        assertDoesNotThrow(() -> service.processPendingOrdersFromDatabase());
        assertTrue(stubOrderRepo.savedFulfillments.size() > 0);
    }

    @Test
    public void testListFulfillmentOrders() {
        stubOrderRepo.savedFulfillments.add("FULFILL-TEST-001");
        List<String> orders = service.listFulfillmentOrders();
        assertEquals(1, orders.size());
        assertEquals("FULFILL-TEST-001", orders.get(0));
    }

    // ================= RETURNS & REVERSE LOGISTICS TESTS =================

    @Test
    public void testInitiateReturn() {
        String returnId = service.initiateReturn("ORD-001", "Defective product");
        assertNotNull(returnId);
        assertTrue(returnId.startsWith("RTN-"));
        assertEquals("LABEL_GENERATED", service.getReturnStatus(returnId));
    }

    @Test
    public void testProcessReturn_PassesInspection() {
        String returnId = service.initiateReturn("ORD-002", "Wrong size");
        String result = service.processReturn(returnId, "LIKE_NEW", "WH-01");
        assertEquals("RESTOCKED", result);
        assertEquals("RESTOCKED", service.getReturnStatus(returnId));
    }

    @Test
    public void testProcessReturn_FailsInspection() {
        String returnId = service.initiateReturn("ORD-003", "Arrived damaged");
        String result = service.processReturn(returnId, "DAMAGED", "WH-01");
        assertEquals("INSPECTION_FAILED", result);
        assertEquals("INSPECTION_FAILED", service.getReturnStatus(returnId));
    }

    @Test
    public void testGetReturnStatus_NotFound() {
        String status = service.getReturnStatus("RTN-NONEXISTENT");
        assertEquals("NOT_FOUND", status);
    }

    // ================= HELPER =================

    private OrderRequest buildDemoOrderRequest() {
        long ts = System.currentTimeMillis();
        return new OrderRequest(
                "ORD-TEST-" + ts, "CUST-TEST-001", "Test Customer",
                "123 Test Street, Test City, TC 12345", "9876543210",
                "ECOMMERCE", "CREDIT_CARD", "AUTHORIZED",
                List.of(
                        new OrderItemRequest("ITEM-" + ts + "-1", "PRD-001", 2, new BigDecimal("10.00")),
                        new OrderItemRequest("ITEM-" + ts + "-2", "PRD-002", 1, new BigDecimal("5.50"))
                ),
                LocalDateTime.now(), "AGENT-TEST-001", "Test Sales Agent"
        );
    }

    // =====================================================
    //  IN-MEMORY STUB IMPLEMENTATIONS (no database needed!)
    //  This demonstrates the power of Adapter + DI pattern.
    // =====================================================

    static class StubInventoryService implements InventoryService {
        boolean stockAvailable = true;

        @Override
        public boolean checkStock(String productId, int quantity) { return stockAvailable; }

        @Override
        public void reserveStock(String productId, int quantity) { }

        @Override
        public String getEstimatedDelivery(String productId, int quantity) { return "2-3 days"; }

        @Override
        public String selectBestWarehouse(String productId, int quantity) { return "TEST-WH-01"; }
    }

    static class StubOrderRepository implements OrderRepository {
        List<String> savedOrders = new ArrayList<>();
        List<String> savedFulfillments = new ArrayList<>();
        List<OrderRequest> pendingOrders = new ArrayList<>();

        @Override
        public void saveOrder(OrderRequest request) { savedOrders.add(request.orderId()); }

        @Override
        public boolean orderExists(String orderId) { return savedOrders.contains(orderId); }

        @Override
        public List<OrderRequest> listPendingOrders() { return pendingOrders; }

        @Override
        public void createFulfillmentRecord(String fulfillmentId, String orderId, OrderRequest request, String warehouse) {
            savedFulfillments.add(fulfillmentId);
        }

        @Override
        public List<String> listFulfillmentOrderIds() { return savedFulfillments; }

        @Override
        public boolean isFulfillmentExists(String orderId) {
            return savedFulfillments.stream().anyMatch(f -> f.contains(orderId));
        }
    }

    static class StubDeliveryService implements DeliveryService {
        int pickTasksCreated = 0;
        boolean packingCreated = false;
        boolean stagingCreated = false;

        @Override
        public String createShipment(String orderId) { return "TRK-TEST-" + orderId; }

        @Override
        public void createPickTask(String orderId, String productId, int quantity, String warehouse) { pickTasksCreated++; }

        @Override
        public void createPackingDetail(String fulfillmentId) { packingCreated = true; }

        @Override
        public void createStagingDispatch(String orderId) { stagingCreated = true; }

        @Override
        public String getTrackingStatus(String trackingId) { return "IN_TRANSIT"; }
    }

    static class StubExceptionService implements ExceptionService {
        List<String> loggedExceptions = new ArrayList<>();

        @Override
        public void logException(String module, String message, String severity) {
            loggedExceptions.add("[" + severity + "] " + module + ": " + message);
        }
    }

    static class StubReturnService implements ReturnService {
        Map<String, String> returns = new HashMap<>();
        int returnCounter = 0;

        @Override
        public String initiateReturn(String orderId, String reason) {
            String returnId = "RTN-" + String.format("%04d", ++returnCounter);
            returns.put(returnId, "INITIATED");
            return returnId;
        }

        @Override
        public String generateReturnLabel(String returnId) {
            returns.put(returnId, "LABEL_GENERATED");
            return "RTNLBL-" + returnId;
        }

        @Override
        public boolean inspectReturnedItem(String returnId, String condition) {
            boolean passes = "LIKE_NEW".equals(condition) || "GOOD".equals(condition);
            returns.put(returnId, passes ? "INSPECTION_PASSED" : "INSPECTION_FAILED");
            return passes;
        }

        @Override
        public void restockItem(String returnId, String warehouseId) {
            returns.put(returnId, "RESTOCKED");
        }

        @Override
        public String getReturnStatus(String returnId) {
            return returns.getOrDefault(returnId, "NOT_FOUND");
        }
    }
}