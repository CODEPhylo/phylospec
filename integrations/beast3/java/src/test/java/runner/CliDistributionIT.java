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
import javax.xml.xpath.XPathFactory;
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
    private static final boolean BUNDLED_BEAGLE = Boolean.getBoolean("phylospec.test.beagle");

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
        Result extracted = execute(List.of("unzip", "-q", archive.toString(), "-d", extraction.toString()), Map.of());
        assertEquals(0, extracted.exitCode(), extracted.details());
        try (var entries = Files.list(extraction)) {
            List<Path> roots = entries.toList();
            assertEquals(1, roots.size(), "Archive should contain one installation directory");
            installation = roots.getFirst();
        }
        if (BUNDLED_BEAGLE) {
            // Remove the original location before launching: no baked-in build/extraction path may work.
            installation = Files.move(installation, temporary.resolve("moved installation with spaces"))
                    .toRealPath();
            for (String file : List.of(
                    "libhmsbeagle-jni.jnilib",
                    "libhmsbeagle.1.dylib",
                    "libhmsbeagle-cpu.40.so",
                    "BEAGLE-LICENSE.txt",
                    "beagle-build.properties")) {
                assertTrue(
                        Files.isRegularFile(
                                installation.resolve("native/macos-arm64").resolve(file)),
                        file);
            }
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
    void enginesHaveSeparateRuntimeJars() throws Exception {
        try (var jars = Files.list(installation.resolve("lib"))) {
            assertFalse(jars.map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.startsWith("phylospec-beastx-") || name.startsWith("beast-mcmc-")));
        }
        try (var jars = Files.list(installation.resolve("engines/beastx/lib"))) {
            assertFalse(jars.map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.startsWith("phylospec-beast3-")
                            || name.startsWith("beast-base-")
                            || name.startsWith("beast-pkgmgmt-")));
        }
    }

    @Test
    void packagedHkyYuleChecksBothEngines() throws Exception {
        String script = installation.resolve("examples/hky-yule.phylospec").toString();
        List<Path> before = fileNames(installation);
        assertSuccessfulCheck(launch("check", script), "beast3", "beast2");
        assertSuccessfulCheck(launch("check", "--engine", "beast3", script), "beast3", "beast2");
        assertSuccessfulCheck(launch("check", script, "--engine", "beastx"), "beastx", "beastX");
        assertEquals(before, fileNames(installation));
        assertTrue(fileNames(workingDirectory).isEmpty(), "check must not write analysis files");
    }

    @Test
    void packagedBeast3ExportsLoadableHkyYule() throws Exception {
        Path source = installation.resolve("examples/hky-yule.phylospec");
        Path xml = workingDirectory.resolve("hky-yule-beast3.xml");

        // export reloads the XML with BEAST and fails if any class in it is not registered
        Result result = launch("export", source.toString(), "--xml", xml.toString());
        assertEquals(0, result.exitCode(), result.details());

        String content = Files.readString(xml);
        assertTrue(content.contains("spec=\"tiling.LiveYuleModel\""), result.details());
        assertTrue(content.contains("spec=\"tiling.LiveDirichletDistribution\""), result.details());
    }

    private static void assertSuccessfulCheck(Result result, String engine, String library) {
        assertEquals(0, result.exitCode(), result.details());
        assertTrue(
                result.stdout().contains("Check passed (engine: " + engine + "; libraries: " + library + ")"),
                result.details());
        assertTrue(result.stdout().contains("No MCMC was run."), result.details());
    }

    @Test
    void packagedBeastXRunsHkyYuleFromSavedXmlWithTheScriptSeed() throws Exception {
        Path source = installation.resolve("examples/hky-yule.phylospec");
        Path alignment = source.getParent().resolve("data/primate-mtDNA.nex");
        Map<Path, String> inputsBefore = hashes(source.getParent());
        Path run = workingDirectory.resolve("BEAST X results");
        Result first = launch(
                beastXEnvironment(),
                "run",
                "--engine",
                "beastx",
                source.toString(),
                "--out",
                run.getFileName().toString());
        assertEquals(0, first.exitCode(), first.details());
        assertTrue(first.stdout().contains("Run completed (engine: beastx"), first.details());

        JsonNode record = JSON.readTree(run.resolve("run.json").toFile());
        String details = record.toPrettyString();
        assertEquals("succeeded", record.path("status").asText(), details);
        assertTrue(record.path("completionConfirmed").asBoolean(), details);
        assertEquals("beastx", record.at("/runtime/engine").asText(), details);
        assertEquals(30, record.at("/settings/chainLength").asLong(), details);
        assertEquals(30, record.path("completedChainLength").asLong(), details);
        assertEquals(1, record.at("/settings/defaultLogEvery").asLong(), details);
        for (String phase : List.of("modelBuilding", "mcmc")) {
            assertEquals(12345L, record.at("/seeds/" + phase + "/requested").asLong(), details);
            assertEquals(12345L, record.at("/seeds/" + phase + "/observed").asLong(), details);
        }
        assertEquals(hash(source), record.at("/source/sha256").asText(), details);
        assertEquals(-1L, Files.mismatch(source, run.resolve("model.phylospec")));
        assertEquals(alignment.toString(), record.at("/inputs/0/path").asText(), details);
        assertEquals(hash(alignment), record.at("/inputs/0/sha256").asText(), details);
        assertTrue(Files.size(run.resolve("console.log")) > 0);

        Path xml = run.resolve("analysis.xml");
        assertEquals("analysis.xml", record.at("/xml/path").asText(), details);
        assertEquals(hash(xml), record.at("/xml/sha256").asText(), details);
        Path exported = temporary.resolve("comparison/hky-yule.xml");
        Result export = launch(
                beastXEnvironment(), "export", "--engine", "beastx", source.toString(), "--xml", exported.toString());
        assertEquals(0, export.exitCode(), export.details());
        assertEquals(-1L, Files.mismatch(xml, exported), "HKY + Yule must use the same XML for export and run");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        assertEquals("30", xpath.evaluate("/beast/mcmc/@chainLength", document));
        assertEquals("12", xpath.evaluate("count(/beast/alignment/sequence)", document));
        assertEquals("hky-yule.log", xpath.evaluate("/beast/mcmc/log[@fileName]/@fileName", document));
        assertEquals("hky-yule.trees", xpath.evaluate("/beast/mcmc/logTree/@fileName", document));

        Set<String> outputs = Set.of("output/hky-yule.log", "output/hky-yule.trees");
        for (String key : List.of("expectedOutputs", "generatedOutputs")) {
            Set<String> recorded = new HashSet<>();
            record.path(key).forEach(output -> recorded.add(output.path("path").asText()));
            assertEquals(outputs, recorded, details);
        }
        Path trace = run.resolve("output/hky-yule.log");
        assertFiniteTrace(trace);
        assertTrue(Files.readString(trace).contains("[seed=12345]"), "BEAST X must report the effective seed");
        assertHkyTrace(trace, "joint", "baseFrequencies");
        String trees = Files.readString(run.resolve("output/hky-yule.trees"));
        assertEquals(
                31,
                trees.lines()
                        .filter(line -> line.strip().startsWith("tree STATE_"))
                        .count());
        assertTrue(trees.contains("tree STATE_30 "), trees);

        Path repeated = workingDirectory.resolve("same seed");
        Result second = launch(
                beastXEnvironment(), "run", source.toString(), "--engine", "beastx", "--out", repeated.toString());
        assertEquals(0, second.exitCode(), second.details());
        assertEquals(
                beastXTraceSamples(trace),
                beastXTraceSamples(repeated.resolve("output/hky-yule.log")),
                "Independent processes using the same seed must reproduce the numeric trace");

        Map<Path, String> previous = hashes(run);
        Result collision =
                launch(beastXEnvironment(), "run", "--engine", "beastx", source.toString(), "--out", run.toString());
        assertNotEquals(0, collision.exitCode(), collision.details());
        assertEquals(previous, hashes(run), "A second run must preserve all previous results");
        try (var children = Files.list(workingDirectory)) {
            assertEquals(Set.of(run, repeated), Set.copyOf(children.toList()));
        }
        assertEquals(inputsBefore, hashes(source.getParent()), "Run must preserve the source and inputs");
    }

    @Test
    void managedBeastXRunKeepsLoggersInsideTheOutputDirectory() throws Exception {
        Path source = temporary.resolve("escaping-prefix.phylospec");
        Files.writeString(source, """
                Real x ~ Normal(mean=0.0, sd=1.0)
                mcmc {
                    Integer chainLength = 2
                    String outputPrefix = "../../sentry"
                }
                """);
        Path sentry = workingDirectory.resolve("sentry.log");
        Files.writeString(sentry, "existing trace");
        Path run = workingDirectory.resolve("unsafe result");
        Result rejected =
                launch(beastXEnvironment(), "run", "--engine", "beastx", source.toString(), "--out", run.toString());
        assertNotEquals(0, rejected.exitCode(), rejected.details());
        assertEquals("existing trace", Files.readString(sentry));
        assertFalse(Files.exists(run.resolve("output/sentry.log")));
        var record = JSON.readTree(run.resolve("run.json").toFile());
        assertEquals("failed", record.path("status").asText(), record.toPrettyString());
        assertFalse(record.has("mcmcStartedAt"), "A rejected output path must prevent native MCMC from starting");
    }

    private static List<String> beastXTraceSamples(Path trace) throws Exception {
        return Files.readAllLines(trace).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && Character.isDigit(line.charAt(0)))
                .toList();
    }

    private static Map<String, String> beastXEnvironment() {
        // Exercise the Apple Silicon ZIP's native libraries after clearing the inherited environment.
        // The generic ZIP uses the native JNI/plugin directory configured for the test JVM.
        return BUNDLED_BEAGLE
                ? Map.of()
                : Map.of(
                        "JDK_JAVA_OPTIONS",
                        "\"-Djava.library.path=" + System.getProperty("java.library.path")
                                + "\" -Dbeagle.resource.order=0");
    }

    @Test
    void packagedBeastXExportsHkyYuleWithoutWritingLogs() throws Exception {
        Path source = installation.resolve("examples/hky-yule.phylospec");
        Path directory = Files.createDirectory(workingDirectory.resolve("exported XML"));
        Path xml = directory.resolve("analysis.xml");
        // Native XML loading can open loggers in the caller's directory. Check both plausible locations.
        Files.writeString(workingDirectory.resolve("hky-yule.log"), "previous trace");
        Files.writeString(directory.resolve("hky-yule.trees"), "previous trees");
        Map<Path, String> before = hashes(workingDirectory);
        Map<Path, String> sourceFiles = hashes(source.getParent());

        Result result = launch(
                beastXEnvironment(),
                "export",
                "--engine",
                "beastx",
                source.toString(),
                "--xml",
                workingDirectory.relativize(xml).toString());
        assertEquals(0, result.exitCode(), result.details());

        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        for (String condition : List.of(
                "count(/beast/alignment/sequence) = 12",
                "count(/beast/alignment/sequence[string-length(normalize-space(.)) = 898]) = 12",
                "/beast/siteModel/substitutionModel/hkyModel/@idref = /beast/hkyModel/@id",
                "/beast/treeLikelihood/strictClockBranchRates/@idref = /beast/strictClockBranchRates/@id",
                "//speciationLikelihood/model/yuleModel/@idref = /beast/yuleModel/@id",
                "/beast/mcmc/joint/likelihood/treeLikelihood/@idref = /beast/treeLikelihood/@id",
                "/beast/mcmc/@chainLength = '30'",
                "count(/beast/mcmc/log[not(@fileName) and @logEvery='1']) = 1",
                "count(/beast/mcmc/log[@fileName='hky-yule.log' and @logEvery='1']) = 1",
                "/beast/mcmc/log[@fileName]/parameter/@idref = /beast/hkyModel/kappa/parameter/@idref",
                "/beast/mcmc/log[@fileName]/parameter/@idref = /beast/frequencyModel/frequencies/parameter/@idref",
                "count(/beast/mcmc/logTree[@fileName='hky-yule.trees' and @logEvery='1']) = 1")) {
            assertTrue(xpath.evaluateExpression(condition, document, Boolean.class), condition);
        }

        Path alignment = source.getParent().resolve("data/primate-mtDNA.nex");
        Result collision = launch(
                beastXEnvironment(), "export", "--engine", "beastx", source.toString(), "--xml", alignment.toString());
        assertEquals(1, collision.exitCode(), collision.details());
        assertTrue(collision.stderr().contains(alignment.toString()), collision.details());

        Map<Path, String> after = hashes(workingDirectory);
        after.remove(workingDirectory.relativize(xml));
        assertEquals(before, after, "Only the requested XML may be written");
        assertEquals(sourceFiles, hashes(source.getParent()), "Export must preserve the tutorial and data");
    }

    @Test
    void missingBeastXRuntimeDoesNotFallBackOrBreakBeast3() throws Exception {
        Path beastX = installation.resolve("engines/beastx/lib");
        Files.move(beastX, temporary.resolve("removed BEAST X libraries"));
        String script = installation.resolve("examples/hky-yule.phylospec").toString();
        Result missing = launch("check", "--engine", "beastx", script);
        assertNotEquals(0, missing.exitCode(), missing.details());
        assertFalse(missing.stdout().contains("Check passed"), missing.details());
        assertSuccessfulCheck(launch("check", script), "beast3", "beast2");
        assertTrue(fileNames(workingDirectory).isEmpty());
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
        JsonNode backends = record.path("runtime").path("likelihoodBackends");
        assertEquals(1, backends.size(), record.toPrettyString());
        if (BUNDLED_BEAGLE) {
            JsonNode backend = backends.get(0);
            assertEquals("BEAGLE", backend.path("implementation").asText(), record.toPrettyString());
            assertTrue(backend.path("version").asText().startsWith("4.0.1"), record.toPrettyString());
            assertEquals(0, backend.path("resourceNumber").asInt(-1), record.toPrettyString());
            assertTrue(backend.path("resourceFlags").asText().contains("PROCESSOR_CPU"), record.toPrettyString());
            assertTrue(completed.stdout().contains(": BEAGLE "), completed.details());
            assertFalse(console.contains("Failed to load BEAGLE library:"), console);
        }
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

        assertHkyTrace(run.resolve("output/hky-yule.log"), "posterior", "baseFrequencies.");
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
        if (BUNDLED_BEAGLE) {
            // A damaged bundled install must not pass acceptance using a system BEAGLE instead.
            Files.delete(installation.resolve("native/macos-arm64/libhmsbeagle-cpu.40.so"));
            Path brokenRun = workingDirectory.resolve("broken native result");
            Result broken = launch("run", source.toString(), "--out", brokenRun.toString());
            assertNotEquals(0, broken.exitCode(), broken.details());
            assertTrue(broken.stderr().contains("Bundled BEAGLE library is missing"), broken.details());
            assertFalse(broken.stdout().contains("Run completed"), broken.details());
            assertFalse(Files.exists(brokenRun));
        }
    }

    @Test
    void packagedCliPreservesGreekIdentifiersInSourceTraceAndState() throws Exception {
        Path source = temporary.resolve("unicode-hky.phylospec");
        Files.writeString(
                source,
                UnicodeIdentifiersTest.greekHkyModel(
                        installation.resolve("examples/data/primate-mtDNA.nex").toString()));
        Result checked = launch("check", source.toString());
        assertEquals(0, checked.exitCode(), checked.details());
        assertTrue(checked.stdout().contains("Check passed"), checked.details());
        assertTrue(checked.stdout().contains("No MCMC was run."), checked.details());
        assertTrue(fileNames(workingDirectory).isEmpty());

        Path run = workingDirectory.resolve("Greek identifier results");
        Result completed = launch("run", source.toString(), "--out", run.toString());
        assertEquals(0, completed.exitCode(), completed.details());
        assertTrue(completed.stdout().contains("Run completed"), completed.details());
        assertSuccessfulRun(run, source);
        String trace = Files.readString(run.resolve("output/unicode-hky.log"));
        String header = trace.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .findFirst()
                .orElseThrow();
        List<String> columns = Arrays.asList(header.strip().split("\\s+"));
        assertTrue(
                columns.containsAll(List.of(
                        "posterior",
                        "prior",
                        "likelihood",
                        "κ",
                        "θ.height",
                        "θ.treeLength",
                        "π.1",
                        "π.2",
                        "π.3",
                        "π.4")),
                header);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var state = factory.newDocumentBuilder()
                .parse(run.resolve("output/unicode-hky.state.xml").toFile());
        assertEquals("30", state.getDocumentElement().getAttribute("sample"));
        var elements = state.getElementsByTagName("*");
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < elements.getLength(); index++) {
            var id = elements.item(index).getAttributes().getNamedItem("id");
            if (id != null) ids.add(id.getNodeValue());
        }
        assertEquals(Set.of("θ", "κ", "π"), ids);
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

    private static void assertHkyTrace(Path trace, String posterior, String frequencyPrefix) throws Exception {
        List<String> lines = Files.readAllLines(trace).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        List<String> columns = Arrays.asList(lines.getFirst().split("\\s+"));
        assertTrue(
                columns.containsAll(List.of(
                        posterior,
                        "prior",
                        "likelihood",
                        "kappa",
                        "tree.height",
                        "tree.treeLength",
                        frequencyPrefix + "1",
                        frequencyPrefix + "2",
                        frequencyPrefix + "3",
                        frequencyPrefix + "4")),
                columns.toString());
        assertEquals(32, lines.size(), "Header and samples 0 through 30 must be present");
        for (int sample = 0; sample <= 30; sample++) {
            String[] values = lines.get(sample + 1).split("\\s+");
            assertEquals(columns.size(), values.length);
            assertEquals(sample, Long.parseLong(values[0]));
            assertEquals(
                    Double.parseDouble(values[columns.indexOf("prior")])
                            + Double.parseDouble(values[columns.indexOf("likelihood")]),
                    Double.parseDouble(values[columns.indexOf(posterior)]),
                    1e-8,
                    "Posterior must equal prior plus likelihood at sample " + sample);
            assertTrue(Double.parseDouble(values[columns.indexOf("kappa")]) > 0.0);
            assertTrue(Double.parseDouble(values[columns.indexOf("tree.height")]) > 0.0);
            assertTrue(Double.parseDouble(values[columns.indexOf("tree.treeLength")]) > 0.0);
            double total = 0.0;
            for (int base = 1; base <= 4; base++) {
                double frequency = Double.parseDouble(values[columns.indexOf(frequencyPrefix + base)]);
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
        return launch(Map.of(), arguments);
    }

    private Result launch(Map<String, String> environment, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(installation.resolve("bin/phylospec").toString());
        command.addAll(List.of(arguments));
        return execute(command, environment);
    }

    private Result execute(List<String> command, Map<String, String> environment) throws Exception {
        Path stdout = Files.createTempFile(processLogs, "stdout-", ".txt");
        Path stderr = Files.createTempFile(processLogs, "stderr-", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        for (String name : List.of(
                "CLASSPATH",
                "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "_JAVA_OPTIONS",
                "BEAGLE_LIB",
                "BEAGLE_PLUGIN_PATH",
                "PHYLOSPEC_BEAGLE_HOME",
                "DYLD_LIBRARY_PATH",
                "DYLD_FALLBACK_LIBRARY_PATH",
                "LD_LIBRARY_PATH")) {
            builder.environment().remove(name);
        }
        builder.environment().putAll(environment);
        if (BUNDLED_BEAGLE) builder.environment().put("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
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
