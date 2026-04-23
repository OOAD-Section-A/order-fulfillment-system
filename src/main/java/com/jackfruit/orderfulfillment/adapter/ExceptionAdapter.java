package com.jackfruit.orderfulfillment.adapter;

import com.jackfruit.orderfulfillment.integration.ExceptionService;
import com.jackfruit.orderfulfillment.service.OrderFulfillmentExceptionLogger;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.SubsystemException;
import com.scm.core.SCMException;
import com.scm.core.Severity;

import java.time.LocalDateTime;

/**
 * Adapter that bridges ExceptionService interface with the DB team's exception facade + SCM handler.
 * Dual-layer: SCM exception handling + database persistence.
 */
public class ExceptionAdapter implements ExceptionService {

    private final SupplyChainDatabaseFacade facade;

    public ExceptionAdapter(SupplyChainDatabaseFacade facade) {
        this.facade = facade;
    }

    @Override
    public void logException(String module, String message, String severity) {
        try {
            Severity scmSeverity = mapSeverity(severity);
            int exceptionId = mapExceptionId(module);
            String exceptionName = mapExceptionName(module);

            // Step 1: Create SCM exception via factory
            SCMException scmException = OrderFulfillmentExceptionLogger.buildScmException(
                    exceptionId, exceptionName, message, scmSeverity);

            // Step 2: Handle via SCM Exception Handler (platform-specific)
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac") || os.contains("darwin")) {
                System.err.println("SCM Exception [" + scmSeverity + "]: " + message);
            } else {
                com.scm.handler.SCMExceptionHandler.INSTANCE.handle(scmException);
            }

            // Step 3: Persist to database via exception facade
            SubsystemException subsystemException = new SubsystemException();
            subsystemException.setExceptionId(exceptionId);
            subsystemException.setExceptionName(exceptionName);
            subsystemException.setSubsystem("ORDER_FULFILLMENT");
            subsystemException.setErrorMessage(message);
            subsystemException.setSeverity(scmSeverity.name());
            subsystemException.setLoggedAt(LocalDateTime.now());
            facade.exceptions().logException(subsystemException);

            System.out.println("[ExceptionAdapter] Exception persisted [" + scmSeverity + "]: " + message);

        } catch (Exception e) {
            // Fallback: at least log to console if DB persistence fails
            System.err.println("[ExceptionAdapter] FALLBACK LOG [" + severity + "] " + module + ": " + message);
            System.err.println("  Persistence failed: " + e.getMessage());
        }
    }

    private Severity mapSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "MAJOR", "HIGH", "CRITICAL" -> Severity.MAJOR;
            case "WARNING", "MEDIUM" -> Severity.WARNING;
            default -> Severity.MINOR;
        };
    }

    private int mapExceptionId(String module) {
        return switch (module.toUpperCase()) {
            case "ORDER_FULFILLMENT", "ORDER_PROCESSING" -> 2;
            case "PACKING", "DISPATCH", "DELIVERY" -> 1;
            case "BATCH_PROCESSING" -> 3;
            case "INVENTORY" -> 4;
            case "WEBHOOK", "COMMISSION" -> 5;
            default -> 99;
        };
    }

    private String mapExceptionName(String module) {
        return switch (module.toUpperCase()) {
            case "ORDER_FULFILLMENT", "ORDER_PROCESSING" -> "ORDER_PROCESSING_FAILED";
            case "PACKING", "DISPATCH", "DELIVERY" -> "PACKING_DISPATCH_FAILED";
            case "BATCH_PROCESSING" -> "BATCH_PROCESSING_FAILED";
            case "INVENTORY" -> "INVENTORY_CHECK_FAILED";
            case "WEBHOOK", "COMMISSION" -> "COMMISSION_WEBHOOK_FAILED";
            default -> "UNKNOWN_EXCEPTION";
        };
    }
}