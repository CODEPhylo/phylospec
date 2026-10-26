package runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Runs the packaged POSIX launcher, requiring unzip to preserve and exercise archive permissions. */
@DisabledOnOs(
        value = OS.WINDOWS,
        disabledReason = "POSIX archive acceptance; Windows launcher needs Windows verification")
class CliDistributionIT {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    private Path installation;
    private Path workingDirectory;
    private Path processLogs;

    @BeforeEach
    void extractDistributionOutsideTheRepository() throws Exception {
        String configured = System.getProperty("phylospec.distribution");
        assertTrue(configured != null && !configured.isBlank(), "cli-dist must supply phylospec.distribution");
        Path archive = Path.of(configured).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(archive), "Distribution ZIP is missing: " + archive);
        Path extraction = Files.createDirectory(temporary.resolve("installation with spaces"))
                .toRealPath();
        workingDirectory = Files.createDirectory(temporary.resolve("independent working directory"))
                .toRealPath();
        processLogs = Files.createDirectory(temporary.resolve("process logs"));
        Result extracted = execute(List.of("unzip", "-q", archive.toString(), "-d", extraction.toString()));
        assertEquals(0, extracted.exitCode(), extracted.details());
        try (var entries = Files.list(extraction)) {
            List<Path> roots = entries.toList();
            assertEquals(1, roots.size(), "Archive should contain one installation directory");
            installation = roots.getFirst();
        }
        assertTrue(
                Files.isExecutable(installation.resolve("bin/phylospec")),
                "ZIP must preserve launcher executable mode");
    }

    @Test
    void extractedPackageOffersHelpVersionAndChecksWithoutProducingResults() throws Exception {
        for (String entry : List.of(
                "bin/phylospec.cmd",
                "USAGE.md",
                "LICENSE",
                "examples/normal.phylospec",
                "examples/jc69-coalescent.phylospec",
                "examples/hky-yule.phylospec",
                "examples/data/tiny.nex",
                "examples/data/primate-mtDNA.nex")) {
            assertTrue(Files.isRegularFile(installation.resolve(entry)), "Missing packaged file: " + entry);
        }
        try (var jars = Files.list(installation.resolve("lib"))) {
            assertTrue(jars.anyMatch(path -> path.toString().endsWith(".jar")), "Runtime JARs must be packaged");
        }
        List<Path> before = fileNames(installation);
        Result help = launch("--help");
        assertEquals(0, help.exitCode(), help.details());
        assertTrue(help.stdout().contains("check"), help.details());
        assertTrue(help.stdout().contains("run"), help.details());
        Result version = launch("--version");
        assertEquals(0, version.exitCode(), version.details());
        assertTrue(version.stdout().contains("PhyloSpec BEAST 3 integration "), version.details());
        assertTrue(version.stdout().contains("BEAST runtime "), version.details());
        assertFalse(version.stdout().contains("unavailable"), version.details());
        assertFalse(version.stdout().contains("${"), version.details());
        for (String example : List.of("normal", "jc69-coalescent", "hky-yule")) {
            Result check = launch(
                    "check",
                    installation.resolve("examples/" + example + ".phylospec").toString());
            assertEquals(0, check.exitCode(), check.details());
            assertTrue(check.stdout().contains("Check passed"), check.details());
            assertTrue(check.stdout().contains("No MCMC was run."), check.details());
        }
        assertEquals(before, fileNames(installation), "check must not create analysis files beside the examples");
        assertTrue(
                fileNames(workingDirectory).isEmpty(),
                "check must not create analysis files in the invocation directory");
    }

    @Test
    void packagedHkyYuleExampleRecordsSampledStateAndInputProvenance() throws Exception {
        Path source = installation.resolve("examples/hky-yule.phylospec");
        Path alignment = installation.resolve("examples/data/primate-mtDNA.nex");
        assertEquals(
                Files.readString(Path.of("src/main/distribution/examples/hky-yule.phylospec")),
                Files.readString(source));
        assertEquals(hash(Path.of("src/main/distribution/examples/data/primate-mtDNA.nex")), hash(alignment));
        Map<Path, String> before = hashes(source.getParent());
        Path run = workingDirectory.resolve("HKY Yule results");
        Result completed =
                launch("run", source.toString(), "--out", run.getFileName().toString());
        assertEquals(0, completed.exitCode(), completed.details());
        assertTrue(completed.stdout().contains("Run completed"), completed.details());
        assertTrue(completed.stdout().contains("[3/4] Running MCMC (chain length: 30)"), completed.details());
        assertTrue(completed.stdout().contains("[4/4] Checking result files and final state"), completed.details());
        for (String filename : List.of("hky-yule.log", "hky-yule.trees", "hky-yule.state.xml")) {
            assertTrue(
                    completed
                            .stdout()
                            .contains(run.resolve("output").resolve(filename).toString()),
                    completed.details());
        }
        String console = Files.readString(run.resolve("console.log"));
        assertFalse(completed.stderr().contains("BEASTObjectStore found non-BEASTObject:"), completed.details());
        assertFalse(completed.stderr().contains("Failed to load BEAGLE library:"), completed.details());
        assertFalse(
                completed.stderr().contains("A restricted method in java.lang.System has been called"),
                completed.details());
        if (console.contains("Failed to load BEAGLE library:")) {
            assertTrue(completed.stderr().contains("optional BEAGLE"), completed.details());
        }
        JsonNode record = assertSuccessfulRun(run, source);
        assertEquals(1, record.path("settings").path("defaultLogEvery").asInt());
        assertEquals(12345, record.path("settings").path("randomSeed").asLong());
        assertEquals(12345, record.path("seeds").path("mcmc").path("observed").asLong());
        assertEquals(
                "output/hky-yule", record.path("settings").path("outputPrefix").asText());
        assertEquals(source.toString(), record.path("source").path("path").asText());
        assertEquals(1, record.path("inputs").size());
        JsonNode input = record.path("inputs").get(0);
        assertEquals(alignment.toString(), input.path("path").asText());
        assertEquals("recorded", input.path("status").asText());
        assertEquals(hash(alignment), input.path("sha256").asText());
        assertEquals(3, record.path("expectedOutputs").size());
        assertEquals(3, record.path("generatedOutputs").size());
        assertEquals(1, record.path("libraries").size());
        assertEquals("beast2", record.path("libraries").get(0).path("id").asText());

        assertHkyTrace(run.resolve("output/hky-yule.log"));
        String trees = Files.readString(run.resolve("output/hky-yule.trees"));
        assertEquals(
                31,
                trees.lines()
                        .filter(line -> line.strip().startsWith("tree STATE_"))
                        .count());
        assertTrue(trees.contains("tree STATE_30"));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var state = factory.newDocumentBuilder()
                .parse(run.resolve("output/hky-yule.state.xml").toFile());
        assertEquals("30", state.getDocumentElement().getAttribute("sample"));
        var elements = state.getElementsByTagName("*");
        Set<String> stateIds = new HashSet<>();
        for (int index = 0; index < elements.getLength(); index++) {
            var id = elements.item(index).getAttributes().getNamedItem("id");
            if (id != null) stateIds.add(id.getNodeValue());
        }
        assertEquals(Set.of("tree", "kappa", "baseFrequencies"), stateIds);
        assertEquals(before, hashes(source.getParent()), "run must leave the packaged examples unchanged");
    }

    @Test
    void packagedPhylogeneticExampleRunsAndCannotOverwriteAnExistingResult() throws Exception {
        Path source = installation.resolve("examples/jc69-coalescent.phylospec");
        Path run = workingDirectory.resolve("results with spaces");
        Result completed =
                launch("run", source.toString(), "--out", run.getFileName().toString());
        assertEquals(0, completed.exitCode(), completed.details());
        assertTrue(completed.stdout().contains("Run completed"), completed.details());
        JsonNode record = assertSuccessfulRun(run, source);
        assertEquals(1, record.path("inputs").size());
        JsonNode input = record.path("inputs").get(0);
        Path alignment = installation.resolve("examples/data/tiny.nex");
        assertEquals(alignment.toString(), input.path("path").asText());
        assertEquals("recorded", input.path("status").asText());
        assertEquals(hash(alignment), input.path("sha256").asText());
        assertEquals(3, record.path("expectedOutputs").size());
        assertFalse(
                Files.exists(installation.resolve("examples/runs")), "--out selects the invocation-relative run root");

        Map<Path, String> previousFiles = hashes(run);
        Result repeated =
                launch("run", source.toString(), "--out", run.getFileName().toString());
        assertNotEquals(0, repeated.exitCode(), repeated.details());
        assertFalse(repeated.stdout().contains("Run completed"), repeated.details());
        assertFalse(repeated.stderr().isBlank(), repeated.details());
        assertEquals(previousFiles, hashes(run), "Refusing an existing run must preserve every result file");
    }

    @Test
    void defaultRunDirectoryIsBesideThePackagedScript() throws Exception {
        Path source = installation.resolve("examples/normal.phylospec");
        Result completed = launch("run", source.toString());
        assertEquals(0, completed.exitCode(), completed.details());
        Path runs = source.getParent().resolve("runs");
        assertTrue(Files.isDirectory(runs), completed.details());
        try (var entries = Files.list(runs)) {
            List<Path> results = entries.toList();
            assertEquals(1, results.size());
            assertTrue(results.getFirst().getFileName().toString().startsWith("normal-"));
            assertSuccessfulRun(results.getFirst(), source);
        }
        assertTrue(fileNames(workingDirectory).isEmpty(), "Default results belong beside the source, not the caller");
    }

    @Test
    void invalidScriptAndMissingDataHaveNonzeroExitCodesAndTruthfulRecords() throws Exception {
        Path invalid = temporary.resolve("invalid script.phylospec");
        Files.writeString(invalid, "Integer value = \"wrong\"\n");
        Path invalidRun = workingDirectory.resolve("invalid result");
        Result rejected = launch("run", invalid.toString(), "--out", invalidRun.toString());
        assertNotEquals(0, rejected.exitCode(), rejected.details());
        assertTrue(rejected.stderr().contains("TYPE_CHECKING"), rejected.details());
        assertFalse(rejected.stdout().contains("Run completed"), rejected.details());
        assertFalse(Files.exists(invalidRun), "Static failures must not create a run directory");

        Path missingSource = temporary.resolve("missing data.phylospec");
        String model = Files.readString(installation.resolve("examples/jc69-coalescent.phylospec"));
        assertTrue(model.contains("data/tiny.nex"), "Example must exercise a script-relative data input");
        Files.writeString(missingSource, model.replace("data/tiny.nex", "data/missing.nex"));
        Path missingRun = workingDirectory.resolve("missing data result");
        Result missing = launch("run", missingSource.toString(), "--out", missingRun.toString());
        assertNotEquals(0, missing.exitCode(), missing.details());
        assertFalse(missing.stdout().contains("Run completed"), missing.details());
        assertFalse(missing.stderr().isBlank(), missing.details());
        JsonNode record = JSON.readTree(missingRun.resolve("run.json").toFile());
        assertEquals("failed", record.path("status").asText());
        assertTrue(record.path("completionConfirmed").asBoolean());
        assertEquals("unavailable", record.path("inputs").get(0).path("status").asText());
        assertEquals(0, record.path("generatedOutputs").size());
        assertTrue(Files.size(missingRun.resolve("console.log")) > 0);
    }

    private JsonNode assertSuccessfulRun(Path run, Path source) throws Exception {
        JsonNode record = JSON.readTree(run.resolve("run.json").toFile());
        assertEquals("succeeded", record.path("status").asText(), record.toPrettyString());
        assertTrue(record.path("completionConfirmed").asBoolean());
        assertEquals(30, record.path("settings").path("chainLength").asLong());
        assertEquals(
                source.getParent().toString(),
                record.path("source").path("inputBase").asText());
        assertEquals(hash(source), record.path("source").path("sha256").asText());
        assertEquals(Files.readString(source), Files.readString(run.resolve("model.phylospec")));
        assertTrue(Files.size(run.resolve("console.log")) > 0);
        assertFalse(record.path("libraries").isEmpty(), "A real packaged provider must be selected");
        for (JsonNode library : record.path("libraries")) {
            Path location = Path.of(URI.create(library.path("location").asText()));
            assertEquals(installation.resolve("lib"), location.getParent(), library.toPrettyString());
            assertTrue(Files.isRegularFile(location), library.toPrettyString());
            assertTrue(location.toString().endsWith(".jar"), library.toPrettyString());
        }
        assertFalse(record.path("expectedOutputs").isEmpty());
        for (JsonNode expected : record.path("expectedOutputs")) {
            Path output = run.resolve(expected.path("path").asText());
            assertTrue(Files.isRegularFile(output) && Files.size(output) > 0, "Missing result: " + output);
            if (expected.path("kind").asText().equals("log")) assertFiniteTrace(output);
        }
        return record;
    }

    private static void assertFiniteTrace(Path trace) throws Exception {
        int samples = 0;
        long lastSample = -1;
        for (String line : Files.readAllLines(trace)) {
            String[] fields = line.trim().split("\\s+");
            if (!fields[0].matches("[0-9]+")) continue;
            samples++;
            lastSample = Long.parseLong(fields[0]);
            for (String field : fields) {
                assertTrue(Double.isFinite(Double.parseDouble(field)), "Nonfinite value in " + trace + ": " + line);
            }
        }
        assertTrue(samples >= 2, "Expected multiple logged samples in " + trace);
        assertEquals(30, lastSample, "The short chain must reach its requested last sample");
    }

    private static void assertHkyTrace(Path trace) throws Exception {
        List<String> lines = Files.readAllLines(trace).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        List<String> columns = Arrays.asList(lines.getFirst().split("\\s+"));
        assertTrue(
                columns.containsAll(List.of(
                        "posterior",
                        "prior",
                        "likelihood",
                        "kappa",
                        "tree.height",
                        "tree.treeLength",
                        "baseFrequencies.1",
                        "baseFrequencies.2",
                        "baseFrequencies.3",
                        "baseFrequencies.4")),
                columns.toString());
        assertEquals(32, lines.size(), "Header and samples 0 through 30 must be present");
        for (int sample = 0; sample <= 30; sample++) {
            String[] values = lines.get(sample + 1).split("\\s+");
            assertEquals(columns.size(), values.length);
            assertEquals(sample, Long.parseLong(values[0]));
            assertEquals(
                    Double.parseDouble(values[columns.indexOf("prior")])
                            + Double.parseDouble(values[columns.indexOf("likelihood")]),
                    Double.parseDouble(values[columns.indexOf("posterior")]),
                    1e-8,
                    "Posterior must equal prior plus likelihood at sample " + sample);
            assertTrue(Double.parseDouble(values[columns.indexOf("kappa")]) > 0.0);
            assertTrue(Double.parseDouble(values[columns.indexOf("tree.height")]) > 0.0);
            assertTrue(Double.parseDouble(values[columns.indexOf("tree.treeLength")]) > 0.0);
            double total = 0.0;
            for (int base = 1; base <= 4; base++) {
                double frequency = Double.parseDouble(values[columns.indexOf("baseFrequencies." + base)]);
                assertTrue(frequency >= 0.0 && frequency <= 1.0);
                total += frequency;
            }
            assertEquals(1.0, total, 1e-12, "Base frequencies must remain a simplex at sample " + sample);
        }
        // Sample 0 is logged after BEAST's first proposal. A short chain need not accept a
        // change to every parameter; HkyYuleStateTest checks operator wiring and each parameter's
        // accept/reject lifecycle independently of random operator selection.
    }

    private Result launch(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(installation.resolve("bin/phylospec").toString());
        command.addAll(List.of(arguments));
        return execute(command);
    }

    private Result execute(List<String> command) throws Exception {
        Path stdout = Files.createTempFile(processLogs, "stdout-", ".txt");
        Path stderr = Files.createTempFile(processLogs, "stderr-", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
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

    private static List<Path> fileNames(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
    }

    private static Map<Path, String> hashes(Path root) throws Exception {
        Map<Path, String> result = new TreeMap<>();
        for (Path relative : fileNames(root)) result.put(relative, hash(root.resolve(relative)));
        return result;
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
