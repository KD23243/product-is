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

package org.wso2.identity.integration.test.rest.api.server.registration.execution.v1;

import org.json.simple.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.identity.integration.test.rest.api.server.registration.execution.v1.model.FlowExecutionRequest;
import org.wso2.identity.integration.test.rest.api.server.registration.execution.v1.model.FlowExecutionResponse;
import org.wso2.identity.integration.test.rest.api.user.common.model.PatchOperationRequestObject;
import org.wso2.identity.integration.test.rest.api.user.common.model.UserItemAddGroupobj;
import org.wso2.identity.integration.test.restclients.AuthenticatorRestClient;
import org.wso2.identity.integration.test.restclients.IdentityGovernanceRestClient;
import org.wso2.identity.integration.test.restclients.RegistrationExecutionClient;
import org.wso2.identity.integration.test.restclients.RegistrationManagementClient;
import org.wso2.identity.integration.test.restclients.SCIM2RestClient;
import org.wso2.identity.integration.test.utils.UserUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test class for Registration Execution API.
 */
public class RegistrationExecutionPositiveTest extends RegistrationExecutionTestBase {

    public static final String USER = "RegExecPosTestUser";
    private static final String USER_SYSTEM_SCHEMA_ATTRIBUTE ="urn:scim:wso2:schema";
    private static final String ACCOUNT_LOCKED_ATTRIBUTE ="accountLocked";
    private RegistrationExecutionClient registrationExecutionClient;
    private RegistrationManagementClient registrationManagementClient;
    private IdentityGovernanceRestClient identityGovernanceRestClient;
    private static String flowId;
    private AuthenticatorRestClient authenticatorRestClient;
    private SCIM2RestClient scim2RestClient;

    @DataProvider(name = "restAPIUserConfigProvider")
    public static Object[][] restAPIUserConfigProvider() {

        return new Object[][]{
                {TestUserMode.SUPER_TENANT_ADMIN},
                {TestUserMode.TENANT_ADMIN}
        };
    }

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public RegistrationExecutionPositiveTest(TestUserMode userMode) throws Exception {

        super.init(userMode);
        this.context = isServer;
        this.authenticatingUserName = context.getContextTenant().getTenantAdmin().getUserName();
        this.authenticatingCredential = context.getContextTenant().getTenantAdmin().getPassword();
        this.tenant = context.getContextTenant().getDomain();
    }

    @BeforeClass(alwaysRun = true)
    public void setupClass() throws Exception {

        super.testInit(API_VERSION, swaggerDefinition, tenantInfo.getDomain());
        registrationExecutionClient = new RegistrationExecutionClient(serverURL, tenantInfo);
        registrationManagementClient = new RegistrationManagementClient(serverURL, tenantInfo);
        identityGovernanceRestClient = new IdentityGovernanceRestClient(serverURL, tenantInfo);
        authenticatorRestClient = new AuthenticatorRestClient(serverURL);
        scim2RestClient = new SCIM2RestClient(serverURL, tenantInfo);
        enableNewRegistrationFlow(identityGovernanceRestClient);
        addRegistrationFlow(registrationManagementClient);
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() throws Exception {

        disableNewRegistrationFlow(identityGovernanceRestClient);
        identityGovernanceRestClient.closeHttpClient();
        registrationExecutionClient.closeHttpClient();
        registrationManagementClient.closeHttpClient();
    }

    @Test
    public void initiateRegistrationFlow() throws Exception {

        Object responseObj = registrationExecutionClient.initiateRegistrationExecution();
        Assert.assertTrue(responseObj instanceof FlowExecutionResponse);
        FlowExecutionResponse response = (FlowExecutionResponse) responseObj;
        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getFlowId());
        flowId = response.getFlowId();
        Assert.assertEquals(response.getFlowStatus(), STATUS_INCOMPLETE);
        Assert.assertEquals(response.getType().toString(), TYPE_VIEW);
        Assert.assertNotNull(response.getData());
        Assert.assertNotNull(response.getData().getComponents());
        Assert.assertEquals(response.getData().getComponents().size(), 2);
    }

    @Test(dependsOnMethods = "initiateRegistrationFlow")
    public void submitRegistrationFlow() throws Exception {

        Object responseObj = registrationExecutionClient
                .submitRegistration(getRegistrationExecutionRequest());
        Assert.assertTrue(responseObj instanceof FlowExecutionResponse);
        FlowExecutionResponse response = (FlowExecutionResponse) responseObj;
        Assert.assertNotNull(response);
        Assert.assertEquals(response.getFlowStatus(), STATUS_COMPLETE);
        Assert.assertEquals(response.getType().toString(), TYPE_REDIRECTION);
        Assert.assertNotNull(response.getData());
    }

    @Test(dependsOnMethods = "submitRegistrationFlow")
    public void verifyUserLogin() throws Exception {

        String userId = UserUtil.getUserId(USER, context.getContextTenant());
        unlockUser(userId);
        JSONObject authenticationResponse =  authenticatorRestClient.login(USER, "Wso2@test");
        Assert.assertNotNull(authenticationResponse.get("token"), "Authentication failed for user: " + USER);
    }

    private static FlowExecutionRequest getRegistrationExecutionRequest() {

        FlowExecutionRequest registrationExecutionRequest = new FlowExecutionRequest();
        registrationExecutionRequest.setFlowId(flowId);
        registrationExecutionRequest.setActionId("button_5zqc");
        Map<String, String> inputs = new HashMap<>();
        inputs.put("http://wso2.org/claims/username", USER);
        inputs.put("password", "Wso2@test");
        inputs.put("http://wso2.org/claims/emailaddress", "test@wso2.com");
        inputs.put("http://wso2.org/claims/givenname", "RegExecPosJohn");
        inputs.put("http://wso2.org/claims/lastname", "RegExecPosDoe");
        registrationExecutionRequest.setInputs(inputs);
        return registrationExecutionRequest;
    }

    private void unlockUser(String loginUserId) throws IOException {

        UserItemAddGroupobj userUnlockPatchOp = new UserItemAddGroupobj().op(UserItemAddGroupobj.OpEnum.REPLACE);
        userUnlockPatchOp.setPath(USER_SYSTEM_SCHEMA_ATTRIBUTE + ":" + ACCOUNT_LOCKED_ATTRIBUTE);
        userUnlockPatchOp.setValue(false);
        scim2RestClient.updateUser(new PatchOperationRequestObject().addOperations(userUnlockPatchOp), loginUserId);
    }
}
