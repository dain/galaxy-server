package com.proofpoint.galaxy.coordinator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RESTARTING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

public class Coordinator
{
    private final ConcurrentMap<String, RemoteAgent> agents;

    private final String environment;
    private final BinaryUrlResolver binaryUrlResolver;
    private final ConfigRepository configRepository;
    private final LocalConfigRepository localConfigRepository;
    private final ScheduledExecutorService timerService;
    private final Duration statusExpiration;
    private final Provisioner provisioner;
    private final RemoteAgentFactory remoteAgentFactory;
    private final ServiceInventory serviceInventory;

    @Inject
    public Coordinator(NodeInfo nodeInfo,
            CoordinatorConfig config,
            RemoteAgentFactory remoteAgentFactory,
            BinaryUrlResolver binaryUrlResolver,
            ConfigRepository configRepository,
            LocalConfigRepository localConfigRepository,
            Provisioner provisioner,
            ServiceInventory serviceInventory)
    {
        this(nodeInfo.getEnvironment(),
                remoteAgentFactory,
                binaryUrlResolver,
                configRepository,
                localConfigRepository,
                provisioner,
                serviceInventory,
                checkNotNull(config, "config is null").getStatusExpiration()
        );
    }

    public Coordinator(String environment,
            RemoteAgentFactory remoteAgentFactory,
            BinaryUrlResolver binaryUrlResolver,
            ConfigRepository configRepository,
            LocalConfigRepository localConfigRepository,
            Provisioner provisioner,
            ServiceInventory serviceInventory,
            Duration statusExpiration)
    {
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(remoteAgentFactory, "remoteAgentFactory is null");
        Preconditions.checkNotNull(configRepository, "repository is null");
        Preconditions.checkNotNull(binaryUrlResolver, "binaryUrlResolver is null");
        Preconditions.checkNotNull(localConfigRepository, "localConfigRepository is null");
        Preconditions.checkNotNull(provisioner, "provisioner is null");
        Preconditions.checkNotNull(serviceInventory, "serviceInventory is null");
        Preconditions.checkNotNull(statusExpiration, "statusExpiration is null");

        this.environment = environment;
        this.remoteAgentFactory = remoteAgentFactory;
        this.binaryUrlResolver = binaryUrlResolver;
        this.configRepository = configRepository;
        this.localConfigRepository = localConfigRepository;
        this.provisioner = provisioner;
        this.serviceInventory = serviceInventory;
        this.statusExpiration = statusExpiration;

        agents = new MapMaker().makeMap();

        timerService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("coordinator-agent-monitor").setDaemon(true).build());

        updateAllAgents();
    }

    @PostConstruct
    public void start() {
        timerService.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                updateAllAgents();
            }
        }, 0, (long) statusExpiration.toMillis(), TimeUnit.MILLISECONDS);
    }

    public List<AgentStatus> getAllAgentStatus()
    {
        return ImmutableList.copyOf(Iterables.transform(agents.values(), new Function<RemoteAgent, AgentStatus>()
        {
            public AgentStatus apply(RemoteAgent agent)
            {
                return agent.status();
            }
        }));
    }

    public List<AgentStatus> getAllAgents()
    {
        ImmutableList.Builder<AgentStatus> builder = ImmutableList.builder();
        for (RemoteAgent remoteAgent : agents.values()) {
            builder.add(remoteAgent.status());
        }
        return builder.build();
    }

    public AgentStatus getAgentStatus(String agentId)
    {
        RemoteAgent agent = agents.get(agentId);
        if (agent == null) {
            return null;
        }
        return agent.status();
    }

    public void updateAllAgents()
    {
        for (Instance instance : this.provisioner.listAgents()) {
            RemoteAgent existing = agents.putIfAbsent(instance.getInstanceId(), remoteAgentFactory.createRemoteAgent(instance.getInstanceId(), instance.getInstanceType(), instance.getUri()));
            if (existing != null) {
                existing.setUri(instance.getUri());
            }
        }

        List<ServiceDescriptor> serviceDescriptors = serviceInventory.getServiceInventory(getAllSlotStatus());
        for (RemoteAgent remoteAgent : agents.values()) {
            remoteAgent.updateStatus();
            remoteAgent.setServiceInventory(serviceDescriptors);
        }
    }

    @VisibleForTesting
    public void setAgentStatus(AgentStatus status)
    {

        RemoteAgent remoteAgent = agents.get(status.getAgentId());
        if (remoteAgent == null) {
            remoteAgent = remoteAgentFactory.createRemoteAgent(status.getAgentId(), status.getInstanceType(), status.getUri());
            agents.put(status.getAgentId(), remoteAgent);
        }
        remoteAgent.setStatus(status);
    }

    public List<AgentStatus> addAgents(int count, String instanceType, String availabilityZone)
            throws Exception
    {
        List<Instance> instances = provisioner.provisionAgents(count, instanceType, availabilityZone);

        List<AgentStatus> agents = newArrayList();
        for (Instance instance : instances) {
            String instanceId = instance.getInstanceId();

            AgentStatus agentStatus = new AgentStatus(
                    instanceId,
                    AgentLifecycleState.PROVISIONING,
                    null,
                    instance.getLocation(),
                    instance.getInstanceType(),
                    ImmutableList.<SlotStatus>of());

            RemoteAgent remoteAgent = remoteAgentFactory.createRemoteAgent(instanceId, instance.getInstanceType(), null);
            this.agents.put(instanceId, remoteAgent);

            agents.add(agentStatus);
        }
        return agents;
    }

    public boolean removeAgent(String agentId)
    {
        return agents.remove(agentId) != null;
    }

    public boolean terminateAgent(String agentId)
    {
        RemoteAgent agent = agents.remove(agentId);
        if (agent == null) {
            return false;
        }
        if (!agent.getSlots().isEmpty()) {
            agents.putIfAbsent(agentId, agent);
            throw new IllegalStateException("Cannot terminate agent that has slots: " + agentId);
        }
        provisioner.terminateAgents(ImmutableList.of(agentId));
        return true;
    }

    public List<SlotStatus> install(Predicate<AgentStatus> filter, int limit, Assignment assignment)
    {
        Map<String,URI> configMap = localConfigRepository.getConfigMap(environment, assignment.getConfig());
        if (configMap == null) {
            configMap = configRepository.getConfigMap(environment, assignment.getConfig());
        }

        Installation installation = new Installation(assignment, binaryUrlResolver.resolve(assignment.getBinary()), configMap);

        List<SlotStatus> slots = newArrayList();
        List<RemoteAgent> agents = newArrayList(filter(this.agents.values(), Predicates.and(filterAgentsBy(filter), filterAgentsWithAssignment(assignment))));

        // randomize agents so all processes don't end up on the same node
        // todo sort agents by number of process already installed on them
        Collections.shuffle(agents);
        for (RemoteAgent agent : agents) {
            if (slots.size() >= limit) {
                break;
            }
            if (agent.status().getState() == ONLINE) {
                slots.add(agent.install(installation));
            }
        }
        return ImmutableList.copyOf(slots);
    }


    public List<SlotStatus> upgrade(Predicate<SlotStatus> filter, UpgradeVersions upgradeVersions)
    {
        HashSet<Assignment> newAssignments = new HashSet<Assignment>();
        List<RemoteSlot> slotsToUpgrade = new ArrayList<RemoteSlot>();
        for (RemoteSlot slot : ImmutableList.copyOf(filter(getAllSlots(), filterSlotsBy(filter)))) {
            SlotStatus status = slot.status();
            SlotLifecycleState state = status.getState();
            if (state != TERMINATED && state != UNKNOWN) {
                Assignment assignment = upgradeVersions.upgradeAssignment(status.getAssignment());
                newAssignments.add(assignment);
                slotsToUpgrade.add(slot);
            }
        }

        // no slots to upgrade
        if (newAssignments.isEmpty()) {
            return ImmutableList.of();
        }

        // must upgrade to a single new version
        if (newAssignments.size() != 1) {
            throw new AmbiguousUpgradeException(newAssignments);
        }
        Assignment assignment = newAssignments.iterator().next();

        Map<String,URI> configMap = localConfigRepository.getConfigMap(environment, assignment.getConfig());
        if (configMap == null) {
            configMap = configRepository.getConfigMap(environment, assignment.getConfig());
        }

        final Installation installation = new Installation(assignment, binaryUrlResolver.resolve(assignment.getBinary()), configMap);

        return ImmutableList.copyOf(transform(slotsToUpgrade, new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                return slot.assign(installation);
            }
        }));
    }

    public List<SlotStatus> terminate(Predicate<SlotStatus> filter)
    {
        Preconditions.checkNotNull(filter, "filter is null");

        ImmutableList.Builder<SlotStatus> builder = ImmutableList.builder();
        for (RemoteAgent agent : agents.values()) {
            for (RemoteSlot slot : agent.getSlots()) {
                if (filter.apply(slot.status())) {
                    SlotStatus slotStatus = agent.terminateSlot(slot.getId());
                    builder.add(slotStatus);
                }
            }
        }
        return builder.build();
    }

    public List<SlotStatus> setState(final SlotLifecycleState state, Predicate<SlotStatus> filter)
    {
        Preconditions.checkArgument(EnumSet.of(RUNNING, RESTARTING, STOPPED).contains(state), "Unsupported lifecycle state: " + state);

        return ImmutableList.copyOf(transform(filter(getAllSlots(), filterSlotsBy(filter)), new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                SlotStatus slotStatus = null;
                switch (state) {
                    case RUNNING:
                        slotStatus = slot.start();
                        break;
                    case RESTARTING:
                        slotStatus = slot.restart();
                        break;
                    case STOPPED:
                        slotStatus = slot.stop();
                        break;
                }
                return slotStatus;
            }
        }));
    }

    public List<SlotStatus> getAllSlotStatus()
    {
        return getAllSlotsStatus(Predicates.<SlotStatus>alwaysTrue());
    }

    public List<SlotStatus> getAllSlotsStatus(Predicate<SlotStatus> slotFilter)
    {
        return ImmutableList.copyOf(filter(transform(getAllSlots(), getSlotStatus()), slotFilter));
    }

    private Predicate<RemoteSlot> filterSlotsBy(final Predicate<SlotStatus> filter)
    {
        return new Predicate<RemoteSlot>()
        {
            @Override
            public boolean apply(RemoteSlot input)
            {
                return filter.apply(input.status());
            }
        };
    }

    private List<? extends RemoteSlot> getAllSlots()
    {
        return ImmutableList.copyOf(concat(Iterables.transform(agents.values(), new Function<RemoteAgent, List<? extends RemoteSlot>>()
        {
            public List<? extends RemoteSlot> apply(RemoteAgent agent)
            {
                return agent.getSlots();
            }
        })));
    }

    private Predicate<RemoteAgent> filterAgentsBy(final Predicate<AgentStatus> filter)
    {
        return new Predicate<RemoteAgent>()
        {
            @Override
            public boolean apply(RemoteAgent input)
            {
                return filter.apply(input.status());
            }
        };
    }

    private Function<RemoteSlot, SlotStatus> getSlotStatus()
    {
        return new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                return slot.status();
            }
        };
    }

    private Predicate<RemoteAgent> filterAgentsWithAssignment(final Assignment assignment)
    {
        return new Predicate<RemoteAgent>()
        {
            @Override
            public boolean apply(RemoteAgent agent)
            {
                for (RemoteSlot slot : agent.getSlots()) {
                    if (assignment.equalsIgnoreVersion(slot.status().getAssignment())) {
                        return false;
                    }
                }
                return true;
            }
        };
    }
}
