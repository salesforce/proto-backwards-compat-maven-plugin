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
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

/**
 * Tests the backwards compatibility check mojo.
 */
public class BasicMojoTest
    extends BetterAbstractMojoTestCase {

    final String testDir = "/src/test/resources/unit/";
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
    public void testProtolockInit()
        throws Exception {
        writeTestFile("init.proto");
        myMojo.execute();
        checkLockFileExists();
        checkExecutableExists();
    }

    /**
     * Tests that backwards compatibility check fails when breaking change is made.
     * @throws Exception if any.
     */
    @Test
    public void testShouldFailCompatibilityCheckBreakingChange()
        throws Exception {
        writeTestFile("init.proto");
        myMojo.execute();
        writeTestFile("bad.proto");
        runMojo(true);
    }

    /**
     * Tests that backwards compatibility check fails when .proto file is deleted.
     * @throws Exception if any.
     */
    @Test
    public void testShouldFailCompatibilityCheckProtoFileDeleted()
        throws Exception {
        writeTestFile("init.proto");
        myMojo.execute();
        File testFile = getTestFile(testDir + "proto/test.proto");
        testFile.delete();
        runMojo(true);
    }

    /**
     * Tests that backwards compatibility check passes when breaking change is forced.
     * @throws Exception if any.
     */
    @Test
    public void testShouldPassCompatibilityCheckForceChange()
        throws Exception {
        writeTestFile("init.proto");
        myMojo.execute();
        writeTestFile("bad.proto");
        File lockFile = getTestFile(testDir + "proto/proto.lock");
        lockFile.delete();
        runMojo(false);
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
        File pom = getTestFile(testDir + "project-to-test/pom.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        myMojo = (BackwardsCompatibilityCheckMojo) lookupConfiguredMojo(pom, "backwards-compatibility-check");
        assertNotNull(myMojo);
        Model m = new Model();
        String classifier = System.getProperty("os.name").toLowerCase();
        if ((classifier.contains("mac"))) {
            classifier = "osx-x86_64";
        } else if (classifier.contains("nux")) {
            classifier = "linux-x86_64";
        } else if (classifier.contains("windows")) {
            classifier = "windows-x86_64";
        }

        m.addProperty("os.detected.classifier", classifier);
        Build b = new Build();
        b.setDirectory(System.getProperty("user.dir") + testDir);
        m.setBuild(b);
        myMojo.project = new MavenProject(m);
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
