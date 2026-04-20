package com.jackfruit.orderfulfillment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jackfruit.orderfulfillment.model.CommissionEvent;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.CommissionModels;
import com.jackfruit.orderfulfillment.service.OrderFulfillmentExceptionLogger;
import com.scm.core.Severity;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

public class CommissionWebhookClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;

    public CommissionWebhookClient(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void sendCommissionEvent(CommissionEvent event, SupplyChainDatabaseFacade facade) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Success - also persist to database
                persistCommissionSale(event, facade);
            } else {
                // Log failure
                logWebhookFailure(event, response, facade);
            }

        } catch (IOException | InterruptedException e) {
            logWebhookFailure(event, e, facade);
        }
    }

    private void persistCommissionSale(CommissionEvent event, SupplyChainDatabaseFacade facade) {
        try {
            // TODO: Implement commission sale persistence when facade supports it
            // For now, just log the event
            System.out.println("Commission sale would be persisted: " + event);
        } catch (Exception e) {
            // Log database persistence failure
            OrderFulfillmentExceptionLogger.logException(
                    facade, 4, "COMMISSION_PERSISTENCE_FAILED",
                    "Failed to persist commission sale: " + e.getMessage(), Severity.MINOR
            );
        }
    }

    private void logWebhookFailure(CommissionEvent event, HttpResponse<String> response, SupplyChainDatabaseFacade facade) {
        String errorMessage = String.format(
                "Commission webhook failed for fulfillment %s: HTTP %d - %s",
                event.fulfillmentId(), response.statusCode(), response.body()
        );
        OrderFulfillmentExceptionLogger.logException(
                facade, 5, "COMMISSION_WEBHOOK_FAILED", errorMessage, Severity.MINOR
        );
    }

    private void logWebhookFailure(CommissionEvent event, Exception e, SupplyChainDatabaseFacade facade) {
        String errorMessage = String.format(
                "Commission webhook failed for fulfillment %s: %s",
                event.fulfillmentId(), e.getMessage()
        );
        OrderFulfillmentExceptionLogger.logException(
                facade, 5, "COMMISSION_WEBHOOK_FAILED", errorMessage, Severity.MINOR
        );
    }
}