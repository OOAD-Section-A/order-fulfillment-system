package com.scm.orderfulfillment.main;

import com.scm.orderfulfillment.service.OrderValidationService;
import com.scm.orderfulfillment.orchestrator.OrderFulfillmentOrchestrator;

import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.adapter.OrderAdapter;

public class Main {

    public static void main(String[] args) {

        System.out.println("Starting Order Fulfillment System...");

        try (SupplyChainDatabaseFacade db = new SupplyChainDatabaseFacade()) {

            OrderFulfillmentOrchestrator system =
                    new OrderFulfillmentOrchestrator(
                            new OrderValidationService(),
                            db
                    );

            OrderAdapter orderDb = new OrderAdapter(db);

            var orders = orderDb.listOrders();

            for (var o : orders) {
                system.process(o.orderId());
            }

        } catch (Exception e) {
            System.out.println("Fatal error: " + e.getMessage());
        }
    }
}