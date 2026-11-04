package tiles.trees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import beast.base.evolution.tree.Tree;
import beast.base.inference.Operator;
import beast.base.spec.evolution.tree.coalescent.BayesianSkyline;
import beast.base.spec.inference.distribution.MarkovChainDistribution;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.parameter.IntSimplexParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beastconfig.BEASTState;
import beastconfig.UniformComposition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import org.phylospec.tiling.TileCatalog;

/**
 * Tiles for the classic Bayesian skyline as used by the CRC09 single-cell model:
 * RandomComposition (group sizes), ExponentialMarkovChain (population sizes) and the
 * groupSizes overload of SkylineCoalescent.
 */
class BayesianSkylineTileTest {

    private static final String DATA = "src/test/java/resources/primate-mtDNA.nex"; // 12 taxa

    private static final String SKYLINE_MODEL = """
            Alignment data = fromNexus("%s")
            Taxa taxa = taxa(data)
            PositiveInteger numGroups = 4
            Vector<PositiveInteger> groupSizes ~ RandomComposition(total=numTaxa(data) - 1, numParts=numGroups)
            PositiveReal initialMean ~ LogNormal(logMean=0.0, logSd=1.0)
            Vector<PositiveReal> populationSizes ~ ExponentialMarkovChain(initialMean=initialMean, numValues=numGroups)
            Tree tree ~ SkylineCoalescent(populationSizes=populationSizes, groupSizes=groupSizes, taxa=taxa)
            """;

    @TempDir
    Path directory;

    @Test
    void randomCompositionIsAnIntSimplexWithUniformPriorAndSumPreservingOperator() throws Exception {
        BEASTState state = evaluate("Vector<PositiveInteger> groups ~ RandomComposition(total=11, numParts=4)");

        IntSimplexParam<?> groups = stateNode(state, "groups", IntSimplexParam.class);
        assertEquals(4, groups.size());
        assertEquals(11, groups.expectedSum());
        assertEquals(List.of(3, 3, 3, 2), List.of(groups.get(0), groups.get(1), groups.get(2), groups.get(3)));

        UniformComposition prior = assertInstanceOf(UniformComposition.class, state.priorDistributions.get(groups));
        assertSame(groups, prior.parameterInput.get());
        // C(10, 3) = 120 compositions of 11 into 4 positive parts
        assertEquals(-Math.log(120.0), prior.calculateLogP(), 1e-12);

        groups.set(0, 4);
        assertEquals(Double.NEGATIVE_INFINITY, prior.calculateLogP(), "compositions with the wrong sum have zero density");

        List<Operator> operators = state.operators;
        assertEquals(1, operators.size());
        DeltaExchangeOperator operator = assertInstanceOf(DeltaExchangeOperator.class, operators.getFirst());
        assertTrue(operator.integerOperatorInput.get());
    }

    @Test
    void randomCompositionRejectsMorePartsThanTotal() {
        Exception failure = assertThrows(
                Exception.class, () -> evaluate("Vector<PositiveInteger> groups ~ RandomComposition(total=3, numParts=5)"));
        assertTrue(failure.getMessage().contains("numParts <= total"), failure.getMessage());
    }

    @Test
    void exponentialMarkovChainMatchesTheExponentialChainDensity() throws Exception {
        BEASTState state = evaluate("""
                PositiveReal initialMean ~ LogNormal(logMean=0.0, logSd=1.0)
                Vector<PositiveReal> sizes ~ ExponentialMarkovChain(initialMean=initialMean, numValues=3)
                """);

        RealVectorParam<?> sizes = stateNode(state, "sizes", RealVectorParam.class);
        RealScalarParam<?> initialMean = stateNode(state, "initialMean", RealScalarParam.class);
        MarkovChainDistribution chain =
                assertInstanceOf(MarkovChainDistribution.class, state.priorDistributions.get(sizes));
        assertSame(sizes, chain.paramInput.get());
        assertSame(initialMean, chain.initialMeanInput.get());

        initialMean.set(2.0);
        sizes.set(0, 1.5);
        sizes.set(1, 0.5);
        sizes.set(2, 3.0);
        chain.initAndValidate();
        double expected = exponentialLogDensity(1.5, 2.0) + exponentialLogDensity(0.5, 1.5)
                + exponentialLogDensity(3.0, 0.5);
        assertEquals(expected, chain.calculateLogP(), 1e-10);
    }

    @Test
    void skylineCoalescentWithGroupSizesBuildsABayesianSkyline() throws Exception {
        BEASTState state = evaluate(SKYLINE_MODEL.formatted(absolute(DATA)));

        Tree tree = stateNode(state, "tree", Tree.class);
        IntSimplexParam<?> groupSizes = stateNode(state, "groupSizes", IntSimplexParam.class);
        RealVectorParam<?> populationSizes = stateNode(state, "populationSizes", RealVectorParam.class);

        BayesianSkyline skyline = assertInstanceOf(BayesianSkyline.class, state.priorDistributions.get(tree));
        assertSame(groupSizes, skyline.groupSizeParamInput.get());
        assertSame(populationSizes, skyline.popSizeParamInput.get());
        assertEquals(11, groupSizes.expectedSum(), "12 taxa have 11 coalescent events");
        assertTrue(Double.isFinite(skyline.calculateLogP()));
    }

    @Test
    void skylineCoalescentRejectsGroupSizesWithTheWrongSum() {
        Exception failure = assertThrows(Exception.class, () -> evaluate("""
                Alignment data = fromNexus("%s")
                Vector<PositiveInteger> groupSizes ~ RandomComposition(total=7, numParts=2)
                Tree tree ~ SkylineCoalescent(populationSizes=[1.0, 2.0], groupSizes=groupSizes, taxa=taxa(data))
                """.formatted(absolute(DATA))));
        assertTrue(failure.getMessage().contains("must sum to the number of coalescent events"), failure.getMessage());
    }

    @Test
    void skylineModelRunsAndKeepsGroupSizesAValidComposition() throws Exception {
        Files.copy(Path.of(DATA), this.directory.resolve("alignment.nex"));
        Path source = this.directory.resolve("model.phylospec");
        Files.writeString(source, SKYLINE_MODEL.formatted("alignment.nex") + """
                QMatrix qMatrix = hky(kappa=2.0, baseFrequencies=[0.25, 0.25, 0.25, 0.25])
                Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=qMatrix) observed as data

                mcmc {
                    Integer chainLength = 20000
                    Integer defaultLogEvery = 1000
                }
                """);
        String prefix = this.directory.resolve("analysis").toString();

        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec(prefix);

        List<String> trace = Files.readAllLines(this.directory.resolve("analysis.log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> columns = List.of(trace.getFirst().trim().split("\\s+"));
        List<Integer> groupColumns = java.util.stream.IntStream.range(0, columns.size())
                .filter(i -> columns.get(i).startsWith("groupSizes"))
                .boxed()
                .toList();
        assertEquals(4, groupColumns.size(), columns.toString());

        for (String row : trace.subList(1, trace.size())) {
            String[] values = row.trim().split("\\s+");
            assertTrue(Double.isFinite(Double.parseDouble(values[columns.indexOf("posterior")])), row);
            int sum = 0;
            for (int column : groupColumns) {
                int value = (int) Double.parseDouble(values[column]);
                assertTrue(value >= 1, row);
                sum += value;
            }
            assertEquals(11, sum, row);
        }
    }

    private static String absolute(String path) {
        return Path.of(path).toAbsolutePath().toString();
    }

    private static double exponentialLogDensity(double x, double mean) {
        return -Math.log(mean) - x / mean;
    }

    private static <T> T stateNode(BEASTState state, String id, Class<T> type) {
        return type.cast(state.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no state node " + id + " in " + state.stateNodes.keySet())));
    }

    private BEASTState evaluate(String source) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState state = evaluator.applyBestTiling(new BEASTState(this.directory.resolve("test").toString(), this.directory));
        catalog.configureState(state);
        state.initializeBEASTObjects();
        return state;
    }
}
