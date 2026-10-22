package tiles.trees;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.State;
import beastconfig.BEASTState;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

class CoalescentTileTest {
    private static Stream<String> populationSizes() {
        return Stream.of(
                "100.0",
                "constantPopulationFunction(populationSize=100.0)",
                "exponentialPopulationFunction(populationSize=100.0, growthRate=0.1)");
    }

    private static Stream<Arguments> treeProposals() {
        return populationSizes()
                .flatMap(populationSize ->
                        Stream.of(Arguments.of(populationSize, false), Arguments.of(populationSize, true)));
    }

    @ParameterizedTest
    @MethodSource("populationSizes")
    void treeIntervalsArePartOfTheCalculationGraph(String populationSize) throws Exception {
        Model model = build(populationSize);
        assertNull(model.posterior().treeInput.get(), "BEAST's tree and treeIntervals inputs are alternatives.");
        TreeIntervals intervals = model.posterior().treeIntervalsInput.get();
        assertNotNull(intervals, "Intervals must be reachable through BEAST Inputs, not hidden inside Coalescent.");
        assertSame(model.tree(), intervals.treeInput.get());
        assertTrue(model.tree().getOutputs().contains(intervals));
        assertTrue(intervals.getOutputs().contains(model.posterior()));
    }

    @ParameterizedTest
    @MethodSource("treeProposals")
    void priorFollowsAcceptedAndRejectedTreeProposals(String populationSize, boolean acceptFirst) throws Exception {
        // Keep numerical checks independent of the graph assertions: the old binding must also
        // fail because it calculates a stale prior, not only because its inputs look different.
        Model model = build(populationSize);
        Tree tree = model.tree();
        Coalescent posterior = model.posterior();
        State state = new State();
        state.initByName("stateNode", List.of(tree));
        state.initialise();
        state.setPosterior(posterior);
        double initial = state.robustlyCalcPosterior(posterior);
        assertEquals(referenceLogP(tree, posterior), initial, 1e-12);

        // Exercise both accept -> reject and reject -> accept, including a proposal after rollback.
        for (int proposal = 0; proposal < 2; proposal++) {
            boolean accept = proposal == 0 ? acceptFirst : !acceptFirst;
            double previousHeight = tree.getRoot().getHeight();
            double previousLogP = posterior.getCurrentLogP();
            double proposedHeight = previousHeight + (proposal == 0 ? 0.5 : 0.25);

            state.store(proposal + 1);
            tree.getRoot().setHeight(proposedHeight);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double proposed = posterior.calculateLogP();
            double expected = referenceLogP(tree, posterior);
            assertNotEquals(previousLogP, expected, 1e-12, "The proposal must change the reference tree prior.");
            assertEquals(expected, proposed, 1e-12, "The prior must use the proposed tree's intervals.");

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            assertEquals(
                    accept ? proposedHeight : previousHeight, tree.getRoot().getHeight());
            assertEquals(accept ? proposed : previousLogP, posterior.getCurrentLogP(), 1e-12);

            // Do not mark anything dirty here: recalculating must also be correct using the
            // accepted/restored interval cache, not just the restored Distribution.logP value.
            assertEquals(referenceLogP(tree, posterior), posterior.calculateLogP(), 1e-12);
        }
    }

    private static double referenceLogP(Tree tree, Coalescent posterior) {
        Tree copy = new Tree(tree.getRoot().copy());
        TreeIntervals intervals = new TreeIntervals();
        intervals.initByName("tree", copy);
        Coalescent reference = new Coalescent();
        reference.initByName("treeIntervals", intervals, "populationModel", posterior.popSizeInput.get());
        double expected = reference.calculateLogP();
        assertTrue(Double.isFinite(expected));
        return expected;
    }

    private static Model build(String populationSize) throws Exception {
        String source = """
                Alignment data = fromNexus(file="%s")
                Tree tree ~ Coalescent(populationSize=%s, taxa=taxa(data))
                """.formatted(
                        Path.of("src/test/java/resources/primate-mtDNA.nex").toAbsolutePath(), populationSize);
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
        var graph = evaluator.applyBestTiling(new BEASTState("coalescent-intervals"));
        graph.initializeBEASTObjects();
        assertEquals(1, graph.stateNodes.size());
        Tree tree = assertInstanceOf(
                Tree.class, graph.stateNodes.keySet().iterator().next());
        Coalescent posterior = assertInstanceOf(Coalescent.class, graph.priorDistributions.get(tree));
        assertTrue(graph.likelihoodDistributions.isEmpty());
        return new Model(tree, posterior);
    }

    private record Model(Tree tree, Coalescent posterior) {}
}
