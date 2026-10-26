package runner;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.util.Randomizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.TypeError;

class PhyloSpecRunnerTest {
    private static final List<String> LIBRARIES = List.of("beast2");
    private static final String CONSTRAINT_WARNING = "Real x ~ Uniform(lower=2.0, upper=1.0)\n";
    private static final String PHYLOGENETIC_MODEL = """
            Alignment data = fromNexus("alignment.nex")
            Tree tree ~ Yule(birthRate=1.0, taxa=taxa(data))
            QMatrix qMatrix = jc69()
            Alignment alignment ~ PhyloCTMC(tree, qMatrix) observed as data
            """;

    @Test
    void reportsLexicalErrorWithoutExiting() {
        PhyloSpecException failure = assertThrows(PhyloSpecException.class, () -> runner("String x = \"unterminated")
                .check());

        assertDiagnostic(failure, PhyloSpecDiagnostic.Stage.LEXING);
        assertInstanceOf(org.phylospec.errors.Error.class, failure.getCause());
    }

    @Test
    void rejectsIncompleteSyntaxInsteadOfCheckingARecoveredScript() {
        for (String source : List.of("Real x =", "mcmc { Integer chainLength = 10")) {
            PhyloSpecException failure =
                    assertThrows(PhyloSpecException.class, () -> runner(source).check());
            assertDiagnostic(failure, PhyloSpecDiagnostic.Stage.PARSING);
            assertInstanceOf(org.phylospec.errors.Error.class, failure.getCause());
        }
    }

    @Test
    void preservesConstantEvaluationFailureEvenWithoutAnAstRange() {
        PhyloSpecException failure = assertThrows(
                PhyloSpecException.class, () -> runner("Integer x = 1 / 0").check());

        assertEquals(
                PhyloSpecDiagnostic.Stage.SIMPLIFICATION, failure.diagnostic().stage());
        assertInstanceOf(ArithmeticException.class, failure.getCause());
        assertNull(failure.diagnostic().error().range());
        assertTrue(failure.diagnostic().format().contains("constant expression"));
    }

    @Test
    void preservesTypeErrorCauseAndSourceFile(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("invalid.phylospec");
        Files.writeString(source, "Integer x = \"wrong\"");
        PhyloSpecException failure =
                assertThrows(PhyloSpecException.class, () -> new PhyloSpecRunner(source, LIBRARIES).check());

        assertDiagnostic(failure, PhyloSpecDiagnostic.Stage.TYPE_CHECKING);
        assertInstanceOf(TypeError.class, failure.getCause());
        assertEquals(source.toAbsolutePath().normalize(), failure.diagnostic().sourceFile());
        assertTrue(failure.diagnostic().format().contains(source.toString()));
    }

    @Test
    void distinguishesMissingBackendMappingFromTypeErrors() throws Exception {
        String source = Files.readString(Path.of("src/test/java/tiling/functions/sqrtOfRVNoMatch.phylospec"));
        PhyloSpecException failure =
                assertThrows(PhyloSpecException.class, () -> runner(source).check());

        assertDiagnostic(failure, PhyloSpecDiagnostic.Stage.MAPPING);
        assertInstanceOf(TileApplicationError.class, failure.getCause());
        assertTrue(failure.diagnostic().error().hint().contains("random variable"));
    }

    @Test
    void returnsWarningsWithoutChangingSeverityOrAccumulatingThem() throws Exception {
        PhyloSpecRunner runner = runner(CONSTRAINT_WARNING);
        List<PhyloSpecDiagnostic> warnings = runner.check().warnings();

        assertFalse(warnings.isEmpty());
        assertTrue(
                warnings.stream().allMatch(w -> w.warning() && w.stage() == PhyloSpecDiagnostic.Stage.TYPE_CHECKING));
        assertNotNull(warnings.getFirst().error().range());
        assertThrows(UnsupportedOperationException.class, warnings::clear);
        assertEquals(warnings.size(), runner.check().warnings().size());
    }

    @Test
    void preservesWarningsReportedBeforeAFatalError() {
        PhyloSpecException failure =
                assertThrows(PhyloSpecException.class, () -> runner(CONSTRAINT_WARNING + "Integer y = \"wrong\"")
                        .check());

        assertDiagnostic(failure, PhyloSpecDiagnostic.Stage.TYPE_CHECKING);
        assertFalse(failure.warnings().isEmpty());
        assertTrue(failure.warnings().stream().allMatch(PhyloSpecDiagnostic::warning));
        assertThrows(
                UnsupportedOperationException.class, () -> failure.warnings().clear());
    }

    @Test
    void checkDoesNotApplyNativeInputsWriteOutputsOrResetRandomStream(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("model.phylospec");
        String prefix = directory.resolve("analysis").toString();
        Files.writeString(source, PHYLOGENETIC_MODEL + mcmc(prefix));
        // alignment.nex deliberately does not exist: static mapping must not apply the input tile.
        Randomizer.setSeed(2468);
        double expected = Randomizer.nextDouble();
        Randomizer.setSeed(2468);

        new PhyloSpecRunner(source, LIBRARIES).check();

        assertEquals(expected, Randomizer.nextDouble());
        try (var files = Files.list(directory)) {
            assertEquals(List.of(source), files.toList());
        }
    }

    @Test
    void runUsesTheSameFrontendDiagnostics(@TempDir Path directory) {
        PhyloSpecRunner runner = runner("Integer x = \"wrong\"");
        PhyloSpecException checked = assertThrows(PhyloSpecException.class, runner::check);
        PhyloSpecException executed = assertThrows(
                PhyloSpecException.class,
                () -> runner.runPhyloSpec(directory.resolve("analysis").toString()));

        assertEquals(checked.diagnostic().stage(), executed.diagnostic().stage());
        assertEquals(
                checked.diagnostic().error().range(),
                executed.diagnostic().error().range());
        assertEquals(
                checked.diagnostic().error().description(),
                executed.diagnostic().error().description());
        assertFalse(Files.exists(directory.resolve("analysis.log")));
    }

    @Test
    void forwardsWarningsToTheCallerBeforeRunFails(@TempDir Path directory) {
        List<PhyloSpecDiagnostic> warnings = new ArrayList<>();
        PhyloSpecException failure =
                assertThrows(PhyloSpecException.class, () -> runner(CONSTRAINT_WARNING + "Integer y = \"wrong\"")
                        .runPhyloSpec(directory.resolve("analysis").toString(), warnings::add));

        assertFalse(warnings.isEmpty());
        assertEquals(failure.warnings(), warnings);
    }

    @Test
    void inputApplicationFailureRemainsAnExecutionError(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, PHYLOGENETIC_MODEL);
        PhyloSpecRunner runner = new PhyloSpecRunner(source, LIBRARIES);
        assertDoesNotThrow(runner::check);

        PhyloSpecException failure = assertThrows(
                PhyloSpecException.class,
                () -> runner.runPhyloSpec(directory.resolve("analysis").toString()));
        assertEquals(
                PhyloSpecDiagnostic.Stage.MODEL_BUILDING, failure.diagnostic().stage());
        assertInstanceOf(TileApplicationError.class, failure.getCause());
        assertDoesNotThrow(() -> failure.diagnostic().format());
        assertFalse(Files.exists(directory.resolve("analysis.log")));
    }

    @Test
    void missingLibraryIsNotReportedAsASourceError() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new PhyloSpecRunner("Real x = 1.0", List.of("missing-library")).check());
        assertTrue(failure.getMessage().contains("missing-library"));
    }

    @Test
    void legacyRunStillExecutesTheBasicPhylogeneticModel(@TempDir Path directory) throws Exception {
        Files.copy(Path.of("src/test/java/resources/primate-mtDNA.nex"), directory.resolve("alignment.nex"));
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, PHYLOGENETIC_MODEL + mcmc(null));
        String prefix = directory.resolve("analysis").toString();

        new PhyloSpecRunner(source, LIBRARIES).runPhyloSpec(prefix);

        List<String> trace = Files.readAllLines(directory.resolve("analysis.log")).stream()
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
        assertTrue(Files.readString(directory.resolve("analysis.trees")).contains("STATE_30"));
        assertTrue(Files.size(directory.resolve("analysis.state.xml")) > 0);
    }

    private static PhyloSpecRunner runner(String source) {
        return new PhyloSpecRunner(source, LIBRARIES);
    }

    private static String mcmc(String prefix) {
        return """
                mcmc {
                    Integer chainLength = 30
                    Integer defaultLogEvery = 10
                    Integer randomSeed = 42
                    %s
                }
                """.formatted(prefix == null ? "" : "String outputPrefix = \"" + prefix + "\"");
    }

    private static void assertDiagnostic(PhyloSpecException failure, PhyloSpecDiagnostic.Stage stage) {
        assertEquals(stage, failure.diagnostic().stage());
        assertFalse(failure.diagnostic().warning());
        assertNotNull(failure.diagnostic().error().range());
        assertFalse(failure.diagnostic().error().description().isBlank());
        assertDoesNotThrow(() -> failure.diagnostic().format());
    }
}
