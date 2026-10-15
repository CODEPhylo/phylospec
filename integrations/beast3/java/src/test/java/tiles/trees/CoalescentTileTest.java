package tiles.trees;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.State;
import beastconfig.BEASTState;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void treeIntervalsFollowAcceptedAndRejectedTreeProposals(boolean accept) throws Exception {
        BEASTState graph = build();
        assertEquals(1, graph.stateNodes.size());
        Tree tree = assertInstanceOf(
                Tree.class, graph.stateNodes.keySet().iterator().next());
        Coalescent posterior = assertInstanceOf(Coalescent.class, graph.priorDistributions.get(tree));
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertNull(posterior.treeInput.get(), "BEAST's tree and treeIntervals inputs are alternatives.");
        TreeIntervals intervals = posterior.treeIntervalsInput.get();
        assertNotNull(intervals, "Intervals must be reachable through BEAST Inputs, not hidden inside Coalescent.");
        assertSame(tree, intervals.treeInput.get());
        assertTrue(tree.getOutputs().contains(intervals));
        assertTrue(intervals.getOutputs().contains(posterior));

        State state = new State();
        state.initByName("stateNode", List.of(tree));
        state.initialise();
        state.setPosterior(posterior);
        double initial = state.robustlyCalcPosterior(posterior);
        assertReference(tree, posterior);
        double rootHeight = tree.getRoot().getHeight();

        state.store(1);
        tree.getRoot().setHeight(rootHeight + 0.5);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        double proposed = posterior.calculateLogP();
        assertNotEquals(initial, proposed, 1e-12);
        assertReference(tree, posterior);
        if (accept) {
            state.acceptCalculationNodes();
        } else {
            state.restore();
            state.restoreCalculationNodes();
        }
        state.setEverythingDirty(false);
        assertEquals(accept ? rootHeight + 0.5 : rootHeight, tree.getRoot().getHeight());
        assertEquals(accept ? proposed : initial, posterior.getCurrentLogP(), 1e-12);
        assertReference(tree, posterior);

        // A second proposal exposes stale interval caches left by the preceding accept/restore.
        state.store(2);
        tree.getRoot().setHeight(tree.getRoot().getHeight() + 0.25);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        posterior.calculateLogP();
        assertReference(tree, posterior);
    }

    private static void assertReference(Tree tree, Coalescent posterior) {
        Tree copy = new Tree(tree.getRoot().copy());
        TreeIntervals intervals = new TreeIntervals();
        intervals.initByName("tree", copy);
        Coalescent reference = new Coalescent();
        reference.initByName("treeIntervals", intervals, "populationModel", posterior.popSizeInput.get());
        double expected = reference.calculateLogP();
        assertTrue(Double.isFinite(expected));
        assertEquals(expected, posterior.getCurrentLogP(), 1e-12);
    }

    private static BEASTState build() throws Exception {
        String source = """
                Alignment data = fromNexus(file="%s")
                Tree tree ~ Coalescent(populationSize=constantPopulationFunction(populationSize=100.0),
                    taxa=taxa(data))
                """.formatted(
                        Path.of("src/test/java/resources/primate-mtDNA.nex").toAbsolutePath());
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
        return graph;
    }
}
