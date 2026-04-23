package com.jackfruit.orderfulfillment.adapter;

import com.jackfruit.orderfulfillment.integration.ReturnService;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter bridging ReturnService interface to the DB team's facade.
 * Manages the full reverse logistics lifecycle: return initiation,
 * label generation, item inspection, and restocking.
 */
public class ReturnAdapter implements ReturnService {

    private final SupplyChainDatabaseFacade facade;

    // In-memory return tracking (in production, this would be persisted via the facade)
    private final Map<String, ReturnRecord> returnRecords = new ConcurrentHashMap<>();

    public ReturnAdapter(SupplyChainDatabaseFacade facade) {
        this.facade = facade;
    }

    @Override
    public String initiateReturn(String orderId, String reason) {
        String returnId = "RTN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ReturnRecord record = new ReturnRecord(
                returnId, orderId, reason,
                "INITIATED", null, null, null
        );
        returnRecords.put(returnId, record);

        System.out.println("[ReturnAdapter] Return initiated: " + returnId
                + " for order " + orderId + " — Reason: " + reason);
        return returnId;
    }

    @Override
    public String generateReturnLabel(String returnId) {
        ReturnRecord record = returnRecords.get(returnId);
        if (record == null) {
            throw new IllegalArgumentException("Return not found: " + returnId);
        }

        String labelTrackingNumber = "RTNLBL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        record.labelTrackingNumber = labelTrackingNumber;
        record.status = "LABEL_GENERATED";

        System.out.println("[ReturnAdapter] Return label generated: " + labelTrackingNumber
                + " for return " + returnId);
        return labelTrackingNumber;
    }

    @Override
    public boolean inspectReturnedItem(String returnId, String condition) {
        ReturnRecord record = returnRecords.get(returnId);
        if (record == null) {
            throw new IllegalArgumentException("Return not found: " + returnId);
        }

        record.inspectionCondition = condition;
        boolean passesInspection = "LIKE_NEW".equals(condition) || "GOOD".equals(condition);
        record.status = passesInspection ? "INSPECTION_PASSED" : "INSPECTION_FAILED";

        System.out.println("[ReturnAdapter] Inspection result for " + returnId
                + ": " + condition + " → " + (passesInspection ? "PASS (restockable)" : "FAIL (non-restockable)"));
        return passesInspection;
    }

    @Override
    public void restockItem(String returnId, String warehouseId) {
        ReturnRecord record = returnRecords.get(returnId);
        if (record == null) {
            throw new IllegalArgumentException("Return not found: " + returnId);
        }

        if (!"INSPECTION_PASSED".equals(record.status)) {
            System.out.println("[ReturnAdapter] Cannot restock " + returnId + " — inspection not passed");
            return;
        }

        record.restockedWarehouse = warehouseId;
        record.status = "RESTOCKED";

        System.out.println("[ReturnAdapter] Item restocked at warehouse " + warehouseId
                + " for return " + returnId);
    }

    @Override
    public String getReturnStatus(String returnId) {
        ReturnRecord record = returnRecords.get(returnId);
        if (record == null) {
            return "NOT_FOUND";
        }
        return record.status;
    }

    /**
     * Internal record to track return state throughout the reverse logistics pipeline.
     */
    private static class ReturnRecord {
        final String returnId;
        final String orderId;
        final String reason;
        String status;
        String labelTrackingNumber;
        String inspectionCondition;
        String restockedWarehouse;

        ReturnRecord(String returnId, String orderId, String reason,
                     String status, String labelTrackingNumber,
                     String inspectionCondition, String restockedWarehouse) {
            this.returnId = returnId;
            this.orderId = orderId;
            this.reason = reason;
            this.status = status;
            this.labelTrackingNumber = labelTrackingNumber;
            this.inspectionCondition = inspectionCondition;
            this.restockedWarehouse = restockedWarehouse;
        }
    }
}
