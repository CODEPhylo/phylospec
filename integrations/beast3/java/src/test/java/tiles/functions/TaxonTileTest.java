package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.State;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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

class TaxonTileTest {
    private static final long SEED = 17321;
    private static final String MANUAL = """
            Taxa samples = [
                taxon(name="a_0", species="population_A"),
                taxon(name="b_1", species="population_A", age=1.0),
                taxon(name="c_2", species="population_A", age=2.0)
            ]
            """;

    @TempDir
    Path directory;

    private record Built(BEASTState graph, List<Tile<?, BEASTState>> tiles) {}

    @Test
    void constructorDefaultsAndOptionalSpeciesArePreserved() throws Exception {
        Built defaults = build("Taxon sample = taxon(name=\"sample_A\")");
        assertEquals(new TaxonValue("sample_A", null, 0.0), value(defaults, "sample"));
        assertTrue(defaults.graph().stateNodes.isEmpty());
        assertTrue(defaults.graph().priorDistributions.isEmpty());
        assertTrue(defaults.graph().operators.isEmpty());

        Built explicit = build("Taxon sample = taxon(name=\"sample_A\", species=\"species_A\", age=2.5)");
        assertEquals(new TaxonValue("sample_A", "species_A", 2.5), value(explicit, "sample"));
    }

    @ParameterizedTest
    @CsvSource({"NonNegativeReal, 0.0", "PositiveReal, 1.5", "Probability, 0.5"})
    void acceptsFixedAgeFromCompatibleNumericDomains(String domain, double age) throws Exception {
        Built built = build("""
                %s samplingAge = %s
                Taxon sample = taxon(name="sample_A", age=samplingAge)
                """.formatted(domain, age));
        assertEquals(
                age, assertInstanceOf(TaxonValue.class, value(built, "sample")).age(), 1e-12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"manual", "tree", "alignment-age", "alignment-date"})
    void countIndexAndAccessorsShareTheSameTaxaWithATreeModel(String origin) throws Exception {
        Built built = build(taxaSource(origin) + """
                NonNegativeInteger count = num(vector=samples)
                String firstName = name(taxon=samples[1])
                NonNegativeReal middleAge = age(taxon=samples[2])
                Tree inferred ~ Coalescent(populationSize=100.0, taxa=samples)
                """);
        assertEquals(3, assertInstanceOf(IntScalar.class, value(built, "count")).get());
        assertEquals(origin.equals("alignment-date") ? "a_2002" : "a_0", value(built, "firstName"));
        assertEquals(
                1.0,
                assertInstanceOf(RealScalar.class, value(built, "middleAge")).get(),
                1e-12);
        Map<String, Double> expected = origin.equals("alignment-date")
                ? Map.of("a_2002", 0.0, "b_2001", 1.0, "c_2000", 2.0)
                : Map.of("a_0", 0.0, "b_1", 1.0, "c_2", 2.0);
        assertEquals(expected, tipHeights(sampledTree(built)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tree", "alignment-age", "alignment-date"})
    void importedTaxaRemainReadOnlyAndReuseNativeMetadata(String origin) throws Exception {
        Built built = build(taxaSource(origin));
        TaxaData samples = assertInstanceOf(TaxaData.class, value(built, "samples"));
        var originalTaxonSet = samples.taxonSet();
        var originalAges = samples.ages();
        List<TaxonValue> before = List.copyOf(samples);

        assertSame(samples, TaxaData.fromList(built.graph(), samples));
        assertSame(originalTaxonSet, samples.taxonSet());
        assertSame(originalAges, samples.ages());
        assertEquals(before, samples);
        assertEquals(
                List.of(0.0, 1.0, 2.0), samples.stream().map(TaxonValue::age).toList());
        assertThrows(UnsupportedOperationException.class, () -> samples.set(0, new TaxonValue("other", null, 0.0)));
        assertEquals(
                origin.equals("alignment-date") ? "date" : "age", samples.ages().getTraitName());
        assertTrue(built.graph().stateNodes.isEmpty());
    }

    @Test
    void manualListKeepsSpeciesAndAgesWhenConvertedToNativeInputs() throws Exception {
        Built built = build(MANUAL);
        List<?> list = assertInstanceOf(List.class, value(built, "samples"));
        List<TaxonValue> samples = list.stream()
                .map(item -> assertInstanceOf(TaxonValue.class, item))
                .toList();
        TaxaData nativeInputs = TaxaData.fromList(built.graph(), samples);
        assertEquals(samples, nativeInputs);
        assertEquals(List.of("a_0", "b_1", "c_2"), nativeInputs.taxonSet().asStringList());
        assertSame(nativeInputs.taxonSet(), nativeInputs.ages().taxaInput.get());
        for (TaxonValue sample : samples) {
            assertEquals("population_A", sample.species());
            assertEquals(sample.age(), nativeInputs.ages().getValue(sample.name()), 1e-12);
        }
        assertTrue(built.graph().stateNodes.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Yule(birthRate=1.0, taxa=samples)",
                "BirthDeath(diversificationRate=1.0, turnover=0.5, taxa=samples)",
                "Coalescent(populationSize=100.0, taxa=samples)",
                "Coalescent(populationSize=constantPopulationFunction(populationSize=100.0), taxa=samples)"
            })
    void allTreeConsumersAcceptManualContemporaryTaxa(String distribution) throws Exception {
        Built built = build("""
                Taxa samples = [taxon(name="a"), taxon(name="b"), taxon(name="c")]
                Tree inferred ~ %s
                """.formatted(distribution));
        Tree tree = sampledTree(built);
        assertEquals(Map.of("a", 0.0, "b", 0.0, "c", 0.0), tipHeights(tree));
        assertTrue(Double.isFinite(built.graph().priorDistributions.get(tree).calculateLogP()));
    }

    private static Stream<Arguments> coalescentProposals() {
        return Stream.of("100.0", "exponentialPopulationFunction(populationSize=100.0, growthRate=0.1)")
                .flatMap(population -> Stream.of(Arguments.of(population, false), Arguments.of(population, true)));
    }

    @ParameterizedTest
    @MethodSource("coalescentProposals")
    void serialManualTaxaMatchNativeCoalescentAcrossAcceptanceAndRejection(String population, boolean acceptFirst)
            throws Exception {
        Built built = build(MANUAL + "Tree inferred ~ Coalescent(populationSize=" + population + ", taxa=samples)");
        Tree tree = sampledTree(built);
        Coalescent posterior = assertInstanceOf(
                Coalescent.class, built.graph().priorDistributions.get(tree));
        assertEquals(Map.of("a_0", 0.0, "b_1", 1.0, "c_2", 2.0), tipHeights(tree));
        assertSame(tree, posterior.treeIntervalsInput.get().treeInput.get());
        assertNull(posterior.treeInput.get());
        State state = new State();
        state.initByName("stateNode", List.of(tree));
        state.initialise();
        state.setPosterior(posterior);
        assertEquals(nativeLogP(tree, posterior), state.robustlyCalcPosterior(posterior), 1e-12);

        for (int proposal = 0; proposal < 2; proposal++) {
            boolean accept = proposal == 0 ? acceptFirst : !acceptFirst;
            double previousHeight = tree.getRoot().getHeight();
            double previousLogP = posterior.getCurrentLogP();
            double proposedHeight = previousHeight + 0.5;
            state.store(proposal + 1);
            tree.getRoot().setHeight(proposedHeight);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double proposedLogP = posterior.calculateLogP();
            assertEquals(nativeLogP(tree, posterior), proposedLogP, 1e-12);
            assertNotEquals(previousLogP, proposedLogP, 1e-12);
            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            assertEquals(
                    accept ? proposedHeight : previousHeight, tree.getRoot().getHeight(), 1e-12);
            assertEquals(accept ? proposedLogP : previousLogP, posterior.getCurrentLogP(), 1e-12);
            assertEquals(nativeLogP(tree, posterior), posterior.calculateLogP(), 1e-12);
            assertEquals(Map.of("a_0", 0.0, "b_1", 1.0, "c_2", 2.0), tipHeights(tree));
        }
    }

    @Test
    void positiveMinimumAgeIsPreservedByListsButRejectedAtTheTreeBoundary() throws Exception {
        String source = """
                Taxa samples = [taxon(name="a", age=2.0), taxon(name="b", age=3.0)]
                """;
        Built built = build(source + """
                NonNegativeReal firstAge = age(taxon=samples[1])
                NonNegativeReal secondAge = age(taxon=samples[2])
                NonNegativeInteger count = num(vector=samples)
                """);
        assertEquals(
                2.0,
                assertInstanceOf(RealScalar.class, value(built, "firstAge")).get(),
                1e-12);
        assertEquals(
                3.0,
                assertInstanceOf(RealScalar.class, value(built, "secondAge")).get(),
                1e-12);
        assertEquals(2, assertInstanceOf(IntScalar.class, value(built, "count")).get());
        assertThrows(
                TileApplicationError.class,
                () -> build(source + "Tree inferred ~ Coalescent(populationSize=100.0, taxa=samples)"));
    }

    @Test
    void duplicateNamesAreValidListValuesButNotValidTreeInputs() throws Exception {
        String source = "Taxa samples = [taxon(name=\"a\"), taxon(name=\"a\")]\n";
        assertEquals(
                2, assertInstanceOf(List.class, value(build(source), "samples")).size());
        assertThrows(
                TileApplicationError.class,
                () -> build(source + "Tree inferred ~ Coalescent(populationSize=100.0, taxa=samples)"));
    }

    @Test
    void rejectsRandomAgeRatherThanTakingAnInitialSnapshot() {
        assertThrows(TileApplicationError.class, () -> build("""
                PositiveReal sampledAge ~ LogNormal(logMean=0.0, logSd=1.0)
                Taxon sample = taxon(name="sample_A", age=sampledAge)
                """));
    }

    @Test
    void rejectsMissingNameAndNegativeAgeThroughCoreValidation() {
        assertThrows(TypeError.class, () -> build("Taxon sample = taxon(age=0.0)"));
        assertThrows(TypeError.class, () -> build("Taxon sample = taxon(name=\"a\", age=-1.0)"));
        assertThrows(TileApplicationError.class, () -> build("Taxon sample = taxon(name=\" \" )"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void valueRejectsInvalidAges(double age) {
        assertThrows(IllegalArgumentException.class, () -> new TaxonValue("sample_A", null, age));
    }

    @Test
    void managedRunnerChecksAndRunsManualTaxaWithoutExternalInputFiles() throws Exception {
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, MANUAL + """
                Tree inferred ~ Coalescent(populationSize=100.0, taxa=samples)
                mcmc {
                    Integer chainLength = 10
                    Integer defaultLogEvery = 1
                    Integer randomSeed = 42
                }
                """);
        PhyloSpecRunner runner = new PhyloSpecRunner(source, List.of("beast2"));
        assertNotNull(runner.check());
        Path run = directory.resolve("run");
        runner.runManaged("taxon", run, warning -> {});
        var record = new ObjectMapper().readTree(run.resolve("run.json").toFile());
        assertEquals("succeeded", record.path("status").asText());
        assertTrue(record.path("completionConfirmed").asBoolean());
        assertEquals(0, record.path("inputs").size());
        List<String> trace = Files.readAllLines(run.resolve("output/taxon.log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(12, trace.size(), "Header and samples 0 through 10");
        List<String> columns = List.of(trace.getFirst().trim().split("\\s+"));
        for (int i = 1; i < trace.size(); i++) {
            String[] values = trace.get(i).trim().split("\\s+");
            assertEquals(i - 1, Integer.parseInt(values[0]));
            for (String name : List.of("posterior", "prior", "likelihood")) {
                assertTrue(columns.contains(name), columns.toString());
                assertTrue(Double.isFinite(Double.parseDouble(values[columns.indexOf(name)])));
            }
        }
        assertTrue(Files.readString(run.resolve("output/taxon.trees")).contains("STATE_10"));
        assertTrue(Files.size(run.resolve("output/taxon.state.xml")) > 0);
    }

    private String taxaSource(String origin) throws Exception {
        if (origin.equals("manual")) return MANUAL;
        if (origin.equals("tree")) return """
                Tree reference = fromNewick(newickString="((a_0:2.0,b_1:1.0):2.0,c_2:2.0);")
                Taxa samples = taxa(tree=reference)
                """;
        boolean dates = origin.equals("alignment-date");
        Files.writeString(
                directory.resolve("samples.fa"),
                dates ? ">a_2002\nACGT\n>b_2001\nACGA\n>c_2000\nACGG\n" : ">a_0\nACGT\n>b_1\nACGA\n>c_2\nACGG\n");
        return """
                Alignment data = fromFasta(file="samples.fa", %s=parse(delimiter="_", part=2))
                Taxa samples = taxa(alignment=data)
                """.formatted(dates ? "date" : "age");
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("taxon", directory));
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

    private static double nativeLogP(Tree tree, Coalescent posterior) {
        TreeIntervals intervals = new TreeIntervals();
        intervals.initByName("tree", new Tree(tree.getRoot().copy()));
        Coalescent reference = new Coalescent();
        reference.initByName("treeIntervals", intervals, "populationModel", posterior.popSizeInput.get());
        double expected = reference.calculateLogP();
        assertTrue(Double.isFinite(expected));
        return expected;
    }
}
