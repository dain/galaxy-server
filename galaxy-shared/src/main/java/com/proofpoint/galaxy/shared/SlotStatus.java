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

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

@Immutable
public class SlotStatus
{
    private final UUID id;
    private final String name;
    private final URI self;
    private final String location;
    private final Assignment assignment;
    private final SlotLifecycleState state;
    private final String version;
    private final String statusMessage;

    private final String installPath;

    private final Map<String, Integer> resources;

    public SlotStatus(UUID id, String name, URI self, String location, SlotLifecycleState state, Assignment assignment, String installPath, Map<String, Integer> resources)
    {
        Preconditions.checkNotNull(id, "id is null");
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(location, "location is null");
        Preconditions.checkNotNull(state, "state is null");
        if (state != TERMINATED && state != UNKNOWN) {
            Preconditions.checkNotNull(assignment, "assignment is null");
        }
        Preconditions.checkNotNull(resources, "resources is null");

        this.id = id;
        this.name = name;
        this.self = self;
        this.location = location;
        this.assignment = assignment;
        this.state = state;
        this.version = createVersion(id, state, assignment);
        this.installPath = installPath;
        this.statusMessage = null;
        this.resources = ImmutableMap.copyOf(resources);
    }

    public SlotStatus(SlotStatus status, SlotLifecycleState state, Assignment assignment)
    {
        Preconditions.checkNotNull(status, "status is null");
        Preconditions.checkNotNull(state, "state is null");
        Preconditions.checkNotNull(assignment, "assignment is null");

        this.id = status.id;
        this.name = status.name;
        this.self = status.self;
        this.location = status.location;
        this.assignment = assignment;
        this.state = state;
        this.version = createVersion(id, state, assignment);
        this.installPath = status.getInstallPath();
        this.statusMessage = null;
        this.resources = status.resources;
    }

    private SlotStatus(SlotStatus status, SlotLifecycleState state, String statusMessage)
    {
        Preconditions.checkNotNull(status, "status is null");
        Preconditions.checkNotNull(state, "state is null");

        this.id = status.id;
        this.name = status.name;
        this.self = status.self;
        this.location = status.location;
        if (state != TERMINATED) {
            this.assignment = status.assignment;
            this.installPath = status.installPath;
            this.resources = status.resources;
        }
        else {
            this.assignment = null;
            this.installPath = null;
            this.resources = ImmutableMap.of();
        }
        this.state = state;
        this.version = createVersion(id, state, assignment);
        this.statusMessage = statusMessage;
    }

    public UUID getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public URI getSelf()
    {
        return self;
    }

    public String getLocation()
    {
        return location;
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    public SlotLifecycleState getState()
    {
        return state;
    }

    public String getVersion()
    {
        return version;
    }

    public SlotStatus updateState(SlotLifecycleState state)
    {
        return updateState(state, null);
    }

    public SlotStatus updateState(SlotLifecycleState state, String statusMessage)
    {
        return new SlotStatus(this, state, statusMessage);
    }

    public SlotStatus clearStatusMessage()
    {
        return new SlotStatus(this, state, (String) null);
    }

    public String getStatusMessage()
    {
        return statusMessage;
    }

    public String getInstallPath()
    {
        return installPath;
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

        SlotStatus that = (SlotStatus) o;

        if (assignment != null ? !assignment.equals(that.assignment) : that.assignment != null) {
            return false;
        }
        if (!id.equals(that.id)) {
            return false;
        }
        if (installPath != null ? !installPath.equals(that.installPath) : that.installPath != null) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        if (!location.equals(that.location)) {
            return false;
        }
        if (!self.equals(that.self)) {
            return false;
        }
        if (state != that.state) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }
        if (!resources.equals(that.resources)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + self.hashCode();
        result = 31 * result + location.hashCode();
        result = 31 * result + (assignment != null ? assignment.hashCode() : 0);
        result = 31 * result + state.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + (installPath != null ? installPath.hashCode() : 0);
        result = 31 * result + resources.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "SlotStatus{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", self=" + self +
                ", location=" + location +
                ", assignment=" + assignment +
                ", state=" + state +
                ", version=" + version +
                ", installPath='" + installPath + '\'' +
                ", resources='" + resources + '\'' +
                '}';
    }

    public static Function<SlotStatus, UUID> uuidGetter()
    {
        return new Function<SlotStatus, UUID>()
        {
            public UUID apply(SlotStatus input)
            {
                return input.getId();
            }
        };
    }

    public static String createVersion(UUID id, SlotLifecycleState state, Assignment assignment)
    {
        try {
            String data = Joiner.on("||").useForNull("--NULL--").join(id, state, assignment);
            byte[] digest = ByteStreams.getDigest(ByteStreams.newInputStreamSupplier(data.getBytes(Charsets.UTF_8)), MessageDigest.getInstance("MD5"));
            return toHex(digest);
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static final char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String toHex(byte[] data) {
        char[] chars = new char[data.length * 2];

        int charIndex = 0;
        for (byte b : data) {
            chars[charIndex++] = HEX[(0xF0 & b) >>> 4];
            chars[charIndex++] = HEX[(0x0F & b)];
        }
        return new String(chars);
    }
}
