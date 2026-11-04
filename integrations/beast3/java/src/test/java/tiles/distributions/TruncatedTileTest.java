package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
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
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiling.LiveTruncatedDistribution;

class TruncatedTileTest {
    private static final double TOLERANCE = 1e-10;

    @ParameterizedTest
    @CsvSource({"-1.0,2.0,false", "2.0,4.0,false", "-4.0,-2.0,false", "-1.0,2.0,true", "2.0,4.0,true"})
    void sampledAndObservedNormalsUseOneNormalizedLaw(double lower, double upper, boolean observed) throws Exception {
        double observation = (lower + upper) / 2.0;
        BEASTState graph = build("Real x ~ Truncated(base=Normal(mean=0.0, sd=1.0), lower=" + lower + ", upper=" + upper
                + ")" + (observed ? " observed as " + observation : ""));
        LiveTruncatedDistribution truncated = assertInstanceOf(
                LiveTruncatedDistribution.class,
                observed
                        ? graph.likelihoodDistributions.getFirst()
                        : graph.priorDistributions.get(parameter(graph, "x")));
        Normal base = assertInstanceOf(Normal.class, truncated.distributionInput.get());
        assertNull(base.paramInput.get(), "The base law must not create a second sampled variable.");
        assertEquals(observed ? 0 : 1, graph.stateNodes.size());
        assertEquals(observed ? 0 : 1, graph.priorDistributions.size());
        assertEquals(observed ? 1 : 0, graph.likelihoodDistributions.size());
        assertEquals(observed ? 0 : 1, graph.operators.size());
        double value = truncated.paramInput.get().get();
        assertTrue(value >= lower && value <= upper);
        assertEquals(truncatedNormalLogDensity(value, 0.0, 1.0, lower, upper), truncated.calculateLogP(), TOLERANCE);
        if (observed) {
            assertEquals(observation, value, 0.0);
        } else {
            RealScalarParam<?> x = parameter(graph, "x");
            assertSame(Real.INSTANCE, x.getDomain());
            assertSame(x, truncated.paramInput.get());
            assertEquals(List.of(x), graph.operators.getFirst().listStateNodes());
            assertInstanceOf(RealRandomWalkOperator.class, graph.operators.getFirst());
            if (lower <= 0.1 && 0.1 <= upper) {
                assertEquals(0.1, value, 0.0, "Keep the valid base initializer.");
            } else {
                var normal = org.apache.commons.statistics.distribution.NormalDistribution.of(0.0, 1.0);
                double lowerP = normal.cumulativeProbability(lower);
                double upperP = normal.cumulativeProbability(upper);
                assertEquals(normal.inverseCumulativeProbability(lowerP + (upperP - lowerP) / 2.0), value, TOLERANCE);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"lower=0.0", "upper=0.0"})
    void omittedBoundsFollowTheCoreDefaultsAndHalfNormalsAreNormalized(String bounds) throws Exception {
        BEASTState graph = build("Real x ~ Truncated(base=Normal(mean=0.0, sd=1.0), " + bounds + ") observed as 0.0");
        LiveTruncatedDistribution likelihood = likelihood(graph, "x_likelihood");
        double normalAtZero = normalLogDensity(0.0, 0.0, 1.0);
        assertEquals(normalAtZero + Math.log(2.0), likelihood.calculateLogP(), TOLERANCE);
        assertEquals(
                bounds.startsWith("lower") ? 0.0 : Double.NEGATIVE_INFINITY, likelihood.getLowerBoundOfParameter());
        assertEquals(
                bounds.startsWith("upper") ? 0.0 : Double.POSITIVE_INFINITY, likelihood.getUpperBoundOfParameter());
    }

    @Test
    void broadFiniteBoundsRetainNormalDensityAtMachinePrecision() throws Exception {
        BEASTState graph = build("""
                Real x ~ Truncated(base=Normal(mean=0.0,sd=1.0),lower=-100.0,upper=100.0) observed as 0.0
                """);
        LiveTruncatedDistribution likelihood = likelihood(graph, "x_likelihood");
        assertEquals(normalLogDensity(0.0, 0.0, 1.0), likelihood.calculateLogP(), TOLERANCE);
        assertEquals(-100.0, likelihood.getLowerBoundOfParameter());
        assertEquals(100.0, likelihood.getUpperBoundOfParameter());
    }

    @Test
    void currentCoreRequiresABoundToInferTheTruncatedOutputType() {
        TypeError failure = assertThrows(
                TypeError.class, () -> build("Real x ~ Truncated(base=Normal(mean=0.0,sd=1.0)) observed as 0.0"));
        assertTrue(failure.getMessage().contains("The type 'T' does not exist"));
    }

    @Test
    void narrowerFixedRealBoundsPreserveTheirValues() throws Exception {
        BEASTState graph = build("""
                PositiveReal lower = 0.5
                PositiveReal upper = 2.0
                Real x ~ Truncated(base=Normal(mean=0.0,sd=1.0),lower=lower,upper=upper) observed as 1.0
                """);
        LiveTruncatedDistribution likelihood = likelihood(graph, "x_likelihood");
        assertEquals(0.5, likelihood.lowerInput.get().get(), 0.0);
        assertEquals(2.0, likelihood.upperInput.get().get(), 0.0);
        assertEquals(truncatedNormalLogDensity(1.0, 0.0, 1.0, 0.5, 2.0), likelihood.calculateLogP(), TOLERANCE);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"-1.0,false", "0.5,false", "1.0,true", "2.0,true", "3.0,true", "3.5,false", "5.0,false"})
    void uniformObservationsRetainTheirValuesAtAndOutsideTheTruncatedSupport(double observation, boolean supported)
            throws Exception {
        BEASTState graph =
                build("Real x ~ Truncated(base=Uniform(lower=0.0, upper=4.0), lower=1.0, upper=3.0) observed as "
                        + observation);
        LiveTruncatedDistribution likelihood = likelihood(graph, "x_likelihood");
        assertEquals(observation, likelihood.paramInput.get().get(), 0.0);
        assertEquals(supported ? -Math.log(2.0) : Double.NEGATIVE_INFINITY, likelihood.calculateLogP(), TOLERANCE);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    @Test
    void fixedComputedRealBoundsAreAccepted() throws Exception {
        BEASTState graph = build("""
                Real lower = sum(vector=[-2.0,1.0])
                Real upper = sum(vector=[1.0,1.0])
                Real x ~ Truncated(base=Normal(mean=0.0, sd=1.0), lower=lower, upper=upper) observed as 0.5
                """);
        LiveTruncatedDistribution likelihood = likelihood(graph, "x_likelihood");
        assertEquals(-1.0, likelihood.lowerInput.get().get(), 0.0);
        assertEquals(2.0, likelihood.upperInput.get().get(), 0.0);
        assertEquals(truncatedNormalLogDensity(0.5, 0.0, 1.0, -1.0, 2.0), likelihood.calculateLogP(), TOLERANCE);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    @Test
    void positiveScalarBaseRetainsItsLawAndProducesARealOutput() throws Exception {
        BEASTState graph = build("Real x ~ Truncated(base=LogNormal(logMean=0.0,logSd=0.5),lower=0.5,upper=2.0)");
        RealScalarParam<?> x = parameter(graph, "x");
        LiveTruncatedDistribution prior =
                assertInstanceOf(LiveTruncatedDistribution.class, graph.priorDistributions.get(x));
        assertSame(Real.INSTANCE, x.getDomain());
        assertEquals(1.0, x.get(), 0.0);
        assertNull(prior.distributionInput.get().paramInput.get());
        var logSpace = org.apache.commons.statistics.distribution.NormalDistribution.of(0.0, 0.5);
        double mass = logSpace.cumulativeProbability(Math.log(2.0)) - logSpace.cumulativeProbability(Math.log(0.5));
        assertEquals(normalLogDensity(0.0, 0.0, 0.5) - Math.log(mass), prior.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Offset(base=Normal(mean=0.0,sd=1.0),offset=1.0)",
                "Truncated(base=Normal(mean=0.0,sd=1.0),lower=-2.0,upper=3.0)"
            })
    void unsupportedComposedBasesAreRejectedInsteadOfUsingTheirUnderlyingUntransformedLaw(String base) {
        assertThrows(
                TileApplicationError.class, () -> build("Real x ~ Truncated(base=" + base + ",lower=-1.0,upper=2.0)"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"Normal(mean=0.0,sd=1.0),lower=1.0,upper=1.0", "Uniform(lower=0.0,upper=1.0),lower=2.0,upper=3.0"
            })
    void zeroWidthAndDisjointInitialIntervalsAreReported(String arguments) {
        assertThrows(TileApplicationError.class, () -> build("Real x ~ Truncated(base=" + arguments + ")"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lower=bound,upper=4.0", "lower=-4.0,upper=bound", "lower=exp(bound),upper=4.0"})
    void stochasticAndStochasticDerivedBoundsAreRejected(String bounds) {
        assertThrows(TileApplicationError.class, () -> build("""
                Real bound ~ Normal(mean=0.0,sd=1.0)
                Real x ~ Truncated(base=Normal(mean=0.0,sd=1.0),%s)
                """.formatted(bounds)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedBaseInputsUpdateNormalizationAndRestoreAcceptedAndRejectedStates(boolean acceptFirst) throws Exception {
        BEASTState graph = build("""
                Real location ~ Normal(mean=0.0,sd=2.0)
                PositiveReal scale ~ LogNormal(logMean=0.0,logSd=0.5)
                Real first ~ Truncated(base=Normal(mean=location,sd=scale),lower=-1.0,upper=2.0) observed as -0.5
                Real second ~ Truncated(base=Normal(mean=location,sd=scale),lower=-1.0,upper=2.0) observed as 1.5
                """);
        RealScalarParam<?> location = parameter(graph, "location");
        RealScalarParam<?> scale = parameter(graph, "scale");
        LiveTruncatedDistribution first = likelihood(graph, "first_likelihood");
        LiveTruncatedDistribution second = likelihood(graph, "second_likelihood");
        assertEquals(2, graph.stateNodes.size());
        assertEquals(2, graph.priorDistributions.size());
        assertEquals(2, graph.operators.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        for (LiveTruncatedDistribution truncated : List.of(first, second)) {
            Normal base = assertInstanceOf(Normal.class, truncated.distributionInput.get());
            assertSame(location, base.meanInput.get());
            assertSame(scale, base.sdInput.get());
            assertNull(base.paramInput.get());
            assertTrue(location.getOutputs().contains(base));
            assertTrue(scale.getOutputs().contains(base));
            assertTrue(base.getOutputs().contains(truncated));
        }
        List<Distribution> factors =
                List.of(graph.priorDistributions.get(location), graph.priorDistributions.get(scale), first, second);
        CompoundDistribution posterior = posterior(factors);
        State state = state(graph, posterior);
        assertEquals(
                sum(expectedFactors(location.get(), scale.get())), state.robustlyCalcPosterior(posterior), TOLERANCE);
        List<RealScalarParam<?>> parameters = List.of(location, scale);
        int[] changed = {0, 0, 1, 1};
        double[] proposals = {-0.5, 0.8, 0.7, 1.4};
        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst, !acceptFirst};
        for (int step = 0; step < proposals.length; step++) {
            double previousLocation = location.get();
            double previousScale = scale.get();
            double previousPosterior = posterior.getCurrentLogP();
            double[] previousFactors =
                    factors.stream().mapToDouble(Distribution::getCurrentLogP).toArray();
            state.store(step + 1);
            parameters.get(changed[step]).set(proposals[step]);
            double proposedLocation = location.get();
            double proposedScale = scale.get();
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double[] expected = expectedFactors(proposedLocation, proposedScale);
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(sum(expected), proposedPosterior, TOLERANCE);
            assertNotEquals(previousPosterior, proposedPosterior, TOLERANCE);
            for (int i = 0; i < factors.size(); i++)
                assertEquals(expected[i], factors.get(i).getCurrentLogP(), TOLERANCE);
            finishProposal(state, decisions[step]);
            assertEquals(decisions[step] ? proposedLocation : previousLocation, location.get(), 0.0);
            assertEquals(decisions[step] ? proposedScale : previousScale, scale.get(), 0.0);
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(
                        decisions[step] ? expected[i] : previousFactors[i],
                        factors.get(i).getCurrentLogP(),
                        TOLERANCE);
            }
            assertEquals(
                    decisions[step] ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(sum(expectedFactors(location.get(), scale.get())), posterior.calculateLogP(), TOLERANCE);
            assertEquals(-0.5, first.paramInput.get().get(), 0.0);
            assertEquals(1.5, second.paramInput.get().get(), 0.0);
        }
    }

    @Test
    void proposalRemovingAllRetainedMassIsRejectedAndRestored() throws Exception {
        BEASTState graph = build("""
                Real lower ~ Uniform(lower=-4.0,upper=4.0)
                Real x ~ Truncated(base=Uniform(lower=lower,upper=5.0),lower=0.0,upper=1.0) observed as 0.5
                """);
        RealScalarParam<?> lower = parameter(graph, "lower");
        LiveTruncatedDistribution likelihood = likelihood(graph, "x_likelihood");
        Uniform base = assertInstanceOf(Uniform.class, likelihood.distributionInput.get());
        assertSame(lower, base.lowerInput.get());
        CompoundDistribution posterior = posterior(List.of(graph.priorDistributions.get(lower), likelihood));
        State state = state(graph, posterior);
        double previousPosterior = state.robustlyCalcPosterior(posterior);
        double previousLikelihood = likelihood.getCurrentLogP();
        assertEquals(-Math.log(8.0), previousPosterior, TOLERANCE);
        state.store(1);
        lower.set(2.0);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        assertEquals(Double.NEGATIVE_INFINITY, posterior.calculateLogP());
        finishProposal(state, false);
        assertEquals(0.0, lower.get(), 0.0);
        assertEquals(previousLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeRandomWalkMovesWithinBoundsAndRestoresState(boolean acceptFirst) throws Exception {
        BEASTState graph = build("Real x ~ Truncated(base=Normal(mean=0.0,sd=1.0),lower=-4.0,upper=4.0)");
        RealScalarParam<?> x = parameter(graph, "x");
        LiveTruncatedDistribution prior =
                assertInstanceOf(LiveTruncatedDistribution.class, graph.priorDistributions.get(x));
        RealRandomWalkOperator operator = assertInstanceOf(RealRandomWalkOperator.class, graph.operators.getFirst());
        CompoundDistribution posterior = posterior(List.of(prior));
        State state = state(graph, posterior);
        state.robustlyCalcPosterior(posterior);
        Randomizer.setSeed(46129);
        boolean[] decisions = {acceptFirst, !acceptFirst};
        for (int step = 0; step < decisions.length; step++) {
            double previous = x.get();
            double previousPrior = prior.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            assertEquals(0.0, operator.proposal(), 0.0);
            double proposed = x.get();
            assertNotEquals(previous, proposed);
            assertTrue(proposed >= -4.0 && proposed <= 4.0);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double expected = truncatedNormalLogDensity(proposed, 0.0, 1.0, -4.0, 4.0);
            assertEquals(expected, posterior.calculateLogP(), TOLERANCE);
            finishProposal(state, decisions[step]);
            assertEquals(decisions[step] ? proposed : previous, x.get(), 0.0);
            assertEquals(decisions[step] ? expected : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(decisions[step] ? expected : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shortChainsMoveTheSampledValueOrObservedModelMean(boolean observed, @TempDir Path directory) throws Exception {
        Path prefix = directory.resolve(observed ? "truncated-observed" : "truncated-sampled");
        String model = observed
                ? "Real location ~ Uniform(lower=-1.0,upper=1.0)\n"
                        + "Real x ~ Truncated(base=Normal(mean=location,sd=1.0),lower=-1.0,upper=2.0) observed as 0.5\n"
                : "Real x ~ Truncated(base=Normal(mean=0.0,sd=1.0),lower=-1.0,upper=2.0)\n";
        String source = model + """
                mcmc {
                    Integer chainLength = 100
                    Integer randomSeed = 72641
                    Integer defaultLogEvery = 5
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("truncated-short-chain");
        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> columns = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int valueColumn = columns.indexOf(observed ? "location" : "x");
        int posteriorColumn = columns.indexOf("posterior");
        assertTrue(valueColumn >= 0 && posteriorColumn >= 0, columns.toString());
        assertEquals(22, lines.size(), "Header plus samples 0,5,...,100.");
        Set<Double> values = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.trim().split("\\s+");
            double value = Double.parseDouble(fields[valueColumn]);
            assertTrue(Double.isFinite(value));
            assertTrue(value >= -1.0 && value <= (observed ? 1.0 : 2.0));
            assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
            values.add(value);
        }
        assertTrue(values.size() > 1, "The sampled parameter must move.");
        assertEquals("100", lines.getLast().trim().split("\\s+")[0]);
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static double[] expectedFactors(double location, double scale) {
        return new double[] {
            normalLogDensity(location, 0.0, 2.0),
            normalLogDensity(Math.log(scale), 0.0, 0.5) - Math.log(scale),
            truncatedNormalLogDensity(-0.5, location, scale, -1.0, 2.0),
            truncatedNormalLogDensity(1.5, location, scale, -1.0, 2.0)
        };
    }

    private static double sum(double[] values) {
        return Arrays.stream(values).sum();
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double residual = (value - mean) / sd;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(sd) - residual * residual / 2.0;
    }

    private static double truncatedNormalLogDensity(double value, double mean, double sd, double lower, double upper) {
        if (value < lower || value > upper) return Double.NEGATIVE_INFINITY;
        var normal = org.apache.commons.statistics.distribution.NormalDistribution.of(mean, sd);
        return normalLogDensity(value, mean, sd)
                - Math.log(normal.cumulativeProbability(upper) - normal.cumulativeProbability(lower));
    }

    private static RealScalarParam<?> parameter(BEASTState graph, String id) {
        return assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(node -> id.equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static LiveTruncatedDistribution likelihood(BEASTState graph, String id) {
        return assertInstanceOf(
                LiveTruncatedDistribution.class,
                graph.likelihoodDistributions.stream()
                        .filter(value -> id.equals(value.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static CompoundDistribution posterior(List<Distribution> factors) {
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        return posterior;
    }

    private static State state(BEASTState graph, Distribution posterior) {
        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        return state;
    }

    private static void finishProposal(State state, boolean accepted) {
        if (accepted) state.acceptCalculationNodes();
        else {
            state.restore();
            state.restoreCalculationNodes();
        }
        state.setEverythingDirty(false);
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("truncated-test"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
