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
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;

public class TestAgentStatusRepresentation
{
    private final JsonCodec<AgentStatusRepresentation> codec = jsonCodec(AgentStatusRepresentation.class);

    private final AgentStatusRepresentation expected = new AgentStatusRepresentation(
            "44444444-4444-4444-4444-444444444444",
            ONLINE,
            URI.create("fake://agent"),
            "unknown/location",
            "instance.type",
            ImmutableList.of(
                    new SlotStatusRepresentation(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                            null,
                            "slot1",
                            URI.create("fake://apple"),
                            "location/apple",
                            APPLE_ASSIGNMENT.getBinary().toString(),
                            APPLE_ASSIGNMENT.getConfig().toString(),
                            STOPPED.toString(),
                            "abc",
                            null,
                            "/apple",
                            null,
                            null,
                            null),
                    new SlotStatusRepresentation(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                            null,
                            "slot2",
                            URI.create("fake://banana"),
                            "location/banana",
                            BANANA_ASSIGNMENT.getBinary().toString(),
                            BANANA_ASSIGNMENT.getConfig().toString(),
                            STOPPED.toString(),
                            "abc",
                            null,
                            "/banana",
                            null,
                            null,
                            null))
    );

    @Test
    public void testJsonRoundTrip()
    {
        String json = codec.toJson(expected);
        AgentStatusRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        String json = Resources.toString(Resources.getResource("agent-status.json"), Charsets.UTF_8);
        AgentStatusRepresentation actual = codec.fromJson(json);

        assertEquals(actual, expected);
    }
}
