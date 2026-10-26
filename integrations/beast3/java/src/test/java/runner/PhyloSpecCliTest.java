package runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PhyloSpecCliTest {

    @Test
    public void displaysHelpWithoutRequiringAModel() {
        assertDoesNotThrow(() -> PhyloSpecCli.execute(new String[] {"--help"}));
    }

    @Test
    public void requiresSourceFile() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> PhyloSpecCli.execute(new String[] {}));

        assertEquals("A PhyloSpec source file is required.", exception.getMessage());
    }

    @Test
    public void requiresTileLibrary(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, "");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PhyloSpecCli.execute(new String[] {source.toString()}));

        assertEquals("At least one --library must be supplied.", exception.getMessage());
    }

    @Test
    public void rejectsUnknownOption() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> PhyloSpecCli.execute(new String[] {"--unknown"}));

        assertEquals("Unknown option: --unknown", exception.getMessage());
    }

    @Test
    public void reportsRunnerDiagnosticsAtTheProcessBoundary(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("invalid.phylospec");
        Files.writeString(source, "Real x ~ Uniform(lower=2.0, upper=1.0)\nInteger y = \"wrong\"\n");
        CliResult result = launch(directory, "--library", "beast2", source.toString());
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("TYPE_CHECKING"), result.stderr());
        assertTrue(result.stderr().contains("Please note:"), result.stderr());
        assertTrue(result.stderr().contains(source.toString()), result.stderr());
        assertFalse(result.stderr().contains("Exception in thread"), result.stderr());
        assertFalse(result.stderr().contains("Usage:"), result.stderr());
        assertFalse(result.stdout().contains("Please note:"));
    }

    private record CliResult(int exitCode, String stdout, String stderr) {}

    private static CliResult launch(Path directory, String... arguments) throws Exception {
        Path stdout = Files.createTempFile(directory, "cli-stdout-", ".txt");
        Path stderr = Files.createTempFile(directory, "cli-stderr-", ".txt");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(List.of("-Djava.only=true", "-cp", classpath, "runner.PhyloSpecCli"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "CLI timed out: " + String.join(" ", arguments));
            return new CliResult(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}
