package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.MarkovChainDistribution;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
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
import tiling.LiveExponentialMarkovChainDistribution;

class ExponentialMarkovChainTileTest {
    private static final double TOLERANCE = 1e-10;
    private static final double[] FIRST = {0.7, 1.4, 0.9};
    private static final double[] SECOND = {1.1, 0.4};

    @ParameterizedTest
    @ValueSource(strings = {"1", "0.7", "PositiveReal"})
    void fixedMeansInitializeTheNativeChainWithAPositiveDomain(String meanSource) throws Exception {
        boolean literal = Character.isDigit(meanSource.charAt(0));
        double initial = meanSource.equals("1") ? 1.0 : 0.7;
        String source = literal ? "" : meanSource + " fixed = 0.7\n";
        BEASTState graph = build(source + "NonNegativeInteger n = 3\n"
                + "Vector<PositiveReal> chain ~ ExponentialMarkovChain(initialMean="
                + (literal ? meanSource : "fixed") + ", numValues=n)");
        RealVectorParam<?> chain = chain(graph);
        MarkovChainDistribution prior = chainPrior(graph, chain);
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertSame(chain, prior.paramInput.get());
        assertSame(PositiveReal.INSTANCE, chain.getDomain());
        assertArrayEquals(new double[] {initial, initial, initial}, chain.getValues(), 0.0);
        assertEquals(initial, prior.initialMeanInput.get().get(), 0.0);
        assertEquals(1.0, prior.shapeInput.get(), 0.0);
        assertFalse(prior.isJeffreysInput.get());
        assertFalse(prior.isReverseInput.get());
        assertFalse(prior.useLogInput.get());
        assertFalse(prior.useLogNormalInput.get());
        assertEquals(logDensity(initial, chain.getValues()), prior.calculateLogP(), TOLERANCE);
        ScaleOperator scale = assertInstanceOf(ScaleOperator.class, operator(graph, chain));
        assertSame(chain, scale.parameterInput.get());
        assertEquals(5.0, scale.m_pWeight.get(), 0.0);
        assertEquals(1, graph.operators.size());
    }

    private static Stream<Arguments> liveMeans() {
        return Stream.of(Arguments.of("PositiveReal", "Gamma(shape=2.0, rate=1.0)", PositiveReal.INSTANCE, 1.0));
    }

    @ParameterizedTest
    @MethodSource("liveMeans")
    void liveMeansKeepTheirOriginalInputIdentityDomainAndInitialValue(
            String declaredType, String law, Real domain, double initial) throws Exception {
        BEASTState graph = build(declaredType + " mean ~ " + law + "\n"
                + "Vector<PositiveReal> chain ~ ExponentialMarkovChain(initialMean=mean, numValues=3)");
        RealScalarParam<?> mean = mean(graph);
        RealVectorParam<?> chain = chain(graph);
        MarkovChainDistribution prior = chainPrior(graph, chain);
        assertSame(mean, prior.initialMeanInput.get());
        assertSame(domain, mean.getDomain());
        assertEquals(initial, mean.get(), 0.0);
        assertArrayEquals(new double[] {initial, initial, initial}, chain.getValues(), 0.0);
        assertTrue(mean.getOutputs().contains(prior));
        double originalLogP = prior.calculateLogP();
        mean.set(initial * 1.7);
        assertArrayEquals(new double[] {initial, initial, initial}, chain.getValues(), 0.0);
        assertEquals(logDensity(mean.get(), chain.getValues()), prior.calculateLogP(), TOLERANCE);
        assertNotEquals(originalLogP, prior.getCurrentLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Real mean ~ Normal(mean=1.0, sd=0.5)",
                "NonNegativeReal mean ~ Exponential(rate=2.0)",
                "Probability mean ~ Beta(alpha=2.0, beta=2.0)"
            })
    void initialMeansThatMayBeNonPositiveAreTypeErrors(String meanStatement) {
        TypeError error = assertThrows(
                TypeError.class,
                () -> build(meanStatement + "\n"
                        + "Vector<PositiveReal> chain ~ ExponentialMarkovChain(initialMean=mean, numValues=3)"));
        assertTrue(error.getMessage().contains("initialMean"), error.getMessage());
    }

    @Test
    void wideningTheGeneratedChainToRealVectorIsAllowed() throws Exception {
        BEASTState graph = build("Vector<Real> chain ~ ExponentialMarkovChain(initialMean=1.5, numValues=3)");
        RealVectorParam<?> chain = chain(graph);
        assertSame(PositiveReal.INSTANCE, chain.getDomain());
        assertEquals(
                logDensity(1.5, chain.getValues()), chainPrior(graph, chain).calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "num(vector=[1,2,3])"})
    void fixedLengthsIncludeAnEmptyChainWithoutAnOperator(String size) throws Exception {
        BEASTState graph =
                build("Vector<PositiveReal> chain ~ ExponentialMarkovChain(initialMean=1.5, numValues=" + size + ")");
        RealVectorParam<?> chain = chain(graph);
        int expected = size.equals("0") ? 0 : size.equals("1") ? 1 : 3;
        assertEquals(expected, chain.size());
        assertSame(PositiveReal.INSTANCE, chain.getDomain());
        MarkovChainDistribution prior = chainPrior(graph, chain);
        assertEquals(logDensity(1.5, chain.getValues()), prior.calculateLogP(), TOLERANCE);
        assertEquals(expected == 0 ? 0 : 1, graph.operators.size());
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
    }

    @Test
    void emptyGeneratedChainSurvivesNativeStateXmlRoundTrip() throws Exception {
        BEASTState graph = build("Vector<PositiveReal> chain ~ ExponentialMarkovChain(initialMean=1.5, numValues=0)");
        RealVectorParam<?> chain = chain(graph);
        MarkovChainDistribution prior = chainPrior(graph, chain);
        CompoundDistribution posterior = posterior(List.of(prior));
        State state = state(graph, posterior);
        assertEquals(0.0, state.robustlyCalcPosterior(posterior), 0.0);
        String xml = state.toXML(17);
        assertDoesNotThrow(() -> state.fromXML(xml));
        assertEquals(0, chain.size());
        assertSame(PositiveReal.INSTANCE, chain.getDomain());
        assertSame(chain, prior.paramInput.get());
        assertTrue(graph.operators.isEmpty());
        assertEquals(0.0, state.robustlyCalcPosterior(posterior), 0.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[0.7,1.4,0.9]", "[1.1,0.4]"})
    void positiveObservationsKeepTheirValues(String values) throws Exception {
        double[] observed = Arrays.stream(
                        values.substring(1, values.length() - 1).split(","))
                .mapToDouble(Double::parseDouble)
                .toArray();
        BEASTState graph = build("Vector<Real> data ~ ExponentialMarkovChain(initialMean=1.5, numValues="
                + observed.length + ") observed as " + values);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        MarkovChainDistribution likelihood = chainDistribution(graph.likelihoodDistributions.getFirst());
        RealVectorParam<?> bound = assertInstanceOf(RealVectorParam.class, likelihood.paramInput.get());
        assertArrayEquals(observed, bound.getValues(), 0.0);
        assertEquals(logDensity(1.5, observed), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1.2,0.0]", "[0.0,1.2]", "[1.2,-0.3]"})
    void nonPositiveObservationsAreRejected(String values) {
        int length = values.split(",").length;
        // The chain values are PositiveReal, as in BEAST 3's MarkovChainDistribution.
        assertThrows(
                TileApplicationError.class,
                () -> build("Vector<Real> data ~ ExponentialMarkovChain(initialMean=1.5, numValues=" + length
                        + ") observed as " + values));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0", "-0.5"})
    void invalidInitialMeansGiveATargetedDiagnostic(String initialMean) {
        // The type checker rejects non-positive literals before tiling.
        for (boolean observed : new boolean[] {false, true}) {
            TypeError error = assertThrows(
                    TypeError.class,
                    () -> build("Vector<Real> chain ~ ExponentialMarkovChain(initialMean=" + initialMean
                            + ", numValues=2)" + (observed ? " observed as [0.7,1.4]" : "")));
            assertTrue(error.getMessage().contains("initialMean"), error.getMessage());
        }

        // A computed mean can still carry a wider native domain; the tile checks its value.
        ExponentialMarkovChainTile tile = new ExponentialMarkovChainTile();
        tile.initialMeanInput.setTile(valueTile(new RealScalarParam<>(Double.parseDouble(initialMean), Real.INSTANCE)));
        tile.numValuesInput.setTile(valueTile(new IntScalarParam<>(2, NonNegativeInt.INSTANCE)));
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> tile.applyTile(new BEASTState("invalid-mean"), new IdentityHashMap<>()));
        assertTrue(error.getMessage().toLowerCase().contains("initial mean"), error.getMessage());
        assertTrue(error.getMessage().contains("initialMean"), error.getMessage());
    }

    @Test
    void bindingRejectsWrongObservationLengthWithoutRegisteringIt() {
        BEASTState graph = new BEASTState("chain-length");
        RealScalarParam<Real> mean = new RealScalarParam<>(1.5, Real.INSTANCE);
        ExponentialMarkovChainTile tile = new ExponentialMarkovChainTile();
        tile.initialMeanInput.setTile(valueTile(mean));
        tile.numValuesInput.setTile(valueTile(new IntScalarParam<>(3, NonNegativeInt.INSTANCE)));
        var distribution = tile.applyTile(graph, new IdentityHashMap<>());
        assertSame(mean, distribution.getDistribution().initialMeanInput.get());
        RealVectorParam<Real> observation = new RealVectorParam<>(new double[] {0.7, 1.4}, Real.INSTANCE);
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> distribution.observeAs(graph, observation, "observed"));
        assertTrue(error.getMessage().contains("numValues"), error.getMessage());
        assertNull(distribution.getDistribution().paramInput.get());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    @Test
    void sampledLengthIsRejectedAtTheTileBoundary() {
        var statements = new Parser(
                        new Lexer("NonNegativeInteger n ~ Poisson(rate=3.0)\n" + "NonNegativeInteger size = n")
                                .scanTokens())
                .parse();
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var expression = ((Stmt.Assignment) statements.getLast()).expression;
        var error = assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> new ExponentialMarkovChainTile()
                .numValuesInput.getCompatibleInputTiles(expression, Map.of(), stochasticity));
        assertTrue(error.getReason().contains("numValues"));
        assertTrue(error.getReason().contains("random variable"));
    }

    @Test
    void actualScaleProposalsCanMoveEveryElementWithoutChangingBounds() throws Exception {
        BEASTState graph = build("Vector<PositiveReal> chain ~ ExponentialMarkovChain(initialMean=1.5, numValues=3)");
        RealVectorParam<?> chain = chain(graph);
        Operator operator = operator(graph, chain);
        double lower = chain.getLower();
        double upper = chain.getUpper();
        boolean[] moved = new boolean[chain.size()];
        Randomizer.setSeed(47001);
        for (int attempt = 0; attempt < 100; attempt++) {
            double[] previous = chain.getValues();
            assertTrue(Double.isFinite(operator.proposal()));
            for (int i = 0; i < chain.size(); i++) {
                moved[i] |= chain.get(i) != previous[i];
                assertTrue(Double.isFinite(chain.get(i)) && chain.get(i) > 0.0);
            }
        }
        for (boolean changed : moved) assertTrue(changed, "Every chain element must be sampled.");
        assertSame(PositiveReal.INSTANCE, chain.getDomain());
        assertEquals(lower, chain.getLower());
        assertEquals(upper, chain.getUpper());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void actualProposalsUpdateTheJointDensityAndRestoreAllDependentCaches(boolean updateMean, boolean acceptFirst)
            throws Exception {
        BEASTState graph = build("PositiveReal mean ~ Gamma(shape=2.0, rate=1.0)\n" + sharedChains());
        RealScalarParam<?> mean = mean(graph);
        RealVectorParam<?> chain = chain(graph);
        MarkovChainDistribution chainPrior = chainPrior(graph, chain);
        MarkovChainDistribution first = observation(graph, "first_likelihood");
        MarkovChainDistribution second = observation(graph, "second_likelihood");
        assertArrayEquals(
                FIRST,
                first.paramInput.get().getElements().stream()
                        .mapToDouble(Double::doubleValue)
                        .toArray());
        assertArrayEquals(
                SECOND,
                second.paramInput.get().getElements().stream()
                        .mapToDouble(Double::doubleValue)
                        .toArray());
        for (MarkovChainDistribution factor : List.of(chainPrior, first, second)) {
            assertSame(mean, factor.initialMeanInput.get());
            assertTrue(mean.getOutputs().contains(factor));
        }
        assertEquals(2, graph.stateNodes.size());
        assertEquals(2, graph.priorDistributions.size());
        assertEquals(2, graph.operators.size());
        List<Distribution> factors = List.of(graph.priorDistributions.get(mean), chainPrior, first, second);
        CompoundDistribution posterior = posterior(factors);
        State state = state(graph, posterior);
        assertEquals(
                Arrays.stream(jointFactors(mean.get(), chain.getValues())).sum(),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);
        Operator operator = operator(graph, updateMean ? mean : chain);
        Randomizer.setSeed(47002);
        for (int step = 0; step < 2; step++) {
            boolean accept = step == 0 ? acceptFirst : !acceptFirst;
            double previousMean = mean.get();
            double[] previousChain = chain.getValues();
            double[] previousFactors =
                    factors.stream().mapToDouble(Distribution::getCurrentLogP).toArray();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            assertTrue(Double.isFinite(operator.proposal()));
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double proposedMean = mean.get();
            double[] proposedChain = chain.getValues();
            if (updateMean) {
                assertNotEquals(previousMean, proposedMean);
                assertArrayEquals(previousChain, proposedChain, 0.0);
            } else {
                assertEquals(previousMean, proposedMean, 0.0);
                assertFalse(Arrays.equals(previousChain, proposedChain));
            }
            double[] expectedFactors = jointFactors(proposedMean, proposedChain);
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(Arrays.stream(expectedFactors).sum(), proposedPosterior, TOLERANCE);
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(expectedFactors[i], factors.get(i).getCurrentLogP(), TOLERANCE);
            }
            finishProposal(state, accept);
            assertEquals(accept ? proposedMean : previousMean, mean.get(), 0.0);
            assertArrayEquals(accept ? proposedChain : previousChain, chain.getValues(), 0.0);
            // Inspect all caches before recalculation could hide a failed restoration.
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(
                        accept ? expectedFactors[i] : previousFactors[i],
                        factors.get(i).getCurrentLogP(),
                        TOLERANCE);
            }
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    Arrays.stream(jointFactors(mean.get(), chain.getValues())).sum(),
                    posterior.calculateLogP(),
                    TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.5})
    void nonPositiveLiveMeansAreRejectedByTheDomain(double invalidMean) throws Exception {
        BEASTState graph = build("PositiveReal mean ~ Gamma(shape=2.0, rate=1.0)\n" + sharedChains());
        RealScalarParam<?> mean = mean(graph);
        RealVectorParam<?> chain = chain(graph);
        List<Distribution> factors = List.of(
                graph.priorDistributions.get(mean),
                chainPrior(graph, chain),
                graph.likelihoodDistributions.get(0),
                graph.likelihoodDistributions.get(1));
        CompoundDistribution posterior = posterior(factors);
        State state = state(graph, posterior);
        double previousPosterior = state.robustlyCalcPosterior(posterior);
        double previousMean = mean.get();
        assertTrue(Double.isFinite(previousPosterior));
        // The mean is a PositiveReal, so a non-positive value never reaches the chain density.
        // LiveExponentialMarkovChainDistributionTest covers the density's own support checks.
        assertThrows(IllegalArgumentException.class, () -> mean.set(invalidMean));
        assertEquals(previousMean, mean.get(), 0.0);
        assertEquals(previousPosterior, posterior.calculateLogP(), TOLERANCE);
    }

    @Test
    void shortChainUpdatesTheMeanAndEveryLatentElementWithObservedData(@TempDir Path directory) throws Exception {
        Path prefix = directory.resolve("exponential-chain");
        String source = "PositiveReal mean ~ Gamma(shape=2.0, rate=1.0)\n" + sharedChains()
                + """
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 47003
                    Integer defaultLogEvery = 1
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("exponential-chain");
        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int posteriorColumn = headers.indexOf("posterior");
        int meanColumn = headers.indexOf("mean");
        List<Integer> chainColumns = IntStream.range(0, headers.size())
                .filter(index -> headers.get(index).startsWith("chain."))
                .boxed()
                .toList();
        assertTrue(posteriorColumn >= 0 && meanColumn >= 0, headers.toString());
        assertEquals(3, chainColumns.size(), headers.toString());
        assertEquals(202, lines.size(), "Header plus samples 0 through 200.");
        for (int column :
                Stream.concat(Stream.of(meanColumn), chainColumns.stream()).toList()) {
            Set<Double> visited = new HashSet<>();
            for (String line : lines.subList(1, lines.size())) {
                String[] fields = line.trim().split("\\s+");
                double value = Double.parseDouble(fields[column]);
                assertTrue(Double.isFinite(value) && value > 0.0);
                assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
                visited.add(value);
            }
            assertTrue(visited.size() > 1, headers.get(column) + " must be sampled.");
        }
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static String sharedChains() {
        return """
                Vector<PositiveReal> chain ~ ExponentialMarkovChain(initialMean=mean, numValues=3)
                Vector<PositiveReal> first ~ ExponentialMarkovChain(initialMean=mean, numValues=3) observed as [0.7,1.4,0.9]
                Vector<PositiveReal> second ~ ExponentialMarkovChain(initialMean=mean, numValues=2) observed as [1.1,0.4]
                """;
    }

    private static double logDensity(double initialMean, double[] values) {
        double result = 0.0;
        double mean = initialMean;
        for (double value : values) {
            if (!Double.isFinite(mean) || mean <= 0.0 || !Double.isFinite(value) || value < 0.0) {
                return Double.NEGATIVE_INFINITY;
            }
            result += -Math.log(mean) - value / mean;
            mean = value;
        }
        return result;
    }

    private static double[] jointFactors(double mean, double[] chain) {
        return new double[] {
            Math.log(mean) - mean, logDensity(mean, chain), logDensity(mean, FIRST), logDensity(mean, SECOND)
        };
    }

    private static MarkovChainDistribution chainDistribution(Distribution distribution) {
        return assertInstanceOf(LiveExponentialMarkovChainDistribution.class, distribution);
    }

    private static MarkovChainDistribution observation(BEASTState graph, String id) {
        return chainDistribution(graph.likelihoodDistributions.stream()
                .filter(distribution -> id.equals(distribution.getID()))
                .findFirst()
                .orElseThrow());
    }

    private static MarkovChainDistribution chainPrior(BEASTState graph, RealVectorParam<?> chain) {
        return chainDistribution(graph.priorDistributions.get(chain));
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static RealVectorParam<?> chain(BEASTState graph) {
        return assertInstanceOf(RealVectorParam.class, node(graph, "chain"));
    }

    private static RealScalarParam<?> mean(BEASTState graph) {
        return assertInstanceOf(RealScalarParam.class, node(graph, "mean"));
    }

    private static Operator operator(BEASTState graph, StateNode parameter) {
        List<Operator> operators = graph.operators.stream()
                .filter(operator -> operator.listStateNodes().contains(parameter))
                .toList();
        assertEquals(1, operators.size());
        assertEquals(List.of(parameter), operators.getFirst().listStateNodes());
        return operators.getFirst();
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("exponential-chain"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
