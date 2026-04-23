package com.jackfruit.orderfulfillment.integration;

public interface ExceptionService {

    void logException(String module, String message, String severity);
}
