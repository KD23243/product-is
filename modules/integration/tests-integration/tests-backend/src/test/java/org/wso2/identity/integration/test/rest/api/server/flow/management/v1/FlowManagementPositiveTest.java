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

package org.wso2.identity.integration.test.rest.api.server.flow.management.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.FlowConfigRequest;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.FlowConfigResponse;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.FlowRequest;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.FlowResponse;
import org.wso2.identity.integration.test.restclients.FlowManagementClient;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/**
 * This class contains the test cases for Flow Management API.
 */
public class FlowManagementPositiveTest extends FlowManagementTestBase {

    private static String registrationFlowRequestJson;
    private static String passwordRecoveryFlowRequestJson;
    private static String invitedUserRegistrationFlowRequestJson;

    private final Map<String, String> flowJsonByType = new HashMap<>();
    private final Map<String, FlowRequest> parsedFlowByType = new HashMap<>();

    private FlowManagementClient flowManagementClient;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public FlowManagementPositiveTest(TestUserMode userMode) throws Exception {

        super.init(userMode);
        this.context = isServer;
        this.authenticatingUserName = context.getContextTenant().getTenantAdmin().getUserName();
        this.authenticatingCredential = context.getContextTenant().getTenantAdmin().getPassword();
        this.tenant = context.getContextTenant().getDomain();
    }

    @DataProvider(name = "restAPIUserConfigProvider")
    public static Object[][] restAPIUserConfigProvider() {

        return new Object[][]{
                {TestUserMode.SUPER_TENANT_ADMIN},
                {TestUserMode.TENANT_ADMIN}
        };
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws IOException {

        super.testInit(API_VERSION, swaggerDefinition, tenantInfo.getDomain());
        flowManagementClient = new FlowManagementClient(serverURL, tenantInfo);
        registrationFlowRequestJson = readResource(REGISTRATION_FLOW);
        passwordRecoveryFlowRequestJson = readResource(PASSWORD_RECOVERY_FLOW);
        invitedUserRegistrationFlowRequestJson = readResource(INVITED_USER_REGISTRATION_FLOW);

        flowJsonByType.put(REGISTRATION, registrationFlowRequestJson);
        flowJsonByType.put(PASSWORD_RECOVERY, passwordRecoveryFlowRequestJson);
        flowJsonByType.put(INVITED_USER_REGISTRATION, invitedUserRegistrationFlowRequestJson);

        for (Map.Entry<String, String> e : flowJsonByType.entrySet()) {
            parsedFlowByType.put(e.getKey(), parseFlow(e.getValue()));
        }
    }

    @AfterClass(alwaysRun = true)
    public void testCleanup() throws Exception {

        flowManagementClient.closeHttpClient();
        super.testConclude();
    }

    @DataProvider(name = "flowsToUpdate")
    public Object[][] flowsToUpdate() {
        return new Object[][]{
                {REGISTRATION},
                {PASSWORD_RECOVERY},
                {INVITED_USER_REGISTRATION}
        };
    }

    @Test(description = "Put flow definitions", dataProvider = "flowsToUpdate")
    public void testPutFlows(String flowType) throws Exception {

        flowManagementClient.putFlow(requireParsedFlow(flowType));
    }

    @Test(description = "Get flow equals expected", dataProvider = "flowsToUpdate",
            dependsOnMethods = "testPutFlows")
    public void testGetFlows(String flowType) throws Exception {

        assertFlowEquals(flowType, requireParsedFlow(flowType));
    }

    @Test
    public void testSetFlowConfigs() throws Exception {

        List<FlowConfigRequest> configsToSet = Arrays.asList(
                new FlowConfigRequest(REGISTRATION, true, true),
                new FlowConfigRequest(PASSWORD_RECOVERY, true, true),
                new FlowConfigRequest(INVITED_USER_REGISTRATION, true, true)
        );

        for (FlowConfigRequest config : configsToSet) {
            flowManagementClient.setFlowConfig(config);
        }

        List<FlowConfigResponse> appliedConfigs = flowManagementClient.getFlowConfigs();

        for (FlowConfigRequest expected : configsToSet) {
            Assert.assertTrue(
                    containsConfig(appliedConfigs, expected.getFlowType(),
                            resp -> Boolean.TRUE.equals(resp.getIsEnabled())
                                    && Boolean.TRUE.equals(resp.getIsAutoLoginEnabled())),
                    "Config not applied for: " + expected.getFlowType());
        }
    }

    @Test(description = "Test get flow configs", dependsOnMethods = "testSetFlowConfigs")
    public void testGetFlowConfigs() throws Exception {

        List<FlowConfigResponse> response = flowManagementClient.getFlowConfigs();
        Assert.assertNotNull(response, "FlowConfigResponse list is null");

        assertFlowEnabled(response, REGISTRATION);
        assertFlowEnabled(response, PASSWORD_RECOVERY);
        assertFlowEnabled(response, INVITED_USER_REGISTRATION);
    }

    private static FlowRequest parseFlow(String json) throws JsonProcessingException {

        return JSON.readValue(json, FlowRequest.class);
    }

    private FlowRequest requireParsedFlow(String flowType) {
        FlowRequest req = parsedFlowByType.get(flowType);
        Assert.assertNotNull(req, "No parsed flow found for type: " + flowType);
        return req;
    }

    private void assertFlowEquals(String flowType, FlowRequest expected) throws Exception {

        FlowResponse actual = flowManagementClient.getFlow(flowType);
        Assert.assertEquals(actual.getSteps(), expected.getSteps(), flowType + " flow mismatch");
    }

    private static FlowConfigResponse requireFlowConfig(List<FlowConfigResponse> list, String type) {

        return list.stream()
                .filter(f -> type.equals(f.getFlowType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + type + " flow"));
    }

    private static void assertFlowEnabled(List<FlowConfigResponse> list, String type) {

        FlowConfigResponse cfg = requireFlowConfig(list, type);
        Assert.assertEquals(cfg.getIsEnabled(), Boolean.TRUE, type + " flow is not enabled");
    }

    private static boolean containsConfig(List<FlowConfigResponse> list, String type,
                                          Predicate<FlowConfigResponse> predicate) {

        return list.stream().anyMatch(f -> type.equals(f.getFlowType()) && predicate.test(f));
    }
}
