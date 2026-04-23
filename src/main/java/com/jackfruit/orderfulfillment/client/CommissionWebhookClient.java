package com.jackfruit.orderfulfillment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jackfruit.orderfulfillment.model.CommissionEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class CommissionWebhookClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;

    public CommissionWebhookClient(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))  // Don't hang on unreachable URLs
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Sends a commission event to the Commission Tracking subsystem via HTTP webhook.
     * Non-blocking: failures are logged to console, never crash the app.
     */
    public void sendCommissionEvent(CommissionEvent event, Object ignored) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("[Commission] Webhook sent successfully for: " + event.fulfillmentId());
            } else {
                System.out.println("[Commission] Webhook response " + response.statusCode() + " for: " + event.fulfillmentId());
            }

        } catch (IOException | InterruptedException e) {
            // Commission subsystem not connected — this is expected in development
            System.out.println("[Commission] Webhook skipped (service unavailable): " + event.fulfillmentId());
        }
    }
}