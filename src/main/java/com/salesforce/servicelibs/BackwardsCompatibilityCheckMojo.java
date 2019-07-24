/*
 *  Copyright (c) 2018, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.utils.Os;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.io.FileUtils;

@Mojo(
    name = "backwards-compatibility-check",
    defaultPhase = LifecyclePhase.VERIFY,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true)
public class BackwardsCompatibilityCheckMojo
    extends AbstractMojo {

    /**
     * The directory where .proto source files can be found.
     */
    @Parameter(defaultValue = "${basedir}/src/main/proto")
    private String protoSourceRoot;

    /**
     * A list of protolock plugins. May be empty.
     */
    @Parameter(property = "plugins", required = false)
    private List<String> plugins;

    /**
     * Additional options to pass to protolock, using command line format. See protolock documentation for details.
     */
    @Parameter(property = "options", required = false)
    private String options;

    /**
     * A directory where native protolock plugins will be stored.
     */
    @Parameter(required = false, defaultValue = "${project.build.directory}/protolock-plugins")
    private File protolockPluginDirectory;

    @Parameter(required = true, readonly = true, property = "localRepository")
    protected ArtifactRepository localRepository;

    @Parameter(required = true, readonly = true, defaultValue = "${project.remoteArtifactRepositories}")
    protected List<ArtifactRepository> remoteRepositories;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    @Component
    protected RepositorySystem repositorySystem;

    @Component
    protected ResolutionErrorHandler resolutionErrorHandler;

    @Component
    protected ArtifactFactory artifactFactory;


    /**
     * Execute the plugin.
     * @throws MojoExecutionException thrown when execution of protolock fails.
     * @throws MojoFailureException thrown when compatibility check fails.
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String classifier = project.getProperties().getProperty("os.detected.classifier");
        if (classifier == null) {
            getLog().error("Add os-maven-plugin to your POM. https://github.com/trustin/os-maven-plugin");
            throw new MojoExecutionException("Unable to detect OS type.");
        }

        try {
            protolockPluginDirectory = protolockPluginDirectory.getCanonicalFile();
        } catch (IOException e) {
            throw new MojoExecutionException("Problem with plugin directory path", e);
        }

        // Copy protolock executable locally if needed
        Path exeDirPath = Paths.get(project.getBuild().getDirectory(), "protolock-bin");
        if (!Files.isDirectory(exeDirPath)) {
            try {
                Files.createDirectory(exeDirPath);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to create the protolock binary directory", e);
            }
        }

        String exeExtension = "";
        if (classifier.startsWith("windows")) {
            exeExtension = ".exe";
        }

        Path exePath = exeDirPath.resolve("protolock" + exeExtension);
        if (!Files.exists(exePath)) {
            String protolockResourcePath = classifier + "/protolock" + exeExtension;

            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(protolockResourcePath)) {
                if (in == null) {
                    throw new MojoExecutionException(
                            "OS not supported. Unable to find a protolock binary for the classifier " + classifier);
                }

                Files.copy(in, exePath);

                PosixFileAttributeView attributes = Files.getFileAttributeView(exePath, PosixFileAttributeView.class);
                if (attributes != null) {
                    attributes.setPermissions(PosixFilePermissions.fromString("rwxrwxr-x"));
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to write the protolock binary", e);
            }
        }

        // Resolve protolock plugins
        List<String> pluginFileNames = new ArrayList<>();
        if (plugins != null) {
            for (String pluginSpec : plugins) {
                if (pluginSpec.contains(":")) {
                    // A maven spec
                    Artifact plugin = createDependencyArtifact(pluginSpec);
                    File pluginFile = resolveBinaryArtifact(plugin);
                    pluginFileNames.add(pluginFile.getName());
                } else {
                    // Not a maven spec
                    pluginFileNames.add(pluginSpec);
                }
            }
        }

        String pathEnv = "PATH=" + System.getenv("PATH");
        if (plugins != null && !plugins.isEmpty()) {
            if (classifier.startsWith("windows")) {
                pathEnv += ";";
            } else {
                pathEnv += ":";
            }
            pathEnv += protolockPluginDirectory.getAbsolutePath();
        }

        String pluginsOption = "";
        if (!pluginFileNames.isEmpty()) {
            pluginsOption = " --plugins=" + StringUtils.join(pluginFileNames.toArray(), ",");
        }


        // Run protolock
        try {
            if (options != null && !StringUtils.isEmpty(options)) {
                options = " " + options.trim();
            } else {
                options = "";
            }

            Path lockFile = Paths.get(protoSourceRoot, "proto.lock");
            if (!Files.exists(lockFile)) {
                Runtime.getRuntime().exec(exePath + " init" + options, new String[]{pathEnv},
                        new File(protoSourceRoot)).waitFor();
                getLog().info("Initialized protolock.");
            } else {
                Process protolock =
                    Runtime.getRuntime().exec(exePath + " status" + pluginsOption + options, new String[]{pathEnv},
                            new File(protoSourceRoot));
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(protolock.getInputStream()));
                String s;
                while ((s = stdInput.readLine()) != null) {
                    getLog().error(s);
                }

                if (protolock.waitFor() != 0) {
                    throw new MojoFailureException("Backwards compatibility check failed!");
                } else {
                    Runtime.getRuntime().exec(exePath + " commit" + options, new String[]{pathEnv},
                            new File(protoSourceRoot));
                    getLog().info("Backwards compatibility check passed.");
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("An error occurred while running protolock", e);
        }
    }

    /**
     * Creates a dependency artifact from a specification in
     * {@code groupId:artifactId:version[:type[:classifier]]} format.
     *
     * @param artifactSpec artifact specification.
     * @return artifact object instance.
     */
    private Artifact createDependencyArtifact(final String artifactSpec) throws MojoExecutionException {
        final String[] parts = artifactSpec.split(":");
        if (parts.length < 3 || parts.length > 5) {
            throw new MojoExecutionException(
                    "Invalid artifact specification format"
                            + ", expected: groupId:artifactId:version[:type[:classifier]]"
                            + ", actual: " + artifactSpec);
        }
        final String type = parts.length >= 4 ? parts[3] : "exe";
        final String classifier = parts.length == 5 ? parts[4] : null;

        final String groupId = parts[0];
        final String artifactId = parts[1];
        final String version = parts[2];

        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        dependency.setType(type);
        dependency.setClassifier(classifier);
        dependency.setScope(Artifact.SCOPE_RUNTIME);

        return repositorySystem.createDependencyArtifact(dependency);
    }

    /**
     * Downloads a binary artifact and installs it in the protolock plugin directory.
     * @param artifact the artifact to download.
     * @return a handle to the downloaded file.
     */
    private File resolveBinaryArtifact(final Artifact artifact) throws MojoExecutionException {
        final ArtifactResolutionResult result;
        try {
            final ArtifactResolutionRequest request = new ArtifactResolutionRequest()
                    .setArtifact(project.getArtifact())
                    .setResolveRoot(false)
                    .setResolveTransitively(false)
                    .setArtifactDependencies(singleton(artifact))
                    .setManagedVersionMap(emptyMap())
                    .setLocalRepository(localRepository)
                    .setRemoteRepositories(remoteRepositories)
                    .setOffline(session.isOffline())
                    .setForceUpdate(session.getRequest().isUpdateSnapshots())
                    .setServers(session.getRequest().getServers())
                    .setMirrors(session.getRequest().getMirrors())
                    .setProxies(session.getRequest().getProxies());

            result = repositorySystem.resolve(request);

            resolutionErrorHandler.throwErrors(request, result);
        } catch (final ArtifactResolutionException e) {
            throw new MojoExecutionException("Unable to resolve artifact: " + e.getMessage(), e);
        }

        final Set<Artifact> artifacts = result.getArtifacts();

        if (artifacts == null || artifacts.isEmpty()) {
            throw new MojoExecutionException("Unable to resolve artifact");
        }

        final Artifact resolvedBinaryArtifact = artifacts.iterator().next();
        if (getLog().isDebugEnabled()) {
            getLog().debug("Resolved artifact: " + resolvedBinaryArtifact);
        }

        // Copy the file to the project build directory and make it executable
        final File sourceFile = resolvedBinaryArtifact.getFile();
        final String sourceFileName = sourceFile.getName();
        final String targetFileName;
        if (Os.isFamily(Os.FAMILY_WINDOWS) && !sourceFileName.endsWith(".exe")) {
            targetFileName = sourceFileName + ".exe";
        } else {
            targetFileName = sourceFileName;
        }
        final File targetFile = new File(protolockPluginDirectory, targetFileName);
        if (targetFile.exists()) {
            // The file must have already been copied in a prior plugin execution/invocation
            getLog().debug("Executable file already exists: " + targetFile.getAbsolutePath());
            return targetFile;
        }
        try {
            FileUtils.forceMkdir(protolockPluginDirectory);
        } catch (final IOException e) {
            throw new MojoExecutionException("Unable to create directory " + protolockPluginDirectory, e);
        }
        try {
            FileUtils.copyFile(sourceFile, targetFile);
        } catch (final IOException e) {
            throw new MojoExecutionException("Unable to copy the file to " + protolockPluginDirectory, e);
        }
        if (!Os.isFamily(Os.FAMILY_WINDOWS)) {
            targetFile.setExecutable(true);
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Executable file: " + targetFile.getAbsolutePath());
        }
        return targetFile;
    }
}
