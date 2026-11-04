package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.LogNormal;
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
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiling.LiveOffsetDistribution;

class OffsetDistributionTileTest {
    private static final double TOLERANCE = 1e-10;

    @ParameterizedTest
    @CsvSource({"-2.0, false", "0.0, false", "3.5, false", "-2.0, true", "0.0, true", "3.5, true"})
    void fixedOffsetsShiftTheLawWithoutDrawingTheBase(double offset, boolean observed) throws Exception {
        BEASTState graph = build("Real x ~ Offset(base=Normal(mean=1.0, sd=2.0), offset=" + offset + ")"
                + (observed ? " observed as -0.5" : ""));
        LiveOffsetDistribution shifted = assertInstanceOf(
                LiveOffsetDistribution.class,
                observed
                        ? graph.likelihoodDistributions.getFirst()
                        : graph.priorDistributions.get(parameter(graph, "x")));
        Normal base = assertInstanceOf(Normal.class, shifted.distributionInput.get());
        assertNull(base.paramInput.get(), "The base describes a law; it must not create a separate sampled value.");
        assertEquals(observed ? 0 : 1, graph.stateNodes.size());
        assertEquals(observed ? 0 : 1, graph.priorDistributions.size());
        assertEquals(observed ? 1 : 0, graph.likelihoodDistributions.size());
        assertEquals(observed ? 0 : 1, graph.operators.size());
        assertEquals(offset, shifted.offsetInput.get().get(), 0.0);
        double value = observed ? -0.5 : 0.1 + offset;
        assertEquals(value, shifted.paramInput.get().get(), 0.0);
        assertEquals(normalLogDensity(value - offset, 1.0, 2.0), shifted.calculateLogP(), TOLERANCE);
        if (!observed) {
            RealScalarParam<?> x = parameter(graph, "x");
            assertSame(Real.INSTANCE, x.getDomain());
            assertSame(x, shifted.paramInput.get());
            Operator operator = graph.operators.getFirst();
            assertInstanceOf(RealRandomWalkOperator.class, operator);
            assertEquals(List.of(x), operator.listStateNodes());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2", "-2"})
    void integerOffsetLiteralsUseTheRealInputPath(String offset) throws Exception {
        BEASTState graph =
                build("Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=" + offset + ") observed as 0.0");
        LiveOffsetDistribution likelihood = likelihood(graph, "x_likelihood");
        assertEquals(Double.parseDouble(offset), likelihood.offsetInput.get().get(), 0.0);
        assertEquals(normalLogDensity(0.0, Double.parseDouble(offset), 1.0), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({"-1.0, true", "0.0, true", "1.0, true", "-1.5, false", "1.5, false"})
    void uniformBaseRetainsShiftedSupportAndImpossibleObservations(double observation, boolean supported)
            throws Exception {
        BEASTState graph =
                build("Real x ~ Offset(base=Uniform(lower=1.0, upper=3.0), offset=-2.0) observed as " + observation);
        LiveOffsetDistribution likelihood = likelihood(graph, "x_likelihood");
        assertInstanceOf(Uniform.class, likelihood.distributionInput.get());
        assertEquals(observation, likelihood.paramInput.get().get(), 0.0);
        assertEquals(-1.0, likelihood.getLowerBoundOfParameter(), 0.0);
        assertEquals(1.0, likelihood.getUpperBoundOfParameter(), 0.0);
        assertEquals(supported ? -Math.log(2.0) : Double.NEGATIVE_INFINITY, likelihood.calculateLogP(), TOLERANCE);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    @Test
    void nestedOffsetComposesLawsWithoutAddingSamplingState() throws Exception {
        BEASTState graph = build("""
                Real x ~ Offset(base=Offset(base=Normal(mean=1.0, sd=2.0), offset=3.0), offset=-5.0)
                """);
        RealScalarParam<?> x = parameter(graph, "x");
        LiveOffsetDistribution outer = assertInstanceOf(LiveOffsetDistribution.class, graph.priorDistributions.get(x));
        LiveOffsetDistribution inner = assertInstanceOf(LiveOffsetDistribution.class, outer.distributionInput.get());
        Normal base = assertInstanceOf(Normal.class, inner.distributionInput.get());
        assertNull(inner.paramInput.get());
        assertNull(base.paramInput.get());
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        assertEquals(0.1 + 3.0 - 5.0, x.get(), 0.0);
        assertEquals(normalLogDensity(x.get(), -1.0, 2.0), outer.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedLiveInputsUpdatePosteriorAndRestoreAcceptedAndRejectedStates(boolean acceptFirst) throws Exception {
        BEASTState graph = build("""
                Real location ~ Normal(mean=0.0, sd=2.0)
                PositiveReal scale ~ LogNormal(logMean=0.0, logSd=0.5)
                Real shift ~ Uniform(lower=-3.0, upper=3.0)
                Real first ~ Offset(base=Normal(mean=location, sd=scale), offset=shift) observed as -1.0
                Real second ~ Offset(base=Normal(mean=location, sd=scale), offset=shift) observed as 2.0
                """);
        RealScalarParam<?> location = parameter(graph, "location");
        RealScalarParam<?> scale = parameter(graph, "scale");
        RealScalarParam<?> shift = parameter(graph, "shift");
        Normal locationPrior = assertInstanceOf(Normal.class, graph.priorDistributions.get(location));
        LogNormal scalePrior = assertInstanceOf(LogNormal.class, graph.priorDistributions.get(scale));
        Uniform shiftPrior = assertInstanceOf(Uniform.class, graph.priorDistributions.get(shift));
        LiveOffsetDistribution first = likelihood(graph, "first_likelihood");
        LiveOffsetDistribution second = likelihood(graph, "second_likelihood");
        Normal firstBase = assertInstanceOf(Normal.class, first.distributionInput.get());
        Normal secondBase = assertInstanceOf(Normal.class, second.distributionInput.get());
        assertEquals(3, graph.stateNodes.size());
        assertEquals(3, graph.priorDistributions.size());
        assertEquals(3, graph.operators.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        for (LiveOffsetDistribution shifted : List.of(first, second)) {
            Normal base = assertInstanceOf(Normal.class, shifted.distributionInput.get());
            assertSame(shift, shifted.offsetInput.get());
            assertSame(location, base.meanInput.get());
            assertSame(scale, base.sdInput.get());
            assertNull(base.paramInput.get());
            assertTrue(shift.getOutputs().contains(shifted));
            assertTrue(location.getOutputs().contains(base));
            assertTrue(scale.getOutputs().contains(base));
            assertTrue(base.getOutputs().contains(shifted));
        }

        List<Distribution> factors = List.of(locationPrior, scalePrior, shiftPrior, first, second);
        CompoundDistribution posterior = posterior(factors);
        State state = state(graph, posterior);
        assertEquals(
                sum(expectedFactors(location.get(), scale.get(), shift.get())),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);
        List<RealScalarParam<?>> parameters = List.of(location, scale, shift);
        int[] updatedInputs = {0, 0, 1, 1, 2, 2};
        // Avoid means reflected around the two observations' midpoint: their total
        // likelihood would be identical even when both dependencies update correctly.
        double[] proposals = {-0.5, 1.0, 0.8, 1.5, -0.75, 1.25};
        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst, !acceptFirst, acceptFirst, !acceptFirst};
        for (int step = 0; step < proposals.length; step++) {
            double previousLocation = location.get();
            double previousScale = scale.get();
            double previousShift = shift.get();
            double previousPosterior = posterior.getCurrentLogP();
            double[] previousFactors =
                    factors.stream().mapToDouble(Distribution::getCurrentLogP).toArray();
            state.store(step + 1);
            // Change one input at a time so each dependency edge is tested independently.
            parameters.get(updatedInputs[step]).set(proposals[step]);
            double proposedLocation = location.get();
            double proposedScale = scale.get();
            double proposedShift = shift.get();
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double[] expected = expectedFactors(location.get(), scale.get(), shift.get());
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(sum(expected), proposedPosterior, TOLERANCE);
            assertNotEquals(previousPosterior, proposedPosterior, TOLERANCE);
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(expected[i], factors.get(i).getCurrentLogP(), TOLERANCE);
            }

            finishProposal(state, decisions[step]);
            assertEquals(decisions[step] ? proposedLocation : previousLocation, location.get(), 0.0);
            assertEquals(decisions[step] ? proposedScale : previousScale, scale.get(), 0.0);
            assertEquals(decisions[step] ? proposedShift : previousShift, shift.get(), 0.0);
            assertSame(location, firstBase.meanInput.get());
            assertSame(location, secondBase.meanInput.get());
            assertSame(scale, firstBase.sdInput.get());
            assertSame(scale, secondBase.sdInput.get());
            assertSame(shift, first.offsetInput.get());
            assertSame(shift, second.offsetInput.get());
            assertEquals(-1.0, first.paramInput.get().get(), 0.0);
            assertEquals(2.0, second.paramInput.get().get(), 0.0);
            // Inspect caches before recalculation could conceal a restoration defect.
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(
                        decisions[step] ? expected[i] : previousFactors[i],
                        factors.get(i).getCurrentLogP(),
                        TOLERANCE);
            }
            assertEquals(
                    decisions[step] ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    sum(expectedFactors(location.get(), scale.get(), shift.get())),
                    posterior.calculateLogP(),
                    TOLERANCE);
        }
    }

    @Test
    void narrowerDomainRandomOffsetRetainsItsOriginalObject() throws Exception {
        BEASTState graph = build("""
                PositiveReal shift ~ LogNormal(logMean=0.0, logSd=0.5)
                Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=shift) observed as -1.0
                """);
        RealScalarParam<?> shift = parameter(graph, "shift");
        LiveOffsetDistribution likelihood = likelihood(graph, "x_likelihood");
        assertSame(shift, likelihood.offsetInput.get());
        assertEquals(1, graph.stateNodes.size());
        for (double value : new double[] {0.1, 1.0, 2.0}) {
            shift.set(value);
            assertEquals(normalLogDensity(-1.0, value, 1.0), likelihood.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void positiveBaseCanProduceNegativeRealValuesAfterShifting(boolean observed) throws Exception {
        BEASTState graph = build("Real x ~ Offset(base=LogNormal(logMean=0.0, logSd=0.5), offset=-2.0)"
                + (observed ? " observed as -1.0" : ""));
        LiveOffsetDistribution shifted = assertInstanceOf(
                LiveOffsetDistribution.class,
                observed
                        ? graph.likelihoodDistributions.getFirst()
                        : graph.priorDistributions.get(parameter(graph, "x")));
        LogNormal base = assertInstanceOf(LogNormal.class, shifted.distributionInput.get());
        assertNull(base.paramInput.get());
        assertEquals(-1.0, shifted.paramInput.get().get(), 0.0);
        assertEquals(-2.0, shifted.getLowerBoundOfParameter(), 0.0);
        assertEquals(normalLogDensity(0.0, 0.0, 0.5), shifted.calculateLogP(), TOLERANCE);
        assertEquals(observed ? 0 : 1, graph.stateNodes.size());
        assertEquals(observed ? 0 : 1, graph.operators.size());
        if (!observed) assertSame(Real.INSTANCE, parameter(graph, "x").getDomain());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualOutputOperatorMovesFromZeroAndRestoresState(boolean acceptFirst) throws Exception {
        BEASTState graph = build("Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=-0.1)");
        RealScalarParam<?> x = parameter(graph, "x");
        assertEquals(0.0, x.get(), 0.0);
        LiveOffsetDistribution prior = assertInstanceOf(LiveOffsetDistribution.class, graph.priorDistributions.get(x));
        RealRandomWalkOperator operator = assertInstanceOf(RealRandomWalkOperator.class, graph.operators.getFirst());
        CompoundDistribution posterior = posterior(List.of(prior));
        State state = state(graph, posterior);
        assertEquals(normalLogDensity(0.0, -0.1, 1.0), state.robustlyCalcPosterior(posterior), TOLERANCE);
        Randomizer.setSeed(72419);
        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst};
        for (int step = 0; step < decisions.length; step++) {
            double previousValue = x.get();
            double previousPrior = prior.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            assertEquals(0.0, operator.proposal(), 0.0);
            assertNotEquals(previousValue, x.get());
            double proposedValue = x.get();
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double expected = normalLogDensity(proposedValue, -0.1, 1.0);
            assertEquals(expected, posterior.calculateLogP(), TOLERANCE);
            assertEquals(expected, prior.getCurrentLogP(), TOLERANCE);
            finishProposal(state, decisions[step]);
            assertEquals(decisions[step] ? proposedValue : previousValue, x.get(), 0.0);
            assertEquals(decisions[step] ? expected : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(decisions[step] ? expected : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(normalLogDensity(x.get(), -0.1, 1.0), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shortSeededChainsMoveTheSampledOutputOrObservedModelOffset(boolean observed, @TempDir Path directory)
            throws Exception {
        Path prefix = directory.resolve(observed ? "offset-observed" : "offset-sampled");
        String model = observed
                ? "Real shift ~ Uniform(lower=-3.0, upper=3.0)\n"
                        + "Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=shift) observed as 1.0\n"
                : "Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=-0.1)\n";
        String source = model + """
                mcmc {
                    Integer chainLength = 100
                    Integer randomSeed = 25191
                    Integer defaultLogEvery = 5
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("offset-distribution-short-chain");
        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> header = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int valueColumn = header.indexOf(observed ? "shift" : "x");
        int posteriorColumn = header.indexOf("posterior");
        assertTrue(valueColumn >= 0 && posteriorColumn >= 0, header.toString());
        assertEquals(22, lines.size(), "Header plus samples 0, 5, ..., 100.");
        Set<Double> values = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.trim().split("\\s+");
            double value = Double.parseDouble(fields[valueColumn]);
            assertTrue(Double.isFinite(value));
            assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
            if (observed) assertTrue(value >= -3.0 && value <= 3.0);
            values.add(value);
        }
        assertTrue(values.size() > 1, "The sampled scalar must move under its default operator.");
        assertEquals("100", lines.getLast().trim().split("\\s+")[0]);
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static double[] expectedFactors(double location, double scale, double shift) {
        return new double[] {
            normalLogDensity(location, 0.0, 2.0),
            normalLogDensity(Math.log(scale), 0.0, 0.5) - Math.log(scale),
            -Math.log(6.0),
            normalLogDensity(-1.0 - shift, location, scale),
            normalLogDensity(2.0 - shift, location, scale)
        };
    }

    private static double sum(double[] values) {
        return Arrays.stream(values).sum();
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double residual = (value - mean) / sd;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(sd) - 0.5 * residual * residual;
    }

    private static RealScalarParam<?> parameter(BEASTState graph, String id) {
        return assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(node -> id.equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static LiveOffsetDistribution likelihood(BEASTState graph, String id) {
        return assertInstanceOf(
                LiveOffsetDistribution.class,
                graph.likelihoodDistributions.stream()
                        .filter(distribution -> id.equals(distribution.getID()))
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

    private static void finishProposal(State state, boolean accept) {
        if (accept) {
            state.acceptCalculationNodes();
        } else {
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("offset-distribution-test"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
