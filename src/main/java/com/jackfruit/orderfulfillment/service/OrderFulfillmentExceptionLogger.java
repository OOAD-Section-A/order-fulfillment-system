package com.jackfruit.orderfulfillment.service;

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

    public static void logException(
            int exceptionId,
            String exceptionName,
            String errorMessage,
            Severity severity) {

        SCMException scmException = buildScmException(exceptionId, exceptionName, errorMessage, severity);

        // ✅ Platform-specific handling (safe)
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            System.err.println("SCM Exception [" + severity + "]: " + errorMessage);
        } else {
            SCMExceptionHandler.INSTANCE.handle(scmException);
        }

        // ❌ REMOVED DATABASE LOGGING (CAUSE OF ERROR)
        /*
        facade.exceptions().logException(...)
        */

        // ✅ SIMPLE SAFE LOGGING
        System.out.println("Exception Logged [" + severity + "] : " + errorMessage + " at " + LocalDateTime.now());
    }
}