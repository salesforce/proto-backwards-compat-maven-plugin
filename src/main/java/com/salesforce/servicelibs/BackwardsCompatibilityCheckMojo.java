/*
 *  Copyright (c) 2018, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs;

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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

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

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject mavenProject;

    /**
     * Execute the plugin.
     * @throws MojoExecutionException thrown when execution of protolock fails.
     * @throws MojoFailureException thrown when compatibility check fails.
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String classifier = mavenProject.getProperties().getProperty("os.detected.classifier");
        if (classifier == null) {
            getLog().error("Add os-maven-plugin to your POM. https://github.com/trustin/os-maven-plugin");
            throw new MojoExecutionException("Unable to detect OS type.");
        }

        // Copy protolock executable locally if needed
        Path exeDirPath = Paths.get(mavenProject.getBuild().getDirectory(), "protolock-bin");
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

        // Run protolock
        try {
            Path lockFile = Paths.get(protoSourceRoot, "proto.lock");
            if (!Files.exists(lockFile)) {
                Runtime.getRuntime().exec(exePath + " init", null, new File(protoSourceRoot)).waitFor();
                getLog().info("Initialized protolock.");
            } else {
                Process protolock =
                    Runtime.getRuntime().exec(exePath + " status", null, new File(protoSourceRoot));
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(protolock.getInputStream()));
                String s;
                while ((s = stdInput.readLine()) != null) {
                    getLog().error(s);
                }

                if (protolock.waitFor() != 0) {
                    throw new MojoFailureException("Backwards compatibility check failed!");
                } else {
                    Runtime.getRuntime().exec(exePath + " commit", null, new File(protoSourceRoot));
                    getLog().info("Backwards compatibility check passed.");
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("An error occurred while running protolock", e);
        }
    }
}
