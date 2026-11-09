package tiles.trees;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.operator.Exchange;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.speciation.BirthDeathGernhard08Model;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BirthDeathRateValue;
import tiling.ExpValue;
import tiling.LiveBirthDeathModel;

class BirthDeathRatesTileTest {
    private static final double TOLERANCE = 1e-11;
    private static final String TAXA =
            "Taxa samples = [taxon(name=\"a\"),taxon(name=\"b\"),taxon(name=\"c\"),taxon(name=\"d\")]\n";
    private static final String NEWICK = "((a:1.0,b:1.0):1.0,(c:1.5,d:1.5):0.5);";
    private static final String OBSERVED = "Tree fixed = fromNewick(newickString=\"" + NEWICK + "\")\n";

    @ParameterizedTest
    @ValueSource(strings = {"", ",samplingProbability=1", ",samplingProbability=0.8"})
    void fixedRatesMatchExistingParameterizationAndNativeModel(String sampling) throws Exception {
        BEASTState graph = build(TAXA + OBSERVED + """
                Tree direct ~ BirthDeath(birthRate=2.0,deathRate=0.5,taxa=samples%s) observed as fixed
                Tree equivalent ~ BirthDeath(diversificationRate=1.5,turnover=0.25,taxa=samples%s) observed as fixed
                """.formatted(sampling, sampling));
        BirthDeathGernhard08Model direct = law(graph, "direct_likelihood");
        BirthDeathGernhard08Model equivalent = law(graph, "equivalent_likelihood");
        assertInstanceOf(LiveBirthDeathModel.class, direct);
        assertInstanceOf(BirthDeathGernhard08Model.class, equivalent);
        assertNull(direct.originHeightParameterInput.get());
        assertFalse(direct.conditionalOnRootInput.get());
        assertEquals(1.5, direct.birthDiffRateParameterInput.get().get());
        assertEquals(0.25, direct.relativeDeathRateParameterInput.get().get());
        assertEquals(nativeLogP(direct), direct.calculateLogP(), TOLERANCE);
        assertEquals(equivalent.calculateLogP(), direct.calculateLogP(), TOLERANCE);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(2, graph.likelihoodDistributions.size());
    }

    @Test
    void namedRatesBindWithoutAddingSamplingState() throws Exception {
        BEASTState graph = build(TAXA + OBSERVED + """
                PositiveReal birth = 0.8
                PositiveReal death = 0.2
                Tree direct ~ BirthDeath(birthRate=birth,deathRate=death,taxa=samples) observed as fixed
                """);
        BirthDeathGernhard08Model law = law(graph, "direct_likelihood");
        BirthDeathRateValue net = assertInstanceOf(BirthDeathRateValue.class, law.birthDiffRateParameterInput.get());
        assertSame(PositiveReal.INSTANCE, net.birthRateInput.get().getDomain());
        assertSame(PositiveReal.INSTANCE, net.deathRateInput.get().getDomain());
        assertEquals(nativeLogP(law), law.calculateLogP(), TOLERANCE);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    private static Stream<Arguments> rateProposals() {
        return Stream.of("direct", "derived").flatMap(family -> Stream.of("birth", "death")
                .flatMap(changed ->
                        Stream.of(Arguments.of(family, changed, true), Arguments.of(family, changed, false))));
    }

    @ParameterizedTest
    @MethodSource("rateProposals")
    void sharedRatesUpdateObservedTreePosteriorsAndRestoreState(String family, String changed, boolean acceptFirst)
            throws Exception {
        BEASTState graph = sampledRates(family, true);
        RealScalarParam<?> birthSource = parameter(graph, family.equals("direct") ? "birth" : "logBirth");
        RealScalarParam<?> death = parameter(graph, "death");
        List<BirthDeathGernhard08Model> laws = List.of(law(graph, "first_likelihood"), law(graph, "second_likelihood"));
        assertEquals(2, graph.stateNodes.size());
        assertEquals(2, graph.priorDistributions.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(2, graph.operators.size());
        assertEquals(family.equals("direct") ? Math.exp(1.1) : 0.1, birthSource.get(), TOLERANCE);
        assertEquals(Math.exp(-0.7), death.get(), TOLERANCE);
        assertOriginalPrior(graph, birthSource, family.equals("direct"), 1.1, 0.3);
        assertOriginalPrior(graph, death, true, -0.7, 0.2);
        for (var operator : graph.operators) {
            // logBirth ~ Normal lives on the whole Real line, so it gets additive proposals.
            if (family.equals("derived") && operator.listStateNodes().contains(birthSource)) {
                assertInstanceOf(RealRandomWalkOperator.class, operator);
            } else assertInstanceOf(ScaleOperator.class, operator);
            assertEquals(5.0, operator.m_pWeight.get());
            assertEquals(1, operator.listStateNodes().size());
            assertTrue(operator.listStateNodes().contains(birthSource)
                    || operator.listStateNodes().contains(death));
        }
        for (BirthDeathGernhard08Model law : laws) {
            BirthDeathRateValue net =
                    assertInstanceOf(BirthDeathRateValue.class, law.birthDiffRateParameterInput.get());
            BirthDeathRateValue turnover =
                    assertInstanceOf(BirthDeathRateValue.class, law.relativeDeathRateParameterInput.get());
            assertSame(net.birthRateInput.get(), turnover.birthRateInput.get());
            assertSame(death, net.deathRateInput.get());
            assertSame(death, turnover.deathRateInput.get());
            if (family.equals("direct")) assertSame(birthSource, net.birthRateInput.get());
            else
                assertSame(
                        birthSource,
                        assertInstanceOf(ExpValue.class, net.birthRateInput.get())
                                .xInput
                                .get());
            assertTrue(death.getOutputs().containsAll(List.of(net, turnover)));
            assertTrue(net.getOutputs().contains(law));
            assertTrue(turnover.getOutputs().contains(law));
        }
        Model model = state(graph, laws);
        assertMatchesReferences(model, expectedPriors(family, birthSource.get(), death.get()));
        RealScalarParam<?> source = changed.equals("birth") ? birthSource : death;
        double[] proposedValues = changed.equals("death")
                ? new double[] {0.35, 0.65, 0.4}
                : family.equals("direct") ? new double[] {2.4, 3.3, 2.8} : new double[] {0.3, 0.6, -0.1};
        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        for (int step = 0; step < proposedValues.length; step++) {
            double previousValue = source.get();
            double[] previous = cachedValues(model);
            model.state().store(step + 1);
            source.set(proposedValues[step]);
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            for (BirthDeathGernhard08Model law : laws) {
                assertFalse(law.treeInput.get().somethingIsDirty(), "Fixed trees must not cause these updates.");
                assertTrue(law.somethingIsDirty(), "Derived-rate changes must invalidate the tree density cache.");
            }
            model.posterior().calculateLogP();
            double[] proposed = cachedValues(model);
            assertNotEquals(previous[0], proposed[0], TOLERANCE);
            assertNotEquals(previous[1], proposed[1], TOLERANCE);
            assertMatchesReferences(model, expectedPriors(family, birthSource.get(), death.get()));
            finish(model, decisions[step]);
            assertEquals(decisions[step] ? proposedValues[step] : previousValue, source.get());
            assertArrayEquals(decisions[step] ? proposed : previous, cachedValues(model), TOLERANCE);
            assertMatchesReferences(model, expectedPriors(family, birthSource.get(), death.get()));
            assertEquals(model.posterior().getCurrentLogP(), model.posterior().calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @CsvSource({"height,true", "height,false", "topology,true", "topology,false"})
    void sampledTreesKeepExistingOperatorsAndRestoreAcceptedAndRejectedChanges(String change, boolean acceptFirst)
            throws Exception {
        BEASTState graph = build(TAXA + "Tree tree ~ BirthDeath(birthRate=2.0,deathRate=0.5,taxa=samples)\n");
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertEquals(6, graph.operators.size());
        Tree tree = assertInstanceOf(
                Tree.class, graph.stateNodes.keySet().iterator().next());
        Tree fixed = new TreeParser(NEWICK);
        fixed.setID(tree.getID());
        tree.assignFrom(fixed);
        BirthDeathGernhard08Model law = assertInstanceOf(LiveBirthDeathModel.class, graph.priorDistributions.get(tree));
        List<String> actualOperators = graph.operators.stream()
                .map(operator -> operator.getClass().getSimpleName())
                .toList();
        assertEquals(
                List.of(
                        "ScaleTreeOperator",
                        "BactrianSubtreeSlide",
                        "BactrianNodeOperator",
                        "Exchange",
                        "Exchange",
                        "WilsonBalding"),
                actualOperators);
        assertEquals(
                List.of(5.0, 15.0, 30.0, 15.0, 5.0, 5.0),
                graph.operators.stream()
                        .map(operator -> operator.m_pWeight.get())
                        .toList());
        Model model = state(graph, List.of(law));
        for (int step = 0; step < 2; step++) {
            boolean accept = step == 0 ? acceptFirst : !acceptFirst;
            String previousTree = tree.getRoot().toNewick();
            double[] previous = cachedValues(model);
            model.state().store(step + 1);
            if (change.equals("height")) tree.getRoot().setHeight(tree.getRoot().getHeight() + 0.2);
            else exchangeTips(tree, "a", "c");
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            model.posterior().calculateLogP();
            double[] proposed = cachedValues(model);
            String proposedTree = tree.getRoot().toNewick();
            assertNotEquals(previousTree, proposedTree);
            if (change.equals("height")) assertNotEquals(previous[0], proposed[0], TOLERANCE);
            else assertEquals(previous[0], proposed[0], TOLERANCE, "This exchange preserves all node heights.");
            assertMatchesReferences(model, 0.0);
            finish(model, accept);
            assertEquals(accept ? proposedTree : previousTree, tree.getRoot().toNewick());
            assertArrayEquals(accept ? proposed : previous, cachedValues(model), TOLERANCE);
            assertMatchesReferences(model, 0.0);
        }
    }

    @ParameterizedTest
    @CsvSource({"1.0,1.0", "0.5,1.0"})
    void unsupportedInitialRatesFailInsteadOfChangingTheModel(double birth, double death) {
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> build(TAXA + OBSERVED + "Tree tree ~ BirthDeath(birthRate=" + birth + ",deathRate=" + death
                        + ",taxa=samples) observed as fixed\n"));
        assertTrue(error.getMessage().contains("birthRate"), error.getMessage());
        assertTrue(error.getMessage().contains("deathRate"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unsupportedProposalsThrowAndManualRollbackRetainsTheValidPosterior(boolean equal) throws Exception {
        BEASTState graph = sampledRates("direct", false);
        BirthDeathGernhard08Model law = law(graph, "first_likelihood");
        Model model = state(graph, List.of(law));
        RealScalarParam<?> birth = parameter(graph, "birth");
        RealScalarParam<?> death = parameter(graph, "death");
        double previousDeath = death.get();
        double[] previous = cachedValues(model);
        model.state().store(1);
        death.set(equal ? birth.get() : birth.get() + 0.5);
        model.state().storeCalculationNodes();
        model.state().checkCalculationNodesDirtiness();
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> model.posterior().calculateLogP());
        assertTrue(error.getMessage().contains("birthRate"), error.getMessage());
        assertTrue(error.getMessage().contains("deathRate"), error.getMessage());
        // The adapter reports an unsupported region; it does not pretend this is a zero-density proposal.
        finish(model, false);
        assertEquals(previousDeath, death.get());
        assertArrayEquals(previous, cachedValues(model), TOLERANCE);
        assertMatchesReferences(model, expectedPriors("direct", birth.get(), death.get()));
    }

    @Test
    void mismatchedObservedRootIsNotTreatedAsAnOriginBound() {
        assertThrows(
                TileApplicationError.class,
                () -> build(
                        TAXA + OBSERVED
                                + "Tree tree ~ BirthDeath(birthRate=2.0,deathRate=0.5,rootAge=5.0,taxa=samples) observed as fixed\n"));
    }

    @Test
    void contemporaneousDecimalNewickLengthsAllowOnlyRoundingResiduals() throws Exception {
        BEASTState graph = build("""
                Taxa samples = [taxon(name="a"),taxon(name="b"),taxon(name="c")]
                Tree fixed = fromNewick(newickString="((a:0.1,b:0.1):0.2,c:0.3);")
                Tree tree ~ BirthDeath(birthRate=2.0,deathRate=0.5,taxa=samples) observed as fixed
                """);
        BirthDeathGernhard08Model law = law(graph, "tree_likelihood");
        assertEquals(nativeLogP(law), law.calculateLogP(), TOLERANCE);
    }

    @Test
    void genuinelyDatedObservedTipsAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> build("""
                Taxa samples = [taxon(name="a"),taxon(name="b"),taxon(name="c")]
                Tree fixed = fromNewick(newickString="((a:0.1,b:0.1):0.2,c:0.2);")
                Tree tree ~ BirthDeath(birthRate=2.0,deathRate=0.5,taxa=samples) observed as fixed
                """));
        assertTrue(error.getMessage().contains("contemporaneous"), error.getMessage());
    }

    private static BEASTState sampledRates(String family, boolean shared) throws Exception {
        String birth = family.equals("direct")
                ? "PositiveReal birth ~ LogNormal(logMean=1.1,logSd=0.3)\n"
                : "Real logBirth ~ Normal(mean=0.0,sd=0.5)\nPositiveReal birth = exp(x=logBirth)\n";
        return build(TAXA + OBSERVED + birth
                + "PositiveReal death ~ LogNormal(logMean=-0.7,logSd=0.2)\n"
                + "Tree first ~ BirthDeath(birthRate=birth,deathRate=death,samplingProbability=0.8,taxa=samples) observed as fixed\n"
                + (shared
                        ? "Tree other = fromNewick(newickString=\"((a:0.5,b:0.5):1.0,(c:0.75,d:0.75):0.75);\")\n"
                                + "Tree second ~ BirthDeath(birthRate=birth,deathRate=death,taxa=samples) observed as other\n"
                        : ""));
    }

    private static void assertOriginalPrior(
            BEASTState graph, RealScalarParam<?> source, boolean logNormal, double mean, double sd) {
        if (logNormal) {
            LogNormal prior = assertInstanceOf(LogNormal.class, graph.priorDistributions.get(source));
            assertSame(source, prior.paramInput.get());
            assertEquals(mean, prior.MParameterInput.get().get());
            assertEquals(sd, prior.SParameterInput.get().get());
            assertFalse(prior.hasMeanInRealSpaceInput.get());
        } else {
            Normal prior = assertInstanceOf(Normal.class, graph.priorDistributions.get(source));
            assertSame(source, prior.paramInput.get());
            assertEquals(0.0, prior.meanInput.get().get());
            assertEquals(0.5, prior.sdInput.get().get());
        }
    }

    private static double expectedPriors(String family, double birthSource, double death) {
        return (family.equals("direct")
                        ? logNormalDensity(birthSource, 1.1, 0.3)
                        : normalDensity(birthSource, 0.0, 0.5))
                + logNormalDensity(death, -0.7, 0.2);
    }

    private static double logNormalDensity(double x, double mean, double sd) {
        return normalDensity(Math.log(x), mean, sd) - Math.log(x);
    }

    private static double normalDensity(double x, double mean, double sd) {
        double z = (x - mean) / sd;
        return -0.5 * Math.log(2 * Math.PI) - Math.log(sd) - 0.5 * z * z;
    }

    private static void exchangeTips(Tree tree, String firstName, String secondName) {
        Node first = tree.getExternalNodes().stream()
                .filter(node -> firstName.equals(node.getID()))
                .findFirst()
                .orElseThrow();
        Node second = tree.getExternalNodes().stream()
                .filter(node -> secondName.equals(node.getID()))
                .findFirst()
                .orElseThrow();
        Node firstParent = first.getParent();
        Node secondParent = second.getParent();
        Exchange exchange = new Exchange();
        exchange.replace(firstParent, first, second);
        exchange.replace(secondParent, second, first);
    }

    private static void finish(Model model, boolean accept) {
        if (accept) model.state().acceptCalculationNodes();
        else {
            model.state().restore();
            model.state().restoreCalculationNodes();
        }
        model.state().setEverythingDirty(false);
    }

    private static double nativeLogP(BirthDeathGernhard08Model law) {
        Tree copy = new Tree(law.treeInput.get().getRoot().copy());
        BirthDeathGernhard08Model nativeModel = new BirthDeathGernhard08Model();
        nativeModel.initByName(
                "tree",
                copy,
                "birthDiffRate",
                new RealScalarParam<>(law.birthDiffRateParameterInput.get().get(), PositiveReal.INSTANCE),
                "relativeDeathRate",
                new RealScalarParam<>(law.relativeDeathRateParameterInput.get().get(), PositiveReal.INSTANCE),
                "sampleProbability",
                new RealScalarParam<>(
                        law.sampleProbabilityInput.get() == null
                                ? 1.0
                                : law.sampleProbabilityInput.get().get(),
                        UnitInterval.INSTANCE));
        double expected = nativeModel.calculateLogP();
        assertTrue(Double.isFinite(expected));
        return expected;
    }

    private static void assertMatchesReferences(Model model, double priors) {
        double expected = priors;
        for (BirthDeathGernhard08Model law : model.laws()) {
            double treeDensity = nativeLogP(law);
            assertEquals(treeDensity, law.getCurrentLogP(), TOLERANCE);
            expected += treeDensity;
        }
        assertEquals(expected, model.posterior().getCurrentLogP(), TOLERANCE);
    }

    private static double[] cachedValues(Model model) {
        double[] values = new double[model.laws().size() + 1];
        for (int i = 0; i < model.laws().size(); i++)
            values[i] = model.laws().get(i).getCurrentLogP();
        values[values.length - 1] = model.posterior().getCurrentLogP();
        return values;
    }

    private static Model state(BEASTState graph, List<BirthDeathGernhard08Model> laws) {
        List<Distribution> terms = new ArrayList<>(graph.priorDistributions.values());
        terms.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", terms);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        for (BirthDeathGernhard08Model law : laws) {
            if (!graph.stateNodes.containsKey(law.treeInput.get()))
                assertInstanceOf(Tree.class, law.treeInput.get()).setEverythingDirty(false);
        }
        return new Model(state, posterior, laws);
    }

    private static RealScalarParam<?> parameter(BEASTState graph, String id) {
        return assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(parameter -> id.equals(parameter.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static BirthDeathGernhard08Model law(BEASTState graph, String id) {
        return assertInstanceOf(
                BirthDeathGernhard08Model.class,
                graph.likelihoodDistributions.stream()
                        .filter(distribution -> id.equals(distribution.getID()))
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("birth-death-rates"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }

    private record Model(State state, CompoundDistribution posterior, List<BirthDeathGernhard08Model> laws) {}
}
