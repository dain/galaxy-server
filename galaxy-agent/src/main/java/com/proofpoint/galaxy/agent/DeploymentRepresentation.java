/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public class DeploymentRepresentation
{
    private final String slotName;
    private final String deploymentId;
    private final UUID nodeId;
    private final AssignmentRepresentation assignment;
    private final Map<String, Integer> resources;

    public static DeploymentRepresentation from(Deployment deployment)
    {
        return new DeploymentRepresentation(deployment.getDeploymentId(),
                deployment.getSlotName(),
                deployment.getNodeId(),
                AssignmentRepresentation.from(deployment.getAssignment()),
                deployment.getResources());
    }

    @JsonCreator
    public DeploymentRepresentation(
            @JsonProperty("deploymentId") String deploymentId,
            @JsonProperty("slotName") String slotName,
            @JsonProperty("nodeId") UUID nodeId,
            @JsonProperty("assignment") AssignmentRepresentation assignment,
            @JsonProperty("resources") Map<String, Integer> resources)
    {
        Preconditions.checkNotNull(deploymentId, "deploymentId is null");
        Preconditions.checkNotNull(slotName, "slotName is null");
        Preconditions.checkNotNull(nodeId, "nodeId is null");
        Preconditions.checkNotNull(assignment, "assignment is null");

        this.deploymentId = deploymentId;
        this.slotName = slotName;
        this.nodeId = nodeId;
        this.assignment = assignment;
        if (resources == null) {
            this.resources = ImmutableMap.of();
        } else {
            this.resources = ImmutableMap.copyOf(resources);
        }
    }

    @JsonProperty
    public String getSlotName()
    {
        return slotName;
    }

    @JsonProperty
    public String getDeploymentId()
    {
        return deploymentId;
    }

    @JsonProperty
    public UUID getNodeId()
    {
        return nodeId;
    }

    @JsonProperty
    public AssignmentRepresentation getAssignment()
    {
        return assignment;
    }

    @JsonProperty
    public Map<String, Integer> getResources()
    {
        return resources;
    }

    public Deployment toDeployment(File deploymentDir, File dataDir, String location)
    {
        return new Deployment(deploymentId, slotName, nodeId, location, deploymentDir, dataDir, assignment.toAssignment(), resources);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeploymentRepresentation that = (DeploymentRepresentation) o;

        if (!deploymentId.equals(that.deploymentId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return deploymentId.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Deployment");
        sb.append("{slotName='").append(slotName).append('\'');
        sb.append(", deploymentId='").append(deploymentId).append('\'');
        sb.append(", nodeId=").append(nodeId);
        sb.append(", assignment=").append(assignment);
        sb.append(", resources=").append(resources);
        sb.append('}');
        return sb.toString();
    }
}
