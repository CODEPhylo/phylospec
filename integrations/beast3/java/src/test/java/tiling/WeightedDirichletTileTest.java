package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import beastlabs.math.distributions.WeightedDirichlet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
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

class WeightedDirichletTileTest {
    private static final Path RSV2 = Path.of("src/test/resources/rsv2").toAbsolutePath();
    private static final double EPSILON = 1e-12;

    @Test
    void rsv2PartitionLengthsBindToBothPriorAndOperator() throws Exception {
        var graph = build(Files.readString(RSV2.resolve("weighted-dirichlet.phylospec")));
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        var rates = assertInstanceOf(
                RealVectorParam.class, graph.stateNodes.keySet().iterator().next());
        var prior = assertInstanceOf(WeightedDirichlet.class, graph.priorDistributions.get(rates));
        var move = assertInstanceOf(DeltaExchangeOperator.class, graph.operators.getFirst());
        assertSame(rates, prior.paramInput.get());
        assertSame(rates, move.rvparameterInput.get());
        assertEquals(List.of(rates), move.listStateNodes());
        assertEquals(List.of(209, 210, 210), move.parameterWeightsInput.get().getElements());
        assertEquals(List.of(209.0, 210.0, 210.0), prior.weightsInput.get().getElements());
        assertEquals(List.of(1.0, 1.0, 1.0), rates.getElements());

        // Unit concentrations have constant simplex density 2, with the native rate-space Jacobian.
        double expected = Math.log(2.0) + Math.log(209.0 / 629) + 2 * Math.log(210.0 / 629);
        assertEquals(expected, prior.calculateLogP(), EPSILON);
    }

    @Test
    void literalWeightsPreserveWeightedMeanAndDensityAcrossAcceptRejectAccept() throws Exception {
        var graph = build("""
                Vector<PositiveReal> r ~ WeightedDirichlet(concentration=[2.0,3.0,4.0], weights=[2,3,5], mean=2.0)
                """);
        RealVectorParam<?> rates = assertInstanceOf(
                RealVectorParam.class, graph.stateNodes.keySet().iterator().next());
        var prior = assertInstanceOf(WeightedDirichlet.class, graph.priorDistributions.get(rates));
        var move = assertInstanceOf(DeltaExchangeOperator.class, graph.operators.getFirst());
        assertEquals(List.of(2, 3, 5), move.parameterWeightsInput.get().getElements());
        var posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(prior));
        var state = new State();
        state.initByName("stateNode", List.of(rates));
        state.initialise();
        state.setPosterior(posterior);
        Randomizer.setSeed(1248);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));

        int moves = 0;
        for (int step = 0; step < 100 && moves < 3; step++) {
            var before = List.copyOf(rates.getElements());
            double oldLogP = posterior.getCurrentLogP();
            state.store(step);
            double hastings = move.proposal();
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            if (!Double.isFinite(hastings)) {
                state.restore();
                state.restoreCalculationNodes();
                state.setEverythingDirty(false);
                continue;
            }
            var after = List.copyOf(rates.getElements());
            assertNotEquals(before, after);
            assertEquals(2.0, (2 * after.get(0) + 3 * after.get(1) + 5 * after.get(2)) / 10, EPSILON);
            // At fixed weights and mean the Jacobian is constant. The exponents alpha-1 are 1, 2, 3.
            double expected = oldLogP;
            for (int i = 0; i < after.size(); i++) expected += (i + 1) * Math.log(after.get(i) / before.get(i));
            double proposed = posterior.calculateLogP();
            assertEquals(expected, proposed, EPSILON);
            boolean accept = moves != 1;
            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            assertEquals(accept ? after : before, rates.getElements());
            assertEquals(accept ? proposed : oldLogP, prior.getCurrentLogP(), EPSILON);
            assertEquals(accept ? proposed : oldLogP, posterior.getCurrentLogP(), EPSILON);
            moves++;
        }
        assertEquals(3, moves);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[0.5,1.0]", "[1,2,3]", "[2147483647,1]"})
    void rejectsWeightsTheNativeExchangeCannotUse(String weights) {
        assertThrows(
                TileApplicationError.class,
                () -> build("Vector<PositiveReal> r ~ WeightedDirichlet(concentration=[1.0,1.0], weights=" + weights
                        + ")"));
    }

    @Test
    void zeroWeightFallsOutsideTheDeclaredDomain() {
        assertThrows(
                TypeError.class,
                () -> build("Vector<PositiveReal> r ~ WeightedDirichlet(concentration=[1.0,1.0], weights=[1,0])"));
    }

    @Test
    void observedRatesKeepConcentrationLiveAndUseTheDefaultMean() throws Exception {
        var graph = build("""
                Vector<PositiveReal> a ~ IID(base=LogNormal(logMean=0.0, logSd=1.0), num=2)
                Vector<PositiveReal> r ~ WeightedDirichlet(concentration=a, weights=[1,3]) observed as [2.5,0.5]
                """);
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertFalse(graph.operators.stream().anyMatch(DeltaExchangeOperator.class::isInstance));
        RealVectorParam<?> a = assertInstanceOf(
                RealVectorParam.class, graph.stateNodes.keySet().iterator().next());
        var density = assertInstanceOf(WeightedDirichlet.class, graph.likelihoodDistributions.getFirst());
        assertSame(a, density.alphaInput.get());
        a.set(0, 2.0);
        a.set(1, 1.0);
        double before = density.calculateLogP();
        a.set(0, 3.0);
        // In simplex coordinates the observed first component is 2.5 * 1 / 4.
        assertEquals(Math.log(1.5 * 2.5 / 4), density.calculateLogP() - before, EPSILON);
    }

    @Test
    void rejectsStochasticWeights() {
        assertThrows(TileApplicationError.class, () -> build("""
                Vector<PositiveReal> w ~ IID(base=LogNormal(logMean=0.0,logSd=1.0),num=2)
                Vector<PositiveReal> r ~ WeightedDirichlet(concentration=[1.0,1.0],weights=w)
                """));
    }

    private static BEASTState build(String source) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        var resolver = new TypeResolver(catalog.getComponentResolver(), new Workspace(RSV2));
        resolver.visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        var graph = evaluator.applyBestTiling(new BEASTState("weightedDirichlet", RSV2));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
