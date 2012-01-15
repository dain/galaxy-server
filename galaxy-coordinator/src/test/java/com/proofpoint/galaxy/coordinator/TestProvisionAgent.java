package com.proofpoint.galaxy.coordinator;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.Test;

import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.experimental.testing.ValidationAssertions.assertValidates;
import static org.testng.Assert.assertEquals;

public class TestProvisionAgent
{
    @Test(groups = "aws", parameters = "aws-credentials-file")
    public void testProvisionAgent(String awsCredentialsFile)
            throws Exception
    {
        String credentialsJson = Files.toString(new File(awsCredentialsFile), Charsets.UTF_8);
        Map<String, String> map = JsonCodec.mapJsonCodec(String.class, String.class).fromJson(credentialsJson);
        String awsAccessKey = map.get("access-id");
        String awsSecretKey = map.get("private-key");

        AwsProvisionerConfig awsProvisionerConfig = new AwsProvisionerConfig()
                .setAwsAccessKey(awsAccessKey)
                .setAwsSecretKey(awsSecretKey)
                .setAwsAgentAmi("ami-27b7744e")
                .setAwsAgentKeypair("keypair")
                .setAwsAgentSecurityGroup("default")
                .setAwsAgentDefaultInstanceType("t1.micro");
        assertValidates(awsProvisionerConfig);

        CoordinatorConfig coordinatorConfig = new CoordinatorConfig()
                .setGalaxyVersion("0.7-SNAPSHOT")
                .setLocalBinaryRepo(
                        "https://oss.sonatype.org/content/repositories/releases/,https://oss.sonatype.org/content/repositories/snapshots,http://10.242.211.107:8000/nexus/content/repositories/proofpoint-dev,http://10.242.211.107:8000/nexus/content/repositories/proofpoint-eng-snapshots")
                .setLocalConfigRepo("git://10.242.211.107/config.git");
        assertValidates(coordinatorConfig);

        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(awsProvisionerConfig.getAwsAccessKey(), awsProvisionerConfig.getAwsSecretKey());
        AmazonEC2Client ec2Client = new AmazonEC2Client(awsCredentials);
        NodeInfo nodeInfo = createTestNodeInfo();
        HttpServerInfo httpServerInfo = new HttpServerInfo(new HttpServerConfig(), nodeInfo);
        BinaryRepository binaryRepository = new BinaryRepository() {
            @Override
            public URI getBinaryUri(BinarySpec binarySpec)
            {
                try {
                    return new URI("file", null, "host", 9999, "/" + binarySpec.toString(), null, null);
                }
                catch (URISyntaxException e) {
                    throw new AssertionError(e);
                }
            }
        };

        ConfigRepository configRepository = new ConfigRepository()
        {
            @Override
            public Map<String, URI> getConfigMap(String environment, ConfigSpec configSpec)
            {
                return ImmutableMap.of();
            }

            @Override
            public URI getConfigResource(String environment, ConfigSpec configSpec, String path)
            {
                return null;
            }

            @Override
            public InputSupplier<? extends InputStream> getConfigFile(String environment, ConfigSpec configSpec, String path)
            {
                return null;
            }
        };

        AwsProvisioner provisioner = new AwsProvisioner(ec2Client, nodeInfo, new BinaryUrlResolver(binaryRepository, httpServerInfo),
                configRepository, coordinatorConfig, awsProvisionerConfig);

        int agentCount = 2;
        List<Instance> provisioned = provisioner.provisionAgents(agentCount, null, null);
        assertEquals(provisioned.size(), agentCount);
        System.err.println("provisioned instances: " + provisioned);

        List<Instance> running = provisioner.listAgents();
        assertEquals(running.size(), agentCount);
        System.err.println("running agents: " + running);

        List<String> instanceIds = newArrayList();
        for (Instance instance : provisioned) {
            instanceIds.add(instance.getInstanceId());
        }
        provisioner.terminateAgents(instanceIds);
    }

    private static NodeInfo createTestNodeInfo()
    {
        String nodeId = UUID.randomUUID().toString();
        return new NodeInfo("test", "foo", nodeId, getTestIp(), "/test/" + nodeId, "binarySpec", "configSpec");
    }

    private static InetAddress getTestIp()
    {
        try {
            return InetAddress.getByName("192.0.2.0");
        }
        catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
    }
}
