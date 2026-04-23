package com.jackfruit.orderfulfillment.integration;

import java.util.List;

/**
 * Interface for inventory operations — abstracts warehouse stock queries.
 * Supports ATP/GTP checks and intelligent warehouse routing.
 */
public interface InventoryService {

    boolean checkStock(String productId, int quantity);

    void reserveStock(String productId, int quantity);

    String getEstimatedDelivery(String productId, int quantity);

    /**
     * Selects the best warehouse to fulfill items based on stock availability.
     * Returns warehouse bin ID or "BACKORDER-WH" if no stock available.
     */
    String selectBestWarehouse(String productId, int quantity);
}