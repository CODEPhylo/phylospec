package runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.core.BEASTVersion2;
import beast.base.inference.MCMC;
import beast.base.parser.XMLParser;
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
import java.util.Map;
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
    void selectsTheRequestedCheckEngineAndItsBaseLibrary() {
        assertEquals(
                "beast3",
                PhyloSpecCli.Options.parse(new String[] {"check", "model.phylospec"})
                        .engine());
        for (String[] args : List.of(
                new String[] {"check", "--engine", "beastx", "model.phylospec"},
                new String[] {"check", "model.phylospec", "--library", "beastX", "--engine", "beastx"})) {
            var options = PhyloSpecCli.Options.parse(args);
            assertEquals("beastx", options.engine());
            assertEquals(List.of("beastX"), options.libraries());
        }
    }

    @Test
    void selectsBeastXForExport() {
        var options = PhyloSpecCli.Options.parse(
                new String[] {"export", "model.phylospec", "--engine", "beastx", "--xml", "model.xml"});
        assertEquals(PhyloSpecCli.Command.EXPORT, options.command());
        assertEquals("beastx", options.engine());
        assertEquals(List.of("beastX"), options.libraries());
        assertEquals(Path.of("model.xml"), options.xmlFile());
    }

    @Test
    void selectsBeastXForManagedRuns() {
        var options = PhyloSpecCli.Options.parse(
                new String[] {"run", "--engine", "beastx", "model.phylospec", "--run-name", "sample"});
        assertEquals(PhyloSpecCli.Command.RUN, options.command());
        assertEquals("beastx", options.engine());
        assertEquals(List.of("beastX"), options.libraries());
        assertEquals("sample", options.runName());
    }

    @Test
    void parsesTheSeedForExportAndRun() {
        assertEquals(
                42L,
                PhyloSpecCli.Options.parse(new String[] {"run", "model.phylospec", "--seed", "42"})
                        .seed());
        assertEquals(
                7L,
                PhyloSpecCli.Options.parse(
                                new String[] {"export", "model.phylospec", "--xml", "model.xml", "--seed", "7"})
                        .seed());
        assertNull(PhyloSpecCli.Options.parse(new String[] {"run", "model.phylospec"})
                .seed());
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
                new String[] {"export", "model.phylospec"},
                new String[] {"export", "model.phylospec", "--xml"},
                new String[] {"run", "model.phylospec", "--xml", "analysis.xml"},
                new String[] {"check", "model.phylospec", "--engine", "unknown"},
                new String[] {"check", "model.phylospec", "--engine", "beastx", "--engine", "beast3"},
                new String[] {"run", "model.phylospec", "--engine", "beastx", "--adapter-path", "local"},
                new String[] {"export", "--engine", "beastx", "model.phylospec"},
                new String[] {
                    "export", "--engine", "beastx", "model.phylospec", "--xml", "out.xml", "--adapter-path", "local"
                },
                new String[] {"--library", "beastX", "model.phylospec", "--engine", "beastx"},
                new String[] {"check", "model.phylospec", "--library", "beast2", "--engine", "beastx"},
                new String[] {"check", "model.phylospec", "--adapter-path", "local", "--engine", "beastx"},
                new String[] {"check", "model.phylospec", "--engine"},
                new String[] {"check", "model.phylospec", "--run-name", "analysis"},
                new String[] {"run", "model.phylospec", "--run-name", " "},
                new String[] {"run", "model.phylospec", "--run-name", "nested/name"},
                new String[] {"check", "model.phylospec", "--seed", "42"},
                new String[] {"run", "model.phylospec", "--seed"},
                new String[] {"run", "model.phylospec", "--seed", "abc"},
                new String[] {"run", "model.phylospec", "--seed", "-1"},
                new String[] {"run", "model.phylospec", "--library", "beast2,beast2"},
                new String[] {"run", "model.phylospec", "--library", "beast2,"},
                new String[] {"check", "model.phylospec", "--unknown"},
                new String[] {"run", "model.phylospec", "--out", "results"},
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
    void exportsLoadableAnalysisWithoutRunningTheChain(@TempDir Path directory) throws Exception {
        Path script = directory.resolve("normal.phylospec");
        Files.writeString(script, """
                Real x ~ Normal(mean=0.0, sd=1.0)
                mcmc {
                    Sampler sampler = mcmc(chainLength=19)
                }
                """);
        Path output = directory.resolve("analysis.xml");
        CliResult result = invoke("export", script.toString(), "--xml", output.toString());
        assertEquals(0, result.exitCode(), result.stderr());
        MCMC analysis = assertInstanceOf(MCMC.class, new XMLParser().parseFile(output.toFile()));
        assertEquals(19L, analysis.chainLengthInput.get());
        assertEquals(
                "x",
                analysis.startStateInput.get().stateNodeInput.get().getFirst().getID());
        assertFalse(analysis.operatorsInput.get().isEmpty());
        assertTrue(analysis.loggersInput.get().stream()
                .anyMatch(logger -> "analysis.log".equals(logger.fileNameInput.get())));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(output, script), files.sorted().toList());
        }
    }

    @Test
    void exportProtectsTheSourceAndPreservesTheDestinationOnFailure(@TempDir Path directory) throws Exception {
        Path script = directory.resolve("model.phylospec");
        String text = "Real x ~ Normal(mean=0.0, sd=1.0)";
        Files.writeString(script, text);
        CliResult sameFile = invoke("export", script.toString(), "--xml", script.toString());
        assertEquals(1, sameFile.exitCode());
        assertTrue(sameFile.stderr().contains("XML output is a model input"), sameFile.stderr());
        assertEquals(text, Files.readString(script));

        Path output = directory.resolve("analysis.xml");
        Files.writeString(output, "previous export");
        Files.writeString(script, "Real x ~ UnknownDistribution()");
        CliResult failure = invoke("export", script.toString(), "--xml", output.toString());
        assertEquals(1, failure.exitCode());
        assertEquals("previous export", Files.readString(output));
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
                assertNoRunRecords(directory);
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
                    Sampler sampler = mcmc(chainLength=1)
                }
                """);
        CliResult normal = launch(directory, "run", source.toString());
        Path normalRun = getOnlyRun(directory);
        CliResult debug = launch(directory, "run", source.toString(), "--debug");

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
    void managedRunUsesWorkingDirectoryAndLegacyRunKeepsItsPrefixBehavior(@TempDir Path directory) throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        Files.copy(Path.of("src/test/java/resources/primate-mtDNA.nex"), directory.resolve("alignment.nex"));
        Path source = directory.resolve("model with spaces.phylospec");
        Files.writeString(source, """
                Alignment data = fromNexus("alignment.nex")
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa(data))
                QMatrix qMatrix = jc69()
                Alignment alignment ~ PhyloCTMC(tree, qMatrix) observed as data
                mcmc {
                    Sampler sampler = mcmc(chainLength=3000)
                }
                """);
        String newPrefix = work.resolve("new prefix").toString();
        String legacyPrefix = directory.resolve("legacy analysis").toString();
        CliResult result =
                launch(work, "run", "../model with spaces.phylospec", "--run-name", "new prefix", "--seed", "42");
        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("Run completed"));
        assertNativeOutputs(newPrefix);
        JsonNode backend = readRecord(getOnlyRun(work))
                .path("runtime")
                .path("likelihoodBackends")
                .get(0);
        assertEquals("Java", backend.path("implementation").asText());
        assertTrue(backend.path("version").isNull());
        assertTrue(backend.path("resourceNumber").isNull());
        assertTrue(result.stdout().contains(": Java (BEAGLE is not active)."));

        CliResult legacy =
                launch(work, "--library", "beast2", "--run-name", legacyPrefix, "--seed", "42", source.toString());
        assertEquals(0, legacy.exitCode(), legacy.stderr());
        assertNativeOutputs(legacyPrefix);
        assertEquals(
                traceLines(Path.of(legacyPrefix + ".log")),
                traceLines(Path.of(newPrefix + ".log")),
                "Managed recording must not alter the existing seeded chain");
        assertFalse(Files.exists(work.resolve("model with spaces.log")));
    }

    @Test
    void runRecordsAreNamedAfterTheRunAndLiveNextToTheOutputs(@TempDir Path directory) throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        Path source = writeModel(directory, "");
        for (String runName : List.of("first", "second")) {
            CliResult result = launch(work, "run", source.toString(), "--run-name", runName);
            assertEquals(0, result.exitCode(), result.stderr());
            assertNativeOutputs(work.resolve(runName).toString());
            JsonNode record = readRecord(work.resolve(runName + ".run"));
            assertEquals(runName, record.at("/settings/runName").asText());
            assertEquals(
                    "../" + runName + ".log",
                    record.at("/generatedOutputs/0/path").asText());
        }
        assertEquals(2, getRunRecords(work).size());
        assertNoRunRecords(directory);
    }

    @Test
    void aSuccessfulRunReservesItsName(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        assertEquals(0, launch(directory, "run", source.toString()).exitCode());
        String trace = Files.readString(directory.resolve("model with spaces.log"));

        CliResult repeated = launch(directory, "run", source.toString());

        assertEquals(1, repeated.exitCode(), repeated.stderr());
        assertTrue(repeated.stderr().contains("already exists"), repeated.stderr());
        assertEquals(trace, Files.readString(directory.resolve("model with spaces.log")));
        assertEquals(
                "succeeded", readRecord(getOnlyRun(directory)).path("status").asText());
    }

    @Test
    void overwriteReplacesTheOutputsAndRecordOfAnEarlierRun(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        assertEquals(0, launch(directory, "run", source.toString()).exitCode());
        Files.writeString(directory.resolve("model with spaces.log"), "outdated");

        CliResult repeated = launch(directory, "run", source.toString(), "--overwrite");

        assertEquals(0, repeated.exitCode(), repeated.stderr());
        assertNativeOutputs(directory.resolve("model with spaces").toString());
        assertEquals(
                "succeeded", readRecord(getOnlyRun(directory)).path("status").asText());
    }

    @Test
    void overwriteIsOnlySupportedForRun(@TempDir Path directory) throws Exception {
        CliResult result = invoke("check", directory.resolve("model.phylospec").toString(), "--overwrite");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("--overwrite is only supported for run."), result.stderr());
    }

    @Test
    void outputPathsCanUseEnvironmentVariables(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, """
                Logger trace = fileLogger(file=env("TRACE_FILE"), logEvery=1000)
                """);

        CliResult result =
                launchWithEnvironment(directory, Map.of("TRACE_FILE", "logs/custom.log"), "run", source.toString());

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrace(directory.resolve("logs/custom.log"));
        JsonNode outputs = readRecord(getOnlyRun(directory)).path("generatedOutputs");
        assertTrue(outputs.toString().contains("\"../logs/custom.log\""), outputs.toString());
    }

    @Test
    void aFailedRunWithoutOutputsCanBeRepeated(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        Path alignment = directory.resolve("alignment.nex");
        Path moved = Files.move(alignment, directory.resolve("moved.nex"));
        assertEquals(1, launch(directory, "run", source.toString()).exitCode());
        assertEquals("failed", readRecord(getOnlyRun(directory)).path("status").asText());

        Files.move(moved, alignment);
        CliResult repeated = launch(directory, "run", source.toString());

        assertEquals(0, repeated.exitCode(), repeated.stderr());
        assertEquals(
                "succeeded", readRecord(getOnlyRun(directory)).path("status").asText());
    }

    @Test
    void runNameAndExplicitLoggersResolveAgainstTheWorkingDirectory(@TempDir Path directory) throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        Path absoluteTrees = directory.resolve("elsewhere/custom.trees");
        Path source = writeModel(directory, """
                Logger trace = fileLogger(logEvery=1000, file="traces/custom.log")
                Logger trees = treeLogger(logEvery=1000, file="%s", tree=tree)
                """.formatted(absoluteTrees));
        CliResult result = launch(work, "run", source.toString(), "--run-name", "my analysis");
        assertEquals(0, result.exitCode(), result.stderr());
        Path realWork = work.toRealPath();
        assertTrace(work.resolve("traces/custom.log"));
        assertTrue(Files.readString(absoluteTrees).contains("STATE_3000"));
        assertTrue(Files.size(work.resolve("my analysis.state.xml")) > 0);
        assertTrue(result.stdout().contains("Trace/log: " + realWork.resolve("traces/custom.log")));
        assertTrue(result.stdout().contains("Trees: " + absoluteTrees));
        assertTrue(result.stdout().contains("Final state: " + realWork.resolve("my analysis.state.xml")));
        assertFalse(Files.exists(directory.resolve("traces")));
    }

    @Test
    void loggersWithoutFileAndCustomStateFileUseTheWorkingDirectory(@TempDir Path directory) throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        Path source = writeModel(directory, """
                Logger trace = fileLogger(logEvery=1000)
                Logger trees = treeLogger(logEvery=1000, tree=tree)
                """);
        Files.writeString(
                source,
                Files.readString(source)
                        .replace("mcmc(chainLength=3000)", "mcmc(chainLength=3000, stateFile=\"states/custom.xml\")"));
        CliResult result = launch(work, "run", source.toString(), "--run-name", "analysis");
        assertEquals(0, result.exitCode(), result.stderr());
        assertTrace(work.resolve("analysis.log"));
        assertTrue(Files.readString(work.resolve("analysis.trees")).contains("STATE_3000"));
        assertTrue(Files.size(work.resolve("states/custom.xml")) > 0);
        assertFalse(Files.exists(work.resolve("analysis.state.xml")));
    }

    @Test
    void refusesExistingOutputsBeforeNativeFileHandling(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        CliResult first = launch(directory, "run", source.toString());
        assertEquals(0, first.exitCode(), first.stderr());
        Path trace = directory.resolve("model with spaces.log");
        String originalTrace = Files.readString(trace);
        CliResult repeated = launch(directory, "run", source.toString());
        assertEquals(1, repeated.exitCode(), repeated.stderr());
        assertTrue(repeated.stderr().contains("already exists"), repeated.stderr());
        assertFalse(repeated.stderr().contains("Usage:"));
        assertEquals(originalTrace, Files.readString(trace));
    }

    @Test
    void managedRunRejectsInvalidAndConflictingScriptOutputs(@TempDir Path directory) throws Exception {
        List<String> settings = List.of(
                "Logger trees = treeLogger(logEvery=1000, file=\"\", tree=tree)",
                "Logger trace = fileLogger(logEvery=1000, file=\"same\")\n"
                        + "Logger trees = treeLogger(logEvery=1000, file=\"nested/../same\", tree=tree)",
                "Logger trace = fileLogger(logEvery=1000, file=\"model with spaces.state.xml\")");
        for (int i = 0; i < settings.size(); i++) {
            Path modelDirectory = Files.createDirectory(directory.resolve("case-" + i));
            Path source = writeModel(modelDirectory, settings.get(i));
            CliResult result = launch(modelDirectory, "run", source.toString());
            assertEquals(1, result.exitCode(), settings.get(i) + "\n" + result.stderr());
            assertFalse(result.stderr().contains("Usage:"), result.stderr());
            assertFalse(result.stdout().contains("Run completed"));
            assertFalse(Files.exists(modelDirectory.resolve("same")));
            assertFalse(Files.exists(modelDirectory.resolve("model with spaces.state.xml")));
        }
    }

    @Test
    void staticFailureDoesNotCreateARunDirectory(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("invalid.phylospec");
        Files.writeString(source, "Integer value = \"wrong\"");
        CliResult result = invoke("run", source.toString());
        assertEquals(1, result.exitCode(), result.stderr());
        assertTrue(result.stderr().contains("TYPE_CHECKING"));
        assertNoRunRecords(directory);
    }

    @Test
    void nativePathOverridesCannotBypassTheManagedDirectory(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        for (String property : List.of("file.name.prefix", "state.file.name")) {
            CliResult result =
                    launchWithJvmOptions(directory, List.of("-D" + property + "=outside"), "run", source.toString());
            assertEquals(1, result.exitCode(), result.stderr());
            assertTrue(result.stderr().contains(property), result.stderr());
            assertNoRunRecords(directory);
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
            String runName = treePath.equals(treeFile.toString()) ? "absolute tree" : "relative tree";
            CliResult result = launch(work, "run", source.toString(), "--run-name", runName);
            assertEquals(0, result.exitCode(), result.stderr());
            assertTrace(work.resolve(runName + ".log"));
            assertTrue(Files.size(work.resolve(runName + ".state.xml")) > 0);
        }
    }

    @Test
    void missingInputsReportTheirResolvedPathWithoutFallingBackToWorkingDirectory(@TempDir Path directory)
            throws Exception {
        Path work = Files.createDirectory(directory.resolve("working directory"));
        // A real CWD input must not hide the missing file beside the script.
        Files.copy(Path.of("src/test/java/resources/primate-mtDNA.nex"), work.resolve("missing.nex"));
        Files.copy(Path.of("src/test/java/resources/valid.newick"), work.resolve("missing.newick"));
        List<String> declarations = List.of(
                "Alignment data = fromNexus(\"missing.nex\")",
                "Tree tree = fromTree(\"missing.nex\")",
                "Tree tree = fromTree(\"missing.newick\")");
        for (int i = 0; i < declarations.size(); i++) {
            Path modelDirectory = Files.createDirectory(directory.resolve("missing-input-" + i));
            Path source = Files.writeString(modelDirectory.resolve("model.phylospec"), declarations.get(i));
            CliResult result = launch(work, "run", source.toString());
            assertEquals(1, result.exitCode(), result.stderr());
            assertTrue(result.stderr().contains("MODEL_BUILDING"), result.stderr());
            assertTrue(result.stderr().contains("Could not read input file"), result.stderr());
            assertTrue(
                    result.stderr()
                            .contains(modelDirectory
                                    .resolve(i == 2 ? "missing.newick" : "missing.nex")
                                    .toString()),
                    result.stderr());
            assertFalse(result.stderr().contains("Usage:"), result.stderr());
            JsonNode failed = readRecord(getOnlyRun(work));
            assertEquals("failed", failed.path("status").asText());
            assertEquals(
                    "unavailable", failed.path("inputs").get(0).path("status").asText());
            assertEquals(0, failed.path("generatedOutputs").size());
        }
    }

    @Test
    void recordsTheActualSourceInputsConfigurationAndGeneratedOutputs(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        CliResult result = launch(directory, "run", source.toString(), "--seed", "42");
        assertEquals(0, result.exitCode(), result.stderr());
        Path run = getOnlyRun(directory);
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
        assertEquals(3000, record.path("settings").path("chainLength").asLong());
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
        Path realRun = run.toRealPath();
        assertTrue(result.stdout().contains("Run record: " + realRun.resolve("run.json")));
        assertTrue(result.stdout().contains("BEAST console log: " + realRun.resolve("console.log")));
        assertTrue(result.stdout().contains("MCMC convergence has not been assessed"));
        assertFalse(Files.exists(run.resolve("alignment.nex")), "Snapshot does not relocate or copy the input data");
    }

    @Test
    void automaticallyChosenSeedCanReproduceTheRecordedShortChain(@TempDir Path directory) throws Exception {
        Path source = writeModel(directory, "");
        CliResult generated = launch(directory, "run", source.toString(), "--run-name", "generated");
        assertEquals(0, generated.exitCode(), generated.stderr());
        Path first = getOnlyRun(directory);
        JsonNode seeds = readRecord(first).path("seeds");
        long seed = seeds.path("modelBuilding").path("requested").asLong();
        assertTrue(seed > 0);
        assertTrue(seeds.path("modelBuilding").path("generated").asBoolean());
        assertTrue(seeds.path("mcmc").path("generated").asBoolean());
        assertEquals(seed, seeds.path("mcmc").path("requested").asLong());
        CliResult replay =
                launch(directory, "run", source.toString(), "--run-name", "explicit", "--seed", Long.toString(seed));
        assertEquals(0, replay.exitCode(), replay.stderr());
        assertEquals(traceLines(directory.resolve("generated.log")), traceLines(directory.resolve("explicit.log")));
        assertEquals(
                Files.readString(directory.resolve("generated.trees")),
                Files.readString(directory.resolve("explicit.trees")));
    }

    @Test
    void orderlyTerminationRecordsCancellationAndKeepsPartialOutputs(@TempDir Path directory) throws Exception {
        Path source = longModel(directory, 1_000_000_000);
        try (RunningCli child = start(directory, List.of(), "run", source.toString())) {
            Path run = awaitRunning(child, directory);
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
        try (RunningCli child = start(directory, List.of(), "run", source.toString())) {
            Path run = awaitRunning(child, directory);
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
        try (RunningCli child = start(directory, List.of(), "run", source.toString())) {
            Path run = awaitRunning(child, directory);
            Path blockedState = directory.resolve("model with spaces.state.xml");
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
            assertTrue(Files.size(directory.resolve("model with spaces.log")) > 0);
            assertFalse(Files.readString(child.stdout()).contains("Run completed"));
        }
    }

    private static Path longModel(Path directory, int chainLength) throws Exception {
        Path source = writeModel(directory, "");
        Files.writeString(source, Files.readString(source).replace("chainLength=3000", "chainLength=" + chainLength));
        return source;
    }

    private static Path awaitRunning(RunningCli child, Path directory) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline && child.process().isAlive()) {
            if (!getRunRecords(directory).isEmpty()) {
                Path run = getOnlyRun(directory);
                if (Files.isRegularFile(run.resolve("run.json"))
                        && readRecord(run).path("status").asText().equals("running")) return run;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Run did not enter running state: " + Files.readString(child.stderr()));
    }

    private static Path getOnlyRun(Path directory) throws Exception {
        List<Path> records = getRunRecords(directory);
        assertEquals(1, records.size());
        return records.getFirst();
    }

    private static void assertNoRunRecords(Path directory) throws Exception {
        for (Path root : List.of(directory, Path.of("").toAbsolutePath())) {
            assertTrue(getRunRecords(root).isEmpty(), "Unexpected run record in " + root);
        }
    }

    private static List<Path> getRunRecords(Path directory) throws Exception {
        try (var entries = Files.list(directory)) {
            return entries.filter(path -> path.getFileName().toString().endsWith(".run"))
                    .filter(Files::isDirectory)
                    .toList();
        }
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
                    Sampler sampler = mcmc(chainLength=3000)
                    %s
                }
                """.formatted(settings));
        return source;
    }

    private static void assertNativeOutputs(String prefix) throws Exception {
        assertTrace(Path.of(prefix + ".log"));
        assertTrue(Files.readString(Path.of(prefix + ".trees")).contains("STATE_3000"));
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static void assertTrace(Path file) throws Exception {
        List<String> trace = Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(5, trace.size(), "Header and samples 0, 1000, 2000, 3000");
        List<String> columns = List.of(trace.getFirst().trim().split("\\s+"));
        for (int sample = 1; sample < trace.size(); sample++) {
            String[] values = trace.get(sample).trim().split("\\s+");
            assertEquals((sample - 1) * 1000, Integer.parseInt(values[0]));
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
        return launch(directory, jvmOptions, Map.of(), arguments);
    }

    private static CliResult launchWithEnvironment(Path directory, Map<String, String> environment, String... arguments)
            throws Exception {
        return launch(directory, List.of(), environment, arguments);
    }

    private static CliResult launch(
            Path directory, List<String> jvmOptions, Map<String, String> environment, String... arguments)
            throws Exception {
        try (RunningCli child = start(directory, jvmOptions, environment, arguments)) {
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
        return start(directory, jvmOptions, Map.of(), arguments);
    }

    private static RunningCli start(
            Path directory, List<String> jvmOptions, Map<String, String> environment, String... arguments)
            throws Exception {
        Path stdout = Files.createTempFile(directory, "cli-stdout-", ".txt");
        Path stderr = Files.createTempFile(directory, "cli-stderr-", ".txt");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvmOptions);
        command.addAll(List.of("-Djava.only=true", "-cp", classpath, "runner.PhyloSpecCli"));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(environment);
        Process process = builder.directory(directory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        return new RunningCli(process, stdout, stderr);
    }
}
