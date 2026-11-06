package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Cauchy;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealScalar;
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

class RealIIDTileTest {
    private static final double TOLERANCE = 1e-10;
    private static final double[] FIRST = {-1.2, 0.0, 2.3};
    private static final double[] SECOND = {0.7, -0.8};

    @ParameterizedTest
    @CsvSource({"Normal,1", "Normal,3", "Cauchy,1", "Cauchy,3"})
    void nativeIidRepeatsTheBaseInitializerWithoutDrawingAnotherScalar(String family, int size) throws Exception {
        BEASTState graph = build("PositiveInteger n = " + size + "\nVector<Real> values ~ IID(base="
                + law(family, "-0.4", "1.3") + ", num=n)");
        RealVectorParam<?> values = vector(graph);
        IID<?, ?, ?> prior = prior(graph, values);
        assertEquals(IID.class, prior.getClass());
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertSame(values, prior.paramInput.get());
        assertSame(Real.INSTANCE, values.getDomain());
        double[] initial = new double[size];
        Arrays.fill(initial, family.equals("Normal") ? 0.1 : 0.0);
        assertArrayEquals(initial, values.getValues(), 0.0);
        assertBase(family, prior, -0.4, 1.3);
        assertDensity(family, prior, values.getValues(), -0.4, 1.3);
        RealRandomWalkOperator walk = assertInstanceOf(RealRandomWalkOperator.class, operator(graph, values));
        assertSame(values, walk.parameterInput.get());
        assertNull(walk.scalarInput.get());
        assertEquals(5.0, walk.m_pWeight.get(), 0.0);
        assertEquals(1.0, walk.getCoercableParameterValue(), 0.0);
        assertEquals(1, graph.operators.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Normal", "Cauchy"})
    void observationsRetainNegativeZeroAndPositiveValuesWithoutSamplingState(String family) throws Exception {
        BEASTState graph = build(
                "Vector<Real> data ~ IID(base=" + law(family, "-0.4", "1.3") + ", num=3) observed as [-1.2,0.0,2.3]");
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        IID<?, ?, ?> likelihood = observation(graph, "data_likelihood");
        RealVectorParam<?> observed = assertInstanceOf(RealVectorParam.class, likelihood.paramInput.get());
        assertArrayEquals(FIRST, observed.getValues(), 0.0);
        assertSame(Real.INSTANCE, observed.getDomain());
        assertBase(family, likelihood, -0.4, 1.3);
        assertDensity(family, likelihood, FIRST, -0.4, 1.3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Normal", "Cauchy"})
    void actualVectorProposalsMoveEveryCoordinateAcrossZero(String family) throws Exception {
        BEASTState graph = build("Vector<Real> values ~ IID(base=" + law(family, "-0.4", "1.3") + ", num=3)");
        RealVectorParam<?> values = vector(graph);
        Operator operator = operator(graph, values);
        double initial = family.equals("Normal") ? 0.1 : 0.0;
        double lower = values.getLower();
        double upper = values.getUpper();
        boolean[] negative = new boolean[values.size()];
        boolean[] positive = new boolean[values.size()];
        Randomizer.setSeed(58101);
        for (int attempt = 0; attempt < 200; attempt++) {
            for (int i = 0; i < values.size(); i++) values.set(i, initial);
            assertEquals(0.0, operator.proposal(), 0.0);
            int changed = 0;
            for (int i = 0; i < values.size(); i++) {
                assertTrue(Double.isFinite(values.get(i)));
                if (values.get(i) != initial) {
                    changed++;
                    negative[i] |= values.get(i) < 0.0;
                    positive[i] |= values.get(i) > 0.0;
                }
            }
            assertEquals(1, changed, "The native random walk selects one coordinate per proposal.");
        }
        for (int i = 0; i < values.size(); i++) {
            assertTrue(negative[i] && positive[i], "Coordinate " + i + " must move to both signs.");
        }
        assertSame(Real.INSTANCE, values.getDomain());
        assertEquals(lower, values.getLower());
        assertEquals(upper, values.getUpper());
    }

    @ParameterizedTest
    @CsvSource({"Normal,location", "Normal,scale", "Cauchy,location", "Cauchy,scale"})
    void eitherBaseHyperparameterCanBeLiveWhileTheOtherRemainsFixed(String family, String liveInput) throws Exception {
        boolean changeLocation = liveInput.equals("location");
        String source = changeLocation
                ? "Real location ~ Normal(mean=-0.2, sd=1.1)\n"
                : "PositiveReal scale ~ Gamma(shape=2.0, rate=1.0)\n";
        String location = changeLocation ? "location" : "-0.4";
        String scale = changeLocation ? "1.3" : "scale";
        BEASTState graph =
                build(source + "Vector<Real> values ~ IID(base=" + law(family, location, scale) + ", num=3)");
        RealScalarParam<?> hyperparameter = scalar(graph, liveInput);
        RealVectorParam<?> values = vector(graph);
        IID<?, ?, ?> prior = prior(graph, values);
        ScalarDistribution<?, ?> base = prior.distInput.get();
        assertSame(hyperparameter, changeLocation ? locationInput(base) : scaleInput(base));
        assertTrue(hyperparameter.getOutputs().contains(base));
        assertTrue(base.getOutputs().contains(prior));
        assertNull(base.paramInput.get());
        assertEquals(2, graph.stateNodes.size());
        assertEquals(2, graph.priorDistributions.size());
        assertEquals(2, graph.operators.size());
        double[] initial = values.getValues();
        double previous = prior.calculateLogP();
        hyperparameter.set(changeLocation ? -0.9 : 1.7);
        assertArrayEquals(initial, values.getValues(), 0.0);
        assertDensity(family, prior, initial, changeLocation ? -0.9 : -0.4, changeLocation ? 1.3 : 1.7);
        assertNotEquals(previous, prior.getCurrentLogP(), TOLERANCE);
    }

    private static Stream<Arguments> proposalCases() {
        return Stream.of("Normal", "Cauchy").flatMap(family -> Stream.of("values", "location", "scale")
                .flatMap(parameter ->
                        Stream.of(Arguments.of(family, parameter, false), Arguments.of(family, parameter, true))));
    }

    @ParameterizedTest
    @MethodSource("proposalCases")
    void actualProposalsPreserveSharedInputsAndRestoreEveryFactorCache(
            String family, String changedParameter, boolean acceptFirst) throws Exception {
        BEASTState graph = build(sharedModel(family));
        RealVectorParam<?> values = vector(graph);
        RealScalarParam<?> location = scalar(graph, "location");
        RealScalarParam<?> scale = scalar(graph, "scale");
        IID<?, ?, ?> vectorPrior = prior(graph, values);
        IID<?, ?, ?> first = observation(graph, "first_likelihood");
        IID<?, ?, ?> second = observation(graph, "second_likelihood");
        assertArrayEquals(
                FIRST,
                assertInstanceOf(RealVectorParam.class, first.paramInput.get()).getValues(),
                0.0);
        assertArrayEquals(
                SECOND,
                assertInstanceOf(RealVectorParam.class, second.paramInput.get()).getValues(),
                0.0);
        for (IID<?, ?, ?> iid : List.of(vectorPrior, first, second)) {
            ScalarDistribution<?, ?> base = iid.distInput.get();
            assertSame(location, locationInput(base));
            assertSame(scale, scaleInput(base));
            assertTrue(location.getOutputs().contains(base));
            assertTrue(scale.getOutputs().contains(base));
            assertTrue(base.getOutputs().contains(iid));
            assertNull(base.paramInput.get(), "Each base remains an unbound law, with no additional scalar draw.");
        }
        assertSame(Real.INSTANCE, location.getDomain());
        assertSame(PositiveReal.INSTANCE, scale.getDomain());
        assertEquals(3, graph.stateNodes.size());
        assertEquals(3, graph.priorDistributions.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(3, graph.operators.size());
        List<Distribution> factors = List.of(
                graph.priorDistributions.get(location),
                graph.priorDistributions.get(scale),
                vectorPrior,
                first,
                second);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        State state = state(graph, posterior);
        assertEquals(
                Arrays.stream(jointFactors(family, location.get(), scale.get(), values.getValues()))
                        .sum(),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);
        Operator operator = operator(graph, node(graph, changedParameter));
        Randomizer.setSeed(58102);
        for (int step = 0; step < 2; step++) {
            boolean accept = step == 0 ? acceptFirst : !acceptFirst;
            double previousLocation = location.get();
            double previousScale = scale.get();
            double[] previousValues = values.getValues();
            double[] previousFactors =
                    factors.stream().mapToDouble(Distribution::getCurrentLogP).toArray();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            assertTrue(Double.isFinite(operator.proposal()));
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double proposedLocation = location.get();
            double proposedScale = scale.get();
            double[] proposedValues = values.getValues();
            if (changedParameter.equals("values")) assertFalse(Arrays.equals(previousValues, proposedValues));
            else assertArrayEquals(previousValues, proposedValues, 0.0);
            if (changedParameter.equals("location")) assertNotEquals(previousLocation, proposedLocation);
            else assertEquals(previousLocation, proposedLocation, 0.0);
            if (changedParameter.equals("scale")) assertNotEquals(previousScale, proposedScale);
            else assertEquals(previousScale, proposedScale, 0.0);
            double[] expectedFactors = jointFactors(family, proposedLocation, proposedScale, proposedValues);
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(Arrays.stream(expectedFactors).sum(), proposedPosterior, TOLERANCE);
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(expectedFactors[i], factors.get(i).getCurrentLogP(), TOLERANCE);
            }
            if (accept) state.acceptCalculationNodes();
            else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            assertEquals(accept ? proposedLocation : previousLocation, location.get(), 0.0);
            assertEquals(accept ? proposedScale : previousScale, scale.get(), 0.0);
            assertArrayEquals(accept ? proposedValues : previousValues, values.getValues(), 0.0);
            // Inspect cached densities before recalculation could mask failed restoration.
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(
                        accept ? expectedFactors[i] : previousFactors[i],
                        factors.get(i).getCurrentLogP(),
                        TOLERANCE);
            }
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    Arrays.stream(jointFactors(family, location.get(), scale.get(), values.getValues()))
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
    void bindingRejectsWrongObservationLengthBeforeRegistration(int size) {
        BEASTState graph = new BEASTState("real-iid-length");
        Normal law = new Normal();
        graph.setInput(law, law.meanInput, new RealScalarParam<>(-0.4, Real.INSTANCE));
        graph.setInput(law, law.sdInput, new RealScalarParam<>(1.3, PositiveReal.INSTANCE));
        BoundDistribution<RealScalarParam<Real>, Normal> base = new BoundDistribution<>(
                law, new RealScalarParam<>(0.1, Real.INSTANCE), ignored -> {}, (parameter, state) -> List.of());
        RealIIDTile tile = new RealIIDTile();
        tile.baseInput.setTile(valueTile(base));
        tile.numInput.setTile(valueTile(new IntScalarParam<>(3, PositiveInt.INSTANCE)));
        var distribution = tile.applyTile(graph, new IdentityHashMap<>());
        assertSame(law, distribution.getDistribution().distInput.get());
        RealVectorParam<Real> observation = new RealVectorParam<>(new double[size], Real.INSTANCE);
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> distribution.observeAs(graph, observation, "observed"));
        assertTrue(error.getMessage().contains("num"), error.getMessage());
        assertNull(distribution.getDistribution().paramInput.get());
        assertNull(law.paramInput.get());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertThrows(RuntimeException.class, base::consumeAsBase, "The base must have been consumed exactly once.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Normal", "Cauchy"})
    void coreStillRejectsAnEmptyIidVector(String family) {
        assertThrows(
                TypeError.class,
                () -> build("Vector<Real> values ~ IID(base=" + law(family, "0.0", "1.0") + ", num=0)"));
    }

    @Test
    void sampledLengthIsRejectedAtTheTileBoundary() {
        var statements = new Parser(
                        new Lexer("Integer n ~ DiscreteUniform(lower=1,upper=4)\nInteger size = n").scanTokens())
                .parse();
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var expression = ((Stmt.Assignment) statements.getLast()).expression;
        var error = assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> new RealIIDTile()
                .numInput.getCompatibleInputTiles(expression, Map.of(), stochasticity));
        assertTrue(error.getReason().contains("num"));
        assertTrue(error.getReason().contains("random variable"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Uniform(lower=-1.0,upper=1.0)",
                "Offset(base=Normal(mean=0.0,sd=1.0),offset=2.0)",
                "Truncated(base=Normal(mean=0.0,sd=1.0),lower=-1.0,upper=1.0)"
            })
    void unsupportedRealBasesRemainUnsupported(String base) {
        for (boolean observed : new boolean[] {false, true}) {
            assertThrows(
                    TileApplicationError.class,
                    () -> build("Vector<Real> values ~ IID(base=" + base + ", num=3)"
                            + (observed ? " observed as [-0.5,0.0,0.5]" : "")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Normal", "Cauchy"})
    void shortSeededChainsUpdateEveryCoordinateWithFinitePosterior(String family, @TempDir Path directory)
            throws Exception {
        Path prefix = directory.resolve("real-iid-" + family.toLowerCase());
        String source = "Vector<Real> values ~ IID(base=" + law(family, "0.0", "1.0") + ", num=3)\n"
                + """
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 58103
                    Integer defaultLogEvery = 1
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("real-iid");
        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int posteriorColumn = headers.indexOf("posterior");
        List<Integer> valueColumns = IntStream.range(0, headers.size())
                .filter(index -> headers.get(index).startsWith("values."))
                .boxed()
                .toList();
        assertTrue(posteriorColumn >= 0, headers.toString());
        assertEquals(3, valueColumns.size(), headers.toString());
        assertEquals(202, lines.size(), "Header plus samples 0 through 200.");
        for (int column : valueColumns) {
            Set<Double> visited = new HashSet<>();
            for (String line : lines.subList(1, lines.size())) {
                String[] fields = line.trim().split("\\s+");
                double value = Double.parseDouble(fields[column]);
                assertTrue(Double.isFinite(value));
                assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
                visited.add(value);
            }
            assertTrue(visited.size() > 1, headers.get(column) + " must be sampled.");
        }
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static String law(String family, String location, String scale) {
        return family.equals("Normal")
                ? "Normal(mean=" + location + ", sd=" + scale + ")"
                : "Cauchy(location=" + location + ", scale=" + scale + ")";
    }

    private static String sharedModel(String family) {
        String base = law(family, "location", "scale");
        return "Real location ~ Normal(mean=-0.2, sd=1.1)\n"
                + "PositiveReal scale ~ Gamma(shape=2.0, rate=1.0)\n"
                + "Vector<Real> values ~ IID(base=" + base + ", num=3)\n"
                + "Vector<Real> first ~ IID(base=" + base + ", num=3) observed as [-1.2,0.0,2.3]\n"
                + "Vector<Real> second ~ IID(base=" + base + ", num=2) observed as [0.7,-0.8]\n";
    }

    private static double[] jointFactors(String family, double location, double scale, double[] values) {
        return new double[] {
            scalarLogDensity("Normal", location, -0.2, 1.1),
            Math.log(scale) - scale,
            logDensity(family, values, location, scale),
            logDensity(family, FIRST, location, scale),
            logDensity(family, SECOND, location, scale)
        };
    }

    private static double logDensity(String family, double[] values, double location, double scale) {
        return Arrays.stream(values)
                .map(value -> scalarLogDensity(family, value, location, scale))
                .sum();
    }

    private static double scalarLogDensity(String family, double value, double location, double scale) {
        double z = (value - location) / scale;
        return family.equals("Normal")
                ? -Math.log(scale) - 0.5 * Math.log(2.0 * Math.PI) - 0.5 * z * z
                : -Math.log(Math.PI * scale) - Math.log1p(z * z);
    }

    private static void assertBase(String family, IID<?, ?, ?> iid, double location, double scale) {
        ScalarDistribution<?, ?> base = iid.distInput.get();
        if (family.equals("Normal")) assertInstanceOf(Normal.class, base);
        else assertInstanceOf(Cauchy.class, base);
        assertNull(base.paramInput.get(), "An IID base must not acquire an extra scalar draw or prior.");
        assertEquals(location, locationInput(base).get(), 0.0);
        assertEquals(scale, scaleInput(base).get(), 0.0);
        assertTrue(base.getOutputs().contains(iid));
    }

    private static void assertDensity(String family, IID<?, ?, ?> iid, double[] values, double location, double scale) {
        double expected = logDensity(family, values, location, scale);
        assertEquals(expected, iid.calculateLogP(), TOLERANCE);
        assertEquals(
                expected,
                Arrays.stream(values).map(iid.distInput.get()::logDensity).sum(),
                TOLERANCE);
    }

    private static RealScalar<?> locationInput(ScalarDistribution<?, ?> base) {
        return base instanceof Normal normal
                ? normal.meanInput.get()
                : assertInstanceOf(Cauchy.class, base).locationInput.get();
    }

    private static RealScalar<?> scaleInput(ScalarDistribution<?, ?> base) {
        return base instanceof Normal normal
                ? normal.sdInput.get()
                : assertInstanceOf(Cauchy.class, base).scaleInput.get();
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("real-iid"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
