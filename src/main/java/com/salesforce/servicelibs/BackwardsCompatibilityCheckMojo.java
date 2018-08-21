/*
 *  Copyright (c) 2018, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
    @Parameter(defaultValue = "src/main/proto")
    private String protoSourceRoot;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject mavenProject;

    /**
     * Execute the plugin.
     * @throws MojoExecutionException thrown when execution of protolock fails.
     * @throws MojoFailureException thrown when compatibility check fails.
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Copy protolock executable locally
        String exeDirPath = mavenProject.getBuild().getDirectory() + "/protolock-bin";
        String exePath = exeDirPath + "/protolock";
        File exeDir = new File(exeDirPath);
        if (!exeDir.exists()) {
            exeDir.mkdir();
            final String os = mavenProject.getProperties().getProperty("os.detected.name");
            if (os == null) {
                getLog().error("Add os-maven-plugin to your POM. https://github.com/trustin/os-maven-plugin");
                throw new MojoExecutionException("Unable to detect OS type.");
            } else if (!os.equals("osx") && !os.equals("linux")) {
                throw new MojoExecutionException("OS not supported.");
            }

            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(os + "/protolock");
                    OutputStream out = new FileOutputStream(exePath)) {
                File exeFile = new File(exePath);
                write(in, out);
                exeFile.setExecutable(true, false);
            } catch (IOException e) {
                throw new MojoExecutionException(e.toString());
            }
        }

        // Run protolock
        try {
            File lockFile = new File(protoSourceRoot + "/proto.lock");
            if (!lockFile.exists()) {
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
            throw new MojoExecutionException(e.toString());
        }
    }

    private void write(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        while (true) {
            int bytesRead = in.read(buffer);
            if (bytesRead == -1) {
                break;
            }
            out.write(buffer, 0, bytesRead);
        }
    }
}
