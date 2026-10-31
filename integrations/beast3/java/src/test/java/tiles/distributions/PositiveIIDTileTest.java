package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Gamma;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.util.GammaFunction;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiling.BoundDistribution;

class PositiveIIDTileTest {
    // GammaFunction.lnGamma documents approximately ten decimal places of accuracy.
    private static final double DENSITY_TOLERANCE = 1e-8;

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(
            strings = {"LogNormal(mean=1.0,logSd=0.5)", "LogNormal(logMean=0.0,logSd=0.5)", "Gamma(shape=2.0,rate=3.0)"
            })
    void registersOnlyVectorPriorAndPreservesScalarLaw(String law) throws Exception {
        BEASTState state = evaluate("Vector<PositiveReal> rates ~ IID(base=" + law + ",num=3)");
        assertEquals(1, state.stateNodes.size());
        assertEquals(1, state.priorDistributions.size());
        RealVectorParam<?> rates = rates(state);
        assertEquals(3, rates.size());
        IID<?, ?, ?> iid = assertInstanceOf(IID.class, state.priorDistributions.get(rates));
        assertSame(rates, iid.paramInput.get());
        assertNull(iid.distInput.get().paramInput.get(), "The base law must not acquire a separate scalar draw.");
        for (int i = 0; i < rates.size(); i++) {
            rates.set(i, 0.2 + i * 0.5);
        }
        assertEquals(referenceLogP(iid, rates), iid.calculateLogP(), DENSITY_TOLERANCE);
        assertFalse(state.operators.isEmpty());
        assertTrue(state.operators.stream()
                .allMatch(operator -> operator.listStateNodes().equals(List.of(rates))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "LogNormal(logMean=0.0,logSd=theta)",
                "LogNormal(mean=2.0,logSd=theta)",
                "LogNormal(mean=theta,logSd=0.5)",
                "Gamma(shape=theta,rate=3.0)",
                "Gamma(shape=2.0,rate=theta)"
            })
    void tracksSeparateVectorAndHyperparameterChanges(String baseExpression) throws Exception {
        for (boolean changeHyperparameter : new boolean[] {false, true}) {
            BEASTState graph = evaluate("""
                    PositiveReal theta ~ Gamma(shape=2.0,rate=1.0)
                    Vector<PositiveReal> rates ~ IID(base=%s,num=3)
                    """.formatted(baseExpression));

            RealVectorParam<?> rates = rates(graph);
            RealScalarParam<?> theta = assertInstanceOf(
                    RealScalarParam.class,
                    graph.stateNodes.keySet().stream()
                            .filter(node -> "theta".equals(node.getID()))
                            .findFirst()
                            .orElseThrow());
            IID<?, ?, ?> iid = assertInstanceOf(IID.class, graph.priorDistributions.get(rates));
            var law = iid.distInput.get();

            assertEquals(2, graph.stateNodes.size());
            assertEquals(2, graph.priorDistributions.size());
            assertEquals(2, graph.operators.size());
            assertTrue(graph.likelihoodDistributions.isEmpty());
            assertNull(law.paramInput.get());
            assertSame(rates, iid.paramInput.get());

            assertTrue(theta.getOutputs().contains(law));
            assertTrue(law.getOutputs().contains(iid));

            if (law instanceof LogNormal logNormal) {
                if (baseExpression.startsWith("LogNormal(mean=theta")) {
                    assertSame(theta, logNormal.MParameterInput.get());
                    assertSame(PositiveReal.INSTANCE, theta.getDomain());
                    assertTrue(logNormal.hasMeanInRealSpaceInput.get());
                } else {
                    assertSame(theta, logNormal.SParameterInput.get());
                }
            } else {
                Gamma gamma = assertInstanceOf(Gamma.class, law);
                assertSame(
                        theta,
                        baseExpression.startsWith("Gamma(shape=theta")
                                ? gamma.alphaInput.get()
                                : gamma.betaInput.get());
            }

            CompoundDistribution posterior = new CompoundDistribution();
            posterior.initByName("distribution", new ArrayList<>(graph.priorDistributions.values()));

            State nativeState = new State();
            nativeState.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
            nativeState.initialise();
            nativeState.setPosterior(posterior);

            assertEquals(
                    referencePosterior(iid, rates, theta),
                    nativeState.robustlyCalcPosterior(posterior),
                    DENSITY_TOLERANCE);

            boolean[] decisions = {true, false, true};
            for (int step = 0; step < decisions.length; step++) {
                boolean accept = decisions[step];
                List<Double> previousRates = List.copyOf(rates.getElements());
                double previousTheta = theta.get();
                double previousIID = iid.getCurrentLogP();
                double previousPosterior = posterior.getCurrentLogP();
                double increment = 0.5 * (step + 1);

                nativeState.store(step + 1);
                if (changeHyperparameter) {
                    // Change only the shared distribution parameter.
                    theta.set(previousTheta + increment);
                    assertEquals(previousRates, rates.getElements());
                } else {
                    // Change only one vector element.
                    rates.set(0, previousRates.getFirst() + increment);
                    assertEquals(previousTheta, theta.get(), 0.0);
                }

                nativeState.storeCalculationNodes();
                nativeState.checkCalculationNodesDirtiness();

                double proposedPosterior = posterior.calculateLogP();
                double expectedIID = referenceLogP(iid, rates);

                assertTrue(Double.isFinite(proposedPosterior));
                assertNotEquals(previousIID, expectedIID, DENSITY_TOLERANCE);
                assertEquals(expectedIID, iid.getCurrentLogP(), DENSITY_TOLERANCE);
                assertEquals(referencePosterior(iid, rates, theta), proposedPosterior, DENSITY_TOLERANCE);

                if (accept) {
                    nativeState.acceptCalculationNodes();
                } else {
                    nativeState.restore();
                    nativeState.restoreCalculationNodes();
                    assertEquals(previousRates, rates.getElements());
                    assertEquals(previousTheta, theta.get(), 0.0);
                }
                nativeState.setEverythingDirty(false);

                // Inspect restored caches before any forced recalculation.
                assertEquals(accept ? expectedIID : previousIID, iid.getCurrentLogP(), DENSITY_TOLERANCE);
                assertEquals(
                        accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), DENSITY_TOLERANCE);
                assertEquals(referencePosterior(iid, rates, theta), posterior.calculateLogP(), DENSITY_TOLERANCE);
                assertEquals(3, rates.size());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 6})
    void supportsFixedDimensions(int size) throws Exception {
        assertEquals(
                size,
                rates(evaluate("Vector<PositiveReal> rates ~ IID(base=LogNormal(mean=1.0,logSd=0.5),num=" + size + ")"))
                        .size());
    }

    @Test
    void supportsBranchCountOfFixedTree() throws Exception {
        var state = evaluate("""
                Tree tree = fromNewick("((A:1,B:1):1,(C:1,D:1):1);")
                Vector<Rate> rates ~ IID(base=LogNormal(mean=1.0,logSd=0.5),num=numBranches(tree))
                """);
        assertEquals(6, rates(state).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "true"})
    void rejectsInvalidDimensions(String size) {
        assertThrows(
                TypeError.class,
                () -> evaluate(
                        "Vector<PositiveReal> rates ~ IID(base=LogNormal(mean=1.0,logSd=0.5),num=" + size + ")"));
    }

    @Test
    void rejectsSampledDimension() {
        var statements = new Parser(
                        new Lexer("Integer n ~ DiscreteUniform(lower=1,upper=4)\nInteger size = n").scanTokens())
                .parse();
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var expression = ((Stmt.Assignment) statements.getLast()).expression;
        var error = assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> new PositiveIIDTile()
                .numInput.getCompatibleInputTiles(expression, Map.of(), stochasticity));
        assertTrue(error.getReason().contains("num"));
        assertTrue(error.getReason().contains("random variable"));
    }

    @Test
    void doesNotNarrowSignedNormalToPositiveRates() {
        assertThrows(
                TileApplicationError.class,
                () -> evaluate("Vector<Real> values ~ IID(base=Normal(mean=0.0,sd=1.0),num=3)"));
    }

    @Test
    void existingRelaxedClockRemainsDiscretized() throws Exception {
        BEASTState state = evaluate("""
                Tree tree = fromNewick("((A:1,B:1):1,(C:1,D:1):1);")
                Vector<Rate> branchRates ~ RelaxedClock(base=LogNormal(mean=1.0,logSd=0.5),clockRate=1.0,tree=tree)
                """);
        assertTrue(state.stateNodes.keySet().stream().anyMatch(IntVectorParam.class::isInstance));
        assertFalse(state.stateNodes.keySet().stream().anyMatch(RealVectorParam.class::isInstance));
    }

    @Test
    void runsVectorPriorAndUpdatesRates() throws Exception {
        String source = """
                Vector<PositiveReal> rates ~ IID(base=LogNormal(mean=1.0,logSd=0.5),num=3)
                mcmc {
                  Integer chainLength=200
                  Integer defaultLogEvery=10
                  Integer randomSeed=12345
                }
                """;
        new PhyloSpecRunner(source, List.of("beast2"))
                .runPhyloSpec(directory.resolve("iid").toString());
        var rows = Files.readAllLines(directory.resolve("iid.log")).stream()
                .filter(line -> line.matches("[0-9]+\\s+.*"))
                .map(line -> line.split("\\s+"))
                .toList();
        assertEquals(21, rows.size());
        assertEquals("200", rows.getLast()[0]);
        assertTrue(rows.stream().map(row -> row[row.length - 1]).distinct().count() > 1);
        for (var row : rows) {
            for (int i = 1; i < row.length; i++) {
                assertTrue(Double.isFinite(Double.parseDouble(row[i])));
            }
        }
    }

    @Test
    void observedVectorCreatesOnlyOneLikelihood() throws Exception {
        BEASTState graph = evaluate("""
                Vector<PositiveReal> rates ~ IID(
                    base=LogNormal(logMean=0.0,logSd=0.5),
                    num=3
                ) observed as [0.5,1.0,2.0]
                """);

        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());

        IID<?, ?, ?> iid = assertInstanceOf(IID.class, graph.likelihoodDistributions.getFirst());
        RealVectorParam<?> observed = assertInstanceOf(RealVectorParam.class, iid.paramInput.get());

        assertEquals(3, observed.size());
        assertNull(iid.distInput.get().paramInput.get());
        assertEquals(referenceLogP(iid, observed), iid.calculateLogP(), DENSITY_TOLERANCE);
    }

    @Test
    void bindingRejectsAnObservedVectorWithTheWrongLength() {
        BEASTState graph = new BEASTState("iid-length");
        Gamma law = new Gamma();
        graph.setInput(law, law.alphaInput, new RealScalarParam<>(2.0, PositiveReal.INSTANCE));
        graph.setInput(law, law.betaInput, new RealScalarParam<>(3.0, PositiveReal.INSTANCE));

        BoundDistribution<RealScalarParam<PositiveReal>, Gamma> base = new BoundDistribution<>(
                law, new RealScalarParam<>(1.0, PositiveReal.INSTANCE), ignored -> {}, (parameter, state) -> List.of());

        PositiveIIDTile tile = new PositiveIIDTile();
        tile.baseInput.setTile(valueTile(base));
        tile.numInput.setTile(valueTile(new IntScalarParam<>(3, PositiveInt.INSTANCE)));

        var distribution = tile.applyTile(graph, new IdentityHashMap<>());
        RealVectorParam<PositiveReal> observation =
                new RealVectorParam<>(new double[] {1.0, 2.0}, PositiveReal.INSTANCE);

        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> distribution.observeAs(graph, observation, "observed"));

        assertTrue(error.getMessage().contains("num"));
        assertNull(distribution.getDistribution().paramInput.get());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    private static double referenceLogP(IID<?, ?, ?> iid, RealVectorParam<?> values) {
        var law = iid.distInput.get();
        double result = 0.0;

        if (law instanceof LogNormal logNormal) {
            double mean = logNormal.MParameterInput.get().get();
            double sd = logNormal.SParameterInput.get().get();
            if (logNormal.hasMeanInRealSpaceInput.get()) {
                mean = Math.log(mean) - 0.5 * sd * sd;
            }

            for (double x : values.getElements()) {
                double z = (Math.log(x) - mean) / sd;
                result += -Math.log(x) - Math.log(sd) - 0.5 * Math.log(2.0 * Math.PI) - 0.5 * z * z;
            }
            return result;
        }

        Gamma gamma = assertInstanceOf(Gamma.class, law);
        double shape = gamma.alphaInput.get().get();
        double rate = gamma.betaInput.get().get();
        double normalizer = shape * Math.log(rate) - GammaFunction.lnGamma(shape);

        for (double x : values.getElements()) {
            result += normalizer + (shape - 1.0) * Math.log(x) - rate * x;
        }
        return result;
    }

    private static double referencePosterior(IID<?, ?, ?> iid, RealVectorParam<?> values, RealScalarParam<?> theta) {
        // The shared parameter has Gamma(shape=2, rate=1) as its prior.
        return referenceLogP(iid, values) + Math.log(theta.get()) - theta.get();
    }

    private static <T> Tile<T, BEASTState> valueTile(T value) {
        Tile<T, BEASTState> tile = new Tile<T, BEASTState>() {
            @Override
            protected T applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
                return value;
            }
        };
        tile.setIndexVariables(Set.of());
        return tile;
    }

    private RealVectorParam<?> rates(BEASTState state) {
        return (RealVectorParam<?>) state.stateNodes.keySet().stream()
                .filter(node -> "rates".equals(node.getID()))
                .findFirst()
                .orElseThrow();
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
        BEASTState state = evaluator.applyBestTiling(
                new BEASTState(directory.resolve("test").toString(), directory));
        catalog.configureState(state);
        state.initializeBEASTObjects();
        return state;
    }
}
