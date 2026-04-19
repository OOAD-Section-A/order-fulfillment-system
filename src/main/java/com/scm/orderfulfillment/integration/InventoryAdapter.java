package com.scm.orderfulfillment.integration;

import inventory_subsystem.InventoryUI;

public class InventoryAdapter {

    private InventoryUI inventory;

    public InventoryAdapter(InventoryUI inventory) {
        this.inventory = inventory;
    }

    public boolean reserveStock(String sku, String loc, String sup, int qty) throws Exception {

        int stock = inventory.getStock(sku, loc, sup);

        if (stock < qty) return false;

        inventory.removeStock(sku, loc, sup, qty);
        return true;
    }
}