package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;

/** Exercises the real CLI and PopFunc archives, with no test classes in the child JVM. */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX archive acceptance requires unzip")
class GompertzCliDistributionIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> PARAMETERS = List.of(
            "initialPopulationSize", "growthRate", "initialProportion", "ancestralPopulationSize");

    @TempDir
    Path temporary;

    @Test
    void packagedExampleChecksAndRunsWithFourEstimatedPopulationParameters() throws Exception {
        Path working = Files.createDirectory(temporary.resolve("independent working directory")).toRealPath();
        Path cliRoot = extract("phylospec.distribution", "CLI installation with spaces", working);
        Path adapter = extract("popfunc.distribution", "PopFunc installation with spaces", working);
        Path launcher = cliRoot.resolve("bin/phylospec");
        Path source = adapter.resolve("examples/gompertz.phylospec");
        Path data = adapter.resolve("examples/data/four-taxa.nex");
        assertTrue(Files.isExecutable(launcher), "Archive must preserve launcher executable permission");
        assertTrue(Files.isRegularFile(adapter.resolve("examples/README.md")));
        assertEquals(Files.readString(Path.of("examples/gompertz.phylospec")), Files.readString(source));
        assertEquals(hash(Path.of("examples/data/four-taxa.nex")), hash(data));
        Map<Path, String> before = hashes(adapter);

        Result checked = execute(working, List.of(
                launcher.toString(), "check", source.toString(),
                "--adapter-path", adapter.toString(), "--library", "popfunc"));
        assertEquals(0, checked.exitCode(), checked.details());
        assertTrue(checked.stdout().contains("Check passed"), checked.details());
        assertTrue(checked.stdout().contains("No MCMC was run."), checked.details());
        assertEquals(before, hashes(adapter), "check must leave the packaged example and bundle unchanged");
        try (var entries = Files.list(working)) {
            assertEquals(0, entries.count(), "check must not produce analysis files");
        }

        Result completed = execute(working, List.of(
                launcher.toString(), "run", source.toString(),
                "--adapter-path", adapter.toString(), "--library", "popfunc",
                "--out", "Gompertz results"));
        assertEquals(0, completed.exitCode(), completed.details());
        assertTrue(completed.stdout().contains("Run completed"), completed.details());
        Path run = working.resolve("Gompertz results");
        JsonNode record = JSON.readTree(run.resolve("run.json").toFile());
        assertEquals("succeeded", record.path("status").asText(), record.toPrettyString());
        assertTrue(record.path("completionConfirmed").asBoolean());
        assertEquals(30, record.path("settings").path("chainLength").asLong());
        assertEquals(1, record.path("settings").path("defaultLogEvery").asInt());
        assertEquals(12345, record.path("seeds").path("mcmc").path("observed").asLong());
        assertEquals(source.toString(), record.path("source").path("path").asText());
        assertEquals(source.getParent().toString(), record.path("source").path("inputBase").asText());
        assertEquals(hash(source), record.path("source").path("sha256").asText());
        assertEquals(Files.readString(source), Files.readString(run.resolve("model.phylospec")));
        assertEquals(1, record.path("inputs").size());
        JsonNode input = record.path("inputs").get(0);
        assertEquals(data.toString(), input.path("path").asText());
        assertEquals("recorded", input.path("status").asText());
        assertEquals(hash(data), input.path("sha256").asText());
        assertTrue(Files.size(run.resolve("console.log")) > 0);
        assertEquals(3, record.path("expectedOutputs").size());
        assertEquals(3, record.path("generatedOutputs").size());
        for (JsonNode output : record.path("expectedOutputs")) {
            assertTrue(Files.size(run.resolve(output.path("path").asText())) > 0, output.toPrettyString());
        }
        assertTrace(run.resolve("output/analysis.log"));
        String trees = Files.readString(run.resolve("output/analysis.trees"));
        assertEquals(31, trees.lines().filter(line -> line.strip().startsWith("tree STATE_")).count());
        assertTrue(trees.contains("tree STATE_30"));

        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var elements = factory.newDocumentBuilder()
                .parse(run.resolve("output/analysis.state.xml").toFile())
                .getElementsByTagName("*");
        var stateIds = new HashSet<String>();
        for (int index = 0; index < elements.getLength(); index++) {
            var id = elements.item(index).getAttributes().getNamedItem("id");
            if (id != null) stateIds.add(id.getNodeValue());
        }
        assertTrue(stateIds.containsAll(PARAMETERS), stateIds.toString());
        assertTrue(stateIds.contains("tree"), stateIds.toString());

        assertEquals(2, record.path("libraries").size());
        var libraryIds = new HashSet<String>();
        for (JsonNode library : record.path("libraries")) {
            String id = library.path("id").asText();
            libraryIds.add(id);
            Path location = Path.of(URI.create(library.path("location").asText()));
            assertEquals(id.equals("popfunc") ? adapter : cliRoot.resolve("lib"), location.getParent());
            assertTrue(Files.isRegularFile(location) && location.toString().endsWith(".jar"));
        }
        assertEquals(new HashSet<>(List.of("popfunc", "beast2")), libraryIds);
        assertEquals(1, record.path("runtime").path("adapterBundles").size());
        JsonNode bundle = record.path("runtime").path("adapterBundles").get(0);
        assertEquals(adapter.toString(), bundle.path("path").asText());
        assertFalse(bundle.path("jars").isEmpty());
        for (JsonNode jar : bundle.path("jars")) {
            assertEquals(hash(Path.of(jar.path("path").asText())), jar.path("sha256").asText());
        }
        assertEquals(before, hashes(adapter), "run must write only to the selected results directory");
    }

    private static void assertTrace(Path trace) throws Exception {
        List<String> lines = Files.readAllLines(trace).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        List<String> columns = Arrays.asList(lines.getFirst().split("\\s+"));
        assertTrue(columns.containsAll(PARAMETERS), columns.toString());
        assertTrue(columns.containsAll(List.of("posterior", "prior", "likelihood")), columns.toString());
        assertEquals(32, lines.size(), "Header and samples 0 through 30 must be present");
        for (int sample = 0; sample <= 30; sample++) {
            String[] values = lines.get(sample + 1).split("\\s+");
            assertEquals(columns.size(), values.length);
            assertEquals(sample, Long.parseLong(values[0]));
            for (String value : values) assertTrue(Double.isFinite(Double.parseDouble(value)), lines.get(sample + 1));
            assertPopulationFunction(columns, values);
        }
        // BEAST logs sample 0 after its first proposal, so it is not an untouched initial state.
        // Exact script defaults are checked before proposals in GompertzStateTest.
        // A 30-step stochastic chain need not accept an update to every parameter. State and
        // operator membership, plus each parameter's accept/reject lifecycle, are tested separately.
    }

    private static void assertPopulationFunction(List<String> columns, String[] values) {
        double initialSize = Double.parseDouble(values[columns.indexOf("initialPopulationSize")]);
        GompertzGrowth_f0 population = new GompertzGrowth_f0();
        population.initByName(
                "N0", new RealScalarParam<>(initialSize, PositiveReal.INSTANCE),
                "b", new RealScalarParam<>(
                        Double.parseDouble(values[columns.indexOf("growthRate")]), PositiveReal.INSTANCE),
                "f0", new RealScalarParam<>(
                        Double.parseDouble(values[columns.indexOf("initialProportion")]), UnitInterval.INSTANCE),
                "NA", new RealScalarParam<>(
                        Double.parseDouble(values[columns.indexOf("ancestralPopulationSize")]), PositiveReal.INSTANCE),
                "I_na", new IntScalarParam<>(1, NonNegativeInt.INSTANCE));
        for (double time : new double[] {0.0, 0.5, 2.0, 5.0}) {
            double size = population.getPopSize(time);
            assertTrue(Double.isFinite(size) && size > 0.0,
                    "Sample " + values[0] + " must define a positive finite population at time " + time);
        }
        assertEquals(initialSize, population.getPopSize(0.0), Math.max(1e-12, initialSize * 1e-10));
    }

    private Path extract(String property, String directory, Path working) throws Exception {
        String configured = System.getProperty(property);
        assertNotNull(configured, "cli-dist must supply " + property);
        Path archive = Path.of(configured).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(archive), "Missing distribution: " + archive);
        Path destination = Files.createDirectory(temporary.resolve(directory)).toRealPath();
        Result result = execute(working, List.of("unzip", "-q", archive.toString(), "-d", destination.toString()));
        assertEquals(0, result.exitCode(), result.details());
        try (var entries = Files.list(destination)) {
            List<Path> roots = entries.toList();
            assertEquals(1, roots.size(), "Archive must have one installation directory");
            return roots.getFirst();
        }
    }

    private Result execute(Path working, List<String> command) throws Exception {
        Path stdout = Files.createTempFile(temporary, "stdout-", ".txt");
        Path stderr = Files.createTempFile(temporary, "stderr-", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(working.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        for (String name : List.of("CLASSPATH", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) {
            builder.environment().remove(name);
        }
        Process child = builder.start();
        try {
            assertTrue(child.waitFor(90, TimeUnit.SECONDS), "Timed out: " + String.join(" ", command));
            return new Result(child.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static Map<Path, String> hashes(Path directory) throws Exception {
        Map<Path, String> hashes = new TreeMap<>();
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                hashes.put(directory.relativize(file), hash(file));
            }
        }
        return hashes;
    }

    private static String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private record Result(int exitCode, String stdout, String stderr) {
        String details() {
            return "exit=" + exitCode + "\nstdout:\n" + stdout + "\nstderr:\n" + stderr;
        }
    }
}
