package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.Input;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Dirichlet;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.util.GammaFunction;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
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
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiling.LiveDirichletDistribution;

class DirichletConcentrationBindingTest {
    // GammaFunction.lnGamma documents approximately ten decimal places of accuracy.
    private static final double TOLERANCE = 1e-8;
    private static final double[] FIRST = {0.2, 0.3, 0.5};
    private static final double[] SECOND = {0.4, 0.35, 0.25};

    @ParameterizedTest
    @CsvSource({"Real,false", "Real,true", "PositiveReal,false", "PositiveReal,true"})
    void fixedDeclaredRealVectorsRetainTheNativeLawAndUniformInitializer(String declaredType, boolean observed)
            throws Exception {
        BEASTState graph = build("Vector<" + declaredType + "> alpha = [1.2,2.3,0.7]\n"
                + "Simplex weights ~ Dirichlet(concentration=alpha)" + (observed ? " observed as [0.2,0.3,0.5]" : ""));
        Dirichlet distribution = observed ? observation(graph, "weights_likelihood") : prior(graph, simplex(graph));
        assertInstanceOf(LiveDirichletDistribution.class, distribution);
        double[] concentration = distribution.alphaInput.get().getElements().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
        assertArrayEquals(new double[] {1.2, 2.3, 0.7}, concentration, 0.0);
        double[] expected = observed ? FIRST : new double[] {1.0 / 3, 1.0 / 3, 1.0 / 3};
        SimplexParam weights = assertInstanceOf(SimplexParam.class, distribution.paramInput.get());
        assertArrayEquals(expected, weights.getValues(), 0.0);
        assertSame(UnitInterval.INSTANCE, weights.getDomain());
        assertEquals(observed ? 0 : 1, graph.stateNodes.size());
        assertEquals(observed ? 0 : 1, graph.priorDistributions.size());
        assertEquals(observed ? 1 : 0, graph.likelihoodDistributions.size());
        assertEquals(observed ? 0 : 1, graph.operators.size());
        assertDensity(distribution, concentration, expected);
        if (!observed) {
            DeltaExchangeOperator exchange = assertInstanceOf(DeltaExchangeOperator.class, operator(graph, weights));
            assertSame(weights, exchange.rvparameterInput.get());
            assertEquals(0.01, exchange.deltaInput.get(), 0.0);
            assertEquals(5.0, exchange.m_pWeight.get(), 0.0);
        }
    }

    private static Stream<Real> concentrationDomains() {
        return Stream.of(Real.INSTANCE, NonNegativeReal.INSTANCE, PositiveReal.INSTANCE, UnitInterval.INSTANCE);
    }

    @ParameterizedTest
    @MethodSource("concentrationDomains")
    void directBindingKeepsTheExactConcentrationObjectAndItsDomain(Real domain) throws Exception {
        BEASTState graph = new BEASTState("dirichlet-identity");
        RealVectorParam<?> concentration = new RealVectorParam<>(new double[] {0.2, 0.3, 0.7}, domain);
        DirichletTile tile = new DirichletTile();
        tile.concentrationInput.setTile(valueTile(concentration));
        var bound = tile.applyTile(graph, new IdentityHashMap<>());
        bound.observeAs(graph, new SimplexParam(FIRST), "observed");
        graph.initializeBEASTObjects();
        assertSame(concentration, bound.getDistribution().alphaInput.get());
        assertSame(domain, concentration.getDomain());
        assertTrue(concentration.getOutputs().contains(bound.getDistribution()));
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertDensity(bound.getDistribution(), concentration.getValues(), FIRST);
    }

    @Test
    void oneElementSimplexKeepsTheExistingInitializationAndOperator() throws Exception {
        BEASTState graph = build("Simplex weights ~ Dirichlet(concentration=[2.0])");
        SimplexParam weights = simplex(graph);
        assertArrayEquals(new double[] {1.0}, weights.getValues(), 0.0);
        assertEquals(0.0, prior(graph, weights).calculateLogP(), TOLERANCE);
        Operator operator = operator(graph, weights);
        assertInstanceOf(DeltaExchangeOperator.class, operator);
        assertEquals(0.0, operator.proposal(), 0.0);
        assertArrayEquals(new double[] {1.0}, weights.getValues(), 0.0);
    }

    private static Stream<Arguments> proposalCases() {
        return Stream.of("Normal", "Gamma", "EMC").flatMap(family -> Stream.of("alpha", "weights")
                .flatMap(parameter ->
                        Stream.of(Arguments.of(family, parameter, false), Arguments.of(family, parameter, true))));
    }

    @ParameterizedTest
    @MethodSource("proposalCases")
    void actualProposalsUpdateSharedConcentrationsAndRestoreEveryFactorCache(
            String family, String changedParameter, boolean acceptFirst) throws Exception {
        BEASTState graph = build(sharedModel(family));
        RealVectorParam<?> alpha = concentration(graph);
        SimplexParam weights = simplex(graph);
        Dirichlet weightsPrior = prior(graph, weights);
        Dirichlet first = observation(graph, "first_likelihood");
        Dirichlet second = observation(graph, "second_likelihood");
        for (Dirichlet factor : List.of(weightsPrior, first, second)) {
            assertInstanceOf(LiveDirichletDistribution.class, factor);
            assertSame(alpha, factor.alphaInput.get());
            assertTrue(alpha.getOutputs().contains(factor));
        }
        assertSame(family.equals("Gamma") ? PositiveReal.INSTANCE : Real.INSTANCE, alpha.getDomain());
        double initial = family.equals("Normal") ? 0.1 : family.equals("Gamma") ? 1.0 : 1.5;
        assertArrayEquals(new double[] {initial, initial, initial}, alpha.getValues(), 0.0);
        assertArrayEquals(new double[] {1.0 / 3, 1.0 / 3, 1.0 / 3}, weights.getValues(), 0.0);
        assertEquals(2, graph.stateNodes.size());
        assertEquals(2, graph.priorDistributions.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(2, graph.operators.size());
        assertDensity(weightsPrior, alpha.getValues(), weights.getValues());
        List<Distribution> factors = List.of(graph.priorDistributions.get(alpha), weightsPrior, first, second);
        CompoundDistribution posterior = posterior(factors);
        State state = state(List.copyOf(graph.stateNodes.keySet()), posterior);
        assertEquals(
                Arrays.stream(jointFactors(family, alpha.getValues(), weights.getValues()))
                        .sum(),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);
        Operator operator = operator(graph, node(graph, changedParameter));
        if (changedParameter.equals("weights")) assertInstanceOf(DeltaExchangeOperator.class, operator);
        else if (family.equals("Normal")) assertInstanceOf(RealRandomWalkOperator.class, operator);
        Randomizer.setSeed(80301);
        for (int step = 0; step < 2; step++) {
            boolean accept = step == 0 ? acceptFirst : !acceptFirst;
            double[] previousAlpha = alpha.getValues();
            double[] previousWeights = weights.getValues();
            double[] previousFactors =
                    factors.stream().mapToDouble(Distribution::getCurrentLogP).toArray();
            double previousPosterior = posterior.getCurrentLogP();
            boolean moved = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                state.store(step * 100L + attempt);
                double logHastings = operator.proposal();
                state.storeCalculationNodes();
                state.checkCalculationNodesDirtiness();
                if (Double.isFinite(logHastings)
                        && Arrays.stream(alpha.getValues()).allMatch(value -> value > 0.0)) {
                    moved = true;
                    break;
                }
                finishProposal(state, false);
            }
            assertTrue(moved, "Exercise an actual proposal in the Dirichlet concentration support.");
            double[] proposedAlpha = alpha.getValues();
            double[] proposedWeights = weights.getValues();
            if (changedParameter.equals("alpha")) {
                assertFalse(Arrays.equals(previousAlpha, proposedAlpha));
                assertArrayEquals(previousWeights, proposedWeights, 0.0);
            } else {
                assertArrayEquals(previousAlpha, proposedAlpha, 0.0);
                assertFalse(Arrays.equals(previousWeights, proposedWeights));
            }
            assertEquals(1.0, Arrays.stream(proposedWeights).sum(), 1e-12);
            assertTrue(Arrays.stream(proposedWeights).allMatch(value -> value > 0.0 && value < 1.0));
            double[] expectedFactors = jointFactors(family, proposedAlpha, proposedWeights);
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(Arrays.stream(expectedFactors).sum(), proposedPosterior, TOLERANCE);
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(expectedFactors[i], factors.get(i).getCurrentLogP(), TOLERANCE);
            }
            finishProposal(state, accept);
            assertArrayEquals(accept ? proposedAlpha : previousAlpha, alpha.getValues(), 0.0);
            assertArrayEquals(accept ? proposedWeights : previousWeights, weights.getValues(), 0.0);
            // Check cached factors before recalculation could hide a failed restoration.
            for (int i = 0; i < factors.size(); i++) {
                assertEquals(
                        accept ? expectedFactors[i] : previousFactors[i],
                        factors.get(i).getCurrentLogP(),
                        TOLERANCE);
            }
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    Arrays.stream(jointFactors(family, alpha.getValues(), weights.getValues()))
                            .sum(),
                    posterior.calculateLogP(),
                    TOLERANCE);
            assertArrayEquals(
                    FIRST,
                    assertInstanceOf(SimplexParam.class, first.paramInput.get()).getValues(),
                    0.0);
            assertArrayEquals(
                    SECOND,
                    assertInstanceOf(SimplexParam.class, second.paramInput.get())
                            .getValues(),
                    0.0);
            for (Dirichlet factor : List.of(weightsPrior, first, second)) assertSame(alpha, factor.alphaInput.get());
        }
    }

    @Test
    void actualNegativeConcentrationProposalIsRejectedWithoutANativeExceptionAndRestoresCaches() throws Exception {
        BEASTState graph = build(sharedModel("Normal"));
        RealVectorParam<?> alpha = concentration(graph);
        SimplexParam weights = simplex(graph);
        List<Distribution> factors = List.of(
                graph.priorDistributions.get(alpha),
                prior(graph, weights),
                observation(graph, "first_likelihood"),
                observation(graph, "second_likelihood"));
        CompoundDistribution posterior = posterior(factors);
        State state = state(List.copyOf(graph.stateNodes.keySet()), posterior);
        double previousPosterior = state.robustlyCalcPosterior(posterior);
        double[] previousFactors =
                factors.stream().mapToDouble(Distribution::getCurrentLogP).toArray();
        double[] previousAlpha = alpha.getValues();
        double[] previousWeights = weights.getValues();
        Operator operator = operator(graph, alpha);
        Randomizer.setSeed(80302);
        boolean negative = false;
        for (int attempt = 0; attempt < 100; attempt++) {
            state.store(attempt);
            assertEquals(0.0, operator.proposal(), 0.0);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            if (Arrays.stream(alpha.getValues()).anyMatch(value -> value < 0.0)) {
                negative = true;
                break;
            }
            finishProposal(state, false);
        }
        assertTrue(negative, "The Real random walk must be allowed to propose negative concentrations.");
        assertTrue(Double.isFinite(factors.getFirst().calculateLogP()));
        for (Distribution factor : factors.subList(1, factors.size())) {
            assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(factor::calculateLogP));
        }
        assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(posterior::calculateLogP));
        finishProposal(state, false);
        assertArrayEquals(previousAlpha, alpha.getValues(), 0.0);
        assertArrayEquals(previousWeights, weights.getValues(), 0.0);
        for (int i = 0; i < factors.size(); i++) {
            assertEquals(previousFactors[i], factors.get(i).getCurrentLogP(), TOLERANCE);
        }
        assertEquals(previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidConcentrationStatesReturnZeroDensityAndRestoreNativeState(double invalid) {
        MutableConcentration alpha = new MutableConcentration();
        alpha.setID("alpha");
        Dirichlet first = observedDistribution(alpha, FIRST);
        Dirichlet second = observedDistribution(alpha, SECOND);
        CompoundDistribution posterior = posterior(List.of(first, second));
        State state = state(List.of(alpha), posterior);
        double previousPosterior = state.robustlyCalcPosterior(posterior);
        double previousFirst = first.getCurrentLogP();
        double previousSecond = second.getCurrentLogP();
        double[] previousAlpha = alpha.getValues();
        state.store(1);
        if (Double.isNaN(invalid)) alpha.proposeNaN();
        else alpha.set(0, invalid);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(first::calculateLogP));
        assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(second::calculateLogP));
        assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(posterior::calculateLogP));
        finishProposal(state, false);
        assertArrayEquals(previousAlpha, alpha.getValues(), 0.0);
        assertSame(alpha, first.alphaInput.get());
        assertSame(alpha, second.alphaInput.get());
        assertEquals(previousFirst, first.getCurrentLogP(), TOLERANCE);
        assertEquals(previousSecond, second.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[0.0,1.0,2.0]", "[-0.5,1.0,2.0]"})
    void invalidFixedConcentrationsHaveATargetedSetupDiagnostic(String concentration) {
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> build("Vector<Real> alpha = " + concentration
                        + "\nSimplex weights ~ Dirichlet(concentration=alpha)"));
        assertTrue(error.getMessage().contains("Dirichlet"), error.getMessage());
        assertTrue(error.getMessage().contains("concentration"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY})
    void nonfiniteInitialConcentrationsAreDiagnosedBeforeNativeInitialization(double invalid) throws Exception {
        MutableConcentration alpha = new MutableConcentration();
        if (Double.isNaN(invalid)) alpha.proposeNaN();
        else alpha.set(0, invalid);
        DirichletTile tile = new DirichletTile();
        tile.concentrationInput.setTile(valueTile(alpha));
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> tile.applyTile(new BEASTState("invalid-concentration"), new IdentityHashMap<>()));
        assertTrue(error.getMessage().contains("Dirichlet"), error.getMessage());
        assertTrue(error.getMessage().contains("finite"), error.getMessage());
    }

    @Test
    void emptyConcentrationAndMismatchedObservationLengthsAreRejectedBeforeBinding() throws Exception {
        RealVectorParam<Real> empty = new RealVectorParam<>();
        empty.dimensionInput.setValue(0, empty);
        empty.valuesInput.setRule(Input.Validate.OPTIONAL);
        empty.initAndValidate();
        DirichletTile emptyTile = new DirichletTile();
        emptyTile.concentrationInput.setTile(valueTile(empty));
        TileApplicationError emptyError = assertThrows(
                TileApplicationError.class,
                () -> emptyTile.applyTile(new BEASTState("empty-concentration"), new IdentityHashMap<>()));
        assertTrue(emptyError.getMessage().contains("nonempty"), emptyError.getMessage());

        BEASTState graph = new BEASTState("dirichlet-length");
        DirichletTile tile = new DirichletTile();
        tile.concentrationInput.setTile(valueTile(new RealVectorParam<>(new double[] {1.2, 2.3, 0.7}, Real.INSTANCE)));
        var bound = tile.applyTile(graph, new IdentityHashMap<>());
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> bound.observeAs(graph, new SimplexParam(new double[] {0.4, 0.6}), "observed"));
        assertTrue(error.getMessage().contains("length"), error.getMessage());
        assertNull(bound.getDistribution().paramInput.get());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    @Test
    void runtimeLengthMismatchReturnsZeroDensityBeforeNativeEvaluation() {
        Dirichlet distribution = observedDistribution(new MutableConcentration(), FIRST);
        distribution.paramInput.setValue(new SimplexParam(new double[] {0.4, 0.6}), distribution);
        assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(distribution::calculateLogP));
    }

    @Test
    void shortExponentialChainHierarchyUpdatesConcentrationsAndSimplex(@TempDir Path directory) throws Exception {
        Path prefix = directory.resolve("dirichlet-emc");
        String source = sharedModel("EMC") + """
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 80303
                    Integer defaultLogEvery = 1
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("dirichlet-emc");
        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int posteriorColumn = headers.indexOf("posterior");
        List<Integer> alphaColumns = IntStream.range(0, headers.size())
                .filter(index -> headers.get(index).startsWith("alpha."))
                .boxed()
                .toList();
        List<Integer> weightColumns = IntStream.range(0, headers.size())
                .filter(index -> headers.get(index).startsWith("weights."))
                .boxed()
                .toList();
        assertTrue(posteriorColumn >= 0, headers.toString());
        assertEquals(3, alphaColumns.size(), headers.toString());
        assertEquals(3, weightColumns.size(), headers.toString());
        assertEquals(202, lines.size(), "Header plus samples 0 through 200.");
        for (int column :
                Stream.concat(alphaColumns.stream(), weightColumns.stream()).toList()) {
            Set<Double> visited = new HashSet<>();
            for (String line : lines.subList(1, lines.size())) {
                String[] fields = line.trim().split("\\s+");
                double value = Double.parseDouble(fields[column]);
                assertTrue(Double.isFinite(value) && value > 0.0);
                if (weightColumns.contains(column)) assertTrue(value < 1.0);
                assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
                assertEquals(
                        1.0,
                        weightColumns.stream()
                                .mapToDouble(index -> Double.parseDouble(fields[index]))
                                .sum(),
                        1e-12);
                visited.add(value);
            }
            assertTrue(visited.size() > 1, headers.get(column) + " must be sampled.");
        }
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static String sharedModel(String family) {
        String concentration =
                switch (family) {
                    case "Normal" -> "Vector<Real> alpha ~ IID(base=Normal(mean=1.0,sd=0.5),num=3)\n";
                    case "Gamma" -> "Vector<PositiveReal> alpha ~ IID(base=Gamma(shape=2.0,rate=1.0),num=3)\n";
                    case "EMC" -> "Vector<Real> alpha ~ ExponentialMarkovChain(initialMean=1.5,numValues=3)\n";
                    default -> throw new AssertionError(family);
                };
        return concentration + """
                Simplex weights ~ Dirichlet(concentration=alpha)
                Simplex first ~ Dirichlet(concentration=alpha) observed as [0.2,0.3,0.5]
                Simplex second ~ Dirichlet(concentration=alpha) observed as [0.4,0.35,0.25]
                """;
    }

    private static double[] jointFactors(String family, double[] alpha, double[] weights) {
        double upstream = 0.0;
        double previous = 1.5;
        for (double value : alpha) {
            upstream += switch (family) {
                case "Normal" -> -Math.log(0.5) - 0.5 * Math.log(2 * Math.PI) - 0.5 * Math.pow((value - 1.0) / 0.5, 2);
                case "Gamma" -> Math.log(value) - value;
                case "EMC" -> -Math.log(previous) - value / previous;
                default -> throw new AssertionError(family);
            };
            previous = value;
        }
        return new double[] {upstream, logDensity(alpha, weights), logDensity(alpha, FIRST), logDensity(alpha, SECOND)};
    }

    private static double logDensity(double[] alpha, double[] weights) {
        double result = GammaFunction.lnGamma(Arrays.stream(alpha).sum());
        for (int i = 0; i < alpha.length; i++) {
            result += (alpha[i] - 1.0) * Math.log(weights[i]) - GammaFunction.lnGamma(alpha[i]);
        }
        return result;
    }

    private static void assertDensity(Dirichlet distribution, double[] alpha, double[] weights) {
        double expected = logDensity(alpha, weights);
        assertEquals(expected, distribution.calculateLogP(), TOLERANCE);
        Dirichlet nativeDistribution =
                new Dirichlet(new SimplexParam(weights), new RealVectorParam<>(alpha, PositiveReal.INSTANCE));
        assertEquals(expected, nativeDistribution.calculateLogP(), TOLERANCE);
    }

    private static Dirichlet observedDistribution(RealVectorParam<?> alpha, double[] weights) {
        Dirichlet distribution = new LiveDirichletDistribution();
        distribution.initByName("alpha", alpha, "param", new SimplexParam(weights));
        return distribution;
    }

    /** Lets the guard see a nonfinite value that the native Real setter itself rejects. */
    public static class MutableConcentration extends RealVectorParam<Real> {
        public MutableConcentration() {
            super(new double[] {1.2, 2.3, 0.7}, Real.INSTANCE);
        }

        void proposeNaN() {
            startEditing(null);
            values[0] = Double.NaN;
            setEverythingDirty(true);
        }
    }

    private static Dirichlet observation(BEASTState graph, String id) {
        return assertInstanceOf(
                Dirichlet.class,
                graph.likelihoodDistributions.stream()
                        .filter(distribution -> id.equals(distribution.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static Dirichlet prior(BEASTState graph, SimplexParam weights) {
        return assertInstanceOf(Dirichlet.class, graph.priorDistributions.get(weights));
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static RealVectorParam<?> concentration(BEASTState graph) {
        return assertInstanceOf(RealVectorParam.class, node(graph, "alpha"));
    }

    private static SimplexParam simplex(BEASTState graph) {
        return assertInstanceOf(SimplexParam.class, node(graph, "weights"));
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

    private static State state(List<StateNode> nodes, Distribution posterior) {
        State state = new State();
        state.initByName("stateNode", nodes);
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("dirichlet-concentration"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
