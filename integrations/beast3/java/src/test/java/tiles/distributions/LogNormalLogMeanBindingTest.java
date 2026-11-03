package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

class LogNormalLogMeanBindingTest {
    private static final double TOLERANCE = 1e-10;
    private static final double LOG_SD = 0.5;
    private static final double OBSERVED = 1.5;

    @ParameterizedTest
    @CsvSource({
        "Gamma, PositiveReal, false",
        "Gamma, PositiveReal, true",
        "LogNormal, PositiveReal, false",
        "LogNormal, PositiveReal, true",
        "Exponential, NonNegativeReal, false",
        "Exponential, NonNegativeReal, true",
        "Beta, Probability, false",
        "Beta, Probability, true",
        "Normal, Real, false",
        "Normal, Real, true"
    })
    void randomLogMeanRemainsConnectedThroughAcceptanceAndRejection(
            String family, String declaredType, boolean observed) throws Exception {
        String upstreamCall =
                switch (family) {
                    case "Gamma" -> "Gamma(shape=2.0, rate=1.0)";
                    case "LogNormal" -> "LogNormal(logMean=0.3, logSd=0.6)";
                    case "Exponential" -> "Exponential(rate=2.0)";
                    case "Beta" -> "Beta(alpha=2.0, beta=2.0)";
                    case "Normal" -> "Normal(mean=-1.0, sd=1.0)";
                    default -> throw new IllegalArgumentException(family);
                };
        Real expectedDomain =
                switch (family) {
                    case "Gamma", "LogNormal" -> PositiveReal.INSTANCE;
                    case "Exponential" -> NonNegativeReal.INSTANCE;
                    case "Beta" -> UnitInterval.INSTANCE;
                    case "Normal" -> Real.INSTANCE;
                    default -> throw new IllegalArgumentException(family);
                };

        String source = declaredType + " m ~ " + upstreamCall + "\n"
                + "PositiveReal x ~ LogNormal(logMean=m, logSd=0.5)"
                + (observed ? " observed as 1.5" : "");

        BEASTState graph = build(source);
        RealScalarParam<?> logMean = parameter(graph, "m");
        var upstreamPrior = graph.priorDistributions.get(logMean);
        assertNotNull(upstreamPrior);

        LogNormal conditional = assertInstanceOf(
                LogNormal.class,
                observed
                        ? graph.likelihoodDistributions.getFirst()
                        : graph.priorDistributions.get(parameter(graph, "x")));
        RealScalarParam<?> value = assertInstanceOf(RealScalarParam.class, conditional.paramInput.get());
        double fixedValue = value.get();

        assertSame(logMean, conditional.MParameterInput.get());
        assertSame(expectedDomain, logMean.getDomain());
        assertSame(PositiveReal.INSTANCE, value.getDomain());
        assertTrue(logMean.getOutputs().contains(conditional));
        assertFalse(conditional.hasMeanInRealSpaceInput.get());
        assertEquals(LOG_SD, conditional.SParameterInput.get().get(), 0.0);

        assertEquals(observed ? 1 : 2, graph.stateNodes.size());
        assertEquals(observed ? 1 : 2, graph.priorDistributions.size());
        assertEquals(observed ? 1 : 0, graph.likelihoodDistributions.size());
        assertEquals(observed ? 1 : 2, graph.operators.size());

        if (observed) {
            assertEquals(OBSERVED, fixedValue, 0.0);
            assertFalse(graph.stateNodes.containsKey(value));
            assertEquals(List.of(logMean), graph.operators.getFirst().listStateNodes());
        } else {
            assertSame(parameter(graph, "x"), value);
            assertEquals(Math.exp(logMean.get()), fixedValue, TOLERANCE);
        }

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(upstreamPrior, conditional));

        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);

        assertEquals(
                upstreamLogDensity(family, logMean.get()) + logNormalLogDensity(fixedValue, logMean.get(), LOG_SD),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);

        // Explicit proposals test binding and restoration, not an operator's ability to cross zero.
        double[] proposedMeans = family.equals("Normal") ? new double[] {-1.0, 0.0, 1.0} : new double[] {0.6, 0.7, 0.8};
        boolean[] decisions = {true, false, true};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            double previousMean = logMean.get();
            double previousPrior = upstreamPrior.getCurrentLogP();
            double previousConditional = conditional.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();

            state.store(step + 1);
            logMean.set(proposedMeans[step]);
            double proposedMean = logMean.get();

            assertEquals(proposedMean, conditional.MParameterInput.get().get(), 0.0);
            assertEquals(fixedValue, value.get(), 0.0);

            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            double expectedPrior = upstreamLogDensity(family, proposedMean);
            double expectedConditional = logNormalLogDensity(fixedValue, proposedMean, LOG_SD);
            double proposedPosterior = posterior.calculateLogP();

            assertTrue(Double.isFinite(proposedPosterior));
            assertNotEquals(previousConditional, expectedConditional, TOLERANCE);
            assertEquals(expectedPrior, upstreamPrior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedConditional, conditional.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedPrior + expectedConditional, proposedPosterior, TOLERANCE);
            assertEquals(fixedValue, value.get(), 0.0, "Updating logMean must not reinitialize x.");

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);

            double currentMean = accept ? proposedMean : previousMean;
            assertEquals(currentMean, logMean.get(), 0.0);
            assertEquals(currentMean, conditional.MParameterInput.get().get(), 0.0);
            assertEquals(fixedValue, value.get(), 0.0);
            assertSame(logMean, conditional.MParameterInput.get());
            assertSame(expectedDomain, logMean.getDomain());
            assertFalse(conditional.hasMeanInRealSpaceInput.get());

            // Check restored caches before recalculating any density.
            assertEquals(accept ? expectedPrior : previousPrior, upstreamPrior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedConditional : previousConditional, conditional.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);

            assertEquals(
                    upstreamLogDensity(family, currentMean) + logNormalLogDensity(fixedValue, currentMean, LOG_SD),
                    posterior.calculateLogP(),
                    TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 0.0, 1.0})
    void constantLogMeansPreserveTheDomainInitializationAndDensity(double logMean) throws Exception {
        BEASTState graph = build("PositiveReal x ~ LogNormal(logMean=" + logMean + ", logSd=0.5)");
        RealScalarParam<?> value = parameter(graph, "x");
        LogNormal prior = assertInstanceOf(LogNormal.class, graph.priorDistributions.get(value));

        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertSame(PositiveReal.INSTANCE, value.getDomain());
        assertEquals(Math.exp(logMean), value.get(), TOLERANCE);
        assertEquals(logMean, prior.MParameterInput.get().get(), 0.0);
        assertFalse(prior.hasMeanInRealSpaceInput.get());
        assertEquals(logNormalLogDensity(value.get(), logMean, LOG_SD), prior.calculateLogP(), TOLERANCE);

        value.set(1.5);
        assertEquals(logNormalLogDensity(1.5, logMean, LOG_SD), prior.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "-1.0"})
    void coreStillRejectsNonPositiveLogStandardDeviations(String logSd) {
        assertThrows(TypeError.class, () -> build("PositiveReal x ~ LogNormal(logMean=0.0, logSd=" + logSd + ")"));
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double z = (value - mean) / sd;
        return -Math.log(sd) - 0.5 * Math.log(2.0 * Math.PI) - 0.5 * z * z;
    }

    private static double logNormalLogDensity(double value, double logMean, double logSd) {
        return normalLogDensity(Math.log(value), logMean, logSd) - Math.log(value);
    }

    private static double upstreamLogDensity(String family, double value) {
        return switch (family) {
            case "Gamma" -> Math.log(value) - value;
            case "LogNormal" -> logNormalLogDensity(value, 0.3, 0.6);
            case "Exponential" -> Math.log(2.0) - 2.0 * value;
            case "Beta" -> Math.log(6.0) + Math.log(value) + Math.log1p(-value);
            case "Normal" -> normalLogDensity(value, -1.0, 1.0);
            default -> throw new IllegalArgumentException(family);
        };
    }

    private static RealScalarParam<?> parameter(BEASTState graph, String id) {
        return assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(node -> id.equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static BEASTState build(String source) throws Exception {
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

        var graph = evaluator.applyBestTiling(new BEASTState("lognormal-log-mean-binding"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
