package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveReal;
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

class LogNormalMeanBindingTest {
    private static final double TOLERANCE = 1e-10;
    private static final double LOG_SD = 0.5;

    @ParameterizedTest
    @CsvSource({"Gamma, false", "Gamma, true", "LogNormal, false", "LogNormal, true"})
    void randomMeanRemainsConnectedThroughAcceptanceAndRejection(String upstreamFamily, boolean observed)
            throws Exception {
        String upstreamCall =
                upstreamFamily.equals("Gamma") ? "Gamma(shape=2.0, rate=1.0)" : "LogNormal(logMean=0.3, logSd=0.6)";

        String source = "PositiveReal m ~ " + upstreamCall + "\n"
                + "PositiveReal x ~ LogNormal(mean=m, logSd=0.5)"
                + (observed ? " observed as 1.7" : "");

        BEASTState graph = build(source);
        RealScalarParam<?> mean = parameter(graph, "m");
        var upstreamPrior = graph.priorDistributions.get(mean);
        assertNotNull(upstreamPrior);

        LogNormal conditional = assertInstanceOf(
                LogNormal.class,
                observed
                        ? graph.likelihoodDistributions.getFirst()
                        : graph.priorDistributions.get(parameter(graph, "x")));
        RealScalarParam<?> value = assertInstanceOf(RealScalarParam.class, conditional.paramInput.get());
        double fixedValue = value.get();

        assertSame(mean, conditional.MParameterInput.get());
        assertSame(PositiveReal.INSTANCE, mean.getDomain());
        assertSame(PositiveReal.INSTANCE, value.getDomain());
        assertTrue(mean.getOutputs().contains(conditional));
        assertTrue(conditional.hasMeanInRealSpaceInput.get());
        assertEquals(LOG_SD, conditional.SParameterInput.get().get(), 0.0);

        assertEquals(observed ? 1 : 2, graph.stateNodes.size());
        assertEquals(observed ? 1 : 2, graph.priorDistributions.size());
        assertEquals(observed ? 1 : 0, graph.likelihoodDistributions.size());
        assertEquals(observed ? 1 : 2, graph.operators.size());

        if (observed) {
            assertEquals(1.7, fixedValue, 0.0);
            assertFalse(graph.stateNodes.containsKey(value));
            assertTrue(graph.operators.stream()
                    .noneMatch(operator -> operator.listStateNodes().contains(value)));
        } else {
            assertSame(parameter(graph, "x"), value);
            assertEquals(mean.get() * Math.exp(-0.5 * LOG_SD * LOG_SD), fixedValue, TOLERANCE);
        }

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(upstreamPrior, conditional));

        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);

        assertEquals(
                upstreamLogDensity(upstreamFamily, mean.get()) + conditionalLogDensity(fixedValue, mean.get()),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);

        boolean[] decisions = {true, false, true};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            double previousMean = mean.get();
            double previousUpstream = upstreamPrior.getCurrentLogP();
            double previousConditional = conditional.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();

            state.store(step + 1);
            mean.set(previousMean + 0.5 * (step + 1));
            double proposedMean = mean.get();

            // Updating the mean must not redraw the conditional value.
            assertEquals(fixedValue, value.get(), 0.0);

            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            double expectedUpstream = upstreamLogDensity(upstreamFamily, proposedMean);
            double expectedConditional = conditionalLogDensity(fixedValue, proposedMean);
            double proposedPosterior = posterior.calculateLogP();

            assertTrue(Double.isFinite(proposedPosterior));
            assertNotEquals(previousConditional, expectedConditional, TOLERANCE);
            assertEquals(expectedUpstream, upstreamPrior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedConditional, conditional.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedUpstream + expectedConditional, proposedPosterior, TOLERANCE);

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);

            assertEquals(accept ? proposedMean : previousMean, mean.get(), 0.0);
            assertEquals(fixedValue, value.get(), 0.0);
            assertSame(mean, conditional.MParameterInput.get());
            assertSame(PositiveReal.INSTANCE, mean.getDomain());

            // Check restored caches before recalculating any density.
            assertEquals(accept ? expectedUpstream : previousUpstream, upstreamPrior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedConditional : previousConditional, conditional.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);

            assertEquals(
                    upstreamLogDensity(upstreamFamily, mean.get()) + conditionalLogDensity(fixedValue, mean.get()),
                    posterior.calculateLogP(),
                    TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "-1.0"})
    void coreStillRejectsNonPositiveMeans(String mean) {
        assertThrows(TypeError.class, () -> build("PositiveReal x ~ LogNormal(mean=" + mean + ", logSd=0.5)"));
    }

    private static double upstreamLogDensity(String family, double value) {
        // Gamma(shape=2, rate=1) has log density log(value) - value.
        return family.equals("Gamma") ? Math.log(value) - value : logNormalLogDensity(value, 0.3, 0.6);
    }

    private static double conditionalLogDensity(double value, double mean) {
        double logMean = Math.log(mean) - 0.5 * LOG_SD * LOG_SD;
        return logNormalLogDensity(value, logMean, LOG_SD);
    }

    private static double logNormalLogDensity(double value, double logMean, double logSd) {
        double z = (Math.log(value) - logMean) / logSd;
        return -Math.log(value) - Math.log(logSd) - 0.5 * Math.log(2.0 * Math.PI) - 0.5 * z * z;
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

        var graph = evaluator.applyBestTiling(new BEASTState("lognormal-mean-binding"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
