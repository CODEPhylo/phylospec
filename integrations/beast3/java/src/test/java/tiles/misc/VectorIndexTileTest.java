package tiles.misc;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Exponential;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.inference.parameter.VectorElement;
import beastconfig.BEASTState;
import java.util.ArrayList;
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
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

class VectorIndexTileTest {
    private static final String VECTOR = "Vector<PositiveReal> r ~ IID(base=LogNormal(logMean=0.0,logSd=1.0),num=3)\n";

    @Test
    void sharesVectorStateAndPreservesLiveValuesAndDomain() throws Exception {
        var graph = build(VECTOR + "PositiveReal a ~ Exponential(mean=r[1])\nPositiveReal b ~ Exponential(mean=r[3])");
        assertEquals(3, graph.stateNodes.size());
        assertEquals(3, graph.priorDistributions.size());
        var vector = (RealVectorParam<?>) graph.stateNodes.keySet().stream()
                .filter(node -> node.getID().equals("r"))
                .findFirst()
                .orElseThrow();
        var views = graph.priorDistributions.values().stream()
                .filter(Exponential.class::isInstance)
                .map(Exponential.class::cast)
                .map(prior -> (VectorElement<?>) prior.meanInput.get())
                .toList();
        assertEquals(2, views.size());
        for (var view : views) {
            assertSame(vector, view.vectorInput.get());
            assertInstanceOf(PositiveReal.class, view.getDomain());
            assertTrue(vector.getOutputs().contains(view));
        }
        double initial = vector.get(0);
        var posterior = new CompoundDistribution();
        posterior.initByName("distribution", new ArrayList<>(graph.priorDistributions.values()));
        var engine = new State();
        engine.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        engine.initialise();
        engine.setPosterior(posterior);
        engine.robustlyCalcPosterior(posterior);
        engine.store(0);
        vector.set(0, 2.0);
        for (var view : views) assertEquals(view.indexInput.get() == 0 ? 2.0 : vector.get(2), view.get());
        engine.restore();
        assertEquals(
                initial,
                views.stream()
                        .filter(v -> v.indexInput.get() == 0)
                        .findFirst()
                        .orElseThrow()
                        .get());
    }

    @Test
    void simplexKeepsUnitIntervalDomain() throws Exception {
        var graph = build("Simplex r ~ Dirichlet(concentration=[1.0,1.0,1.0])\nProbability y = r[2]");
        var vector = (SimplexParam) graph.stateNodes.keySet().iterator().next();
        var view = (VectorElement<?>) vector.getOutputs().stream()
                .filter(VectorElement.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertInstanceOf(UnitInterval.class, view.getDomain());
        vector.set(0, 0.2);
        vector.set(1, 0.5);
        vector.set(2, 0.3);
        assertEquals(0.5, view.get());
        assertEquals(1, graph.stateNodes.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 4})
    void rejectsOutOfBoundsIndices(int index) {
        var error = assertThrows(
                Exception.class, () -> build(VECTOR + "PositiveReal y ~ Exponential(mean=r[" + index + "] )"));
        assertTrue(error.getMessage().toLowerCase().contains("index"), error.getMessage());
    }

    @Test
    void doesNotSnapshotAnEstimatedIndex() {
        assertThrows(
                TileApplicationError.class,
                () -> build(
                        VECTOR
                                + "Integer i ~ DiscreteUniform(lower=1,upper=3)\nPositiveReal y ~ Exponential(mean=r[i])",
                        false));
    }

    @Test
    void indexedScalarDrawsStillUseTheExistingListPath() throws Exception {
        var graph = build("PositiveReal r[i] ~ LogNormal(logMean=0.0,logSd=1.0) for i in 1:3\n"
                + "PositiveReal y ~ Exponential(mean=r[1])");
        assertEquals(4, graph.stateNodes.size());
        assertEquals(4, graph.priorDistributions.size());
    }

    @Test
    void loopIndicesCanSelectNumericVectorElements() throws Exception {
        var graph = build(VECTOR + "PositiveReal y[i] ~ Exponential(mean=r[i]) for i in 1:3");
        assertEquals(4, graph.stateNodes.size());
    }

    private static BEASTState build(String source) throws Exception {
        return build(source, true);
    }

    private static BEASTState build(String source, boolean checkSemantics) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var parser = new Parser(new Lexer(source).scanTokens());
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        var resolver = new TypeResolver(catalog.getComponentResolver());
        resolver.registerEventListener(error -> {
            throw new AssertionError(error.toStdOutString(source));
        });
        // The estimated-index case exercises the engine guard independently of semantic rejection.
        if (checkSemantics) resolver.visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        var state = evaluator.applyBestTiling(new BEASTState("vector-index"));
        state.initializeBEASTObjects();
        return state;
    }
}
