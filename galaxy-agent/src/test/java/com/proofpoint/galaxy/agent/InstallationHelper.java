package com.proofpoint.galaxy.agent;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.shared.Installation;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.ArchiveHelper.createArchive;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;

public class InstallationHelper
{
    public static final Installation APPLE_INSTALLATION = new Installation(APPLE_ASSIGNMENT,
            URI.create("fake://localhost/apple.tar.gz"),
            ImmutableMap.of("config", URI.create("fake://localhost/apple.config")));
    public static final Installation BANANA_INSTALLATION = new Installation(BANANA_ASSIGNMENT,
            URI.create("fake://localhost/banana.tar.gz"),
            ImmutableMap.of("config", URI.create("fake://localhost/banana.config")));

    private final File targetRepo;
    private final Installation appleInstallation;
    private final Installation bananaInstallation;

    public InstallationHelper()
            throws Exception
    {
        this.targetRepo = createTempDir("repo");

        File binaryFile;
        try {
            binaryFile = new File(targetRepo, "binary.tar.gz");
            createArchive(binaryFile);
        }
        catch (Exception e) {
            deleteRecursively(targetRepo);
            throw e;
        }

        ImmutableMap<String, URI> configFiles = ImmutableMap.of("readme.txt", new File("README.txt").toURI());

        appleInstallation = new Installation(APPLE_ASSIGNMENT, binaryFile.toURI(), configFiles);
        bananaInstallation = new Installation(BANANA_ASSIGNMENT, binaryFile.toURI(), configFiles);
    }

    public void destroy()
    {
        deleteRecursively(targetRepo);
    }

    public Installation getAppleInstallation()
    {
        return appleInstallation;
    }

    public Installation getBananaInstallation()
    {
        return bananaInstallation;
    }

    public Installation createInstallation(String deployScriptContents)
            throws Exception
    {
        File binaryFile = new File(targetRepo, "binary-" + UUID.randomUUID() + ".tar.gz");
        createArchive(binaryFile, deployScriptContents);
        return new Installation(APPLE_ASSIGNMENT, binaryFile.toURI(), Collections.<String, URI>emptyMap());
    }
}
