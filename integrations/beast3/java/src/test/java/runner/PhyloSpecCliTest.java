package runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.core.BEASTVersion2;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PhyloSpecCliTest {
    private static final ObjectMapper JSON = new ObjectMapper();

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

    @Test
    void newCommandsUseTheBaseLibraryByDefault() {
        for (String command : List.of("check", "run")) {
            assertEquals(
                    List.of("beast2"),
                    PhyloSpecCli.Options.parse(new String[] {command, "model.phylospec"})
                            .libraries());
        }
    }

    @Test
    void newCommandsPreserveProviderOrderAndAppendBaseOnlyWhenMissing() {
        for (String command : List.of("check", "run")) {
            assertEquals(
                    List.of("first", "second", "beast2"),
                    PhyloSpecCli.Options.parse(new String[] {command, "model.phylospec", "--library", "first,second"})
                            .libraries());
            assertEquals(
                    List.of("beast2", "first"),
                    PhyloSpecCli.Options.parse(new String[] {command, "model.phylospec", "--library", "beast2,first"})
                            .libraries());
        }
        assertEquals(
                List.of("first"),
                PhyloSpecCli.Options.parse(new String[] {"--library", "first", "model.phylospec"})
                        .libraries());
    }

    @Test
    void helpAndVersionDoNotRequireAScriptOrInstalledLibrary() {
        for (String[] arguments : List.of(new String[] {"--help"}, new String[] {"check", "--help"}, new String[] {
            "run", "--help", "--library", "missing-library"
        })) {
            CliResult result = invoke(arguments);
            assertEquals(0, result.exitCode(), result.stderr());
            assertTrue(result.stdout().contains("phylospec check"));
            assertTrue(result.stdout().contains("phylospec run"));
            assertTrue(result.stdout().contains("--version"));
            assertTrue(result.stderr().isEmpty());
        }
        CliResult version = invoke("--version", "--library", "missing-library");
        assertEquals(0, version.exitCode(), version.stderr());
        assertTrue(version.stdout().contains("PhyloSpec BEAST 3 integration "));
        assertFalse(version.stdout().contains("version unavailable"), version.stdout());
        assertFalse(version.stdout().contains("${"), version.stdout());
        assertTrue(version.stdout().contains("BEAST runtime " + BEASTVersion2.INSTANCE.getVersionString()));
    }

    @Test
    void rejectsUnsupportedOrIncompleteCommandsAsUsageErrors() {
        for (String[] arguments : List.of(
                new String[] {"check"},
                new String[] {"run"},
                new String[] {"check", "model.phylospec", "--engine", "beastx"},
                new String[] {"check", "model.phylospec", "--engine"},
                new String[] {"check", "model.phylospec", "--run-name", "analysis"},
                new String[] {"run", "model.phylospec", "--run-name", " "},
                new String[] {"run", "model.phylospec", "--library", "beast2,beast2"},
                new String[] {"run", "model.phylospec", "--library", "beast2,"},
                new String[] {"check", "model.phylospec", "--unknown"},
                new String[] {"run", "model.phylospec", "--out"},
                new String[] {"run", "model.phylospec", "--out", " "},
                new String[] {"check", "model.phylospec", "--out", "results"},
                new String[] {"--library", "beast2", "model.phylospec", "--out", "results"},
                new String[] {"check", "model.phylospec", "--adapter-path"},
                new String[] {"check", "model.phylospec", "--adapter-path", " "},
                new String[] {"--library", "beast2", "model.phylospec", "--adapter-path", "adapter"})) {
            CliResult result = invoke(arguments);
            assertEquals(2, result.exitCode(), String.join(" ", arguments) + "\n" + result.stderr());
            assertTrue(result.stderr().contains("Usage:"), result.stderr());
            assertTrue(result.stdout().isEmpty());
        }
    }

    @Test
    void distinguishesMissingFilesAndLibrariesFromCommandErrors(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, "Real x ~ Normal(mean=0.0, sd=1.0)");
        for (String command : List.of("check", "run")) {
            for (String[] arguments : List.of(
                    new String[] {
                        command, directory.resolve("missing.phylospec").toString()
                    },
                    new String[] {command, directory.toString()},
                    new String[] {command, source.toString(), "--library", "missing-library"})) {
                CliResult result = invoke(arguments);
                assertEquals(1, result.exitCode(), result.stderr());
                assertFalse(result.stderr().contains("Usage:"), result.stderr());
                assertFalse(result.stderr().contains("\tat "), result.stderr());
                assertFalse(result.stderr().isBlank());
            }
        }
    }

    @Test
    void checkSucceedsWithoutProducingAnalysisFiles(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, "Real x ~ Normal(mean=0.0, sd=1.0)");
        CliResult result = invoke("check", source.toString(), "--engine", "beast3");

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("libraries: beast2"));
        assertTrue(result.stdout().contains("No MCMC was run."));
        assertTrue(result.stdout().contains("Checked: syntax, static types and available BEAST 3 mappings."));
        assertTrue(result.stdout().contains("Not checked: complete input-data validity, native model initialization"));
        assertTrue(result.stdout().contains("use 'phylospec run'"));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(source), files.toList());
        }
    }

    @Test
    void checkPreservesNonfatalWarningsAndBothCommandsReportWarningsOnce(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("model.phylospec");
        String warningModel = "Real x ~ Uniform(lower=2.0, upper=1.0)\n";
        Files.writeString(source, warningModel);
        CliResult checked = invoke("check", source.toString());
        assertEquals(0, checked.exitCode(), checked.stderr());
        assertEquals(1, occurrences(checked.stderr(), "Please note:"));

        Files.writeString(source, warningModel + "Integer y = \"wrong\"\n");
        for (String command : List.of("check", "run")) {
            CliResult failure = invoke(command, source.toString());
            assertEquals(1, failure.exitCode(), failure.stderr());
            assertEquals(1, occurrences(failure.stderr(), "Please note:"));
            assertEquals(1, occurrences(failure.stderr(), "There is a problem:"));
            assertFalse(failure.stderr().contains("Usage:"));
            if (command.equals("check")) {
                assertTrue(failure.stdout().isEmpty());
            } else {
                assertEquals(
                        "[1/4] Checking syntax, static types and BEAST 3 mappings..." + System.lineSeparator(),
                        failure.stdout());
                assertFalse(Files.exists(directory.resolve("runs")));
            }
        }
    }

    @Test
    void supportsAnOptionTerminatorForFileNames() {
        assertEquals(
                Path.of("-model.phylospec"),
                PhyloSpecCli.Options.parse(new String[] {"check", "--", "-model.phylospec"})
                        .source());
    }

    @Test
    void debugControlsNativeExceptionStackTraces(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("invalid-tree.phylospec");
        Files.writeString(source, """
                Tree tree = fromNewick("(")
                mcmc {
                    Integer chainLength = 1
                }
                """);
        Path normalRun = directory.resolve("normal");
        CliResult normal = launch(directory, "run", source.toString(), "--out", normalRun.toString());
        CliResult debug = launch(
                directory,
                "run",
                source.toString(),
                "--out",
                directory.resolve("debug").toString(),
                "--debug");

        assertEquals(1, normal.exitCode(), normal.stderr());
        assertTrue(normal.stderr().contains("MODEL_BUILDING"), normal.stderr());
        assertFalse(normal.stderr().contains("\tat "), normal.stderr());
        assertEquals(1, debug.exitCode(), debug.stderr());
        assertTrue(debug.stderr().contains("runner.PhyloSpecException"), debug.stderr());
        assertTrue(debug.stderr().contains("\tat "), debug.stderr());
        JsonNode failed = readRecord(normalRun);
        assertEquals("failed", failed.path("status").asText());
        assertTrue(failed.path("completionConfirmed").asBoolean());
        assertTrue(failed.path("error").path("diagnostic").asText().contains("MODEL_BUILDING"));
        assertEquals(0, failed.path("generatedOutputs").size());
        assertEquals(Files.readString(source), Files.readString(normalRun.resolve("model.phylospec")));
        assertTrue(Files.readString(normalRun.resolve("console.log")).contains("runner.PhyloSpecException"));
        for (CliResult failure : List.of(normal, debug)) {
            assertTrue(failure.stdout().contains("[2/4] Building and initializing"));
            assertFalse(failure.stdout().contains("[3/4]"));
            assertFalse(failure.stdout().contains("[4/4]"));
            assertFalse(failure.stdout().contains("Run completed"));
            assertFalse(failure.stdout().contains("Final state:"));
            assertTrue(failure.stdout().contains("BEAST console log:"));
        }
    }

    @Test
    void versionAndCheckWorkFromAnotherDirectoryInAChildJvm(@TempDir Path directory) throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        Path source = directory.resolve("model with spaces.phylospec");
        Files.writeString(source, "Real x ~ Normal(mean=0.0, sd=1.0)");
        CliResult version = launch(work, "--version");
        assertEquals(0, version.exitCode(), version.stderr());
        assertTrue(version.stdout().contains("PhyloSpec BEAST 3 integration "));
        assertFalse(version.stdout().contains("version unavailable"));
        CliResult checked = launch(work, "check", source.toString());
        assertEquals(0, checked.exitCode(), checked.stderr());
        assertTrue(checked.stdout().contains("Check passed"));
        assertFalse(Files.exists(directory.resolve("model with spaces.log")));
    }

    @Test
    void managedRunUsesOutputDirectoryAndLegacyRunKeepsItsPrefixBehavior(@TempDir Path directory) throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        Files.copy(Path.of("src/test/java/resources/primate-mtDNA.nex"), directory.resolve("alignment.nex"));
        Path source = directory.resolve("model with spaces.phylospec");
        Files.writeString(source, """
                Alignment data = fromNexus("alignment.nex")
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa(data))
                QMatrix qMatrix = jc69()
                Alignment alignment ~ PhyloCTMC(tree, qMatrix) observed as data
                mcmc {
                    Integer chainLength = 30
                    Integer defaultLogEvery = 10
                    Integer randomSeed = 42
                }
                """);
        String newPrefix = work.resolve("new analysis/output/nested/new prefix").toString();
        String legacyPrefix = directory.resolve("legacy analysis").toString();
        CliResult result = launch(
                work,
                "run",
                "../model with spaces.phylospec",
                "--out",
                "new analysis",
                "--run-name",
                "nested/new prefix");
        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("Run completed"));
        assertNativeOutputs(newPrefix);
        JsonNode backend = readRecord(work.resolve("new analysis"))
                .path("runtime").path("likelihoodBackends").get(0);
        assertEquals("Java", backend.path("implementation").asText());
        assertTrue(backend.path("version").isNull());
        assertTrue(backend.path("resourceNumber").isNull());
        assertTrue(result.stdout().contains(": Java (BEAGLE is not active)."));

        CliResult legacy = launch(work, "--library", "beast2", "--run-name", legacyPrefix, source.toString());
        assertEquals(0, legacy.exitCode(), legacy.stderr());
        assertNativeOutputs(legacyPrefix);
        assertEquals(
                traceLines(Path.of(legacyPrefix + ".log")),
                traceLines(Path.of(newPrefix + ".log")),
                "Managed recording must not alter the existing seeded chain");
        assertFalse(Files.exists(work.resolve("model with spaces.log")));
    }

    @Test
    void defaultRunsCreateSeparateDirectoriesBesideTheOriginalScript(@TempDir Path directory) throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        Path source = writeModel(directory, "");
        for (int i = 0; i < 2; i++) {
            CliResult result = launch(work, "run", source.toString());
            assertEquals(0, result.exitCode(), result.stderr());
            assertTrue(result.stdout().contains("Results directory: " + directory.resolve("runs")));
        }
        try (var entries = Files.list(directory.resolve("runs"))) {
            List<Path> runs = entries.toList();
            assertEquals(2, runs.size());
            for (Path run : runs) {
                assertTrue(run.getFileName().toString().startsWith("model with spaces-"));
                assertNativeOutputs(run.resolve("output/model with spaces").toString());
            }
        }
        assertFalse(Files.exists(work.resolve("runs")));
        assertFalse(Files.exists(directory.resolve("model with spaces.log")));
    }

    @Test
    void scriptPrefixAndExplicitLoggersShareTheManagedOutputRoot(@TempDir Path directory) throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        Path source = writeModel(directory, """
                String outputPrefix = "state/my analysis"
                Logger trace = fileLogger(logEvery=10, file="traces/custom.log")
                Logger trees = treeLogger(logEvery=10, file="trees/custom.trees", tree=tree)
                """);
        Path run = Files.createDirectory(directory.resolve("existing empty run"));
        CliResult result = launch(work, "run", source.toString(), "--out", run.toString(), "--run-name", "ignored");
        assertEquals(0, result.exitCode(), result.stderr());
        assertTrace(run.resolve("output/traces/custom.log"));
        assertTrue(Files.readString(run.resolve("output/trees/custom.trees")).contains("STATE_30"));
        assertTrue(Files.size(run.resolve("output/state/my analysis.state.xml")) > 0);
        assertTrue(result.stdout().contains("Trace/log: " + run.resolve("output/traces/custom.log")));
        assertTrue(result.stdout().contains("Trees: " + run.resolve("output/trees/custom.trees")));
        assertTrue(result.stdout().contains("Final state: " + run.resolve("output/state/my analysis.state.xml")));
        try (var paths = Files.walk(run.resolve("output"))) {
            assertEquals(3, paths.filter(Files::isRegularFile).count());
        }
        assertFalse(Files.exists(directory.resolve("state")));
        assertFalse(Files.exists(work.resolve("traces")));
    }

    @Test
    void refusesAnExistingRunBeforeNativeFileHandling(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        Path run = directory.resolve("results");
        CliResult first = launch(directory, "run", source.toString(), "--out", run.toString());
        assertEquals(0, first.exitCode(), first.stderr());
        Path trace = run.resolve("output/model with spaces.log");
        String originalTrace = Files.readString(trace);
        CliResult repeated = launch(directory, "run", source.toString(), "--out", run.toString());
        assertEquals(1, repeated.exitCode(), repeated.stderr());
        assertTrue(repeated.stderr().contains("not empty"), repeated.stderr());
        assertFalse(repeated.stderr().contains("Usage:"));
        assertFalse(repeated.stdout().contains("overwrite"));
        assertEquals(originalTrace, Files.readString(trace));
    }

    @Test
    void managedRunRejectsUnsafeAndConflictingScriptOutputs(@TempDir Path directory) throws Exception {
        List<String> settings = List.of(
                "String outputPrefix = \"../escape\"",
                "String outputPrefix = \"" + directory.resolve("absolute prefix") + "\"",
                "Logger trace = fileLogger(logEvery=10, file=\"../escape.log\")",
                "Logger trace = fileLogger(logEvery=10, file=\"" + directory.resolve("absolute.log") + "\")",
                "Logger trees = treeLogger(logEvery=10, file=\"\", tree=tree)",
                "Logger trees = treeLogger(logEvery=10, file=\"$(tree).trees\", tree=tree)",
                "Logger trace = fileLogger(logEvery=10, file=\"same\")\n"
                        + "Logger trees = treeLogger(logEvery=10, file=\"nested/../same\", tree=tree)",
                "Logger trace = fileLogger(logEvery=10, file=\"model with spaces.state.xml\")",
                "Logger trace = fileLogger(logEvery=10, file=\"model with spaces.state.xml.new\")");
        for (int i = 0; i < settings.size(); i++) {
            Path modelDirectory = Files.createDirectory(directory.resolve("case-" + i));
            Path source = writeModel(modelDirectory, settings.get(i));
            Path run = modelDirectory.resolve("results");
            CliResult result = launch(modelDirectory, "run", source.toString(), "--out", run.toString());
            assertEquals(1, result.exitCode(), settings.get(i) + "\n" + result.stderr());
            assertFalse(result.stderr().contains("Usage:"), result.stderr());
            assertFalse(result.stdout().contains("Run completed"));
            try (var paths = Files.walk(run.resolve("output"))) {
                assertEquals(0, paths.filter(Files::isRegularFile).count(), result.stderr());
            }
        }
        assertFalse(Files.exists(directory.resolve("absolute.log")));
    }

    @Test
    void staticFailureDoesNotCreateTheRequestedRunDirectory(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("invalid.phylospec");
        Files.writeString(source, "Integer value = \"wrong\"");
        Path run = directory.resolve("results");
        CliResult result = invoke("run", source.toString(), "--out", run.toString());
        assertEquals(1, result.exitCode(), result.stderr());
        assertTrue(result.stderr().contains("TYPE_CHECKING"));
        assertFalse(Files.exists(run));
    }

    @Test
    void nativePathOverridesCannotBypassTheManagedDirectory(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        for (String property : List.of("file.name.prefix", "state.file.name")) {
            Path run = directory.resolve(property);
            CliResult result = launchWithJvmOptions(
                    directory,
                    List.of("-D" + property + "=outside"),
                    "run",
                    source.toString(),
                    "--out",
                    run.toString());
            assertEquals(1, result.exitCode(), result.stderr());
            assertTrue(result.stderr().contains(property), result.stderr());
            assertFalse(Files.exists(run));
        }
    }

    @Test
    void absoluteAlignmentAndRelativeOrAbsoluteTreeInputsWorkFromAnotherDirectory(@TempDir Path directory)
            throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        Path source = writeModel(directory, "");
        Path treeFile =
                Files.copy(Path.of("src/test/java/resources/valid.newick"), directory.resolve("reference tree.newick"));
        String model = Files.readString(source)
                .replace("fromNexus(\"alignment.nex\")", "fromNexus(\"" + directory.resolve("alignment.nex") + "\")");
        for (String treePath : List.of(treeFile.getFileName().toString(), treeFile.toString())) {
            Files.writeString(source, "Tree reference = fromTree(\"" + treePath + "\")\n" + model);
            Path run = directory.resolve(treePath.equals(treeFile.toString()) ? "absolute tree" : "relative tree");
            CliResult result = launch(work, "run", source.toString(), "--out", run.toString());
            assertEquals(0, result.exitCode(), result.stderr());
            assertTrace(run.resolve("output/model with spaces.log"));
            assertTrue(Files.size(run.resolve("output/model with spaces.state.xml")) > 0);
        }
    }

    @Test
    void missingInputsReportTheirResolvedPathWithoutFallingBackToWorkingDirectory(@TempDir Path directory)
            throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        // A real CWD input must not hide the missing file beside the script.
        Files.copy(Path.of("src/test/java/resources/primate-mtDNA.nex"), work.resolve("missing.nex"));
        Files.copy(Path.of("src/test/java/resources/valid.newick"), work.resolve("missing.newick"));
        Path source = directory.resolve("model.phylospec");
        List<String> declarations = List.of(
                "Alignment data = fromNexus(\"missing.nex\")",
                "Tree tree = fromTree(\"missing.nex\")",
                "Tree tree = fromTree(\"missing.newick\")");
        for (int i = 0; i < declarations.size(); i++) {
            Files.writeString(source, declarations.get(i));
            Path run = directory.resolve("missing-input-" + i);
            CliResult result = launch(work, "run", source.toString(), "--out", run.toString());
            assertEquals(1, result.exitCode(), result.stderr());
            assertTrue(result.stderr().contains("MODEL_BUILDING"), result.stderr());
            assertTrue(result.stderr().contains("Could not read input file"), result.stderr());
            assertTrue(
                    result.stderr()
                            .contains(directory
                                    .resolve(i == 2 ? "missing.newick" : "missing.nex")
                                    .toString()),
                    result.stderr());
            assertFalse(result.stderr().contains("Usage:"), result.stderr());
            JsonNode failed = readRecord(run);
            assertEquals("failed", failed.path("status").asText());
            assertEquals(
                    "unavailable", failed.path("inputs").get(0).path("status").asText());
            assertEquals(0, failed.path("generatedOutputs").size());
        }
    }

    @Test
    void recordsTheActualSourceInputsConfigurationAndGeneratedOutputs(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        Path run = directory.resolve("recorded run");
        CliResult result = launch(directory, "run", source.toString(), "--out", run.toString());
        assertEquals(0, result.exitCode(), result.stderr());
        JsonNode record = readRecord(run);
        assertEquals("succeeded", record.path("status").asText());
        assertTrue(record.path("completionConfirmed").asBoolean());
        assertFalse(record.path("startedAt").asText().isBlank());
        assertFalse(record.path("endedAt").asText().isBlank());
        assertEquals(
                directory.toString(), record.path("source").path("inputBase").asText());
        assertEquals(Files.readString(source), Files.readString(run.resolve("model.phylospec")));
        assertEquals(sha256(source), record.path("source").path("sha256").asText());
        assertEquals("beast2", record.path("libraries").get(0).path("id").asText());
        assertEquals(1, record.path("inputs").size());
        JsonNode input = record.path("inputs").get(0);
        assertEquals(
                directory.resolve("alignment.nex").toString(),
                input.path("path").asText());
        assertEquals(
                sha256(directory.resolve("alignment.nex")), input.path("sha256").asText());
        assertEquals(30, record.path("settings").path("chainLength").asLong());
        assertEquals(10, record.path("settings").path("defaultLogEvery").asInt());
        assertEquals(42, record.path("settings").path("randomSeed").asLong());
        for (String phase : List.of("modelBuilding", "mcmc")) {
            assertEquals(42, record.path("seeds").path(phase).path("requested").asLong());
            assertTrue(record.path("seeds").path(phase).path("observed").isNumber());
            assertFalse(record.path("seeds").path(phase).path("generated").asBoolean());
        }
        assertEquals(3, record.path("expectedOutputs").size());
        assertEquals(3, record.path("generatedOutputs").size());
        for (JsonNode file : record.path("generatedOutputs")) {
            assertEquals(
                    Files.size(run.resolve(file.path("path").asText())),
                    file.path("size").asLong());
        }
        String console = Files.readString(run.resolve("console.log"));
        assertTrue(console.contains("Total calculation time"), console);
        assertTrue(console.contains("End likelihood"), console);
        for (String text : List.of(result.stdout(), console)) {
            int previous = -1;
            for (String phase : List.of(
                    "[1/4] Checking",
                    "[2/4] Building and initializing",
                    "[3/4] Running MCMC",
                    "[4/4] Checking result")) {
                int position = text.indexOf(phase);
                assertTrue(position > previous, text);
                assertEquals(position, text.lastIndexOf(phase), text);
                previous = position;
            }
        }
        assertTrue(result.stdout().contains("Run record: " + run.resolve("run.json")));
        assertTrue(result.stdout().contains("BEAST console log: " + run.resolve("console.log")));
        assertTrue(result.stdout().contains("MCMC convergence has not been assessed"));
        assertFalse(Files.exists(run.resolve("alignment.nex")), "Snapshot does not relocate or copy the input data");
    }

    @Test
    void automaticallyChosenSeedCanReproduceTheRecordedShortChain(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        String model = Files.readString(source).replace("Integer randomSeed = 42", "");
        Files.writeString(source, model);
        Path first = directory.resolve("generated seed");
        CliResult generated = launch(directory, "run", source.toString(), "--out", first.toString());
        assertEquals(0, generated.exitCode(), generated.stderr());
        JsonNode seeds = readRecord(first).path("seeds");
        long seed = seeds.path("modelBuilding").path("requested").asLong();
        assertTrue(seed > 0);
        assertTrue(seeds.path("modelBuilding").path("generated").asBoolean());
        assertTrue(seeds.path("mcmc").path("generated").asBoolean());
        assertEquals(seed, seeds.path("mcmc").path("requested").asLong());
        Files.writeString(source, model.replace("mcmc {", "mcmc {\nInteger randomSeed = " + seed));
        Path repeated = directory.resolve("explicit seed");
        CliResult replay = launch(directory, "run", source.toString(), "--out", repeated.toString());
        assertEquals(0, replay.exitCode(), replay.stderr());
        assertEquals(
                traceLines(first.resolve("output/model with spaces.log")),
                traceLines(repeated.resolve("output/model with spaces.log")));
        assertEquals(
                Files.readString(first.resolve("output/model with spaces.trees")),
                Files.readString(repeated.resolve("output/model with spaces.trees")));
    }

    @Test
    void orderlyTerminationRecordsCancellationAndKeepsPartialOutputs(@TempDir Path directory) throws Exception {
        Path source = longModel(directory, 1_000_000_000);
        Path run = directory.resolve("cancelled");
        try (RunningCli child = start(directory, List.of(), "run", source.toString(), "--out", run.toString())) {
            awaitRunning(child, run);
            child.process().destroy();
            assertTrue(child.process().waitFor(15, TimeUnit.SECONDS), "Cancellation did not finish promptly");
            assertTrue(child.process().exitValue() != 0);
            JsonNode record = readRecord(run);
            assertEquals("cancelled", record.path("status").asText(), record.toPrettyString());
            assertTrue(record.path("completionConfirmed").asBoolean());
            assertFalse(record.path("endedAt").isNull());
            assertTrue(Files.exists(run.resolve("console.log")));
            assertFalse(Files.readString(child.stdout()).contains("Run completed"));
        }
    }

    @Test
    void forcedTerminationLeavesAnExplicitlyUnconfirmedRecord(@TempDir Path directory) throws Exception {
        Path source = longModel(directory, 1_000_000_000);
        Path run = directory.resolve("killed");
        try (RunningCli child = start(directory, List.of(), "run", source.toString(), "--out", run.toString())) {
            awaitRunning(child, run);
            child.process().destroyForcibly();
            assertTrue(child.process().waitFor(15, TimeUnit.SECONDS));
            assertTrue(child.process().exitValue() != 0);
            JsonNode record = readRecord(run);
            assertEquals("running", record.path("status").asText(), record.toPrettyString());
            assertFalse(record.path("completionConfirmed").asBoolean());
            assertTrue(record.path("completionNote").asText().contains("unconfirmed"));
            assertTrue(record.path("endedAt").isNull());
        }
    }

    @Test
    void nativeStateWriteFailureCannotBeReportedAsSuccess(@TempDir Path directory) throws Exception {
        Path source = longModel(directory, 100_000);
        Path run = directory.resolve("state write failure");
        try (RunningCli child = start(directory, List.of(), "run", source.toString(), "--out", run.toString())) {
            awaitRunning(child, run);
            Path blockedState = run.resolve("output/model with spaces.state.xml");
            Files.createDirectory(blockedState);
            Files.writeString(
                    blockedState.resolve("blocker"), "Keep the directory nonempty so BEAST cannot replace it.");
            assertTrue(child.process().waitFor(30, TimeUnit.SECONDS), "Failure fixture did not complete");
            assertEquals(1, child.process().exitValue(), Files.readString(child.stderr()));
            JsonNode record = readRecord(run);
            assertEquals("failed", record.path("status").asText(), record.toPrettyString());
            assertTrue(record.path("completionConfirmed").asBoolean());
            assertFalse(record.path("mcmcStartedAt").asText().isBlank());
            assertTrue(record.path("generatedOutputs").size() > 0);
            assertTrue(Files.size(run.resolve("output/model with spaces.log")) > 0);
            assertFalse(Files.readString(child.stdout()).contains("Run completed"));
        }
    }

    private static Path longModel(Path directory, int chainLength) throws Exception {
        Path source = writeModel(directory, "");
        Files.writeString(
                source,
                Files.readString(source)
                        .replace("chainLength = 30", "chainLength = " + chainLength)
                        .replace("defaultLogEvery = 10", "defaultLogEvery = 10000"));
        return source;
    }

    private static void awaitRunning(RunningCli child, Path run) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline && child.process().isAlive()) {
            if (Files.isRegularFile(run.resolve("run.json"))
                    && readRecord(run).path("status").asText().equals("running")) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Run did not enter running state: " + Files.readString(child.stderr()));
    }

    private static JsonNode readRecord(Path run) throws Exception {
        return JSON.readTree(run.resolve("run.json").toFile());
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static List<String> traceLines(Path file) throws Exception {
        return Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }

    private static Path writeModel(Path directory, String settings) throws Exception {
        Files.copy(Path.of("src/test/java/resources/primate-mtDNA.nex"), directory.resolve("alignment.nex"));
        Path source = directory.resolve("model with spaces.phylospec");
        Files.writeString(source, """
                Alignment data = fromNexus("alignment.nex")
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa(data))
                QMatrix qMatrix = jc69()
                Alignment alignment ~ PhyloCTMC(tree, qMatrix) observed as data
                mcmc {
                    Integer chainLength = 30
                    Integer defaultLogEvery = 10
                    Integer randomSeed = 42
                    %s
                }
                """.formatted(settings));
        return source;
    }

    private static void assertNativeOutputs(String prefix) throws Exception {
        assertTrace(Path.of(prefix + ".log"));
        assertTrue(Files.readString(Path.of(prefix + ".trees")).contains("STATE_30"));
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static void assertTrace(Path file) throws Exception {
        List<String> trace = Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(5, trace.size(), "Header and samples 0, 10, 20, 30");
        List<String> columns = List.of(trace.getFirst().trim().split("\\s+"));
        for (int sample = 1; sample < trace.size(); sample++) {
            String[] values = trace.get(sample).trim().split("\\s+");
            assertEquals((sample - 1) * 10, Integer.parseInt(values[0]));
            for (String name : List.of("posterior", "prior", "likelihood")) {
                assertTrue(columns.contains(name), columns.toString());
                assertTrue(Double.isFinite(Double.parseDouble(values[columns.indexOf(name)])));
            }
        }
    }

    private static int occurrences(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static CliResult invoke(String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            int code = PhyloSpecCli.run(arguments, out, err);
            return new CliResult(
                    code, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
        }
    }

    private record CliResult(int exitCode, String stdout, String stderr) {}

    private static CliResult launch(Path directory, String... arguments) throws Exception {
        return launchWithJvmOptions(directory, List.of(), arguments);
    }

    private static CliResult launchWithJvmOptions(Path directory, List<String> jvmOptions, String... arguments)
            throws Exception {
        try (RunningCli child = start(directory, jvmOptions, arguments)) {
            assertTrue(child.process().waitFor(30, TimeUnit.SECONDS), "CLI timed out: " + String.join(" ", arguments));
            return new CliResult(
                    child.process().exitValue(), Files.readString(child.stdout()), Files.readString(child.stderr()));
        }
    }

    private record RunningCli(Process process, Path stdout, Path stderr) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static RunningCli start(Path directory, List<String> jvmOptions, String... arguments) throws Exception {
        Path stdout = Files.createTempFile(directory, "cli-stdout-", ".txt");
        Path stderr = Files.createTempFile(directory, "cli-stderr-", ".txt");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvmOptions);
        command.addAll(List.of("-Djava.only=true", "-cp", classpath, "runner.PhyloSpecCli"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        return new RunningCli(process, stdout, stderr);
    }
}
