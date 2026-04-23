package com.jackfruit.orderfulfillment.integration;

/**
 * Interface for returns and reverse logistics operations.
 * Handles the full return lifecycle: initiation, label generation,
 * inspection, restocking, and refund processing.
 */
public interface ReturnService {

    /**
     * Initiates a return for a fulfilled order.
     * @param orderId the original order ID
     * @param reason customer-provided return reason
     * @return unique return ID (e.g., "RTN-xxx")
     */
    String initiateReturn(String orderId, String reason);

    /**
     * Generates a prepaid return shipping label for the customer.
     * @param returnId the return ID
     * @return return label tracking number
     */
    String generateReturnLabel(String returnId);

    /**
     * Records the inspection result of a returned item.
     * @param returnId the return ID
     * @param condition inspection result (e.g., "LIKE_NEW", "DAMAGED", "DEFECTIVE")
     * @return true if item passes inspection for restocking
     */
    boolean inspectReturnedItem(String returnId, String condition);

    /**
     * Restocks the returned item back into warehouse inventory.
     * @param returnId the return ID
     * @param warehouseId target warehouse for restocking
     */
    void restockItem(String returnId, String warehouseId);

    /**
     * Gets the current status of a return.
     * @param returnId the return ID
     * @return status string (e.g., "INITIATED", "IN_TRANSIT", "INSPECTED", "RESTOCKED", "REFUNDED")
     */
    String getReturnStatus(String returnId);
}
