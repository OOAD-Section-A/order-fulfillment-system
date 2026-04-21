package com.jackfruit.orderfulfillment.service;

import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.SubsystemException;
import com.scm.core.SCMException;
import com.scm.core.Severity;
import com.scm.factory.SCMExceptionFactory;
import com.scm.handler.SCMExceptionHandler;

import java.time.LocalDateTime;

public final class OrderFulfillmentExceptionLogger {

    private OrderFulfillmentExceptionLogger() {
        // Utility class
    }

    public static SCMException buildScmException(int exceptionId,
                                                  String exceptionName,
                                                  String errorMessage,
                                                  Severity severity) {
        return SCMExceptionFactory.create(exceptionId, exceptionName, errorMessage, "ORDER_FULFILLMENT", severity);
    }

    public static SubsystemException convertToSubsystemException(SCMException scmException) {
        SubsystemException subsystemException = new SubsystemException();
        subsystemException.setExceptionId(scmException.getExceptionId());
        subsystemException.setExceptionName(scmException.getExceptionName());
        subsystemException.setSubsystem(scmException.getSubsystem());
        subsystemException.setErrorMessage(scmException.getErrorMessage());
        subsystemException.setSeverity(scmException.getSeverity().name());
        subsystemException.setLoggedAt(LocalDateTime.now());
        return subsystemException;
    }

    public static void logException(SupplyChainDatabaseFacade facade,
                                    int exceptionId,
                                    String exceptionName,
                                    String errorMessage,
                                    Severity severity) {
        SCMException scmException = buildScmException(exceptionId, exceptionName, errorMessage, severity);

        // Handle platform-specific exception logging
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            // On macOS, skip Windows Event Viewer logging and just log to console
            System.err.println("SCM Exception [" + severity + "]: " + errorMessage);
        } else {
            // On Windows, use the Event Viewer
            SCMExceptionHandler.INSTANCE.handle(scmException);
        }

        // Always log to database
        facade.exceptions().logException(convertToSubsystemException(scmException));
    }
}
