package com.scm.orderfulfillment.model;

public class Order {

    public String orderId;
    public String sku;
    public String locationId;
    public String supplierId;
    public int quantity;
    public String address;
    public boolean paymentStatus;

    public Order(String orderId, String sku, String locationId,
                 String supplierId, int quantity,
                 String address, boolean paymentStatus) {

        this.orderId = orderId;
        this.sku = sku;
        this.locationId = locationId;
        this.supplierId = supplierId;
        this.quantity = quantity;
        this.address = address;
        this.paymentStatus = paymentStatus;
    }
}