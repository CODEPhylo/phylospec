package tiles.misc;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Dirichlet;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.inference.parameter.VectorElement;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastCoreTileLibrary;

class LiveVectorTileTest {
    private static final String VECTOR = "Vector<PositiveReal> r ~ IID(base=LogNormal(logMean=0.0,logSd=1.0),num=2)\n";

    @Test
    void twoVectorsShareOneScalarDraw() throws Exception {
        BEASTState state = build("""
                PositiveReal x ~ LogNormal(logMean=0.0,logSd=1.0)
                Simplex first ~ Dirichlet(concentration=[1.0,x])
                Simplex second ~ Dirichlet(concentration=[x,2.0])
                """);
        assertEquals(3, state.stateNodes.size());
        assertEquals(3, state.priorDistributions.size());
        var scalar = state.stateNodes.keySet().stream()
                .filter(node -> "x".equals(node.getID()))
                .findFirst()
                .orElseThrow();
        state.priorDistributions.values().stream()
                .filter(Dirichlet.class::isInstance)
                .map(prior -> (ScalarVector<?>) ((Dirichlet) prior).alphaInput.get())
                .forEach(vector -> assertTrue(vector.valuesInput.get().stream().anyMatch(value -> value == scalar)));
    }

    @Test
    void mixedArrayKeepsLiveValuesAndSharedStateWithoutAddingPriors() throws Exception {
        BEASTState state = build("""
                PositiveReal x ~ LogNormal(logMean=0.0,logSd=1.0)
                Simplex freq ~ Dirichlet(concentration=[0.5,x,1.5,x])
                """);
        assertEquals(2, state.stateNodes.size());
        assertEquals(2, state.priorDistributions.size());
        assertTrue(state.likelihoodDistributions.isEmpty());
        Dirichlet prior = dirichlet(state);
        ScalarVector<?> vector = assertInstanceOf(ScalarVector.class, prior.alphaInput.get());
        assertInstanceOf(PositiveReal.class, vector.getDomain());
        RealScalarParam<?> scalar = (RealScalarParam<?>) state.stateNodes.keySet().stream()
                .filter(node -> node.getID().equals("x"))
                .findFirst()
                .orElseThrow();
        assertSame(scalar, vector.valuesInput.get().get(1));
        assertSame(scalar, vector.valuesInput.get().get(3));
        assertTrue(scalar.getOutputs().contains(vector));
        assertTrue(vector.getOutputs().contains(prior));
        assertEquals(List.of(0.5, 0.5, 1.5, 0.5), vector.getElements());
        double before = prior.calculateLogP();
        scalar.set(2.0);
        assertEquals(List.of(0.5, 2.0, 1.5, 2.0), vector.getElements());
        assertTrue(Double.isFinite(prior.calculateLogP()));
        assertNotEquals(before, prior.calculateLogP());
        scalar.set(0.5);
        assertEquals(before, prior.calculateLogP(), 1e-12);
    }

    @Test
    void namedArrayRetainsTheSameDependencies() throws Exception {
        BEASTState state = build("""
                PositiveReal x ~ LogNormal(logMean=0.0,logSd=1.0)
                Vector<PositiveReal> values = [1.0,x,3.0]
                Simplex freq ~ Dirichlet(concentration=values)
                """);
        assertEquals(3, dirichlet(state).alphaInput.get().size());
        assertEquals(2, state.stateNodes.size());
        assertInstanceOf(ScalarVector.class, dirichlet(state).alphaInput.get());
    }

    @Test
    void constantArraysKeepExistingRepresentation() throws Exception {
        BEASTState state = build("Simplex freq ~ Dirichlet(concentration=[1.0,2.0,3.0])");
        assertInstanceOf(RealVectorParam.class, dirichlet(state).alphaInput.get());
        assertEquals(1, state.stateNodes.size());
    }

    @Test
    void indexedArraysKeepSeparateScalarDependencies() throws Exception {
        BEASTState state = build("""
                PositiveReal x[i] ~ LogNormal(logMean=0.0,logSd=1.0) for i in 1:2
                Simplex freq[i] ~ Dirichlet(concentration=[1.0,x[i]]) for i in 1:2
                """);
        assertEquals(4, state.stateNodes.size());
        var vectors = state.priorDistributions.values().stream()
                .filter(Dirichlet.class::isInstance)
                .map(prior -> (ScalarVector<?>) ((Dirichlet) prior).alphaInput.get())
                .toList();
        assertEquals(2, vectors.size());
        assertNotSame(vectors.get(0), vectors.get(1));
        RealScalarParam<?> first =
                (RealScalarParam<?>) vectors.get(0).valuesInput.get().get(1);
        assertNotSame(first, vectors.get(1).valuesInput.get().get(1));
        first.set(2.0);
        assertEquals(2.0, vectors.get(0).get(1));
        assertEquals(0.5, vectors.get(1).get(1));
    }

    @Test
    void signedDomainIsNotSilentlyAcceptedAsPositive() {
        assertThrows(TileApplicationError.class, () -> build("""
                Real x ~ Normal(mean=0.0,sd=1.0)
                Simplex freq ~ Dirichlet(concentration=[1.0,x,-1.0])
                """, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1.0,r[1]]", "[r[1],1.0]", "[x,r[1]]", "[r[1],x]", "[r[1],r[2]]"})
    void mixesScalarRepresentationsWithoutAddingStateOrOperators(String elements) throws Exception {
        String declarations =
                VECTOR + (elements.contains("x") ? "PositiveReal x ~ LogNormal(logMean=0.0,logSd=1.0)\n" : "");
        BEASTState graph = build(declarations + "Simplex freq ~ Dirichlet(concentration=" + elements + ")");
        BEASTState control = build(declarations + "Simplex freq ~ Dirichlet(concentration=[1.0,1.0])");
        assertEquals(control.stateNodes.size(), graph.stateNodes.size());
        assertEquals(control.priorDistributions.size(), graph.priorDistributions.size());
        assertEquals(control.operators.size(), graph.operators.size());
        assertTrue(graph.operators.stream()
                .allMatch(operator -> graph.stateNodes.keySet().containsAll(operator.listStateNodes())));
        ScalarVector<?> alpha =
                assertInstanceOf(ScalarVector.class, dirichlet(graph).alphaInput.get());
        assertEquals(2, alpha.size());
        assertInstanceOf(PositiveReal.class, alpha.getDomain());
        assertTrue(Double.isFinite(dirichlet(graph).calculateLogP()));
    }

    @ParameterizedTest
    @CsvSource({"false,vector", "true,vector", "false,scalar", "true,scalar", "false,both", "true,both"})
    void mixedViewsFollowAcceptedAndRejectedProposals(boolean accept, String changed) throws Exception {
        BEASTState graph = build(VECTOR + """
                PositiveReal x ~ LogNormal(logMean=0.0,logSd=1.0)
                Vector<PositiveReal> values = [1.0,r[1],x,r[1]]
                Simplex first ~ Dirichlet(concentration=values)
                Simplex second ~ Dirichlet(concentration=[x,r[2]])
                """);
        RealVectorParam<?> rates = assertInstanceOf(RealVectorParam.class, stateNode(graph, "r"));
        RealScalarParam<?> scalar = assertInstanceOf(RealScalarParam.class, stateNode(graph, "x"));
        var first = assertInstanceOf(Dirichlet.class, graph.priorDistributions.get(stateNode(graph, "first")));
        var second = assertInstanceOf(Dirichlet.class, graph.priorDistributions.get(stateNode(graph, "second")));
        ScalarVector<?> firstAlpha = assertInstanceOf(ScalarVector.class, first.alphaInput.get());
        ScalarVector<?> secondAlpha = assertInstanceOf(ScalarVector.class, second.alphaInput.get());
        assertEquals(4, graph.stateNodes.size());
        assertEquals(4, graph.priorDistributions.size());
        assertEquals(4, graph.operators.size());
        assertSame(scalar, firstAlpha.valuesInput.get().get(2));
        assertSame(scalar, secondAlpha.valuesInput.get().get(0));
        for (var alpha : List.of(firstAlpha, secondAlpha)) {
            assertTrue(scalar.getOutputs().contains(alpha));
            for (var value : alpha.valuesInput.get()) {
                if (value instanceof VectorElement<?> view) {
                    assertSame(rates, view.vectorInput.get());
                    assertTrue(rates.getOutputs().contains(view));
                    assertTrue(view.getOutputs().contains(alpha));
                }
            }
        }
        assertTrue(firstAlpha.getOutputs().contains(first));
        assertTrue(secondAlpha.getOutputs().contains(second));
        var posterior = new CompoundDistribution();
        posterior.initByName("distribution", new ArrayList<>(graph.priorDistributions.values()));
        var engine = new State();
        engine.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        engine.initialise();
        engine.setPosterior(posterior);
        engine.robustlyCalcPosterior(posterior);

        // A second proposal checks that accept/restore did not leave stale dirty flags or caches.
        for (int step = 1; step <= 2; step++) {
            double previous = posterior.getCurrentLogP();
            double previousRate = rates.get(0);
            double previousScalar = scalar.get();
            double unchangedRate = rates.get(1);
            double proposedRate = previousRate + (changed.equals("scalar") ? 0 : step);
            double proposedScalar = previousScalar + (changed.equals("vector") ? 0 : 0.25 * step);
            engine.store(step);
            // Separate proposals ensure one owner cannot hide missing dirty propagation from the other.
            if (!changed.equals("scalar")) rates.set(0, proposedRate);
            if (!changed.equals("vector")) scalar.set(proposedScalar);
            engine.storeCalculationNodes();
            engine.checkCalculationNodesDirtiness();
            // Calculate the posterior first: directly recalculating a child would hide broken wiring.
            double proposed = posterior.calculateLogP();
            assertTrue(Double.isFinite(proposed));
            assertNotEquals(previous, proposed, 1e-12);
            assertEquals(List.of(1.0, rates.get(0), scalar.get(), rates.get(0)), firstAlpha.getElements());
            assertEquals(List.of(scalar.get(), unchangedRate), secondAlpha.getElements());
            assertReference(first);
            assertReference(second);
            if (accept) {
                engine.acceptCalculationNodes();
            } else {
                engine.restore();
                engine.restoreCalculationNodes();
            }
            engine.setEverythingDirty(false);
            assertEquals(accept ? proposedRate : previousRate, rates.get(0));
            assertEquals(accept ? proposedScalar : previousScalar, scalar.get());
            assertEquals(accept ? proposed : previous, posterior.getCurrentLogP(), 1e-12);
            assertEquals(List.of(1.0, rates.get(0), scalar.get(), rates.get(0)), firstAlpha.getElements());
            assertEquals(List.of(scalar.get(), unchangedRate), secondAlpha.getElements());
            assertReference(first);
            assertReference(second);
        }
    }

    @Test
    void mixedArraysKeepLoopIndicesSeparate() throws Exception {
        BEASTState graph = build(VECTOR + "Simplex freq[i] ~ Dirichlet(concentration=[1.0,r[i]]) for i in 1:2");
        RealVectorParam<?> rates = assertInstanceOf(RealVectorParam.class, stateNode(graph, "r"));
        var priors = graph.priorDistributions.values().stream()
                .filter(Dirichlet.class::isInstance)
                .map(Dirichlet.class::cast)
                .toList();
        assertEquals(2, priors.size());
        assertEquals(3, graph.stateNodes.size());
        assertEquals(3, graph.operators.size());
        var indices = new HashSet<Integer>();
        rates.set(0, 2.0);
        for (var prior : priors) {
            ScalarVector<?> alpha = assertInstanceOf(ScalarVector.class, prior.alphaInput.get());
            VectorElement<?> view = assertInstanceOf(
                    VectorElement.class, alpha.valuesInput.get().get(1));
            assertSame(rates, view.vectorInput.get());
            int index = view.indexInput.get();
            indices.add(index);
            assertEquals(rates.get(index), alpha.get(1));
        }
        assertEquals(Set.of(0, 1), indices);
    }

    @Test
    void longMixedArrayFiltersByDomainBeforeCombining() throws Exception {
        String values = String.join(",", Collections.nCopies(16, "0.5")) + ",r[1]";
        var graph = build(VECTOR + "Simplex freq ~ Dirichlet(concentration=[" + values + "])");
        assertEquals(17, dirichlet(graph).alphaInput.get().size());
        assertEquals(2, graph.stateNodes.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[x,r[1]]", "[r[1],x]"})
    void signedScalarIsNotNarrowedToIndexedPositiveDomain(String elements) {
        // Exercise the binding boundary even when public semantic validation would reject it first.
        assertThrows(
                TileApplicationError.class,
                () -> build(
                        VECTOR
                                + "Real x ~ Normal(mean=0.0,sd=1.0)\n"
                                + "Simplex freq ~ Dirichlet(concentration=" + elements + ")",
                        false));
    }

    @Test
    void indexedProbabilityIsNotRelabelledAsStrictlyPositive() {
        assertThrows(TileApplicationError.class, () -> build("""
                Simplex r ~ Dirichlet(concentration=[1.0,1.0])
                Simplex freq ~ Dirichlet(concentration=[1.0,r[1]])
                """, false));
    }

    private static StateNode stateNode(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static void assertReference(Dirichlet prior) {
        var alpha = new RealVectorParam<>(
                prior.alphaInput.get().getElements().stream()
                        .mapToDouble(Double::doubleValue)
                        .toArray(),
                PositiveReal.INSTANCE);
        var simplex = new SimplexParam(prior.paramInput.get().getElements().stream()
                .mapToDouble(Double::doubleValue)
                .toArray());
        var reference = new Dirichlet(simplex, alpha);
        assertEquals(reference.calculateLogP(), prior.getCurrentLogP(), 1e-12);
    }

    private static Dirichlet dirichlet(BEASTState state) {
        return state.priorDistributions.values().stream()
                .filter(Dirichlet.class::isInstance)
                .map(Dirichlet.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static BEASTState build(String source) throws Exception {
        return build(source, true);
    }

    private static BEASTState build(String source, boolean checkSemantics) throws Exception {
        var statements = new EvaluateLiterals().transform(new Parser(new Lexer(source).scanTokens()).parse());
        if (checkSemantics) {
            var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
            var resolver = new TypeResolver(catalog.getComponentResolver());
            resolver.registerEventListener(error -> fail(error.toStdOutString(source)));
            resolver.visitStatements(statements);
        }
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<BEASTState>(
                new BeastCoreTileLibrary().getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState state = evaluator.applyBestTiling(new BEASTState("live-vector"));
        state.initializeBEASTObjects();
        return state;
    }
}
