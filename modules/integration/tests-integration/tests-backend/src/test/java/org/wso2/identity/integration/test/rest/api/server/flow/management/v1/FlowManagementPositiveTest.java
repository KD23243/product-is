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

import com.fasterxml.jackson.core.JsonFactory;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class contains the test cases for Flow Management API.
 */
public class FlowManagementPositiveTest extends FlowManagementTestBase {

    private static String registrationFlowRequestJson;
    private FlowManagementClient flowManagementClient;

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

    private static FlowRequest getFlowRequest(ObjectMapper jsonReader)
            throws JsonProcessingException {

        return jsonReader.readValue(registrationFlowRequestJson, FlowRequest.class);
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws IOException {

        super.testInit(API_VERSION, swaggerDefinition, tenantInfo.getDomain());
        flowManagementClient = new FlowManagementClient(serverURL, tenantInfo);
        registrationFlowRequestJson = readResource(REGISTRATION_FLOW);
    }

    @AfterClass(alwaysRun = true)
    public void testCleanup() throws Exception {

        flowManagementClient.closeHttpClient();
        super.testConclude();
    }

    @Test(description = "Test update registration flow")
    public void testUpdateRegistrationFlow() throws Exception {

        ObjectMapper jsonReader = new ObjectMapper(new JsonFactory());
        FlowRequest registrationFlowRequest = getFlowRequest(jsonReader);
        flowManagementClient.putFlow(registrationFlowRequest);
    }

    @Test(description = "Test get registration flow", dependsOnMethods = "testUpdateRegistrationFlow")
    public void testGetRegistrationFlow() throws Exception {

        ObjectMapper jsonReader = new ObjectMapper(new JsonFactory());
        FlowRequest expectedRegistrationFlowRequest = getFlowRequest(jsonReader);
        FlowResponse registrationFlowResponse = flowManagementClient.getFlow("REGISTRATION");
        assert registrationFlowResponse.getSteps().equals(expectedRegistrationFlowRequest.getSteps())
                : "Registration flow mismatch";
    }

    @Test
    public void testSetFlowConfigs() throws Exception {

        // Define flows to set
        List<FlowConfigRequest> configsToSet = Arrays.asList(
                new FlowConfigRequest("REGISTRATION", true, true),
                new FlowConfigRequest("PASSWORD_RECOVERY", true, true),
                new FlowConfigRequest("INVITED_USER_REGISTRATION", true, true)
        );

        // Apply configs
        for (FlowConfigRequest config : configsToSet) {
            flowManagementClient.setFlowConfig(config);
        }

        // Fetch back
        List<FlowConfigResponse> appliedConfigs = flowManagementClient.getFlowConfigs();

        // Verify each flow is enabled with auto login enabled
        for (FlowConfigRequest expected : configsToSet) {
            boolean match = appliedConfigs.stream().anyMatch(resp ->
                    expected.getFlowType().equals(resp.getFlowType()) &&
                            Boolean.TRUE.equals(resp.getIsEnabled()) &&
                            Boolean.TRUE.equals(resp.getIsAutoLoginEnabled())
            );
            Assert.assertTrue(match, "Config not applied for: " + expected.getFlowType());
        }
    }

    @Test(description = "Test get flow configs", dependsOnMethods = "testSetFlowConfigs")
    public void testGetFlowConfigs() throws Exception {

        // Call the API — should return a List of FlowConfigResponse
        List<FlowConfigResponse> response = flowManagementClient.getFlowConfigs();

        Assert.assertNotNull(response, "FlowConfigResponse list is null");

        // Validate that expected flows exist
        FlowConfigResponse registrationFlow = response.stream()
                .filter(f -> "REGISTRATION".equals(f.getFlowType()))
                .findFirst().orElse(null);

        FlowConfigResponse passwordRecoveryFlow = response.stream()
                .filter(f -> "PASSWORD_RECOVERY".equals(f.getFlowType()))
                .findFirst().orElse(null);

        FlowConfigResponse invitedUserFlow = response.stream()
                .filter(f -> "INVITED_USER_REGISTRATION".equals(f.getFlowType()))
                .findFirst().orElse(null);

        Assert.assertNotNull(registrationFlow, "Missing REGISTRATION flow");
        Assert.assertNotNull(passwordRecoveryFlow, "Missing PASSWORD_RECOVERY flow");
        Assert.assertNotNull(invitedUserFlow, "Missing INVITED_USER_REGISTRATION flow");

        Assert.assertTrue(registrationFlow.getIsEnabled(), "REGISTRATION flow is not enabled");
        Assert.assertTrue(passwordRecoveryFlow.getIsEnabled(), "PASSWORD_RECOVERY flow is not enabled");
        Assert.assertTrue(invitedUserFlow.getIsEnabled(), "INVITED_USER_REGISTRATION flow is not enabled");
    }
}
