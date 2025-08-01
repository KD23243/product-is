/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.integration.test.webhooks;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.identity.integration.test.rest.api.server.webhook.management.v1.model.WebhookRequest;
import org.wso2.identity.integration.test.rest.api.server.webhook.management.v1.model.WebhookRequestEventProfile;
import org.wso2.identity.integration.test.rest.api.server.webhook.management.v1.model.WebhookResponse;
import org.wso2.identity.integration.test.restclients.WebhooksRestClient;
import org.wso2.identity.integration.test.webhooks.mockservice.WebhookMockService;
import org.wso2.identity.integration.test.webhooks.util.EventPayloadStack;
import org.wso2.identity.integration.test.webhooks.util.EventPayloadValidator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manager class for handling and verifying webhook events in tests.
 */
public class WebhookEventTestManager {

    private static final int START_PORT = 8580;
    private static final int PORT_LIMIT = 8590;
    private static final AtomicInteger currentPort = new AtomicInteger(START_PORT);
    private static final String SERVER_BASE_URL = "https://localhost:9853/";
    private static final String WSO2_EVENT_PROFILE_URI = "https://schemas.identity.wso2.org/events";

    private final String webhookEndpointPath;
    private final String eventProfile;
    private final List<String> channelsSubscribed;
    private final String testName;
    private final WebhooksRestClient webhooksRestClient;
    private final EventPayloadStack eventPayloadStack;
    private WebhookMockService mockService;
    private String webhookEndpoint;
    private final String webhookId;

    private static final Logger LOG = LoggerFactory.getLogger(WebhookEventTestManager.class);

    /**
     * Constructor to initialize the WebhookEventTestManager.
     *
     * @param webhookEndpointPath the path for the webhook endpoint.
     * @param eventProfile        the event profile to be used for the webhook. Typically, "WSO2" for WSO2 events.
     * @param channelsSubscribed  the list of channels to which the webhook is subscribed.
     * @param testName            the name of the test case for which the webhook is being set up.
     * @param automationContext   the automation context containing tenant information and other configurations.
     * @throws Exception if an error occurs during initialization, such as starting the mock server or creating the webhook.
     */
    public WebhookEventTestManager(String webhookEndpointPath, String eventProfile, List<String> channelsSubscribed,
                                   String testName, AutomationContext automationContext) throws Exception {

        this.webhookEndpointPath = webhookEndpointPath;
        this.eventProfile = eventProfile;
        this.channelsSubscribed = channelsSubscribed;
        this.testName = testName;
        this.eventPayloadStack = new EventPayloadStack();

        startMockServer();
        this.webhooksRestClient = new WebhooksRestClient(SERVER_BASE_URL, automationContext.getContextTenant());
        this.webhookId = createWebhook();
    }

    /**
     * Teardown method to clean up resources after tests.
     *
     * @throws Exception if an error occurs during teardown.
     */
    public void teardown() throws Exception {

        stopMockServer();
        deleteWebhook();
        eventPayloadStack.clearStack();
    }

    /**
     * Stacks an expected payload for a specific event URI.
     *
     * @param eventUri        the URI of the event for which the payload is expected.
     * @param expectedPayload the expected JSON payload for the event.
     */
    public void stackExpectedPayload(String eventUri, JSONObject expectedPayload) {

        eventPayloadStack.addExpectedPayload(eventUri, expectedPayload);
    }

    /**
     * Validates the event payloads received by the webhook against the expected payloads stacked earlier.
     *
     * @throws Exception if an error occurs during validation, such as if no payloads are received or if validation fails.
     */
    public void validateEventPayloads() throws Exception {

        while (!eventPayloadStack.isEmpty()) {
            Map.Entry<String, JSONObject> expectedEntry = eventPayloadStack.popExpectedPayload();
            validatePayloadForEventUri(expectedEntry.getKey(), expectedEntry.getValue());
        }
    }

    private void validatePayloadForEventUri(String eventUri, JSONObject expectedPayload) throws Exception {

        List<JSONObject> receivedPayloads = extractReceivedPayloads();

        if (receivedPayloads.isEmpty()) {
            throw new AssertionError("No received payloads found for event URI: " + eventUri);
        }

        boolean isMatched = matchAndValidatePayload(eventUri, expectedPayload, receivedPayloads);

        if (!isMatched) {
            throw new AssertionError("No matching payload found for event URI: " + eventUri);
        }
    }

    private List<JSONObject> extractReceivedPayloads() {

        return mockService.getOrderedRequests()
                .stream()
                .map(request -> {
                    try {
                        return new JSONObject(request.getBodyAsString());
                    } catch (JSONException e) {
                        LOG.error("Invalid JSON payload: {}", e.getMessage());
                        return null;
                    }
                })
                .collect(Collectors.toList());
    }

    private boolean matchAndValidatePayload(String eventUri, JSONObject expectedPayload,
                                            List<JSONObject> receivedPayloads) throws Exception {

        for (int i = 0; i < receivedPayloads.size(); i++) {
            JSONObject receivedPayload = receivedPayloads.get(i);

            if (receivedPayload != null && receivedPayload.has("events") &&
                    receivedPayload.getJSONObject("events").has(eventUri)) {

                try {
                    EventPayloadValidator.validateCommonEventPayloadFields(eventUri, receivedPayload);
                    JSONObject eventField = EventPayloadValidator.extractEventPayload(eventUri, receivedPayload);

                    LOG.info("Validating received event payload: {} against expected payload: {} for event URI: {}.",
                            eventField, expectedPayload, eventUri);

                    EventPayloadValidator.validateEventField(eventField, expectedPayload);
                    receivedPayloads.remove(i);
                    return true;

                } catch (IllegalArgumentException e) {
                    throw new AssertionError("Payload validation failed for event URI: " + eventUri, e);
                }
            }
        }
        return false;
    }

    private void startMockServer() throws Exception {

        int port = getNextAvailablePort();
        mockService = new WebhookMockService();

        try {
            mockService.startServer(port);
            webhookEndpoint = mockService.registerWebhookEndpoint(webhookEndpointPath);

            LOG.info("Webhook mock server started on port: {}", port);
            LOG.info("Webhook endpoint registered at: {}", webhookEndpoint);

            Thread.sleep(500); // Ensure server is fully initialized
        } catch (Exception e) {
            LOG.error("Failed to start the mock server on port: {}", port, e);
            throw e;
        }
    }

    private void stopMockServer() {

        if (mockService != null) {
            mockService.stopServer();
        }
    }

    private int getNextAvailablePort() {

        int port = currentPort.getAndIncrement();
        if (port > PORT_LIMIT) {
            currentPort.set(START_PORT);
            port = currentPort.getAndIncrement();
        }
        return port;
    }

    private String createWebhook() throws Exception {

        WebhookRequest webhookRequest = new WebhookRequest();
        webhookRequest.name(testName)
                .endpoint(webhookEndpoint)
                .secret("secretKey")
                .status(WebhookRequest.StatusEnum.ACTIVE)
                .eventProfile(new WebhookRequestEventProfile().name(eventProfile)
                        .uri(getEventProfileURI()));

        for (String channel : channelsSubscribed) {
            webhookRequest.addChannelsSubscribedItem(channel);
        }

        WebhookResponse response = webhooksRestClient.createWebhook(webhookRequest);
        return response.getId();
    }

    private void deleteWebhook() throws Exception {

        if (webhookId != null) {
            webhooksRestClient.deleteWebhook(webhookId);
        }
    }

    private String getEventProfileURI() {

        return "WSO2".equals(eventProfile) ? WSO2_EVENT_PROFILE_URI : null;
    }

}
