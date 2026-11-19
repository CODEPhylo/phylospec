package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;
import runner.PhyloSpecRunner;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;

/** Tests the LPhy-style {@code taxa(num=...)} and {@code taxa(names=..., species=..., ages=...)} generators. */
class GeneratedTaxaTileTest {
    private static final long SEED = 17321;

    @TempDir
    Path directory;

    private record Built(BEASTState graph, List<Tile<?, BEASTState>> tiles) {}

    @Test
    void numberedTaxaAreNamedFromOneToNumAndSampledAtAgeZero() throws Exception {
        Built built = build("Taxa samples = taxa(num=4)");
        TaxaData samples = assertInstanceOf(TaxaData.class, value(built, "samples"));

        assertEquals(
                List.of(
                        new TaxonValue("1", null, 0.0),
                        new TaxonValue("2", null, 0.0),
                        new TaxonValue("3", null, 0.0),
                        new TaxonValue("4", null, 0.0)),
                samples);
        assertEquals(List.of("1", "2", "3", "4"), samples.taxonSet().asStringList());
        assertNull(samples.ages());
        assertTrue(built.graph().stateNodes.isEmpty());
    }

    @Test
    void namedTaxaKeepSpeciesAndAgesInOrder() throws Exception {
        Built built = build("""
                Taxa samples = taxa(names=["a", "b", "c"], species=["x", "x", "y"], ages=[0.0, 1.0, 2.5])
                """);
        TaxaData samples = assertInstanceOf(TaxaData.class, value(built, "samples"));

        assertEquals(
                List.of(new TaxonValue("a", "x", 0.0), new TaxonValue("b", "x", 1.0), new TaxonValue("c", "y", 2.5)),
                samples);
        assertEquals(2.5, samples.ages().getValue("c"), 1e-12);
    }

    @Test
    void namedTaxaWithoutOptionalArgumentsAreContemporary() throws Exception {
        TaxaData samples =
                assertInstanceOf(TaxaData.class, value(build("Taxa samples = taxa(names=[\"a\", \"b\"])"), "samples"));

        assertEquals(List.of(new TaxonValue("a", null, 0.0), new TaxonValue("b", null, 0.0)), samples);
        assertNull(samples.ages());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Yule(birthRate=1.0, taxa=taxa(num=5))",
                "BirthDeath(diversificationRate=1.0, turnover=0.5, taxa=taxa(num=5))",
                "Coalescent(populationSize=100.0, taxa=taxa(num=5))",
                "Coalescent(populationSize=constantPopulationFunction(populationSize=100.0), taxa=taxa(num=5))"
            })
    void allTreeConsumersAcceptNumberedTaxa(String distribution) throws Exception {
        Built built = build("Tree simulated ~ " + distribution);
        Tree tree = sampledTree(built);

        Map<String, Double> expected =
                IntStream.rangeClosed(1, 5).boxed().collect(Collectors.toMap(String::valueOf, i -> 0.0));
        assertEquals(expected, tipHeights(tree));
        assertTrue(Double.isFinite(built.graph().priorDistributions.get(tree).calculateLogP()));
    }

    @Test
    void serialNamedTaxaReachTheTreeAsTipHeights() throws Exception {
        Built built = build("""
                Tree simulated ~ Coalescent(populationSize=100.0, taxa=taxa(names=["a", "b", "c"], ages=[0.0, 1.0, 2.0]))
                """);

        assertEquals(Map.of("a", 0.0, "b", 1.0, "c", 2.0), tipHeights(sampledTree(built)));
    }

    @Test
    void existingAlignmentAndTreeOverloadsStillResolve() throws Exception {
        Built built = build("""
                Tree reference = fromNewick(newickString="((a:1.0,b:1.0):1.0,c:2.0);")
                Taxa samples = taxa(reference)
                """);

        assertEquals(
                List.of("a", "b", "c"),
                assertInstanceOf(TaxaData.class, value(built, "samples"))
                        .taxonSet()
                        .asStringList());
    }

    @Test
    void rejectsArgumentsOfTheWrongLength() {
        assertThrows(TileApplicationError.class, () -> build("Taxa samples = taxa(names=[\"a\", \"b\"], ages=[0.0])"));
        assertThrows(
                TileApplicationError.class,
                () -> build("Taxa samples = taxa(names=[\"a\", \"b\"], species=[\"x\", \"y\", \"z\"])"));
    }

    @Test
    void rejectsInvalidCountsAndNames() {
        assertThrows(TypeError.class, () -> build("Taxa samples = taxa(num=0)"));
        assertThrows(TileApplicationError.class, () -> build("Taxa samples = taxa(names=[\"a\", \" \"])"));
        assertThrows(TileApplicationError.class, () -> build("Taxa samples = taxa(names=[\"a\", \"a\"])"));
    }

    @Test
    void managedRunnerSamplesATreeOverNumberedTaxaWithoutInputFiles() throws Exception {
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, """
                Tree simulated ~ Yule(birthRate=1.0, taxa=taxa(num=10))
                mcmc {
                    Integer chainLength = 10
                    Integer defaultLogEvery = 1
                    Integer randomSeed = 42
                }
                """);
        PhyloSpecRunner runner = new PhyloSpecRunner(source, List.of("beast2"));
        assertNotNull(runner.check());
        Path run = directory.resolve("run");
        runner.runManaged("taxa", run, warning -> {});

        var record = new ObjectMapper().readTree(run.resolve("run.json").toFile());
        assertEquals("succeeded", record.path("status").asText());
        assertEquals(0, record.path("inputs").size());
        String trees = Files.readString(run.resolve("output/taxa.trees"));
        assertTrue(trees.contains("STATE_10"));
        for (int i = 1; i <= 10; i++) {
            assertTrue(
                    trees.matches("(?s).*\\b" + i + "\\s+" + i + "[,;\\s].*"), "Missing taxon " + i + " in " + trees);
        }
    }

    private Built build(String source) throws Exception {
        Randomizer.setSeed(SEED);
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("taxa", directory));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return new Built(graph, tiles);
    }

    private static Object value(Built built, String name) {
        return built.tiles().stream()
                .filter(tile -> tile.getRootNode() instanceof Stmt stmt
                        && stmt.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing output " + name))
                .apply(built.graph(), new IdentityHashMap<>());
    }

    private static Tree sampledTree(Built built) {
        assertEquals(1, built.graph().stateNodes.size());
        return assertInstanceOf(
                Tree.class, built.graph().stateNodes.keySet().iterator().next());
    }

    private static Map<String, Double> tipHeights(Tree tree) {
        return tree.getExternalNodes().stream().collect(Collectors.toMap(Node::getID, Node::getHeight));
    }
}
