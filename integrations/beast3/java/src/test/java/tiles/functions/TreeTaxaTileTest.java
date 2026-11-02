package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.tree.TreeParser;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;
import runner.PhyloSpecRunner;
import tiles.input.DecoratedAlignment;
import tiles.input.FromFastaTile;
import tiles.input.FromNewickTile;
import tiles.input.FromTreeTile;
import tiles.input.TaxaData;

class TreeTaxaTileTest {
    private static final String CONTEMPORARY = "((a:2.0,b:2.0):2.0,c:4.0);";
    private static final String SERIAL = "((a_0:2.0,b_1:1.0):2.0,c_2:2.0);";
    private static final long SEED = 17321;

    @TempDir
    Path directory;

    private record Built(BEASTState graph, List<Tile<?, BEASTState>> tiles) {}

    @Test
    void extractsContemporaryTaxaWithoutAnAlignmentOrSampledState() throws Exception {
        Built built = build(treeTaxaSource(CONTEMPORARY));
        TaxaData samples = result(built);
        Tree reference = cachedTile(built, FromNewickTile.class).apply(built.graph(), new IdentityHashMap<>());

        assertEquals(List.of("a", "b", "c"), samples.taxonSet().asStringList());
        assertNull(samples.taxonSet().alignmentInput.get());
        assertNull(samples.ages());
        assertEquals(
                new TreeParser(CONTEMPORARY).getRoot().toNewick(),
                reference.getRoot().toNewick());
        assertFalse(reference.isEstimatedInput.get());
        assertNoSampledState(built.graph());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nwk", "nex"})
    void extractsSerialSamplingAgesFromRelativeTreeFiles(String extension) throws Exception {
        Path inputs = Files.createDirectories(directory.resolve("data"));
        String contents =
                extension.equals("nex") ? "#NEXUS\nbegin trees;\ntree reference = " + SERIAL + "\nend;\n" : SERIAL;
        Files.writeString(inputs.resolve("reference." + extension), contents);
        Built built = build("""
                Tree reference = fromTree(file="data/reference.%s")
                Taxa samples = taxa(tree=reference)
                """.formatted(extension));
        TaxaData samples = result(built);
        Tree reference = cachedTile(built, FromTreeTile.class).apply(built.graph(), new IdentityHashMap<>());

        assertEquals(List.of("a_0", "b_1", "c_2"), samples.taxonSet().asStringList());
        assertNull(samples.taxonSet().alignmentInput.get());
        assertNotNull(samples.ages());
        assertSame(samples.taxonSet(), samples.ages().taxaInput.get());
        assertEquals(Map.of("a_0", 0.0, "b_1", 1.0, "c_2", 2.0), tipHeights(reference));
        for (Node leaf : reference.getExternalNodes()) {
            assertEquals(leaf.getHeight(), samples.ages().getValue(leaf.getID()), 1e-12);
        }
        assertEquals(
                new TreeParser(SERIAL).getRoot().toNewick(), reference.getRoot().toNewick());
        assertFalse(reference.isEstimatedInput.get());
        assertNoSampledState(built.graph());
    }

    @ParameterizedTest
    @ValueSource(strings = {"age", "date"})
    void alignmentAccessorRetainsTaxonAndSamplingMetadataIdentity(String kind) throws Exception {
        String names = kind.equals("age") ? ">a_0\nACGT\n>b_2\nACGA\n" : ">a_2000\nACGT\n>b_2002\nACGA\n";
        Files.writeString(directory.resolve("samples.fa"), names);
        Built built = build("""
                Alignment data = fromFasta(file="samples.fa", %s=parse(delimiter="_", part=2))
                Taxa samples = taxa(alignment=data)
                """.formatted(kind));
        TaxaData samples = result(built);
        DecoratedAlignment data = cachedTile(built, FromFastaTile.class).apply(built.graph(), new IdentityHashMap<>());

        assertSame(data.taxonSet(), samples.taxonSet());
        assertSame(data.ages(), samples.ages());
        assertEquals(kind, samples.ages().getTraitName());
        assertNoSampledState(built.graph());
    }

    private static Stream<Arguments> treeModels() {
        return Stream.of(
                Arguments.of("Yule(birthRate=1.0, taxa=samples)", false),
                Arguments.of("Yule(birthRate=1.0, rootAge=100.0, taxa=samples)", false),
                Arguments.of("BirthDeath(diversificationRate=1.0, turnover=0.5, taxa=samples)", false),
                Arguments.of(
                        "BirthDeath(diversificationRate=1.0, turnover=0.5, samplingProbability=0.8, rootAge=100.0, taxa=samples)",
                        false),
                Arguments.of("Coalescent(populationSize=100.0, taxa=samples)", false),
                Arguments.of(
                        "Coalescent(populationSize=constantPopulationFunction(populationSize=100.0), taxa=samples)",
                        false),
                Arguments.of("Coalescent(populationSize=100.0, taxa=samples)", true),
                Arguments.of(
                        "Coalescent(populationSize=constantPopulationFunction(populationSize=100.0), taxa=samples)",
                        true));
    }

    @ParameterizedTest
    @MethodSource("treeModels")
    void treeAndAlignmentTaxaGiveTheSameInitialStateAndModelProbability(String distribution, boolean serial)
            throws Exception {
        String newick = serial ? SERIAL : CONTEMPORARY;
        Files.writeString(
                directory.resolve("samples.fa"),
                serial ? ">a_0\nACGT\n>b_1\nACGA\n>c_2\nACGG\n" : ">a\nACGT\n>b\nACGA\n>c\nACGG\n");
        String alignmentSource = "Alignment data = fromFasta(file=\"samples.fa\""
                + (serial ? ", age=parse(delimiter=\"_\", part=2)" : "") + ")\n"
                + "Taxa samples = taxa(alignment=data)\n";
        Built fromTree = build(treeTaxaSource(newick) + "Tree inferred ~ " + distribution);
        Built fromAlignment = build(alignmentSource + "Tree inferred ~ " + distribution);
        Tree tree = sampledTree(fromTree);
        Tree alignmentTree = sampledTree(fromAlignment);
        Tree reference = cachedTile(fromTree, FromNewickTile.class).apply(fromTree.graph(), new IdentityHashMap<>());
        Distribution treePrior = fromTree.graph().priorDistributions.get(tree);
        Distribution alignmentPrior = fromAlignment.graph().priorDistributions.get(alignmentTree);

        assertNotSame(reference, tree);
        assertEquals(
                new TreeParser(newick).getRoot().toNewick(), reference.getRoot().toNewick());
        assertFalse(reference.isEstimatedInput.get());
        assertFalse(fromTree.graph().stateNodes.containsKey(reference));
        assertFalse(fromTree.graph().priorDistributions.containsKey(reference));
        assertEquals(1, fromTree.graph().stateNodes.size());
        assertEquals(1, fromTree.graph().priorDistributions.size());
        assertTrue(fromTree.graph().likelihoodDistributions.isEmpty());
        assertNull(assertInstanceOf(RandomTree.class, tree).taxaInput.get());
        assertNotNull(tree.m_taxonset.get());
        assertEquals(tree.getRoot().toNewick(), alignmentTree.getRoot().toNewick());
        assertEquals(tipHeights(reference), tipHeights(tree));
        assertEquals(alignmentPrior.getClass(), treePrior.getClass());
        double logP = treePrior.calculateLogP();
        assertTrue(Double.isFinite(logP));
        assertEquals(alignmentPrior.calculateLogP(), logP, 1e-12);
        assertEquals(
                fromAlignment.graph().operators.stream()
                        .map(op -> op.getClass().getName())
                        .toList(),
                fromTree.graph().operators.stream()
                        .map(op -> op.getClass().getName())
                        .toList());
        assertEquals(
                fromAlignment.graph().operators.stream()
                        .map(op -> op.getWeight())
                        .toList(),
                fromTree.graph().operators.stream().map(op -> op.getWeight()).toList());
        assertFalse(fromTree.graph().operators.isEmpty());
        assertTrue(fromTree.graph().operators.stream()
                .noneMatch(op -> op.listStateNodes().contains(reference)));
        assertTrue(fromTree.graph().operators.stream()
                .anyMatch(op -> op.listStateNodes().contains(tree)));
    }

    private static Stream<Arguments> coalescentProposals() {
        return Stream.of("100.0", "constantPopulationFunction(populationSize=100.0)")
                .flatMap(population -> Stream.of(Arguments.of(population, false), Arguments.of(population, true)));
    }

    @ParameterizedTest
    @MethodSource("coalescentProposals")
    void treeInputCoalescentRetainsIntervalsAcrossAcceptanceAndRejection(String population, boolean acceptFirst)
            throws Exception {
        Built built = build(
                treeTaxaSource(SERIAL) + "Tree inferred ~ Coalescent(populationSize=" + population + ", taxa=samples)");
        Tree tree = sampledTree(built);
        Coalescent posterior = assertInstanceOf(
                Coalescent.class, built.graph().priorDistributions.get(tree));
        assertSame(tree, posterior.treeIntervalsInput.get().treeInput.get());
        assertNull(posterior.treeInput.get());
        State state = new State();
        state.initByName("stateNode", List.of(tree));
        state.initialise();
        state.setPosterior(posterior);
        assertEquals(referenceCoalescent(tree, posterior), state.robustlyCalcPosterior(posterior), 1e-12);

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
            assertEquals(referenceCoalescent(tree, posterior), proposedLogP, 1e-12);
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
            assertEquals(referenceCoalescent(tree, posterior), posterior.calculateLogP(), 1e-12);
        }
    }

    @Test
    void rejectsRandomTreeInsteadOfTakingAnInitialSnapshot() {
        assertThrows(TileApplicationError.class, () -> build(treeTaxaSource(CONTEMPORARY) + """
                Tree inferred ~ Coalescent(populationSize=100.0, taxa=samples)
                Taxa changingSamples = taxa(tree=inferred)
                """));
    }

    @Test
    void managedRunnerChecksAndRunsTheTreeOnlyWorkflow() throws Exception {
        Path reference = directory.resolve("reference.nwk");
        Files.writeString(reference, CONTEMPORARY);
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, """
                Tree reference = fromTree(file="reference.nwk")
                Taxa samples = taxa(tree=reference)
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
        runner.runManaged("taxa", run, warning -> {});

        var record = new ObjectMapper().readTree(run.resolve("run.json").toFile());
        assertEquals("succeeded", record.path("status").asText());
        assertTrue(record.path("completionConfirmed").asBoolean());
        assertEquals(1, record.path("inputs").size());
        assertEquals(
                reference.toString(), record.path("inputs").get(0).path("path").asText());
        List<String> trace = Files.readAllLines(run.resolve("output/taxa.log")).stream()
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
        assertTrue(Files.readString(run.resolve("output/taxa.trees")).contains("STATE_10"));
        assertTrue(Files.size(run.resolve("output/taxa.state.xml")) > 0);
        assertEquals(CONTEMPORARY, Files.readString(reference));
    }

    @Test
    void extractionDoesNotMutateFixedTreeOrRegisterItAsSampled() {
        Tree tree = fixedTree(SERIAL);
        String before = tree.getRoot().toNewick();
        BEASTState graph = new BEASTState("fixed-taxa", directory);
        TaxaData samples = TreeTaxaTile.fromFixedTree(graph, tree);
        graph.initializeBEASTObjects();

        assertEquals(before, tree.getRoot().toNewick());
        assertFalse(tree.isEstimatedInput.get());
        assertNull(tree.getDateTrait());
        assertEquals(List.of("a_0", "b_1", "c_2"), samples.taxonSet().asStringList());
        assertEquals(1.0, samples.ages().getValue("b_1"), 1e-12);
        assertNoSampledState(graph);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsMissingLeafNames(String name) {
        Tree tree = fixedTree(CONTEMPORARY);
        tree.getNode(0).setID(name);
        assertThrows(TileApplicationError.class, () -> TreeTaxaTile.fromFixedTree(new BEASTState("invalid"), tree));
    }

    @Test
    void rejectsDuplicateLeafNames() {
        Tree tree = fixedTree(CONTEMPORARY);
        tree.getNode(0).setID(tree.getNode(1).getID());
        assertThrows(TileApplicationError.class, () -> TreeTaxaTile.fromFixedTree(new BEASTState("invalid"), tree));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidTipHeights(double height) {
        Tree tree = fixedTree(CONTEMPORARY);
        tree.getNode(0).setHeight(height);
        assertThrows(TileApplicationError.class, () -> TreeTaxaTile.fromFixedTree(new BEASTState("invalid"), tree));
    }

    @Test
    void rejectsPositiveMinimumAgeRatherThanSilentlyChangingTheTimeOrigin() {
        Tree tree = fixedTree(SERIAL);
        for (Node node : tree.getNodesAsArray()) {
            node.setHeight(node.getHeight() + 1.0);
        }
        assertThrows(TileApplicationError.class, () -> TreeTaxaTile.fromFixedTree(new BEASTState("invalid"), tree));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a,b", "a=b", " a", "a  b"})
    void rejectsNamesThatCannotBePreservedInAgeTraits(String name) {
        Tree tree = fixedTree(SERIAL);
        tree.getNode(0).setID(name);
        assertThrows(TileApplicationError.class, () -> TreeTaxaTile.fromFixedTree(new BEASTState("invalid"), tree));
    }

    @Test
    void rejectsAnEstimatedTreeAtTheNativeBoundary() {
        Tree tree = fixedTree(CONTEMPORARY);
        tree.isEstimatedInput.setValue(true, tree);
        assertThrows(TileApplicationError.class, () -> TreeTaxaTile.fromFixedTree(new BEASTState("invalid"), tree));
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("tree-taxa", directory));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return new Built(graph, tiles);
    }

    private static TaxaData result(Built built) {
        assertEquals(1, built.tiles().size());
        return assertInstanceOf(TaxaData.class, built.tiles().getFirst().apply(built.graph(), new IdentityHashMap<>()));
    }

    private static <T> T cachedTile(Built built, Class<T> type) {
        for (Tile<?, BEASTState> tile : built.tiles()) {
            T found = findTile(tile, type);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError("Missing tile " + type.getSimpleName());
    }

    private static <T> T findTile(Tile<?, ?> tile, Class<T> type) {
        if (type.isInstance(tile)) {
            return type.cast(tile);
        }
        try {
            for (Tile<?, ?> child : tile.getWiredUpInputs().values()) {
                T found = findTile(child, type);
                if (found != null) {
                    return found;
                }
            }
        } catch (InconsistentTilingException e) {
            throw new AssertionError("Chosen tiling is inconsistent", e);
        }
        return null;
    }

    private static String treeTaxaSource(String newick) {
        return "Tree reference = fromNewick(newickString=\"" + newick + "\")\n"
                + "Taxa samples = taxa(tree=reference)\n";
    }

    private static Tree fixedTree(String newick) {
        Tree tree = new TreeParser(newick);
        tree.isEstimatedInput.setValue(false, tree);
        return tree;
    }

    private static Tree sampledTree(Built built) {
        assertEquals(1, built.graph().stateNodes.size());
        return assertInstanceOf(
                Tree.class, built.graph().stateNodes.keySet().iterator().next());
    }

    private static Map<String, Double> tipHeights(Tree tree) {
        return tree.getExternalNodes().stream().collect(Collectors.toMap(Node::getID, Node::getHeight));
    }

    private static void assertNoSampledState(BEASTState graph) {
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    private static double referenceCoalescent(Tree tree, Coalescent posterior) {
        Tree copy = new Tree(tree.getRoot().copy());
        TreeIntervals intervals = new TreeIntervals();
        intervals.initByName("tree", copy);
        Coalescent reference = new Coalescent();
        reference.initByName("treeIntervals", intervals, "populationModel", posterior.popSizeInput.get());
        double expected = reference.calculateLogP();
        assertTrue(Double.isFinite(expected));
        return expected;
    }
}
