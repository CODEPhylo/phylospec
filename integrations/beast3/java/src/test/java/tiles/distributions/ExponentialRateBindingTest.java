package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Exponential;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import runner.PhyloSpecRunner;
import tiling.ReciprocalValue;

class ExponentialRateBindingTest {
    private static final double TOLERANCE = 1e-10;

    @ParameterizedTest
    @CsvSource({"Gamma, false", "Gamma, true", "LogNormal, false", "LogNormal, true"})
    void randomRateRemainsConnectedThroughAcceptanceAndRejection(String upstreamFamily, boolean observed)
            throws Exception {
        String upstreamCall =
                upstreamFamily.equals("Gamma") ? "Gamma(shape=2.0, rate=1.0)" : "LogNormal(logMean=0.3, logSd=0.6)";

        String source = "PositiveReal lambda ~ " + upstreamCall + "\n"
                + "PositiveReal x ~ Exponential(rate=lambda)"
                + (observed ? " observed as 1.5" : "");

        BEASTState graph = build(source);
        RealScalarParam<?> rate = parameter(graph, "lambda");
        var upstreamPrior = graph.priorDistributions.get(rate);
        assertNotNull(upstreamPrior);

        Exponential conditional = assertInstanceOf(
                Exponential.class,
                observed
                        ? graph.likelihoodDistributions.getFirst()
                        : graph.priorDistributions.get(parameter(graph, "x")));
        RealScalarParam<?> value = assertInstanceOf(RealScalarParam.class, conditional.paramInput.get());
        ReciprocalValue mean = assertInstanceOf(ReciprocalValue.class, conditional.meanInput.get());
        double fixedValue = value.get();

        assertSame(rate, mean.rateInput.get());
        assertSame(PositiveReal.INSTANCE, rate.getDomain());
        assertSame(PositiveReal.INSTANCE, mean.getDomain());
        assertTrue(rate.getOutputs().contains(mean));
        assertTrue(mean.getOutputs().contains(conditional));
        assertEquals(1.0 / rate.get(), mean.get(), 0.0);

        assertEquals(observed ? 1 : 2, graph.stateNodes.size());
        assertEquals(observed ? 1 : 2, graph.priorDistributions.size());
        assertEquals(observed ? 1 : 0, graph.likelihoodDistributions.size());
        assertEquals(observed ? 1 : 2, graph.operators.size());

        if (observed) {
            assertEquals(1.5, fixedValue, 0.0);
            // A positive observed literal may retain its narrower domain.
            assertInstanceOf(NonNegativeReal.class, value.getDomain());
            assertFalse(graph.stateNodes.containsKey(value));
            assertEquals(List.of(rate), graph.operators.getFirst().listStateNodes());
        } else {
            assertSame(parameter(graph, "x"), value);
            assertSame(NonNegativeReal.INSTANCE, value.getDomain());
            assertEquals(1.0, fixedValue, 0.0);
        }

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(upstreamPrior, conditional));

        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);

        assertEquals(
                upstreamLogDensity(upstreamFamily, rate.get()) + exponentialLogDensity(fixedValue, rate.get()),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);

        boolean[] decisions = {true, false, true};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            double previousRate = rate.get();
            double previousUpstream = upstreamPrior.getCurrentLogP();
            double previousConditional = conditional.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();

            state.store(step + 1);
            rate.set(previousRate + 0.5 * (step + 1));
            double proposedRate = rate.get();

            assertEquals(1.0 / proposedRate, mean.get(), 0.0);
            // A change in rate must not redraw the conditional value.
            assertEquals(fixedValue, value.get(), 0.0);

            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            double expectedUpstream = upstreamLogDensity(upstreamFamily, proposedRate);
            double expectedConditional = exponentialLogDensity(fixedValue, proposedRate);
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

            double currentRate = accept ? proposedRate : previousRate;
            assertEquals(currentRate, rate.get(), 0.0);
            assertEquals(1.0 / currentRate, mean.get(), 0.0);
            assertEquals(fixedValue, value.get(), 0.0);
            assertSame(rate, mean.rateInput.get());
            assertSame(mean, conditional.meanInput.get());

            // Check restored caches before recalculating any density.
            assertEquals(accept ? expectedUpstream : previousUpstream, upstreamPrior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedConditional : previousConditional, conditional.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);

            assertEquals(
                    upstreamLogDensity(upstreamFamily, currentRate) + exponentialLogDensity(fixedValue, currentRate),
                    posterior.calculateLogP(),
                    TOLERANCE);
        }
    }

    @Test
    void constantRateRetainsTheNonNegativeBackendDomain() throws Exception {
        BEASTState graph = build("PositiveReal x ~ Exponential(rate=2.3)");
        RealScalarParam<?> value = parameter(graph, "x");
        Exponential distribution = assertInstanceOf(Exponential.class, graph.priorDistributions.get(value));
        ReciprocalValue mean = assertInstanceOf(ReciprocalValue.class, distribution.meanInput.get());

        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertEquals(1, graph.operators.size());

        assertSame(NonNegativeReal.INSTANCE, value.getDomain());
        assertEquals(1.0, value.get(), 0.0);
        assertEquals(1.0 / 2.3, mean.get(), 0.0);
        assertEquals(exponentialLogDensity(1.0, 2.3), distribution.calculateLogP(), TOLERANCE);

        // Test the existing native boundary, without changing the core definition.
        value.set(0.0);
        assertEquals(Math.log(2.3), distribution.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "-1.0"})
    void coreStillRejectsNonPositiveRates(String rate) {
        assertThrows(TypeError.class, () -> build("PositiveReal x ~ Exponential(rate=" + rate + ")"));
    }

    @Test
    void coreStillRejectsAnUnconstrainedRandomRate() {
        String source = """
                Real rate ~ Normal(mean=1.0, sd=2.0)
                PositiveReal x ~ Exponential(rate=rate)
                """;

        assertThrows(TypeError.class, () -> build(source));
    }

    @Test
    void shortChainEstimatesRateForAnObservedValue(@TempDir Path directory) throws Exception {
        String source = """
                PositiveReal lambda ~ Gamma(shape=2.0, rate=1.0)
                PositiveReal x ~ Exponential(rate=lambda) observed as 1.5

                mcmc {
                    Integer chainLength = 200
                    Integer defaultLogEvery = 10
                    Integer randomSeed = 12345
                }
                """;

        Path prefix = directory.resolve("exponential-rate");
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec(prefix.toString());

        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();

        assertEquals(22, lines.size(), "Header plus samples 0, 10, ..., 200.");
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int rateColumn = headers.indexOf("lambda");
        int posteriorColumn = headers.indexOf("posterior");
        assertTrue(rateColumn >= 0 && posteriorColumn >= 0, headers.toString());

        Set<Double> values = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.trim().split("\\s+");
            double rate = Double.parseDouble(fields[rateColumn]);
            double posterior = Double.parseDouble(fields[posteriorColumn]);

            assertTrue(Double.isFinite(rate) && rate > 0.0);
            assertTrue(Double.isFinite(posterior));
            values.add(rate);
        }

        assertEquals("200", lines.getLast().trim().split("\\s+")[0]);
        assertTrue(values.size() > 1, "The existing operator must update lambda.");
    }

    private static double exponentialLogDensity(double value, double rate) {
        return Math.log(rate) - rate * value;
    }

    private static double upstreamLogDensity(String family, double value) {
        if (family.equals("Gamma")) {
            // Gamma(shape=2, rate=1).
            return Math.log(value) - value;
        }

        // LogNormal(logMean=0.3, logSd=0.6).
        double logSd = 0.6;
        double z = (Math.log(value) - 0.3) / logSd;
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

        var graph = evaluator.applyBestTiling(new BEASTState("exponential-rate-binding"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
