package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;

import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_BINARY_REPO;
import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_CONFIG_REPO;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.*;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestCoordinatorSlotResource
{
    private CoordinatorSlotResource resource;
    private Coordinator coordinator;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        BinaryUrlResolver urlResolver = new BinaryUrlResolver(MOCK_BINARY_REPO, new HttpServerInfo(new HttpServerConfig(), new NodeInfo("testing")));

        coordinator = new Coordinator(new MockRemoteAgentFactory(),
                urlResolver,
                MOCK_CONFIG_REPO,
                new LocalConfigRepository(new CoordinatorConfig(), null));
        resource = new CoordinatorSlotResource(coordinator,
                MOCK_BINARY_REPO,
                MOCK_CONFIG_REPO,
                new LocalConfigRepository(new CoordinatorConfig(), null)
        );
    }

    @Test
    public void testGetAllSlots()
    {
        SlotStatus slot1 = new SlotStatus(UUID.randomUUID(), "slot1", URI.create("fake://localhost/v1/agent/slot/slot1"), STOPPED, APPLE_ASSIGNMENT);
        SlotStatus slot2 = new SlotStatus(UUID.randomUUID(), "slot2", URI.create("fake://localhost/v1/agent/slot/slot2"), STOPPED, APPLE_ASSIGNMENT);
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID(), ONLINE, URI.create("fake://foo/"), ImmutableList.of(slot1, slot2));
        coordinator.updateAgentStatus(agentStatus);

        int prefixSize = max(CoordinatorSlotResource.MIN_PREFIX_SIZE, Strings.shortestUniquePrefix(asList(slot1.getId().toString(), slot2.getId().toString())));

        URI requestUri = URI.create("http://localhost/v1/slot");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of(SlotStatusRepresentation.from(slot1, prefixSize), SlotStatusRepresentation.from(slot2, prefixSize)));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAllSlotsWithFilter()
    {
        SlotStatus slot1 = new SlotStatus(UUID.randomUUID(), "slot1", URI.create("fake://foo/v1/agent/slot/slot1"), STOPPED, APPLE_ASSIGNMENT);
        SlotStatus slot2 = new SlotStatus(UUID.randomUUID(), "slot2", URI.create("fake://bar/v1/agent/slot/slot2"), STOPPED, APPLE_ASSIGNMENT);
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID(), ONLINE, URI.create("fake://foo/"), ImmutableList.of(slot1, slot2));
        coordinator.updateAgentStatus(agentStatus);

        int prefixSize = max(CoordinatorSlotResource.MIN_PREFIX_SIZE, Strings.shortestUniquePrefix(asList(slot1.getId().toString(), slot2.getId().toString())));

        URI requestUri = URI.create("http://localhost/v1/slot?host=foo");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of(SlotStatusRepresentation.from(slot1, prefixSize)));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAllSlotEmpty()
    {
        URI requestUri = URI.create("http://localhost/v1/slot?state=unknown");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }


    @Test
    public void testInstallOne()
    {
        testInstall(1, 1, APPLE_ASSIGNMENT);
    }

    @Test
    public void testInstallLimit()
    {
        testInstall(10, 3, APPLE_ASSIGNMENT);
    }

    @Test
    public void testInstallNotEnoughAgents()
    {
        testInstall(3, 10, APPLE_ASSIGNMENT);
    }

    public void testInstall(int numberOfAgents, int limit, Assignment assignment)
    {
        for (int i = 0; i < numberOfAgents; i++) {
            coordinator.updateAgentStatus(new AgentStatus(UUID.randomUUID(),
                    ONLINE,
                    URI.create("fake://appleServer1/"),
                    ImmutableList.<SlotStatus>of()));
        }

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.install(AssignmentRepresentation.from(assignment), limit, uriInfo);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        Collection<SlotStatusRepresentation> slots = (Collection<SlotStatusRepresentation>) response.getEntity();
        assertEquals(slots.size(), min(numberOfAgents, limit));
        for (SlotStatusRepresentation slotRepresentation : slots) {
            SlotStatus slot = slotRepresentation.toSlotStatus();
            assertEquals(slot.getAssignment(), assignment);
            assertEquals(slot.getState(), STOPPED);
        }

        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
