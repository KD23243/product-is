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

package org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Objects;

@ApiModel(description = "Request payload for updating a flow config")
public class FlowConfigRequest {

    private String flowType;
    private Boolean isEnabled;
    private Boolean isAutoLoginEnabled;

    public FlowConfigRequest(String flowType, Boolean isEnabled, Boolean isAutoLoginEnabled) {
        this.flowType = flowType;
        this.isEnabled = isEnabled;
        this.isAutoLoginEnabled = isAutoLoginEnabled;
    }

    /**
     * Type of the flow being updated.
     **/
    public FlowConfigRequest flowType(String flowType) {
        this.flowType = flowType;
        return this;
    }

    @ApiModelProperty(example = "REGISTRATION", required = true, value = "Type of the flow being updated")
    @JsonProperty("flowType")
    @Valid
    @NotNull(message = "Property flowType cannot be null.")
    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    /**
     * Whether the flow is enabled.
     **/
    public FlowConfigRequest isEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
        return this;
    }

    @ApiModelProperty(example = "true", required = true, value = "Whether the flow is enabled")
    @JsonProperty("isEnabled")
    @NotNull(message = "Property isEnabled cannot be null.")
    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    /**
     * Whether auto-login is enabled after registration.
     **/
    public FlowConfigRequest isAutoLoginEnabled(Boolean isAutoLoginEnabled) {
        this.isAutoLoginEnabled = isAutoLoginEnabled;
        return this;
    }

    @ApiModelProperty(example = "false", required = true, value = "Whether auto login is enabled after registration")
    @JsonProperty("isAutoLoginEnabled")
    @NotNull(message = "Property isAutoLoginEnabled cannot be null.")
    public Boolean getIsAutoLoginEnabled() {
        return isAutoLoginEnabled;
    }

    public void setIsAutoLoginEnabled(Boolean isAutoLoginEnabled) {
        this.isAutoLoginEnabled = isAutoLoginEnabled;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }
        if (!(o instanceof FlowConfigRequest)) {
            return false;
        }
        FlowConfigRequest that = (FlowConfigRequest) o;
        return Objects.equals(flowType, that.flowType) &&
                Objects.equals(isEnabled, that.isEnabled) &&
                Objects.equals(isAutoLoginEnabled, that.isAutoLoginEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowType, isEnabled, isAutoLoginEnabled);
    }

    @Override
    public String toString() {
        return "FlowConfigPatchRequest{" +
                "flowType='" + flowType + '\'' +
                ", isEnabled=" + isEnabled +
                ", isAutoLoginEnabled=" + isAutoLoginEnabled +
                '}';
    }
}
