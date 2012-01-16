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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.testing.EquivalenceTester;
import org.testng.annotations.Test;

import java.io.File;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestDeployment
{
    private static final ImmutableMap<String, Integer> RESOURCES = ImmutableMap.of("memory", 512);

    @Test
    public void testConstructor()
    {
        Deployment deployment = new Deployment("one", "slot", UUID.randomUUID(), "location", new File("one"), new File("data"), APPLE_ASSIGNMENT, RESOURCES);

        assertEquals(deployment.getDeploymentId(), "one");
        assertEquals(deployment.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(deployment.getDeploymentDir(), new File("one"));
        assertEquals(deployment.getLocation(), "location");
        assertEquals(deployment.getResources(), RESOURCES);
    }

    @Test
    public void testNullConstructorArgs()
    {
        try {
            new Deployment("one", null, UUID.randomUUID(), "location", new File("one"), new File("data"), APPLE_ASSIGNMENT, RESOURCES);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
        try {
            new Deployment(null, "slot", UUID.randomUUID(), "location", new File("one"), new File("data"), APPLE_ASSIGNMENT, RESOURCES);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
        try {
            new Deployment("one", "slot", UUID.randomUUID(), "location", null, new File("data"), APPLE_ASSIGNMENT, RESOURCES);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
        try {
            new Deployment("one", "slot", UUID.randomUUID(), "location", new File("one"), null, APPLE_ASSIGNMENT, RESOURCES);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
        try {
            new Deployment("one", "slot", UUID.randomUUID(), "location", new File("one"), new File("data"), null, RESOURCES);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
        try {
            new Deployment("one", "slot", UUID.randomUUID(), "location", new File("one"), new File("data"), APPLE_ASSIGNMENT, null);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
    }

    @Test
    public void testEquivalence()
    {
        // identity is only based on deploymentId
        EquivalenceTester.check(
                asList(
                        new Deployment("one", "slot", UUID.randomUUID(), "location", new File("one"), new File("data"), APPLE_ASSIGNMENT, RESOURCES),
                        new Deployment("one", "slot", UUID.randomUUID(), "locationB", new File("one"), new File("data"), APPLE_ASSIGNMENT, RESOURCES),
                        new Deployment("one", "slot", UUID.randomUUID(), "location", new File("other"), new File("data"), APPLE_ASSIGNMENT, RESOURCES),
                        new Deployment("one", "slot", UUID.randomUUID(), "location", new File("one"), new File("theta"), APPLE_ASSIGNMENT, RESOURCES),
                        new Deployment("one", "slot", UUID.randomUUID(), "location", new File("one"), new File("data"), BANANA_ASSIGNMENT, RESOURCES),
                        new Deployment("one", "slot", UUID.randomUUID(), "location", new File("one"), new File("data"), APPLE_ASSIGNMENT, ImmutableMap.<String, Integer>of("cpu", 1))
                ),
                asList(
                        new Deployment("two", "slot", UUID.randomUUID(), "location", new File("one"), new File("data"), APPLE_ASSIGNMENT, RESOURCES),
                        new Deployment("two", "slot", UUID.randomUUID(), "locationB", new File("one"), new File("data"), APPLE_ASSIGNMENT, RESOURCES),
                        new Deployment("two", "slot", UUID.randomUUID(), "location", new File("other"), new File("data"), APPLE_ASSIGNMENT, RESOURCES),
                        new Deployment("two", "slot", UUID.randomUUID(), "location", new File("one"), new File("theta"), APPLE_ASSIGNMENT, RESOURCES),
                        new Deployment("two", "slot", UUID.randomUUID(), "location", new File("one"),new File("data"),  BANANA_ASSIGNMENT, RESOURCES),
                        new Deployment("two", "slot", UUID.randomUUID(), "location", new File("one"), new File("data"), APPLE_ASSIGNMENT, ImmutableMap.<String, Integer>of("cpu", 1))
                )
        );
    }
}
