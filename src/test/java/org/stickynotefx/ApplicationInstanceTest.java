package org.stickynotefx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationInstanceTest {
    @TempDir Path directory;

    @Test
    void repeatedLaunchesActivateOnlyTheExistingProfile() throws Exception {
        var activations = new LinkedBlockingQueue<Boolean>();
        var otherActivations = new LinkedBlockingQueue<Boolean>();
        try (var primary = ApplicationInstance.open(directory, () -> activations.add(true));
             var other = ApplicationInstance.open(directory.resolve("other"), () -> otherActivations.add(true))) {
            assertTrue(primary.isPrimary());
            assertTrue(other.isPrimary());
            for (int attempt = 0; attempt < 2; attempt++) {
                try (var secondary = ApplicationInstance.open(directory, () -> fail("Secondary must not listen"))) {
                    assertFalse(secondary.isPrimary());
                    assertEquals(Boolean.TRUE, activations.poll(5, TimeUnit.SECONDS));
                }
            }
            assertTrue(otherActivations.isEmpty());
        }
    }

    @Test
    void separateProcessActivatesTheLockOwner() throws Exception {
        var activations = new LinkedBlockingQueue<Boolean>();
        try (var primary = ApplicationInstance.open(directory, () -> activations.add(true))) {
            assertTrue(primary.isPrimary());
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            Process secondary = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", classpath, getClass().getName(), directory.toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            try {
                assertTrue(secondary.waitFor(10, TimeUnit.SECONDS));
                assertEquals(0, secondary.exitValue());
                assertEquals(Boolean.TRUE, activations.poll(5, TimeUnit.SECONDS));
            } finally {
                if (secondary.isAlive()) secondary.destroyForcibly();
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        try (var instance = ApplicationInstance.open(Path.of(arguments[0]), () -> { })) {
            if (instance.isPrimary()) throw new AssertionError("Existing process must retain the lock");
        }
    }

    @Test
    void shutdownReleasesLockForTheNextInstance() throws Exception {
        var first = ApplicationInstance.open(directory, () -> { });
        assertTrue(first.isPrimary());
        first.close();
        first.close();
        try (var replacement = ApplicationInstance.open(directory, () -> { })) {
            assertTrue(replacement.isPrimary());
        }
    }
}