package com.proofpoint.galaxy.standalone;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import org.fusesource.jansi.Ansi.Color;

import static com.proofpoint.galaxy.standalone.Ansi.colorize;

public class AgentRecord implements Record
{
    public static ImmutableList<Record> toAgentRecords(Iterable<AgentStatusRepresentation> agents)
    {
        return ImmutableList.copyOf(Iterables.transform(agents, new Function<AgentStatusRepresentation, Record>()
        {
            @Override
            public AgentRecord apply(AgentStatusRepresentation agent)
            {
                return new AgentRecord(agent);
            }
        }));
    }

    private final AgentStatusRepresentation agentStatus;

    public AgentRecord(AgentStatusRepresentation agentStatus)
    {
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");

        this.agentStatus = agentStatus;
    }

    public Object getObjectValue(Column column)
    {
        switch (column) {
            case shortId:
                return agentStatus.getAgentId();
            case uuid:
                return agentStatus.getAgentId();
            case host:
                return agentStatus.getHost();
            case ip:
                return agentStatus.getIp();
            case status:
                return agentStatus.getState();
            case location:
                return agentStatus.getLocation();
            case instanceType:
                return agentStatus.getInstanceType();
            default:
                return null;
        }
    }

    @Override
    public String getValue(Column column)
    {
        Object value = getObjectValue(column);
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    @Override
    public String getColorizedValue(Column column)
    {
        Object value = getObjectValue(column);
        if (Column.status == column) {
            AgentLifecycleState state = AgentLifecycleState.valueOf(toString(value));
            if (AgentLifecycleState.ONLINE == state) {
                return colorize(state, Color.GREEN);
            } else if (AgentLifecycleState.OFFLINE == state) {
                return colorize(state, Color.RED);
            } else if (AgentLifecycleState.PROVISIONING == state) {
                return colorize(state, Color.BLUE);
            }
        } else if (Column.statusMessage == column) {
            return colorize(value, Color.RED);
        }
        return toString(value);
    }

    private String toString(Object value)
    {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }
}
