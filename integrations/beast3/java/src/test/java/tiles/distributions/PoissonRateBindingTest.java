package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Poisson;
import beast.base.spec.inference.parameter.IntScalarParam;
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

class PoissonRateBindingTest {
    private static final double TOLERANCE = 1e-10;

    @ParameterizedTest
    @CsvSource({"Gamma, 0", "Gamma, 3", "LogNormal, 0", "LogNormal, 3"})
    void randomRateRemainsConnectedThroughAcceptanceAndRejection(String upstreamFamily, int observedCount)
            throws Exception {
        String upstreamCall =
                upstreamFamily.equals("Gamma") ? "Gamma(shape=2.0, rate=1.0)" : "LogNormal(logMean=0.3, logSd=0.6)";

        String source = "PositiveReal lambda ~ " + upstreamCall + "\n"
                + "NonNegativeInteger count ~ Poisson(rate=lambda) observed as "
                + observedCount;

        BEASTState graph = build(source);
        RealScalarParam<?> rate = parameter(graph, "lambda");
        var upstreamPrior = graph.priorDistributions.get(rate);
        assertNotNull(upstreamPrior);

        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(1, graph.operators.size());

        Poisson likelihood = assertInstanceOf(Poisson.class, graph.likelihoodDistributions.getFirst());
        IntScalarParam<?> count = assertInstanceOf(IntScalarParam.class, likelihood.paramInput.get());

        assertSame(rate, likelihood.lambdaInput.get());
        assertSame(PositiveReal.INSTANCE, rate.getDomain());
        assertTrue(rate.getOutputs().contains(likelihood));
        assertEquals(observedCount, count.get());

        // A positive integer literal may retain the narrower PositiveInt domain.
        assertInstanceOf(NonNegativeInt.class, count.getDomain());
        assertFalse(graph.stateNodes.containsKey(count));
        assertEquals(List.of(rate), graph.operators.getFirst().listStateNodes());

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(upstreamPrior, likelihood));

        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);

        assertEquals(
                upstreamLogDensity(upstreamFamily, rate.get()) + poissonLogProbability(observedCount, rate.get()),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);

        boolean[] decisions = {true, false, true};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            double previousRate = rate.get();
            double previousPrior = upstreamPrior.getCurrentLogP();
            double previousLikelihood = likelihood.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();

            state.store(step + 1);
            rate.set(previousRate + 0.5 * (step + 1));
            double proposedRate = rate.get();

            assertEquals(observedCount, count.get());

            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            double expectedPrior = upstreamLogDensity(upstreamFamily, proposedRate);
            double expectedLikelihood = poissonLogProbability(observedCount, proposedRate);
            double proposedPosterior = posterior.calculateLogP();

            assertTrue(Double.isFinite(proposedPosterior));
            assertNotEquals(previousLikelihood, expectedLikelihood, TOLERANCE);
            assertEquals(expectedPrior, upstreamPrior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedPrior + expectedLikelihood, proposedPosterior, TOLERANCE);

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);

            assertEquals(accept ? proposedRate : previousRate, rate.get(), 0.0);
            assertEquals(observedCount, count.get());
            assertSame(rate, likelihood.lambdaInput.get());
            assertSame(PositiveReal.INSTANCE, rate.getDomain());

            // Check restored caches before recalculating any probability.
            assertEquals(accept ? expectedPrior : previousPrior, upstreamPrior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedLikelihood : previousLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);

            assertEquals(
                    upstreamLogDensity(upstreamFamily, rate.get()) + poissonLogProbability(observedCount, rate.get()),
                    posterior.calculateLogP(),
                    TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void constantRateStillSupportsObservedCounts(int observedCount) throws Exception {
        BEASTState graph = build("NonNegativeInteger count ~ Poisson(rate=2.3) observed as " + observedCount);

        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());

        Poisson likelihood = assertInstanceOf(Poisson.class, graph.likelihoodDistributions.getFirst());
        assertEquals(2.3, likelihood.lambdaInput.get().get(), 0.0);
        assertEquals(observedCount, likelihood.paramInput.get().get());
        assertEquals(poissonLogProbability(observedCount, 2.3), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "-1.0"})
    void coreStillRejectsNonPositiveRates(String rate) {
        assertThrows(
                TypeError.class, () -> build("NonNegativeInteger count ~ Poisson(rate=" + rate + ") observed as 3"));
    }

    @Test
    void shortChainEstimatesRateForAnObservedCount(@TempDir Path directory) throws Exception {
        String source = """
                PositiveReal lambda ~ Gamma(shape=2.0, rate=1.0)
                NonNegativeInteger count ~ Poisson(rate=lambda) observed as 3

                mcmc {
                    Integer chainLength = 200
                    Integer defaultLogEvery = 10
                    Integer randomSeed = 12345
                }
                """;

        Path prefix = directory.resolve("poisson-rate");
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

    private static double poissonLogProbability(int count, double rate) {
        double logFactorial = 0.0;
        for (int i = 2; i <= count; i++) {
            logFactorial += Math.log(i);
        }
        return count * Math.log(rate) - rate - logFactorial;
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

        var graph = evaluator.applyBestTiling(new BEASTState("poisson-rate-binding"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
