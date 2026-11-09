package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Beta;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.util.GammaFunction;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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

class ProbabilityIIDTileTest {
    // GammaFunction.lnGamma documents approximately ten decimal places of accuracy.
    private static final double TOLERANCE = 1e-8;
    private static final double[] FIRST = {0.2, 0.6, 0.8};
    private static final double[] SECOND = {0.4, 0.7};

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void nativeIidInitializesIndependentProbabilitiesWithoutAScalarDraw(int size) throws Exception {
        BEASTState graph = build("PositiveInteger n = " + size
                + "\nVector<Probability> values ~ IID(base=Beta(alpha=2.0,beta=3.0),num=n)");
        RealVectorParam<?> values = vector(graph);
        IID<?, ?, ?> prior = prior(graph, values);
        assertEquals(IID.class, prior.getClass());
        assertEquals(RealVectorParam.class, values.getClass());
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertSame(values, prior.paramInput.get());
        assertSame(UnitInterval.INSTANCE, values.getDomain());
        double[] initial = new double[size];
        Arrays.fill(initial, 0.5);
        assertArrayEquals(initial, values.getValues(), 0.0);
        assertNotEquals(1.0, Arrays.stream(values.getValues()).sum(), "IID probabilities need not sum to one.");
        assertEquals(0.0, values.getLower(), 0.0);
        assertEquals(1.0, values.getUpper(), 0.0);
        assertBase(prior, 2.0, 3.0);
        assertDensity(prior, values.getValues(), 2.0, 3.0);
        ScaleOperator scale = assertInstanceOf(ScaleOperator.class, operator(graph, values));
        assertSame(values, scale.parameterInput.get());
        assertEquals(5.0, scale.m_pWeight.get(), 0.0);
        assertFalse(scale.scaleAllInput.get());
        assertFalse(scale.scaleAllIndependentlyInput.get());
        assertEquals(1, graph.operators.size());
    }

    @Test
    void fixedShapesAndObservedProbabilitiesRemainUnchangedWithoutSamplingState() throws Exception {
        BEASTState graph = build("""
                PositiveReal alpha = 2.0
                PositiveReal beta = 3.0
                Vector<Probability> data ~ IID(base=Beta(alpha=alpha,beta=beta),num=3) observed as [0.2,0.6,0.8]
                """);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        IID<?, ?, ?> likelihood = observation(graph, "data_likelihood");
        RealVectorParam<?> observed = assertInstanceOf(RealVectorParam.class, likelihood.paramInput.get());
        assertEquals(RealVectorParam.class, observed.getClass());
        assertArrayEquals(FIRST, observed.getValues(), 0.0);
        assertSame(UnitInterval.INSTANCE, observed.getDomain());
        assertNotEquals(1.0, Arrays.stream(observed.getValues()).sum());
        assertBase(likelihood, 2.0, 3.0);
        assertDensity(likelihood, FIRST, 2.0, 3.0);
    }

    @ParameterizedTest
    @CsvSource({"0.0,1.0,3.0", "0.0,2.0,3.0", "0.0,0.5,2.0", "1.0,3.0,1.0", "1.0,3.0,2.0", "1.0,2.0,0.5"})
    void observationsPreserveFiniteZeroAndInfiniteNativeEndpointDensities(double endpoint, double alpha, double beta)
            throws Exception {
        double[] observed = {endpoint, 0.4};
        BEASTState graph = build("Vector<Probability> data ~ IID(base=Beta(alpha=" + alpha + ",beta=" + beta
                + "),num=2) observed as [" + endpoint + ",0.4]");
        IID<?, ?, ?> likelihood = observation(graph, "data_likelihood");
        assertArrayEquals(
                observed,
                assertInstanceOf(RealVectorParam.class, likelihood.paramInput.get())
                        .getValues(),
                0.0);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        double endpointShape = endpoint == 0.0 ? alpha : beta;
        double expected = endpointShape < 1.0
                ? Double.POSITIVE_INFINITY
                : endpointShape > 1.0 ? Double.NEGATIVE_INFINITY : logDensity(observed, alpha, beta);
        assertEquals(expected, likelihood.calculateLogP(), TOLERANCE);
        assertDensity(likelihood, observed, alpha, beta);
    }

    @Test
    void integerEndpointsRemainValidObservedProbabilities() throws Exception {
        BEASTState graph = build("""
                Vector<Probability> data ~ IID(base=Beta(alpha=1.0,beta=1.0),num=2) observed as [0,1]
                """);
        IID<?, ?, ?> likelihood = observation(graph, "data_likelihood");
        RealVectorParam<?> observed = assertInstanceOf(RealVectorParam.class, likelihood.paramInput.get());
        assertArrayEquals(new double[] {0.0, 1.0}, observed.getValues(), 0.0);
        assertTrue(observed.isValid());
        assertEquals(0.0, likelihood.calculateLogP(), TOLERANCE);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "1.1"})
    void coreRejectsObservationsOutsideTheProbabilityDomain(String value) {
        assertThrows(
                TypeError.class,
                () -> build("Vector<Probability> data ~ IID(base=Beta(alpha=2.0,beta=3.0),num=2) observed as [" + value
                        + ",0.4]"));
    }

    @Test
    void actualScaleProposalsMoveEveryCoordinateWithinTheUnitInterval() throws Exception {
        BEASTState graph = build("Vector<Probability> values ~ IID(base=Beta(alpha=2.0,beta=3.0),num=3)");
        RealVectorParam<?> values = vector(graph);
        Operator operator = operator(graph, values);
        boolean[] moved = new boolean[values.size()];
        Randomizer.setSeed(69201);
        for (int attempt = 0; attempt < 150; attempt++) {
            for (int i = 0; i < values.size(); i++) values.set(i, 0.5);
            double logHastings = operator.proposal();
            if (logHastings == Double.NEGATIVE_INFINITY) {
                assertArrayEquals(new double[] {0.5, 0.5, 0.5}, values.getValues(), 0.0);
            } else {
                assertTrue(Double.isFinite(logHastings));
                int changed = 0;
                for (int i = 0; i < values.size(); i++) {
                    if (values.get(i) != 0.5) {
                        moved[i] = true;
                        changed++;
                    }
                }
                assertEquals(1, changed);
            }
            for (double value : values.getValues()) assertTrue(value >= 0.0 && value <= 1.0);
        }
        for (boolean changed : moved) assertTrue(changed, "Every probability coordinate must be sampled.");
        assertSame(UnitInterval.INSTANCE, values.getDomain());
        assertEquals(0.0, values.getLower(), 0.0);
        assertEquals(1.0, values.getUpper(), 0.0);
    }

    @Test
    void proposalsAboveOneAreRejectedWithoutChangingTheVector() throws Exception {
        BEASTState graph = build("Vector<Probability> values ~ IID(base=Beta(alpha=1.0,beta=1.0),num=3)");
        RealVectorParam<?> values = vector(graph);
        Operator operator = operator(graph, values);
        boolean rejected = false;
        boolean moved = false;
        Randomizer.setSeed(69202);
        for (int attempt = 0; attempt < 100; attempt++) {
            for (int i = 0; i < values.size(); i++) values.set(i, 1.0);
            double logHastings = operator.proposal();
            if (logHastings == Double.NEGATIVE_INFINITY) {
                rejected = true;
                assertArrayEquals(new double[] {1.0, 1.0, 1.0}, values.getValues(), 0.0);
            } else {
                assertTrue(Double.isFinite(logHastings));
                moved |= Arrays.stream(values.getValues()).anyMatch(value -> value < 1.0);
            }
            for (double value : values.getValues()) assertTrue(value >= 0.0 && value <= 1.0);
        }
        assertTrue(rejected && moved, "Exercise both outward rejection and an inward move at the upper boundary.");
        assertSame(UnitInterval.INSTANCE, values.getDomain());
        assertEquals(0.0, values.getLower(), 0.0);
        assertEquals(1.0, values.getUpper(), 0.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alpha", "beta"})
    void eitherShapeCanRemainLiveWhileTheOtherIsFixed(String liveInput) throws Exception {
        boolean changeAlpha = liveInput.equals("alpha");
        BEASTState graph = build("PositiveReal " + liveInput + " ~ Gamma(shape=2.0,rate=1.0)\n"
                + "Vector<Probability> values ~ IID(base=Beta(alpha=" + (changeAlpha ? "alpha" : "2.0")
                + ",beta=" + (changeAlpha ? "3.0" : "beta") + "),num=3)");
        RealScalarParam<?> shape = scalar(graph, liveInput);
        RealVectorParam<?> values = vector(graph);
        IID<?, ?, ?> prior = prior(graph, values);
        Beta base = assertInstanceOf(Beta.class, prior.distInput.get());
        assertSame(shape, changeAlpha ? base.alphaInput.get() : base.betaInput.get());
        assertSame(PositiveReal.INSTANCE, shape.getDomain());
        assertTrue(shape.getOutputs().contains(base));
        assertTrue(base.getOutputs().contains(prior));
        assertNull(base.paramInput.get());
        assertEquals(2, graph.stateNodes.size());
        assertEquals(2, graph.priorDistributions.size());
        assertEquals(2, graph.operators.size());
        double[] initial = values.getValues();
        double previous = prior.calculateLogP();
        shape.set(1.7);
        assertArrayEquals(initial, values.getValues(), 0.0);
        assertDensity(prior, initial, changeAlpha ? 1.7 : 2.0, changeAlpha ? 3.0 : 1.7);
        assertNotEquals(previous, prior.getCurrentLogP(), TOLERANCE);
    }

    private static Stream<Arguments> proposalCases() {
        return Stream.of("values", "alpha", "beta")
                .flatMap(parameter -> Stream.of(Arguments.of(parameter, false), Arguments.of(parameter, true)));
    }

    @ParameterizedTest
    @MethodSource("proposalCases")
    void actualProposalsPreserveSharedShapesAndRestoreEveryFactorCache(String changedParameter, boolean acceptFirst)
            throws Exception {
        BEASTState graph = build(sharedModel());
        RealVectorParam<?> values = vector(graph);
        RealScalarParam<?> alpha = scalar(graph, "alpha");
        RealScalarParam<?> beta = scalar(graph, "beta");
        IID<?, ?, ?> vectorPrior = prior(graph, values);
        IID<?, ?, ?> first = observation(graph, "first_likelihood");
        IID<?, ?, ?> second = observation(graph, "second_likelihood");
        for (IID<?, ?, ?> iid : List.of(vectorPrior, first, second)) {
            Beta base = assertInstanceOf(Beta.class, iid.distInput.get());
            assertSame(alpha, base.alphaInput.get());
            assertSame(beta, base.betaInput.get());
            assertTrue(alpha.getOutputs().contains(base));
            assertTrue(beta.getOutputs().contains(base));
            assertTrue(base.getOutputs().contains(iid));
            assertNull(base.paramInput.get(), "The base must not acquire an additional scalar draw or prior.");
        }
        assertEquals(3, graph.stateNodes.size());
        assertEquals(3, graph.priorDistributions.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(3, graph.operators.size());
        assertSame(PositiveReal.INSTANCE, alpha.getDomain());
        assertSame(PositiveReal.INSTANCE, beta.getDomain());
        // Use nonuniform shapes so a vector proposal changes its prior density.
        alpha.set(2.0);
        beta.set(3.0);
        List<Distribution> factors = List.of(
                graph.priorDistributions.get(alpha), graph.priorDistributions.get(beta), vectorPrior, first, second);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        State state = state(graph, posterior);
        assertEquals(
                Arrays.stream(jointFactors(alpha.get(), beta.get(), values.getValues()))
                        .sum(),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);
        Operator operator = operator(graph, node(graph, changedParameter));
        Randomizer.setSeed(69203);
        for (int step = 0; step < 2; step++) {
            boolean accept = step == 0 ? acceptFirst : !acceptFirst;
            double previousAlpha = alpha.get();
            double previousBeta = beta.get();
            double[] previousValues = values.getValues();
            double[] previousFactors =
                    factors.stream().mapToDouble(Distribution::getCurrentLogP).toArray();
            double previousPosterior = posterior.getCurrentLogP();
            boolean moved = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                state.store(step * 100L + attempt);
                double logHastings = operator.proposal();
                state.storeCalculationNodes();
                state.checkCalculationNodesDirtiness();
                if (Double.isFinite(logHastings)) {
                    moved = true;
                    break;
                }
                state.restore();
                state.restoreCalculationNodes();
                state.setEverythingDirty(false);
            }
            assertTrue(moved, "Exercise a finite actual proposal, including when a scale move exceeds one.");
            double proposedAlpha = alpha.get();
            double proposedBeta = beta.get();
            double[] proposedValues = values.getValues();
            if (changedParameter.equals("values")) assertFalse(Arrays.equals(previousValues, proposedValues));
            else assertArrayEquals(previousValues, proposedValues, 0.0);
            if (changedParameter.equals("alpha")) assertNotEquals(previousAlpha, proposedAlpha);
            else assertEquals(previousAlpha, proposedAlpha, 0.0);
            if (changedParameter.equals("beta")) assertNotEquals(previousBeta, proposedBeta);
            else assertEquals(previousBeta, proposedBeta, 0.0);
            double[] expectedFactors = jointFactors(proposedAlpha, proposedBeta, proposedValues);
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(Arrays.stream(expectedFactors).sum(), proposedPosterior, TOLERANCE);
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(expectedFactors[i], factors.get(i).getCurrentLogP(), TOLERANCE);
            }
            finishProposal(state, accept);
            assertEquals(accept ? proposedAlpha : previousAlpha, alpha.get(), 0.0);
            assertEquals(accept ? proposedBeta : previousBeta, beta.get(), 0.0);
            assertArrayEquals(accept ? proposedValues : previousValues, values.getValues(), 0.0);
            // Inspect cached factors before recalculation can mask a restoration failure.
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(
                        accept ? expectedFactors[i] : previousFactors[i],
                        factors.get(i).getCurrentLogP(),
                        TOLERANCE);
            }
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    Arrays.stream(jointFactors(alpha.get(), beta.get(), values.getValues()))
                            .sum(),
                    posterior.calculateLogP(),
                    TOLERANCE);
            assertArrayEquals(
                    FIRST,
                    assertInstanceOf(RealVectorParam.class, first.paramInput.get())
                            .getValues(),
                    0.0);
            assertArrayEquals(
                    SECOND,
                    assertInstanceOf(RealVectorParam.class, second.paramInput.get())
                            .getValues(),
                    0.0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    void bindingRejectsWrongObservationLengthBeforeRegistration(int size) throws Exception {
        BEASTState graph = new BEASTState("probability-iid-length");
        Beta law = new Beta();
        graph.setInput(law, law.alphaInput, new RealScalarParam<>(2.0, PositiveReal.INSTANCE));
        graph.setInput(law, law.betaInput, new RealScalarParam<>(3.0, PositiveReal.INSTANCE));
        BoundDistribution<RealScalarParam<UnitInterval>, Beta> base = new BoundDistribution<>(
                law, new RealScalarParam<>(0.5, UnitInterval.INSTANCE), ignored -> {}, (parameter, state) -> List.of());
        ProbabilityIIDTile tile = new ProbabilityIIDTile();
        tile.baseInput.setTile(valueTile(base));
        tile.numInput.setTile(valueTile(new IntScalarParam<>(3, PositiveInt.INSTANCE)));
        var distribution = tile.applyTile(graph, new IdentityHashMap<>());
        assertSame(law, distribution.getDistribution().distInput.get());
        RealVectorParam<UnitInterval> observation = new RealVectorParam<>(new double[size], UnitInterval.INSTANCE);
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> distribution.observeAs(graph, observation, "observed"));
        assertTrue(error.getMessage().contains("num"), error.getMessage());
        assertNull(distribution.getDistribution().paramInput.get());
        assertNull(law.paramInput.get());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertThrows(RuntimeException.class, base::consumeAsBase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5"})
    void coreStillRejectsInvalidFixedLengths(String size) {
        assertThrows(
                TypeError.class,
                () -> build("Vector<Probability> values ~ IID(base=Beta(alpha=2.0,beta=3.0),num=" + size + ")"));
    }

    @Test
    void sampledLengthIsRejectedAtTheTileBoundary() {
        var statements = new Parser(
                        new Lexer("Integer n ~ DiscreteUniform(lower=1,upper=4)\nInteger size = n").scanTokens())
                .parse();
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var expression = ((Stmt.Assignment) statements.getLast()).expression;
        var error = assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> new ProbabilityIIDTile()
                .numInput.getCompatibleInputTiles(expression, Map.of(), stochasticity));
        assertTrue(error.getReason().contains("num"));
        assertTrue(error.getReason().contains("random variable"));
    }

    @Test
    void shortSeededChainUpdatesEveryProbabilityAndBothShapes(@TempDir Path directory) throws Exception {
        Path prefix = directory.resolve("probability-iid");
        String source = sharedModel() + """
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 69204
                    Integer defaultLogEvery = 1
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("probability-iid");
        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int posteriorColumn = headers.indexOf("posterior");
        int alphaColumn = headers.indexOf("alpha");
        int betaColumn = headers.indexOf("beta");
        List<Integer> valueColumns = IntStream.range(0, headers.size())
                .filter(index -> headers.get(index).startsWith("values."))
                .boxed()
                .toList();
        assertTrue(posteriorColumn >= 0 && alphaColumn >= 0 && betaColumn >= 0, headers.toString());
        assertEquals(3, valueColumns.size(), headers.toString());
        assertEquals(202, lines.size(), "Header plus samples 0 through 200.");
        for (int column : Stream.concat(Stream.of(alphaColumn, betaColumn), valueColumns.stream())
                .toList()) {
            Set<Double> visited = new HashSet<>();
            for (String line : lines.subList(1, lines.size())) {
                String[] fields = line.trim().split("\\s+");
                double value = Double.parseDouble(fields[column]);
                assertTrue(Double.isFinite(value) && value > 0.0);
                if (valueColumns.contains(column)) assertTrue(value <= 1.0);
                assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
                visited.add(value);
            }
            assertTrue(visited.size() > 1, headers.get(column) + " must be sampled.");
        }
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static String sharedModel() {
        return """
                PositiveReal alpha ~ Gamma(shape=2.0,rate=1.0)
                PositiveReal beta ~ Gamma(shape=2.0,rate=1.0)
                Vector<Probability> values ~ IID(base=Beta(alpha=alpha,beta=beta),num=3)
                Vector<Probability> first ~ IID(base=Beta(alpha=alpha,beta=beta),num=3) observed as [0.2,0.6,0.8]
                Vector<Probability> second ~ IID(base=Beta(alpha=alpha,beta=beta),num=2) observed as [0.4,0.7]
                """;
    }

    private static double[] jointFactors(double alpha, double beta, double[] values) {
        return new double[] {
            Math.log(alpha) - alpha,
            Math.log(beta) - beta,
            logDensity(values, alpha, beta),
            logDensity(FIRST, alpha, beta),
            logDensity(SECOND, alpha, beta)
        };
    }

    private static double logDensity(double[] values, double alpha, double beta) {
        double normalizer =
                GammaFunction.lnGamma(alpha + beta) - GammaFunction.lnGamma(alpha) - GammaFunction.lnGamma(beta);
        double result = 0.0;
        for (double value : values) {
            if (value == 0.0) {
                if (alpha < 1.0) return Double.POSITIVE_INFINITY;
                if (alpha > 1.0) return Double.NEGATIVE_INFINITY;
                result += normalizer;
            } else if (value == 1.0) {
                if (beta < 1.0) return Double.POSITIVE_INFINITY;
                if (beta > 1.0) return Double.NEGATIVE_INFINITY;
                result += normalizer;
            } else {
                result += normalizer + (alpha - 1.0) * Math.log(value) + (beta - 1.0) * Math.log1p(-value);
            }
        }
        return result;
    }

    private static void assertBase(IID<?, ?, ?> iid, double alpha, double beta) {
        Beta base = assertInstanceOf(Beta.class, iid.distInput.get());
        assertNull(base.paramInput.get(), "An IID base must not acquire an extra scalar draw or prior.");
        assertEquals(alpha, base.alphaInput.get().get(), 0.0);
        assertEquals(beta, base.betaInput.get().get(), 0.0);
        assertTrue(base.getOutputs().contains(iid));
    }

    private static void assertDensity(IID<?, ?, ?> iid, double[] values, double alpha, double beta) {
        double expected = logDensity(values, alpha, beta);
        assertEquals(expected, iid.calculateLogP(), TOLERANCE);
        assertEquals(
                expected,
                Arrays.stream(values).map(iid.distInput.get()::logDensity).sum(),
                TOLERANCE);
    }

    private static IID<?, ?, ?> observation(BEASTState graph, String id) {
        return assertInstanceOf(
                IID.class,
                graph.likelihoodDistributions.stream()
                        .filter(distribution -> id.equals(distribution.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static IID<?, ?, ?> prior(BEASTState graph, RealVectorParam<?> values) {
        return assertInstanceOf(IID.class, graph.priorDistributions.get(values));
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static RealVectorParam<?> vector(BEASTState graph) {
        return assertInstanceOf(RealVectorParam.class, node(graph, "values"));
    }

    private static RealScalarParam<?> scalar(BEASTState graph, String id) {
        return assertInstanceOf(RealScalarParam.class, node(graph, id));
    }

    private static Operator operator(BEASTState graph, StateNode parameter) {
        List<Operator> operators = graph.operators.stream()
                .filter(operator -> operator.listStateNodes().contains(parameter))
                .toList();
        assertEquals(1, operators.size());
        assertEquals(List.of(parameter), operators.getFirst().listStateNodes());
        return operators.getFirst();
    }

    private static State state(BEASTState graph, Distribution posterior) {
        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        return state;
    }

    private static void finishProposal(State state, boolean accept) {
        if (accept) state.acceptCalculationNodes();
        else {
            state.restore();
            state.restoreCalculationNodes();
        }
        state.setEverythingDirty(false);
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("probability-iid"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
