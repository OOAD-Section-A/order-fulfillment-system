package com.jackfruit.orderfulfillment;

import com.jackfruit.orderfulfillment.model.OrderItemRequest;
import com.jackfruit.orderfulfillment.model.OrderRequest;
import com.jackfruit.orderfulfillment.service.OrderFulfillmentService;
import com.jackfruit.scm.database.adapter.OrderFulfillmentAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderFulfillmentApplication {
    public static void main(String[] args) {
        System.out.println("Starting Order Fulfillment Subsystem...");
        System.out.println("Make sure DB_URL, DB_USERNAME, DB_PASSWORD are defined.");

        try (SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade()) {
            OrderFulfillmentAdapter adapter = new OrderFulfillmentAdapter(facade);
            OrderFulfillmentService service = new OrderFulfillmentService(facade, adapter);

            System.out.println("Processing pending orders from the database...");
            service.processPendingOrdersFromDatabase();

            System.out.println("Fulfillment flow completed successfully.");
            System.out.println("Existing fulfillment orders:");
            service.listFulfillmentOrders().forEach(System.out::println);
        } catch (Exception e) {
            System.err.println("Order fulfillment subsystem failed: " + e.getMessage());
            e.printStackTrace();
            // Exception handling is already done in the service layer
        }
    }
}
