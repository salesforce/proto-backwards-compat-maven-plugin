/*
 *  Copyright (c) 2018, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests the backwards compatibility check mojo.
 * This test is disabled because it requires a go toolchain to install the sample plugin.
 */
@Ignore
public class PluginMojoTest
    extends BetterAbstractMojoTestCase {

    BackwardsCompatibilityCheckMojo myMojo;

    /**
     * {@inheritDoc}
     * */
    protected void setUp()
        throws Exception {
        // required
        super.setUp();
        setupMojo();
    }

    /**
     * {@inheritDoc}
     * */
    protected void tearDown()
        throws Exception {
        // required
        super.tearDown();
        String path = testDir + "protolock-bin";
        File exeDir = getTestFile(path);
        File exeFile = getTestFile(path + "/protolock");
        exeFile.delete();
        exeDir.delete();
        File lockFile = getTestFile(testDir + "proto/proto.lock");
        lockFile.delete();
        File testFile = getTestFile(testDir + "proto/test.proto");
        testFile.delete();
    }

    /**
     * Tests that protolock is properly initialized.
     * @throws Exception if any.
     */
    @Test
    public void testProtolockPlugin()
        throws Exception {
        writeTestFile("init.proto");
        // Init
        runMojo(false);
        // Verify - should fail because demo plugin returns errors
        runMojo(true);
    }

    /**
     * Setup backwards compatibility check mojo.
     */
    private void setupMojo()
        throws Exception {
        File pom = getTestFile(testDir + "project-to-test/pom-with-plugin.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        myMojo = (BackwardsCompatibilityCheckMojo) lookupConfiguredMojo(pom, "backwards-compatibility-check");
        assertNotNull(myMojo);

        myMojo.project = new MavenProject(getModel());
        myMojo.project.setArtifact(myMojo.repositorySystem.createArtifact("com.salesforce.servicelibs.unit",
                "project-to-test", "1.0-SNAPSHOT", "pom"));

        myMojo.session = newMavenSession();
        myMojo.localRepository = myMojo.session.getLocalRepository();
    }

    /**
     * Write desired test file to proto directory.
     * @param filename the proto file.
     */
    private void writeTestFile(String filename)
        throws Exception {

        File testFile = getTestFile(testDir + "proto/test.proto");
        if (testFile.exists()) {
            testFile.delete();
        }
        testFile.getParentFile().mkdirs();
        testFile.createNewFile();
        File protoFile = getTestFile(testDir + "testProtos/" + filename);
        try (InputStream is = new FileInputStream(protoFile);
            OutputStream os = new FileOutputStream(testFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }

    /**
     * Run mojo with expected result.
     * @param shouldFail backwards compatibility check should pass or fail.
     */
    private void runMojo(boolean shouldFail)
        throws MojoExecutionException {
        try {
            myMojo.execute();
            if (shouldFail) {
                fail();
            }
        } catch (MojoFailureException ex) {
            if (shouldFail) {
                assertEquals("Backwards compatibility check failed!", ex.getMessage());
            } else {
                fail();
            }
        }
    }
}
