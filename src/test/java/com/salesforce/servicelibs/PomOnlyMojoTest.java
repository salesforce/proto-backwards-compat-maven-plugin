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
import org.junit.Test;

/**
 * Tests the backwards compatibility check mojo.
 */
public class PomOnlyMojoTest
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
     * Tests that backwards compatibility check passes when non-breaking change is made.
     * @throws Exception if any.
     */
    @Test
    public void testShouldPassCompatibilityCheckNonBreakingChange()
        throws Exception {
        writeTestFile("init.proto");
        myMojo.execute();
        writeTestFile("good.proto");
        runMojo(false);
    }

    /**
     * Setup backwards compatibility check mojo.
     */
    private void setupMojo()
        throws Exception {
        File pom = getTestFile(testDir + "project-to-test/pom-only-pom.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        myMojo = (BackwardsCompatibilityCheckMojo) lookupConfiguredMojo(pom, "backwards-compatibility-check");
        assertNotNull(myMojo);
        myMojo.project = new MavenProject(getModel());
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

    /**
     * Check that the proto.lock file exists.
     */
    private void checkLockFileExists() {
        File lockFile = getTestFile(testDir + "proto/proto.lock");
        assertTrue(lockFile.exists());
    }

    /**
     * Check that the protolock executable exists.
     */
    private void checkExecutableExists() {
        String os = System.getProperty("os.name").toLowerCase();

        String protolockExtension = "";
        if (os.contains("windows")) {
            protolockExtension = ".exe";
        }

        File exeFile = getTestFile(testDir + "protolock-bin/protolock" + protolockExtension);
        assertTrue(exeFile.exists());
    }
}
