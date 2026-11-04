package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import runner.PhyloSpecRunner;
import tiling.NormalMixtureDistribution;

class MixtureTileTest {
    private static final double TOLERANCE = 1e-10;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sampledAndObservedMixturesHaveOnlyOneOutputAndOneFactor(boolean observed) throws Exception {
        BEASTState graph = build("""
                Simplex weights = [0.25,0.75]
                Real y ~ Mixture(components=[Normal(mean=-1.0,sd=1.0),Normal(mean=2.0,sd=0.5)],weights=weights)
                """.stripTrailing() + (observed ? " observed as -0.5" : ""));
        NormalMixtureDistribution mixture = assertInstanceOf(
                NormalMixtureDistribution.class,
                observed ? graph.likelihoodDistributions.getFirst() : graph.priorDistributions.get(node(graph, "y")));
        assertEquals(observed ? 0 : 1, graph.stateNodes.size());
        assertEquals(observed ? 0 : 1, graph.priorDistributions.size());
        assertEquals(observed ? 1 : 0, graph.likelihoodDistributions.size());
        assertEquals(observed ? 0 : 1, graph.operators.size());
        assertEquals(2, mixture.componentsInput.get().size());
        for (Normal component : mixture.componentsInput.get()) {
            assertSame(mixture.paramInput.get(), component.paramInput.get());
            assertFalse(graph.priorDistributions.containsValue(component));
            assertFalse(graph.likelihoodDistributions.contains(component));
        }
        double value = mixture.paramInput.get().get();
        assertTrue(Double.isFinite(value));
        double expected = mixtureLogDensity(value, -1.0, 1.0, 2.0, 0.5, 0.25);
        assertEquals(expected, mixture.calculateLogP(), TOLERANCE);
        assertNotEquals(normalLogDensity(value, -1.0, 1.0) + normalLogDensity(value, 2.0, 0.5), expected, TOLERANCE);
        if (observed) assertEquals(-0.5, value, 0.0);
        else {
            RealScalarParam<?> y = real(graph, "y");
            assertSame(Real.INSTANCE, y.getDomain());
            assertSame(y, mixture.paramInput.get());
            assertInstanceOf(RealRandomWalkOperator.class, graph.operators.getFirst());
            assertEquals(List.of(y), graph.operators.getFirst().listStateNodes());
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {-2.0, 0.0, 1.0, 40.0})
    void observationsIncludeNegativeZeroAndFarTailValues(double observed) throws Exception {
        BEASTState graph = build("""
                Simplex weights = [0.3,0.7]
                Real y ~ Mixture(components=[Normal(mean=0.0,sd=1.0),Normal(mean=1.0,sd=1.0)],weights=weights) observed as %s
                """.formatted(observed));
        NormalMixtureDistribution mixture = mixture(graph, "y_likelihood");
        assertEquals(observed, mixture.paramInput.get().get(), 0.0);
        assertEquals(mixtureLogDensity(observed, 0.0, 1.0, 1.0, 1.0, 0.3), mixture.calculateLogP(), TOLERANCE);
        assertTrue(Double.isFinite(mixture.getCurrentLogP()));
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    @Test
    void oneAndThreeComponentsIncludingZeroWeightsRetainTheirMathematicalMeaning() throws Exception {
        BEASTState graph = build("""
                Simplex one = [1.0]
                Simplex three = [0.0,0.25,0.75]
                Real first ~ Mixture(components=[Normal(mean=1.0,sd=2.0)],weights=one) observed as 0.5
                Real second ~ Mixture(components=[Normal(mean=20.0,sd=1.0),Normal(mean=-1.0,sd=1.0),Normal(mean=2.0,sd=0.5)],weights=three) observed as 0.5
                """);
        assertEquals(
                normalLogDensity(0.5, 1.0, 2.0),
                mixture(graph, "first_likelihood").calculateLogP(),
                TOLERANCE);
        assertEquals(
                mixtureLogDensity(0.5, -1.0, 1.0, 2.0, 0.5, 0.25),
                mixture(graph, "second_likelihood").calculateLogP(),
                TOLERANCE);
        assertEquals(2, graph.likelihoodDistributions.size());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
    }

    @Test
    void mismatchedWeightCountIsRejected() {
        RuntimeException error = assertThrows(RuntimeException.class, () -> build("""
                Simplex weights = [0.2,0.3,0.5]
                Real y ~ Mixture(components=[Normal(mean=0.0,sd=1.0),Normal(mean=1.0,sd=1.0)],weights=weights)
                """));
        assertTrue(error instanceof TypeError || error instanceof TileApplicationError, error.toString());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[Normal(mean=0.0,sd=1.0),Uniform(lower=-1.0,upper=1.0)]",
                "[Uniform(lower=-1.0,upper=1.0),Uniform(lower=-2.0,upper=2.0)]"
            })
    void otherDistributionFamiliesRemainOutsideThisTile(String components) {
        assertThrows(
                TileApplicationError.class,
                () -> build("Simplex weights = [0.5,0.5]\n" + "Real y ~ Mixture(components=" + components
                        + ",weights=weights)"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedWeightsMeanAndSdUpdateBothObservationsAndRestoreState(boolean acceptFirst) throws Exception {
        BEASTState graph = build("""
                Simplex weights ~ Dirichlet(concentration=[2.0,2.0])
                Real location ~ Uniform(lower=-2.0,upper=2.0)
                PositiveReal scale ~ LogNormal(logMean=0.0,logSd=0.5)
                Real first ~ Mixture(components=[Normal(mean=location,sd=scale),Normal(mean=2.0,sd=1.0)],weights=weights) observed as -0.5
                Real second ~ Mixture(components=[Normal(mean=location,sd=scale),Normal(mean=2.0,sd=1.0)],weights=weights) observed as 1.5
                """);
        SimplexParam weights = assertInstanceOf(SimplexParam.class, node(graph, "weights"));
        RealScalarParam<?> location = real(graph, "location");
        RealScalarParam<?> scale = real(graph, "scale");
        NormalMixtureDistribution first = mixture(graph, "first_likelihood");
        NormalMixtureDistribution second = mixture(graph, "second_likelihood");
        assertEquals(3, graph.stateNodes.size());
        assertEquals(3, graph.priorDistributions.size());
        assertEquals(3, graph.operators.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        for (NormalMixtureDistribution mixture : List.of(first, second)) {
            assertSame(weights, mixture.weightsInput.get());
            Normal component = mixture.componentsInput.get().getFirst();
            assertSame(location, component.meanInput.get());
            assertSame(scale, component.sdInput.get());
            assertTrue(weights.getOutputs().contains(mixture));
            assertTrue(component.getOutputs().contains(mixture));
            for (Normal normal : mixture.componentsInput.get())
                assertSame(mixture.paramInput.get(), normal.paramInput.get());
        }
        List<Distribution> factors = List.of(
                graph.priorDistributions.get(weights),
                graph.priorDistributions.get(location),
                graph.priorDistributions.get(scale),
                first,
                second);
        List<Distribution> cached = new ArrayList<>(factors);
        cached.addAll(first.componentsInput.get());
        cached.addAll(second.componentsInput.get());
        CompoundDistribution posterior = posterior(factors);
        State state = state(graph, posterior);
        assertEquals(
                sum(expectedFactors(weights.get(0), location.get(), scale.get())),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);
        int[] changed = {0, 0, 1, 1, 2, 2};
        double[] proposed = {0.2, 0.7, -0.7, 0.8, 0.7, 1.3};
        for (int step = 0; step < changed.length; step++) {
            boolean accepted = step % 2 == 0 ? acceptFirst : !acceptFirst;
            double previousWeight = weights.get(0);
            double previousLocation = location.get();
            double previousScale = scale.get();
            double previousPosterior = posterior.getCurrentLogP();
            double[] oldLogs =
                    cached.stream().mapToDouble(Distribution::getCurrentLogP).toArray();
            state.store(step + 1);
            if (changed[step] == 0) {
                weights.set(0, proposed[step]);
                weights.set(1, 1.0 - proposed[step]);
            } else if (changed[step] == 1) location.set(proposed[step]);
            else scale.set(proposed[step]);
            double proposedWeight = weights.get(0);
            double proposedLocation = location.get();
            double proposedScale = scale.get();
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double[] expected = expectedFactors(proposedWeight, proposedLocation, proposedScale);
            assertEquals(sum(expected), posterior.calculateLogP(), TOLERANCE);
            assertNotEquals(previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            for (int i = 0; i < factors.size(); i++)
                assertEquals(expected[i], factors.get(i).getCurrentLogP(), TOLERANCE);
            assertChildDensities(first, proposedLocation, proposedScale);
            assertChildDensities(second, proposedLocation, proposedScale);
            double[] proposedLogs =
                    cached.stream().mapToDouble(Distribution::getCurrentLogP).toArray();
            finishProposal(state, accepted);
            assertEquals(accepted ? proposedWeight : previousWeight, weights.get(0), 0.0);
            assertEquals(accepted ? 1.0 - proposedWeight : 1.0 - previousWeight, weights.get(1), TOLERANCE);
            assertEquals(accepted ? proposedLocation : previousLocation, location.get(), 0.0);
            assertEquals(accepted ? proposedScale : previousScale, scale.get(), 0.0);
            // Check native component, wrapper and posterior caches before any fresh calculation.
            for (int i = 0; i < cached.size(); i++)
                assertEquals(
                        accepted ? proposedLogs[i] : oldLogs[i], cached.get(i).getCurrentLogP(), TOLERANCE);
            assertEquals(accepted ? sum(expected) : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    sum(expectedFactors(weights.get(0), location.get(), scale.get())),
                    posterior.calculateLogP(),
                    TOLERANCE);
            assertEquals(-0.5, first.paramInput.get().get(), 0.0);
            assertEquals(1.5, second.paramInput.get().get(), 0.0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeRandomWalkMovesTheSingleMixtureValueAndRestoresCaches(boolean acceptFirst) throws Exception {
        BEASTState graph = build("""
                Simplex weights = [0.25,0.75]
                Real y ~ Mixture(components=[Normal(mean=-1.0,sd=1.0),Normal(mean=2.0,sd=0.5)],weights=weights)
                """);
        RealScalarParam<?> y = real(graph, "y");
        NormalMixtureDistribution prior =
                assertInstanceOf(NormalMixtureDistribution.class, graph.priorDistributions.get(y));
        RealRandomWalkOperator operator = assertInstanceOf(RealRandomWalkOperator.class, graph.operators.getFirst());
        CompoundDistribution posterior = posterior(List.of(prior));
        State state = state(graph, posterior);
        state.robustlyCalcPosterior(posterior);
        Randomizer.setSeed(81132);
        for (boolean accepted : new boolean[] {acceptFirst, !acceptFirst}) {
            double previous = y.get();
            double previousLogP = posterior.getCurrentLogP();
            double[] previousComponents = prior.componentsInput.get().stream()
                    .mapToDouble(Distribution::getCurrentLogP)
                    .toArray();
            state.store(1);
            assertEquals(0.0, operator.proposal(), 0.0);
            double proposed = y.get();
            assertNotEquals(previous, proposed);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double expected = mixtureLogDensity(proposed, -1.0, 1.0, 2.0, 0.5, 0.25);
            assertEquals(expected, posterior.calculateLogP(), TOLERANCE);
            double[] proposedComponents = prior.componentsInput.get().stream()
                    .mapToDouble(Distribution::getCurrentLogP)
                    .toArray();
            finishProposal(state, accepted);
            assertEquals(accepted ? proposed : previous, y.get(), 0.0);
            assertEquals(accepted ? expected : previousLogP, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accepted ? expected : previousLogP, posterior.getCurrentLogP(), TOLERANCE);
            for (int i = 0; i < 2; i++)
                assertEquals(
                        accepted ? proposedComponents[i] : previousComponents[i],
                        prior.componentsInput.get().get(i).getCurrentLogP(),
                        TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shortChainsMoveSampledOutputsOrObservedModelWeights(boolean observed, @TempDir Path directory)
            throws Exception {
        Path prefix = directory.resolve(observed ? "mixture-observed" : "mixture-sampled");
        String model = (observed
                        ? "Simplex weights ~ Dirichlet(concentration=[2.0,2.0])\n"
                        : "Simplex weights = [0.25,0.75]\n")
                + "Real y ~ Mixture(components=[Normal(mean=-1.0,sd=1.0),Normal(mean=1.0,sd=1.0)],weights=weights)"
                + (observed ? " observed as 0.5\n" : "\n");
        String source = model + """
                mcmc {
                    Integer chainLength = 100
                    Integer randomSeed = 94731
                    Integer defaultLogEvery = 5
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("mixture-short-chain");
        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int valueColumn = observed ? firstWeightColumn(headers) : headers.indexOf("y");
        int posteriorColumn = headers.indexOf("posterior");
        assertTrue(valueColumn >= 0 && posteriorColumn >= 0, headers.toString());
        assertEquals(22, lines.size(), "Header plus samples 0,5,...,100.");
        Set<Double> values = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.trim().split("\\s+");
            double value = Double.parseDouble(fields[valueColumn]);
            assertTrue(Double.isFinite(value));
            if (observed) assertTrue(value > 0.0 && value < 1.0);
            assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
            values.add(value);
        }
        assertTrue(values.size() > 1, "The sampled value or mixture weights must move.");
        assertEquals("100", lines.getLast().trim().split("\\s+")[0]);
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static int firstWeightColumn(List<String> headers) {
        for (int i = 0; i < headers.size(); i++) if (headers.get(i).startsWith("weights")) return i;
        return -1;
    }

    private static void assertChildDensities(NormalMixtureDistribution mixture, double location, double scale) {
        double observation = mixture.paramInput.get().get();
        assertEquals(
                normalLogDensity(observation, location, scale),
                mixture.componentsInput.get().get(0).getCurrentLogP(),
                TOLERANCE);
        assertEquals(
                normalLogDensity(observation, 2.0, 1.0),
                mixture.componentsInput.get().get(1).getCurrentLogP(),
                TOLERANCE);
    }

    private static double[] expectedFactors(double weight, double location, double scale) {
        return new double[] {
            Math.log(6.0) + Math.log(weight) + Math.log1p(-weight),
            -Math.log(4.0),
            normalLogDensity(Math.log(scale), 0.0, 0.5) - Math.log(scale),
            mixtureLogDensity(-0.5, location, scale, 2.0, 1.0, weight),
            mixtureLogDensity(1.5, location, scale, 2.0, 1.0, weight)
        };
    }

    private static double sum(double[] values) {
        return Arrays.stream(values).sum();
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double residual = (value - mean) / sd;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(sd) - residual * residual / 2.0;
    }

    private static double mixtureLogDensity(
            double value, double firstMean, double firstSd, double secondMean, double secondSd, double firstWeight) {
        double first = Math.log(firstWeight) + normalLogDensity(value, firstMean, firstSd);
        double second = Math.log1p(-firstWeight) + normalLogDensity(value, secondMean, secondSd);
        double maximum = Math.max(first, second);
        return maximum + Math.log(Math.exp(first - maximum) + Math.exp(second - maximum));
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static RealScalarParam<?> real(BEASTState graph, String id) {
        return assertInstanceOf(RealScalarParam.class, node(graph, id));
    }

    private static NormalMixtureDistribution mixture(BEASTState graph, String id) {
        return graph.likelihoodDistributions.stream()
                .filter(value -> id.equals(value.getID()))
                .map(value -> assertInstanceOf(NormalMixtureDistribution.class, value))
                .findFirst()
                .orElseThrow();
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("mixture-test"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
