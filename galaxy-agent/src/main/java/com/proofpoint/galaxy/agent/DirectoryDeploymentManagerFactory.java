package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import java.io.File;
import java.util.List;

import static com.proofpoint.galaxy.shared.FileUtils.listFiles;

public class DirectoryDeploymentManagerFactory implements DeploymentManagerFactory
{
    private final String location;
    private final Duration tarTimeout;
    private final File slotDir;

    @Inject
    public DirectoryDeploymentManagerFactory(NodeInfo nodeInfo, AgentConfig config)
    {
        this(nodeInfo.getLocation(), config.getSlotsDir(), config.getTarTimeout());
    }

    public DirectoryDeploymentManagerFactory(String location, String slotsDir, Duration tarTimeout)
    {
        Preconditions.checkNotNull(location, "location is null");
        Preconditions.checkNotNull(slotsDir, "slotsDir is null");
        Preconditions.checkNotNull(tarTimeout, "tarTimeout is null");

        this.location = location;
        this.tarTimeout = tarTimeout;

        this.slotDir = new File(slotsDir);

        slotDir.mkdirs();
        if (!slotDir.isDirectory()) {
            throw new IllegalArgumentException("slotDir is not a directory");
        }
    }

    @Override
    public List<DeploymentManager>  loadSlots()
    {
        ImmutableList.Builder<DeploymentManager> builder = ImmutableList.builder();
        for (File dir : listFiles(slotDir)) {
            if (dir.isDirectory() && new File(dir, "galaxy-slot-id.txt").canRead()) {
                builder.add(createDeploymentManager(dir.getName()));
            }
        }
        return builder.build();
    }

    @Override
    public DirectoryDeploymentManager createDeploymentManager(String slotName)
    {
        return new DirectoryDeploymentManager(slotName, new File(slotDir, slotName), location + "/" + slotName, tarTimeout);
    }
}
