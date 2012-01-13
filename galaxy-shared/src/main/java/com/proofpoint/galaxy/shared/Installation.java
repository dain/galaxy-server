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
package com.proofpoint.galaxy.shared;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.Map;

@Immutable
public class Installation
{
    private final Assignment assignment;
    private final URI binaryFile;
    private final Map<String, URI> configFiles;
    private final Map<String, Integer> resources;

    public Installation(Assignment assignment, URI binaryFile, Map<String, URI> configFiles, Map<String, Integer> resources)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");
        Preconditions.checkNotNull(binaryFile, "binaryFile is null");
        Preconditions.checkNotNull(configFiles, "configFiles is null");
        Preconditions.checkNotNull(resources, "resources is null");

        this.assignment = assignment;
        this.binaryFile = binaryFile;
        this.configFiles = ImmutableMap.copyOf(configFiles);
        this.resources = ImmutableMap.copyOf(resources);
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    public URI getBinaryFile()
    {
        return binaryFile;
    }

    public Map<String, URI> getConfigFiles()
    {
        return configFiles;
    }

    public Map<String, Integer> getResources()
    {
        return resources;
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

        Installation that = (Installation) o;

        if (!assignment.equals(that.assignment)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return assignment.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Installation");
        sb.append("{assignment=").append(assignment);
        sb.append(", binaryFile=").append(binaryFile);
        sb.append(", configFiles=").append(configFiles);
        sb.append(", resources=").append(resources);
        sb.append('}');
        return sb.toString();
    }
}
