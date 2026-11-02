package tiles.input;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;

class FromCSVTileTest {
    @TempDir
    Path directory;

    @Test
    void readsDefaultHeadersAndRetainsOrderedRowsAndStringValues() throws Exception {
        write("taxon,species,code\nsampleA,spA,001\nsampleB,spB,002\n");
        List<?> table = assertInstanceOf(List.class, build(tableSource("")));
        assertEquals(
                List.of(
                        Map.of("taxon", "sampleA", "species", "spA", "code", "001"),
                        Map.of("taxon", "sampleB", "species", "spB", "code", "002")),
                table);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void suppliedHeadersKeepTheFirstRecordAsData(boolean named) throws Exception {
        write("sampleA,spA\nsampleB,spB\n");
        String source = named ? "Vector<String> columns = [\"taxon\", \"species\"]\n" : "";
        source += tableSource(", headers=" + (named ? "columns" : "[\"taxon\", \"species\"]"));
        List<?> table = assertInstanceOf(List.class, build(source));
        assertEquals(
                List.of(Map.of("taxon", "sampleA", "species", "spA"), Map.of("taxon", "sampleB", "species", "spB")),
                table);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\t", "||", "."})
    void treatsCustomDelimiterAsALiteralString(String delimiter) throws Exception {
        write("taxon" + delimiter + "species\nsampleA" + delimiter + "spA\n");
        assertEquals(
                List.of(Map.of("taxon", "sampleA", "species", "spA")),
                build(tableSource(", delimiter=\"" + delimiter + "\"")));
    }

    @Test
    void preservesQuotedSeparatorsEscapedQuotesMultilineFieldsAndEmptyValues() throws Exception {
        write("\uFEFFtaxon,description,empty\r\n\r\nsampleA,\"a,b and \"\"quoted\"\"\",\r\n"
                + "sampleB,\"first\r\nsecond\",\"\"\r\n");
        assertEquals(
                List.of(
                        Map.of("taxon", "sampleA", "description", "a,b and \"quoted\"", "empty", ""),
                        Map.of("taxon", "sampleB", "description", "first\r\nsecond", "empty", "")),
                build(tableSource("")));
    }

    @Test
    void preservesWhitespaceInsideFieldValues() throws Exception {
        write("taxon,description\nsampleA,  untrimmed  \n");
        assertEquals(List.of(Map.of("taxon", "sampleA", "description", "  untrimmed  ")), build(tableSource("")));
    }

    @Test
    void headerOnlyAndExplicitHeaderEmptyFilesProduceEmptyTables() throws Exception {
        write("taxon,species\n");
        assertEquals(List.of(), build(tableSource("")));
        write("");
        assertEquals(List.of(), build(tableSource(", headers=[\"taxon\", \"species\"]")));
    }

    @Test
    void countsRowsThroughTheExistingNumGenerator() throws Exception {
        write("sampleA,spA\nsampleB,spB\n");
        Object count = build(tableSource(", headers=[\"taxon\", \"species\"]")
                + "\nNonNegativeInteger count = num(vector=metadata)");
        assertEquals(2, assertInstanceOf(IntScalar.class, count).get());
    }

    @Test
    void supportsNestedAndNamedMapKeysWithExactCase() throws Exception {
        write("taxon,Taxon\nsampleA,otherA\nsampleB,otherB\n");
        assertEquals("sampleB", build(tableSource("") + "\nString value = metadata[2][\"taxon\"]"));
        assertEquals("otherA", build(tableSource("") + """

                Map<String, String> row = metadata[1]
                String key = "Taxon"
                String value = row[key]
                """));
    }

    @Test
    void missingMapKeysAreExplicitErrors() throws Exception {
        write("taxon\nsampleA\n");
        TileApplicationError error = assertThrows(
                TileApplicationError.class, () -> build(tableSource("") + "\nString value = metadata[1][\"missing\"]"));
        assertTrue(error.getMessage().contains("missing"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void rowAccessRetainsOneBasedBoundsChecks(int index) throws Exception {
        write("taxon\nsampleA\nsampleB\n");
        String source = tableSource("") + "\nString value = metadata[" + index + "][\"taxon\"]";
        if (index == 0) {
            // The existing type checker rejects a literal zero before backend indexing.
            assertThrows(TypeError.class, () -> build(source));
        } else {
            assertThrows(TileApplicationError.class, () -> build(source));
        }
    }

    @Test
    void rejectsMultipleIndicesRatherThanIgnoringExtraKeys() throws Exception {
        write("taxon,species\nsampleA,spA\n");
        assertThrows(
                TypeError.class, () -> build(tableSource("") + "\nString value = metadata[1][\"taxon\", \"species\"]"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "taxon,taxon\nsampleA,spA\n",
                "taxon, \nsampleA,spA\n",
                "taxon,species\nsampleA\n",
                "taxon\nsampleA,spA\n",
                "taxon\n\"unterminated",
                "taxon\nsa\"mple\n",
                "taxon\n\"sample\"extra\n"
            })
    void invalidInputReportsTheFileInsteadOfReturningPartialOrOverwrittenData(String contents) throws Exception {
        write(contents);
        TileApplicationError error = assertThrows(TileApplicationError.class, () -> build(tableSource("")));
        assertTrue(error.getMessage().contains("metadata.csv"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\n", "\r"})
    void rejectsInvalidDelimiterStrings(String delimiter) throws Exception {
        write("taxon\nsampleA\n");
        assertThrows(TileApplicationError.class, () -> build(tableSource(", delimiter=\"" + delimiter + "\"")));
    }

    @Test
    void validatesExplicitHeadersToo() throws Exception {
        write("sampleA,spA\n");
        assertThrows(TileApplicationError.class, () -> build(tableSource(", headers=[\"taxon\", \"taxon\"]")));
        assertThrows(TileApplicationError.class, () -> build(tableSource(", headers=[\"taxon\", \" \"]")));
    }

    @Test
    void resolvesRelativeInputAgainstTheSourceDirectoryAndNotifiesTheRunRecorder() throws Exception {
        Path path = write("taxon\nsampleA\n");
        BEASTState graph = new BEASTState("csv", directory);
        Map<String, Path> observed = new LinkedHashMap<>();
        graph.setInputObserver(observed::put);
        build(tableSource(""), graph);
        assertEquals(Map.of("metadata.csv", path.toAbsolutePath().normalize()), observed);
    }

    @Test
    void missingInputReportsTheResolvedPath() {
        TileApplicationError error = assertThrows(TileApplicationError.class, () -> build(tableSource("")));
        assertTrue(error.getMessage().contains(directory.resolve("metadata.csv").toString()), error.getMessage());
    }

    @Test
    void stochasticFileSelectionIsStillRejected() throws Exception {
        write("taxon\nsampleA\n");
        TileApplicationError error = assertThrows(TileApplicationError.class, () -> build("""
                Vector<String> files = ["metadata.csv", "metadata.csv"]
                NonNegativeInteger selected ~ Poisson(rate=2.0)
                Vector<Map<String, String>> metadata = fromCSV(file=files[selected])
                """));
        assertTrue(error.getMessage().contains("file"), error.getMessage());
        assertTrue(error.getMessage().contains("random variable"), error.getMessage());
    }

    @Test
    void stochasticRowSelectionCannotBeFrozenIntoAFixedString() throws Exception {
        write("taxon\nsampleA\nsampleB\n");
        assertEquals("sampleA", build(tableSource("") + """

                PositiveInteger selected = 1
                String name = metadata[selected]["taxon"]
                """));
        assertThrows(TileApplicationError.class, () -> build(tableSource("") + """

                NonNegativeInteger selected ~ Poisson(rate=2.0)
                String name = metadata[selected]["taxon"]
                """));
    }

    @Test
    void cliUsesACsvSelectedAlignmentAndRecordsBothInputs() throws Exception {
        Path modelDirectory = Files.createDirectory(directory.resolve("model files"));
        Path callerDirectory = Files.createDirectory(directory.resolve("caller"));
        Path csv = Files.writeString(modelDirectory.resolve("metadata.csv"), "alignment\nalignment.fa\n");
        Path fasta = Files.writeString(modelDirectory.resolve("alignment.fa"), ">a\nACGT\n>b\nACGA\n>c\nACGG\n");
        Path source = Files.writeString(modelDirectory.resolve("model.phylospec"), """
                Vector<Map<String, String>> metadata = fromCSV(file="metadata.csv")
                Alignment data = fromFasta(file=metadata[1]["alignment"])
                Tree tree ~ Coalescent(populationSize=100.0, taxa=taxa(alignment=data))
                Alignment observed ~ PhyloCTMC(tree=tree, qMatrix=jc69()) observed as data
                mcmc {
                    Integer chainLength = 10
                    Integer defaultLogEvery = 1
                    Integer randomSeed = 42
                }
                """);
        CliResult check = launch(callerDirectory, "check", source.toString());
        assertEquals(0, check.exitCode(), check.stdout() + "\n" + check.stderr());
        assertTrue(check.stdout().contains("Check passed"));
        assertFalse(Files.exists(callerDirectory.resolve("results")));

        CliResult run = launch(callerDirectory, "run", source.toString(), "--out", "results");
        assertEquals(0, run.exitCode(), run.stdout() + "\n" + run.stderr());
        Path results = callerDirectory.resolve("results");
        JsonNode record =
                new ObjectMapper().readTree(results.resolve("run.json").toFile());
        assertEquals("succeeded", record.path("status").asText(), record.toPrettyString());
        assertTrue(record.path("completionConfirmed").asBoolean());
        Map<String, String> recordedHashes = new LinkedHashMap<>();
        for (JsonNode input : record.path("inputs")) {
            assertEquals("recorded", input.path("status").asText(), input.toPrettyString());
            recordedHashes.put(input.path("path").asText(), input.path("sha256").asText());
        }
        assertEquals(Map.of(csv.toString(), hash(csv), fasta.toString(), hash(fasta)), recordedHashes);
        List<String> trace = Files.readAllLines(results.resolve("output/model.log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(12, trace.size(), "Header and samples 0 through 10");
        List<String> columns = List.of(trace.getFirst().trim().split("\\s+"));
        for (int index = 1; index < trace.size(); index++) {
            String[] values = trace.get(index).trim().split("\\s+");
            assertEquals(index - 1, Integer.parseInt(values[0]));
            for (String name : List.of("posterior", "prior", "likelihood")) {
                assertTrue(columns.contains(name), columns.toString());
                assertTrue(Double.isFinite(Double.parseDouble(values[columns.indexOf(name)])));
            }
        }
        assertTrue(Files.readString(results.resolve("output/model.trees")).contains("STATE_10"));
        assertTrue(Files.size(results.resolve("output/model.state.xml")) > 0);
    }

    private Path write(String contents) throws Exception {
        return Files.writeString(directory.resolve("metadata.csv"), contents);
    }

    private static String tableSource(String optionalArguments) {
        return "Vector<Map<String, String>> metadata = fromCSV(file=\"metadata.csv\"" + optionalArguments + ")";
    }

    private Object build(String source) throws Exception {
        return build(source, new BEASTState("csv", directory));
    }

    private Object build(String source, BEASTState graph) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver(), new Workspace(directory)).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        var tiles = evaluator.getBestTiling(statements);
        evaluator.applyBestTiling(graph);
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        assertEquals(1, tiles.size());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        return tiles.getFirst().apply(graph, new IdentityHashMap<>());
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static CliResult launch(Path directory, String... arguments) throws Exception {
        Path stdout = Files.createTempFile(directory, "csv-stdout-", ".txt");
        Path stderr = Files.createTempFile(directory, "csv-stderr-", ".txt");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.only=true",
                "-cp",
                classpath,
                "runner.PhyloSpecCli"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "CSV CLI timed out");
            return new CliResult(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private record CliResult(int exitCode, String stdout, String stderr) {}
}
