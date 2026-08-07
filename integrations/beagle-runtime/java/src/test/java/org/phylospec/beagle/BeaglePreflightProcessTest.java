package org.phylospec.beagle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaglePreflightProcessTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void returnsResourcesFromAnIsolatedJvm() throws Exception {
        BeaglePreflightProcess.Result result = process(AvailableFactory.class)
                .run(request(Duration.ofSeconds(5)));

        assertTrue(result.available(), result::problem);
        assertEquals(0, result.exitCode());
        assertNotNull(result.verification());
        assertEquals("4.0.1", result.verification().version());
        assertEquals("CPU", result.verification().resources().getFirst().name());
    }

    @Test
    void preservesACompletedUnavailableResult() throws Exception {
        BeaglePreflightProcess.Result result = process(EmptyFactory.class)
                .run(request(Duration.ofSeconds(5)));

        assertFalse(result.available());
        assertEquals(BeaglePreflightProcess.Status.UNAVAILABLE, result.status());
        assertEquals(BeagleRuntimeVerifier.Status.NO_RESOURCES,
                result.verification().status());
    }

    @Test
    void containsAChildJvmCrash() throws Exception {
        BeaglePreflightProcess.Result result = process(CrashingFactory.class)
                .run(request(Duration.ofSeconds(5)));

        assertEquals(BeaglePreflightProcess.Status.CRASHED, result.status());
        assertEquals(23, result.exitCode());
        assertTrue(result.problem().contains("terminated the JVM"));
    }

    @Test
    void terminatesAPreflightThatExceedsItsTimeout() throws Exception {
        BeaglePreflightProcess.Result result = process(HangingFactory.class)
                .run(request(Duration.ofMillis(250)));

        assertEquals(BeaglePreflightProcess.Status.TIMED_OUT, result.status());
        assertTrue(result.problem().contains("exceeded"));
    }

    @Test
    void resolvesReusableBeagleJvmArgumentsAndEnvironment() throws Exception {
        BeagleRuntimeInstallation installation = installation();
        BeaglePreflightProcess.Request request = new BeaglePreflightProcess.Request(
                installation,
                javaExecutable(),
                System.getProperty("surefire.test.class.path"),
                List.of(
                        "-Xmx128m",
                        "-Djava.library.path=/existing/native"
                ),
                Map.of("BEAGLE_PLUGIN_PATH", "/custom/plugins"),
                Duration.ofSeconds(5)
        );

        assertTrue(request.resolvedJvmArguments().contains("-Xmx128m"));
        assertTrue(request.resolvedJvmArguments().contains(
                "--enable-native-access=ALL-UNNAMED"
        ));
        assertTrue(request.resolvedJvmArguments().stream().anyMatch(
                argument -> argument.startsWith(
                        "-Djava.library.path=" + installation.libraryDirectory()
                ) && argument.contains("/existing/native")
        ));
        assertEquals("/custom/plugins",
                request.resolvedEnvironment().get("BEAGLE_PLUGIN_PATH"));
    }

    private BeaglePreflightProcess process(Class<?> factory) {
        return new BeaglePreflightProcess(factory.getName());
    }

    private BeaglePreflightProcess.Request request(Duration timeout) throws Exception {
        return new BeaglePreflightProcess.Request(
                installation(),
                javaExecutable(),
                System.getProperty("surefire.test.class.path"),
                List.of(),
                Map.of(),
                timeout
        );
    }

    private Path javaExecutable() {
        String executable = System.getProperty("os.name", "")
                .toLowerCase()
                .contains("windows")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    private BeagleRuntimeInstallation installation() throws Exception {
        Path library = Files.createFile(temporaryDirectory.resolve("fake-jni"));
        return new BeagleRuntimeInstallation(temporaryDirectory, library);
    }

    static final class AvailableFactory {
        public static String getVersion() {
            return "4.0.1";
        }

        public static List<FakeResource> getResourceDetails() {
            return List.of(new FakeResource());
        }
    }

    static final class EmptyFactory {
        public static String getVersion() {
            return "4.0.1";
        }

        public static List<FakeResource> getResourceDetails() {
            return List.of();
        }
    }

    static final class CrashingFactory {
        public static String getVersion() {
            Runtime.getRuntime().halt(23);
            return "unreachable";
        }

        public static List<FakeResource> getResourceDetails() {
            return List.of();
        }
    }

    static final class HangingFactory {
        public static String getVersion() {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return "unreachable";
        }

        public static List<FakeResource> getResourceDetails() {
            return List.of();
        }
    }

    static final class FakeResource {
        public int getNumber() {
            return 0;
        }

        public String getName() {
            return "CPU";
        }

        public String getDescription() {
            return "Reference CPU implementation";
        }

        public long getFlags() {
            return 17L;
        }
    }
}
