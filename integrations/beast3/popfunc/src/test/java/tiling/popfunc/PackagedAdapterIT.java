package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PackagedAdapterIT {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsUnsupportedConsExpConsConfigurationWithoutStartingMcmc(
            boolean sampled, @TempDir Path directory) throws Exception {
        String source = """
                use popfunc.functions.coalescent
                %s
                PopulationFunction population = constantExponentialConstantPopulationFunction(
                    recentPopulationSize=1000.0, growthRate=%s, growthStartAge=2.0, growthEndAge=%s)
                """.formatted(sampled ? "PositiveReal rate ~ LogNormal(logMean=0.0, logSd=1.0)" : "",
                        sampled ? "rate" : "0.2", sampled ? "10.0" : "1.0");
        assertRejected(source, sampled ? "expects a constant for 'growthRate'"
                : "0 <= growthStartAge <= growthEndAge", directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"recentPopulationSize", "growthRate"})
    void rejectsOverflowedPositiveInputsWithoutStartingMcmc(String argument, @TempDir Path directory) throws Exception {
        String source = """
                use popfunc.functions.coalescent
                PopulationFunction population = constantExponentialConstantPopulationFunction(
                    recentPopulationSize=1000.0, growthRate=0.2, growthStartAge=2.0, growthEndAge=10.0)
                """.replaceAll(argument + "=[0-9.]+", argument + "=" + "9".repeat(400) + ".0");
        assertRejected(source, "finite, positive " + argument, directory);
    }

    private static void assertRejected(String content, String message, Path directory) throws Exception {
        Path adapter = Path.of(System.getProperty("phylospec.adapterDirectory")).toAbsolutePath();
        assertTrue(Files.isDirectory(adapter), "Missing packaged adapter: " + adapter);
        Path source = directory.resolve("analysis.phylospec");
        Files.writeString(source, content);
        Path output = directory.resolve("runner-output.txt");
        Process process = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Djava.only=true", "-cp", adapter.resolve("*").toString(),
                        "runner.PhyloSpecCli", "--library", "popfunc,beast2", source.toString())
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean finished;
        try {
            finished = process.waitFor(10, TimeUnit.SECONDS);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        String console = Files.readString(output);
        assertTrue(finished, "Invalid input must fail promptly, not loop in native initialization");
        assertNotEquals(0, process.exitValue(), console);
        assertTrue(console.contains(message), console);
        assertFalse(console.contains("End likelihood:"), console);
        assertFalse(Files.exists(directory.resolve("analysis.log")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void runsModelSelectionUsingOnlyPackagedJars(boolean ancestral, @TempDir Path directory) throws Exception {
        Path adapter = Path.of(System.getProperty("phylospec.adapterDirectory"))
                .toAbsolutePath();
        assertTrue(Files.isDirectory(adapter), "Missing packaged adapter: " + adapter);
        Path source = directory.resolve("analysis.phylospec");
        if (ancestral) {
            Files.copy(Path.of("examples/four-taxa.nex"), directory.resolve("four-taxa.nex"));
            Files.writeString(source, Files.readString(Path.of("examples/ancestral-selection.phylospec"))
                    .replace("chainLength = 30", "chainLength = 20"));
        } else {
            Files.copy(Path.of("../java/src/test/java/resources/primate-mtDNA.nex"), directory.resolve("data.nex"));
            Files.writeString(source, PopFuncWorkflowTest.modelSelectionSource(Path.of("data.nex"), 20));
        }
        Path output = directory.resolve("runner-output.txt");
        Path prefix = directory.resolve("analysis");

        // Do not inherit Maven's test classpath: discovery and execution must work from the bundle.
        Process process = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Djava.only=true", "-cp", adapter.resolve("*").toString(),
                        "runner.PhyloSpecCli", "--library", "popfunc,beast2",
                        "--run-name", prefix.toString(), source.toString())
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        boolean finished;
        try {
            finished = process.waitFor(60, TimeUnit.SECONDS);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        String console = Files.readString(output);
        assertTrue(finished, "Packaged runner timed out:\n" + console);
        assertEquals(0, process.exitValue(), console);
        assertTrue(console.contains("ModelIndicatorOperator"), console);

        List<String> trace = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(22, trace.size(), "Expected a header and samples 0 through 20");
        List<String> columns = Arrays.asList(trace.getFirst().strip().split("\\s+"));
        int posterior = columns.indexOf("posterior");
        int indicator = columns.indexOf(ancestral ? "I_na" : "modelIndex");
        assertTrue(posterior >= 0 && indicator >= 0, trace.getFirst());
        if (ancestral) {
            assertTrue(columns.containsAll(List.of("N0", "b", "f0", "NA")), trace.getFirst());
        }
        for (int i = 1; i < trace.size(); i++) {
            String[] values = trace.get(i).strip().split("\\s+");
            assertEquals(i - 1, Integer.parseInt(values[0]));
            assertTrue(Double.isFinite(Double.parseDouble(values[posterior])), trace.get(i));
            int model = Integer.parseInt(values[indicator]);
            assertTrue(model == 0 || model == 1, trace.get(i));
        }
        assertTrue(Files.readString(Path.of(prefix + ".trees")).contains("#NEXUS"));
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }
}
