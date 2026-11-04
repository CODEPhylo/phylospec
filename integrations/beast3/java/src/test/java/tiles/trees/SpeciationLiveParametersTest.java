package tiles.trees;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.speciation.BirthDeathGernhard08Model;
import beast.base.spec.evolution.speciation.YuleModel;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import tiling.ExpValue;
import tiling.LiveBirthDeathGernhard08Model;
import tiling.LiveYuleModel;

class SpeciationLiveParametersTest {
    private static final double TOLERANCE = 1e-11;
    private static final String TAXA =
            "Taxa samples = [taxon(name=\"a\"),taxon(name=\"b\"),taxon(name=\"c\"),taxon(name=\"d\")]\n";
    private static final String OBSERVED = """
            Tree fixed = fromNewick(newickString="((a:1.0,b:1.0):1.0,(c:1.5,d:1.5):0.5);")
            Tree other = fromNewick(newickString="((a:0.5,b:0.5):1.0,(c:0.75,d:0.75):0.75);")
            """;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fixedParametersRetainTheNativeDensityWithoutAddingState(boolean birthDeath) throws Exception {
        BEASTState graph =
                build(TAXA + OBSERVED + "Tree tree ~ " + generator(birthDeath, "1.5", "0.25") + " observed as fixed\n");
        YuleModel law = law(graph, "tree_likelihood");
        if (birthDeath) assertInstanceOf(LiveBirthDeathGernhard08Model.class, law);
        else assertInstanceOf(LiveYuleModel.class, law);
        assertNull(law.originHeightParameterInput.get());
        assertFalse(law.conditionalOnRootInput.get());
        assertEquals(nativeDensity(law), law.calculateLogP(), TOLERANCE);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    private static Stream<Arguments> proposals() {
        return Stream.of(false, true).flatMap(birthDeath -> Stream.of(false, true)
                .flatMap(derived -> (birthDeath ? Stream.of("rate", "turnover") : Stream.of("rate"))
                        .flatMap(changed -> Stream.of(
                                Arguments.of(birthDeath, derived, changed, false),
                                Arguments.of(birthDeath, derived, changed, true)))));
    }

    @ParameterizedTest
    @MethodSource("proposals")
    void sharedParametersUpdateObservedTreePosteriorsAndRestoreState(
            boolean birthDeath, boolean derived, String changed, boolean acceptFirst) throws Exception {
        BEASTState graph = sampled(birthDeath, derived);
        RealScalarParam<?> rate = parameter(graph, derived ? "logRate" : "rate");
        RealScalarParam<?> turnover = birthDeath ? parameter(graph, derived ? "logTurnover" : "turnover") : null;
        List<YuleModel> laws = List.of(law(graph, "first_likelihood"), law(graph, "second_likelihood"));
        int expectedParameters = birthDeath ? 2 : 1;
        assertEquals(expectedParameters, graph.stateNodes.size());
        assertEquals(expectedParameters, graph.priorDistributions.size());
        assertEquals(expectedParameters, graph.operators.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(derived ? 0.1 : 1.0, rate.get(), TOLERANCE);
        if (birthDeath) assertEquals(derived ? -1.5 : Math.exp(-1.5), turnover.get(), TOLERANCE);
        if (derived) {
            assertInstanceOf(Normal.class, graph.priorDistributions.get(rate));
            if (birthDeath) assertInstanceOf(Uniform.class, graph.priorDistributions.get(turnover));
        } else {
            assertInstanceOf(LogNormal.class, graph.priorDistributions.get(rate));
            if (birthDeath) assertInstanceOf(LogNormal.class, graph.priorDistributions.get(turnover));
        }
        for (var operator : graph.operators) {
            if (birthDeath && derived && operator.listStateNodes().contains(turnover)) {
                RealRandomWalkOperator walk = assertInstanceOf(RealRandomWalkOperator.class, operator);
                assertEquals(0.25, walk.windowSizeInput.get());
            } else assertInstanceOf(ScaleOperator.class, operator);
            assertEquals(5.0, operator.m_pWeight.get());
            assertEquals(1, operator.listStateNodes().size());
            assertTrue(operator.listStateNodes().stream().allMatch(graph.stateNodes::containsKey));
        }
        for (YuleModel law : laws) {
            if (derived) {
                ExpValue value = assertInstanceOf(ExpValue.class, law.birthDiffRateParameterInput.get());
                assertSame(rate, value.xInput.get());
                if (birthDeath) {
                    var bd = assertInstanceOf(BirthDeathGernhard08Model.class, law);
                    assertSame(
                            turnover,
                            assertInstanceOf(ExpValue.class, bd.relativeDeathRateParameterInput.get())
                                    .xInput
                                    .get());
                }
            } else {
                assertSame(rate, law.birthDiffRateParameterInput.get());
                if (birthDeath)
                    assertSame(turnover, ((BirthDeathGernhard08Model) law).relativeDeathRateParameterInput.get());
            }
        }

        Model model = state(graph, laws);
        assertMatchesReference(model, expectedPriors(birthDeath, derived, rate, turnover));
        RealScalarParam<?> source = changed.equals("rate") ? rate : turnover;
        double[] proposedValues = changed.equals("rate")
                ? (derived ? new double[] {0.25, 0.5, -0.2} : new double[] {1.4, 1.8, 0.8})
                : (derived ? new double[] {-1.2, -1.8, -1.4} : new double[] {0.25, 0.3, 0.2});
        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        for (int step = 0; step < decisions.length; step++) {
            double previousValue = source.get();
            double[] previous = cached(model);
            model.state().store(step + 1);
            source.set(proposedValues[step]);
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            for (YuleModel law : laws) {
                assertFalse(law.treeInput.get().somethingIsDirty(), "Fixed trees must not trigger these updates.");
                assertTrue(law.somethingIsDirty(), "A changed derived parameter must invalidate the density cache.");
            }
            model.posterior().calculateLogP();
            double[] proposed = cached(model);
            assertNotEquals(previous[0], proposed[0], TOLERANCE);
            assertNotEquals(previous[1], proposed[1], TOLERANCE);
            assertMatchesReference(model, expectedPriors(birthDeath, derived, rate, turnover));
            if (decisions[step]) model.state().acceptCalculationNodes();
            else {
                model.state().restore();
                model.state().restoreCalculationNodes();
            }
            model.state().setEverythingDirty(false);
            assertEquals(decisions[step] ? proposedValues[step] : previousValue, source.get());
            assertArrayEquals(decisions[step] ? proposed : previous, cached(model), TOLERANCE);
            assertMatchesReference(model, expectedPriors(birthDeath, derived, rate, turnover));
            assertEquals(model.posterior().getCurrentLogP(), model.posterior().calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sampledTreesRetainTheExistingInitializationAndOperatorConfiguration(boolean birthDeath) throws Exception {
        BEASTState graph = build(TAXA + "Tree tree ~ " + generator(birthDeath, "1.5", "0.25") + "\n");
        Tree tree = assertInstanceOf(
                Tree.class, graph.stateNodes.keySet().iterator().next());
        YuleModel law = assertInstanceOf(YuleModel.class, graph.priorDistributions.get(tree));
        assertSame(tree, law.treeInput.get());
        assertEquals(4, tree.getLeafNodeCount());
        assertTrue(tree.getRoot().getHeight() > 0.0);
        assertEquals(
                List.of(
                        "ScaleTreeOperator",
                        "BactrianSubtreeSlide",
                        "BactrianNodeOperator",
                        "Exchange",
                        "Exchange",
                        "WilsonBalding"),
                graph.operators.stream()
                        .map(operator -> operator.getClass().getSimpleName())
                        .toList());
        assertEquals(
                List.of(5.0, 15.0, 30.0, 15.0, 5.0, 5.0),
                graph.operators.stream()
                        .map(operator -> operator.m_pWeight.get())
                        .toList());
        assertEquals(nativeDensity(law), law.calculateLogP(), TOLERANCE);
    }

    private static BEASTState sampled(boolean birthDeath, boolean derived) throws Exception {
        String rates = derived
                ? "Real logRate ~ Normal(mean=0.0,sd=1.0)\nPositiveReal rate = exp(x=logRate)\n"
                : "PositiveReal rate ~ LogNormal(logMean=0.0,logSd=1.0)\n";
        if (birthDeath)
            rates += derived
                    ? "Real logTurnover ~ Uniform(lower=-2.0,upper=-1.0)\nPositiveReal turnover = exp(x=logTurnover)\n"
                    : "PositiveReal turnover ~ LogNormal(logMean=-1.5,logSd=0.25)\n";
        return build(TAXA + OBSERVED + rates
                + "Tree first ~ " + generator(birthDeath, "rate", "turnover") + " observed as fixed\n"
                + "Tree second ~ " + generator(birthDeath, "rate", "turnover") + " observed as other\n");
    }

    private static String generator(boolean birthDeath, String rate, String turnover) {
        return birthDeath
                ? "BirthDeath(diversificationRate=" + rate + ",turnover=" + turnover
                        + ",samplingProbability=0.8,taxa=samples)"
                : "Yule(birthRate=" + rate + ",taxa=samples)";
    }

    private static double expectedPriors(
            boolean birthDeath, boolean derived, RealScalarParam<?> rate, RealScalarParam<?> turnover) {
        double result = derived ? normalDensity(rate.get(), 0.0, 1.0) : logNormalDensity(rate.get(), 0.0, 1.0);
        if (birthDeath) result += derived ? 0.0 : logNormalDensity(turnover.get(), -1.5, 0.25);
        return result;
    }

    private static double normalDensity(double value, double mean, double sd) {
        double z = (value - mean) / sd;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(sd) - 0.5 * z * z;
    }

    private static double logNormalDensity(double value, double mean, double sd) {
        return normalDensity(Math.log(value), mean, sd) - Math.log(value);
    }

    private static double nativeDensity(YuleModel law) {
        YuleModel reference =
                law instanceof BirthDeathGernhard08Model ? new BirthDeathGernhard08Model() : new YuleModel();
        reference.treeInput.setValue(new Tree(law.treeInput.get().getRoot().copy()), reference);
        reference.birthDiffRateParameterInput.setValue(
                new RealScalarParam<>(law.birthDiffRateParameterInput.get().get(), PositiveReal.INSTANCE), reference);
        if (reference instanceof BirthDeathGernhard08Model bd) {
            BirthDeathGernhard08Model original = (BirthDeathGernhard08Model) law;
            bd.relativeDeathRateParameterInput.setValue(
                    new RealScalarParam<>(
                            original.relativeDeathRateParameterInput.get().get(), PositiveReal.INSTANCE),
                    bd);
            bd.sampleProbabilityInput.setValue(
                    new RealScalarParam<>(original.sampleProbabilityInput.get().get(), UnitInterval.INSTANCE), bd);
        }
        reference.initAndValidate();
        double result = reference.calculateLogP();
        assertTrue(Double.isFinite(result));
        return result;
    }

    private static void assertMatchesReference(Model model, double priors) {
        double expected = priors;
        for (YuleModel law : model.laws()) {
            double density = nativeDensity(law);
            assertEquals(density, law.getCurrentLogP(), TOLERANCE);
            expected += density;
        }
        assertEquals(expected, model.posterior().getCurrentLogP(), TOLERANCE);
    }

    private static Model state(BEASTState graph, List<YuleModel> laws) {
        List<Distribution> terms = new ArrayList<>(graph.priorDistributions.values());
        terms.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", terms);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        for (YuleModel law : laws) ((Tree) law.treeInput.get()).setEverythingDirty(false);
        return new Model(state, posterior, laws);
    }

    private static double[] cached(Model model) {
        double[] values = new double[model.laws().size() + 1];
        for (int i = 0; i < model.laws().size(); i++)
            values[i] = model.laws().get(i).getCurrentLogP();
        values[values.length - 1] = model.posterior().getCurrentLogP();
        return values;
    }

    private static YuleModel law(BEASTState graph, String id) {
        return assertInstanceOf(
                YuleModel.class,
                graph.likelihoodDistributions.stream()
                        .filter(distribution -> id.equals(distribution.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static RealScalarParam<?> parameter(BEASTState graph, String id) {
        return assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(parameter -> id.equals(parameter.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static BEASTState build(String source) throws Exception {
        Randomizer.setSeed(7211);
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("speciation-live-parameters"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }

    private record Model(State state, CompoundDistribution posterior, List<YuleModel> laws) {}
}
