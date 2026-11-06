package tiles.trees;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.operator.Exchange;
import beast.base.evolution.tree.IntervalType;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.tree.TreeParser;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
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
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.PiecewiseConstantPopulation;
import tiling.RealVectorFromScalars;
import tiling.SkylineCoalescentModel;

class SkylineCoalescentTileTest {
    private static final double TOLERANCE = 1e-11;
    private static final double[] TIMES = {0.5, 1.5};
    private static final String TAXA =
            "Taxa samples = [taxon(name=\"a\"),taxon(name=\"b\"),taxon(name=\"c\"),taxon(name=\"d\")]\n";
    private static final String NEWICK = "((a:1.0,b:1.0):1.0,(c:1.5,d:1.5):0.5);";
    private static final String OBSERVED = "Tree fixed = fromNewick(newickString=\"" + NEWICK + "\")\n";

    @ParameterizedTest
    @ValueSource(strings = {"[2.0,4.0,8.0]", "[2,4,8]"})
    void fixedEpochsMatchIndependentTreeDensityAndUseObservedTree(String populations) throws Exception {
        BEASTState graph = build(TAXA + OBSERVED + "Tree tree ~ SkylineCoalescent(populationSizes=" + populations
                + ",changeTimes=[0.5,1.5],taxa=samples) observed as fixed\n");
        Coalescent law = law(graph, "tree_likelihood");
        assertInstanceOf(SkylineCoalescentModel.class, law);
        assertNull(law.treeInput.get());
        TreeIntervals intervals = law.treeIntervalsInput.get();
        assertNotNull(intervals);
        assertEquals(2.0, intervals.treeInput.get().getRoot().getHeight(), TOLERANCE);
        assertEquals(
                List.of("a", "b", "c", "d"),
                intervals.treeInput.get().getExternalNodes().stream()
                        .map(Node::getID)
                        .sorted()
                        .toList());
        assertTrue(intervals.treeInput.get().getOutputs().contains(intervals));
        assertTrue(intervals.getOutputs().contains(law));
        assertEquals(referenceLogP(law, new double[] {2, 4, 8}, TIMES), law.calculateLogP(), TOLERANCE);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    @Test
    void equalEpochSizesMatchExistingConstantCoalescent() throws Exception {
        BEASTState graph = build(TAXA + OBSERVED + """
                Tree skyline ~ SkylineCoalescent(populationSizes=[4.0,4.0,4.0],changeTimes=[0.5,1.5],taxa=samples) observed as fixed
                Tree constant ~ Coalescent(populationSize=4.0,taxa=samples) observed as fixed
                """);
        assertEquals(
                law(graph, "constant_likelihood").calculateLogP(),
                law(graph, "skyline_likelihood").calculateLogP(),
                TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({"vector,true", "vector,false", "scalars,true", "scalars,false"})
    void sharedPopulationUpdatesInvalidateLikelihoodsAndRestorePosterior(String family, boolean acceptFirst)
            throws Exception {
        BEASTState graph = sampledPopulations(family, true);
        List<Coalescent> laws = List.of(law(graph, "first_likelihood"), law(graph, "second_likelihood"));
        assertEquals(family.equals("vector") ? 1 : 3, graph.stateNodes.size());
        assertEquals(graph.stateNodes.size(), graph.priorDistributions.size());
        assertEquals(graph.stateNodes.size(), graph.operators.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        for (var operator : graph.operators) {
            assertEquals(5.0, operator.m_pWeight.get());
            assertTrue(operator.listStateNodes().stream().allMatch(graph.stateNodes::containsKey));
        }
        PiecewiseConstantPopulation firstPopulation = population(laws.getFirst());
        PiecewiseConstantPopulation secondPopulation = population(laws.getLast());
        if (family.equals("vector")) {
            var values = vector(graph);
            assertSame(values, firstPopulation.populationSizesInput.get());
            assertSame(values, secondPopulation.populationSizesInput.get());
            assertEquals(List.of("sizes"), laws.getFirst().getConditions());
            IID<?, ?, ?> prior = assertInstanceOf(IID.class, graph.priorDistributions.get(values));
            assertSame(values, prior.paramInput.get());
            LogNormal base = assertInstanceOf(LogNormal.class, prior.distInput.get());
            assertEquals(0.0, base.MParameterInput.get().get());
            assertEquals(1.0, base.SParameterInput.get().get());
        } else {
            for (Coalescent law : laws) {
                var values = assertInstanceOf(
                        RealVectorFromScalars.class,
                        population(law).populationSizesInput.get());
                assertSame(scalar(graph, "recent"), values.valuesInput.get().getFirst());
                assertSame(scalar(graph, "old"), values.valuesInput.get().getLast());
                assertEquals(List.of("recent", "middle", "old"), law.getConditions());
            }
            for (var prior : graph.priorDistributions.values()) {
                LogNormal logNormal = assertInstanceOf(LogNormal.class, prior);
                assertEquals(0.0, logNormal.MParameterInput.get().get());
                assertEquals(1.0, logNormal.SParameterInput.get().get());
            }
        }
        Model model = state(graph, laws);
        assertMatchesReference(model, populationValues(laws.getFirst()), true);
        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        double[] proposals = {2.0, 3.0, 1.5};
        for (int step = 0; step < decisions.length; step++) {
            double[] previousValues = populationValues(laws.getFirst());
            double[] previous = cached(model);
            model.state().store(step + 1);
            setPopulation(graph, family, 0, proposals[step]);
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            for (Coalescent law : laws) {
                assertFalse(law.treeIntervalsInput.get().treeInput.get().somethingIsDirty());
                assertTrue(law.somethingIsDirty(), "Population-only changes must invalidate observed-tree density.");
            }
            model.posterior().calculateLogP();
            double[] proposed = cached(model);
            assertNotEquals(previous[0], proposed[0], TOLERANCE);
            assertNotEquals(previous[1], proposed[1], TOLERANCE);
            assertMatchesReference(model, populationValues(laws.getFirst()), true);
            finish(model, decisions[step]);
            assertEquals(decisions[step] ? proposals[step] : previousValues[0], populationValues(laws.getFirst())[0]);
            assertArrayEquals(decisions[step] ? proposed : previous, cached(model), TOLERANCE);
            assertMatchesReference(model, populationValues(laws.getFirst()), true);
            assertEquals(model.posterior().getCurrentLogP(), model.posterior().calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @CsvSource({"height,true", "height,false", "topology,true", "topology,false"})
    void sampledTreeChangesRestoreIntervalsAndRetainExistingOperators(String change, boolean acceptFirst)
            throws Exception {
        BEASTState graph = build(TAXA
                + "Tree tree ~ SkylineCoalescent(populationSizes=[2.0,4.0,8.0],changeTimes=[0.5,1.5],taxa=samples)\n");
        BEASTState constant = build(TAXA + "Tree tree ~ Coalescent(populationSize=2.0,taxa=samples)\n");
        assertEquals(
                constant.operators.stream().map(operator -> operator.getClass()).toList(),
                graph.operators.stream().map(operator -> operator.getClass()).toList());
        assertEquals(
                constant.operators.stream()
                        .map(operator -> operator.m_pWeight.get())
                        .toList(),
                graph.operators.stream()
                        .map(operator -> operator.m_pWeight.get())
                        .toList());
        assertEquals(1, graph.stateNodes.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        Tree tree = assertInstanceOf(
                Tree.class, graph.stateNodes.keySet().iterator().next());
        Tree fixed = new TreeParser(NEWICK);
        fixed.setID(tree.getID());
        tree.assignFrom(fixed);
        Coalescent law = assertInstanceOf(Coalescent.class, graph.priorDistributions.get(tree));
        assertSame(tree, law.treeIntervalsInput.get().treeInput.get());
        Model model = state(graph, List.of(law));
        for (int step = 0; step < 3; step++) {
            boolean accept = step == 1 ? !acceptFirst : acceptFirst;
            String previousTree = tree.getRoot().toNewick();
            double[] previous = cached(model);
            model.state().store(step + 1);
            if (change.equals("height")) tree.getRoot().setHeight(tree.getRoot().getHeight() + 0.2);
            else exchangeTips(tree, "a", "c");
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            model.posterior().calculateLogP();
            double[] proposed = cached(model);
            String proposedTree = tree.getRoot().toNewick();
            assertNotEquals(previousTree, proposedTree);
            if (change.equals("height")) assertNotEquals(previous[0], proposed[0], TOLERANCE);
            assertMatchesReference(model, new double[] {2, 4, 8}, false);
            finish(model, accept);
            assertEquals(accept ? proposedTree : previousTree, tree.getRoot().toNewick());
            assertArrayEquals(accept ? proposed : previous, cached(model), TOLERANCE);
            assertMatchesReference(model, new double[] {2, 4, 8}, false);
            assertEquals(model.posterior().getCurrentLogP(), model.posterior().calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1000.0, 1000.0})
    void invalidDerivedPopulationProposalsHaveZeroDensityAndRecoverAfterRejection(double proposed) throws Exception {
        // Native PositiveReal parameters reject zero/negative/NaN at assignment. A live
        // exp value can instead underflow/overflow after an otherwise valid Real proposal.
        BEASTState graph = build(TAXA + OBSERVED + """
                Real logSize ~ Normal(mean=0.0,sd=1.0)
                PositiveReal recent = exp(x=logSize)
                Vector<PositiveReal> sizes = [recent,recent,recent]
                Tree tree ~ SkylineCoalescent(populationSizes=sizes,changeTimes=[0.5,1.5],taxa=samples) observed as fixed
                """);
        Coalescent law = law(graph, "tree_likelihood");
        Model model = state(graph, List.of(law));
        RealScalarParam<?> source = scalar(graph, "logSize");
        double[] previous = cached(model);
        double previousValue = source.get();
        double initialExpected = referenceLogP(law, populationValues(law), TIMES)
                - 0.5 * Math.log(2.0 * Math.PI)
                - 0.5 * previousValue * previousValue;
        assertEquals(initialExpected, model.posterior().getCurrentLogP(), TOLERANCE);
        model.state().store(1);
        source.set(proposed);
        model.state().storeCalculationNodes();
        model.state().checkCalculationNodesDirtiness();
        assertEquals(proposed < 0.0 ? 0.0 : Double.POSITIVE_INFINITY, populationValues(law)[0]);
        assertEquals(Double.NEGATIVE_INFINITY, model.posterior().calculateLogP());
        assertEquals(Double.NEGATIVE_INFINITY, law.getCurrentLogP());
        finish(model, false);
        assertEquals(previousValue, source.get());
        assertArrayEquals(previous, cached(model), TOLERANCE);
        assertEquals(referenceLogP(law, populationValues(law), TIMES), law.calculateLogP(), TOLERANCE);
        assertEquals(initialExpected, model.posterior().calculateLogP(), TOLERANCE);
    }

    @Test
    void datedSamplesRetainTheirHeights() throws Exception {
        BEASTState graph = build("""
                Taxa samples = [taxon(name="a",age=0.0),taxon(name="b",age=0.25),taxon(name="c",age=0.5)]
                Tree tree ~ SkylineCoalescent(populationSizes=[2.0,4.0,8.0],changeTimes=[0.5,1.5],taxa=samples)
                """);
        Tree tree = assertInstanceOf(
                Tree.class, graph.stateNodes.keySet().iterator().next());
        for (Node tip : tree.getExternalNodes()) {
            double expected =
                    switch (tip.getID()) {
                        case "a" -> 0.0;
                        case "b" -> 0.25;
                        case "c" -> 0.5;
                        default -> throw new AssertionError(tip.getID());
                    };
            assertEquals(expected, tip.getHeight(), TOLERANCE);
        }
        Coalescent law = assertInstanceOf(Coalescent.class, graph.priorDistributions.get(tree));
        // The runner initializes the posterior after StateNodeInitialisers finish constructing
        // the final tree. Follow that lifecycle so intervals are refreshed for the final state.
        state(graph, List.of(law));
        assertEquals(referenceLogP(law, new double[] {2, 4, 8}, TIMES), law.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[0.5]", "[1.5,0.5]", "[0.5,0.5]"})
    void invalidFixedEpochSchedulesAreRejected(String times) {
        assertThrows(
                TileApplicationError.class,
                () -> build(TAXA
                        + "Tree tree ~ SkylineCoalescent(populationSizes=[2.0,4.0,8.0],changeTimes=" + times
                        + ",taxa=samples)\n"));
    }

    @Test
    void groupedConfigurationIsNotSilentlyIgnored() {
        assertThrows(
                TileApplicationError.class,
                () -> build(
                        TAXA
                                + "Tree tree ~ SkylineCoalescent(populationSizes=[2.0,4.0,8.0],changeTimes=[0.5,1.5],groupSizes=[1,1,1],taxa=samples)\n"));
    }

    private static BEASTState sampledPopulations(String family, boolean shared) throws Exception {
        String declaration = family.equals("vector")
                ? "Vector<PositiveReal> sizes ~ IID(base=LogNormal(logMean=0.0,logSd=1.0),num=3)\n"
                : """
                        PositiveReal recent ~ LogNormal(logMean=0.0,logSd=1.0)
                        PositiveReal middle ~ LogNormal(logMean=0.0,logSd=1.0)
                        PositiveReal old ~ LogNormal(logMean=0.0,logSd=1.0)
                        Vector<PositiveReal> sizes = [recent,middle,old]
                        """;
        return build(TAXA + OBSERVED + declaration
                + "Tree first ~ SkylineCoalescent(populationSizes=sizes,changeTimes=[0.5,1.5],taxa=samples) observed as fixed\n"
                + (shared
                        ? "Tree other = fromNewick(newickString=\"((a:0.75,b:0.75):1.5,(c:1.0,d:1.0):1.25);\")\n"
                                + "Tree second ~ SkylineCoalescent(populationSizes=sizes,changeTimes=[0.5,1.5],taxa=samples) observed as other\n"
                        : ""));
    }

    private static void setPopulation(BEASTState graph, String family, int index, double value) {
        if (family.equals("vector")) vector(graph).set(index, value);
        else scalar(graph, new String[] {"recent", "middle", "old"}[index]).set(value);
    }

    private static PiecewiseConstantPopulation population(Coalescent law) {
        return assertInstanceOf(PiecewiseConstantPopulation.class, law.popSizeInput.get());
    }

    private static double[] populationValues(Coalescent law) {
        return population(law).populationSizesInput.get().getElements().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
    }

    private static RealVectorParam<?> vector(BEASTState graph) {
        return assertInstanceOf(
                RealVectorParam.class, graph.stateNodes.keySet().iterator().next());
    }

    private static RealScalarParam<?> scalar(BEASTState graph, String name) {
        return assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(value -> name.equals(value.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static Coalescent law(BEASTState graph, String id) {
        return assertInstanceOf(
                Coalescent.class,
                graph.likelihoodDistributions.stream()
                        .filter(value -> id.equals(value.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static double referenceLogP(Coalescent law, double[] sizes, double[] times) {
        TreeIntervals intervals = new TreeIntervals();
        intervals.initByName(
                "tree",
                new Tree(law.treeIntervalsInput.get().treeInput.get().getRoot().copy()));
        double result = 0.0;
        double start = 0.0;
        for (int i = 0; i < intervals.getIntervalCount(); i++) {
            double finish = start + intervals.getInterval(i);
            double area = 0.0;
            for (int epoch = 0; epoch < sizes.length; epoch++) {
                double left = Math.max(start, epoch == 0 ? 0.0 : times[epoch - 1]);
                double right = Math.min(finish, epoch == times.length ? Double.POSITIVE_INFINITY : times[epoch]);
                area += Math.max(0.0, right - left) / sizes[epoch];
            }
            int lineages = intervals.getLineageCount(i);
            result -= lineages * (lineages - 1) / 2.0 * area;
            if (intervals.getIntervalType(i) == IntervalType.COALESCENT) {
                int epoch = 0;
                while (epoch < times.length && finish >= times[epoch]) epoch++;
                result -= Math.log(sizes[epoch]);
            }
            start = finish;
        }
        assertTrue(Double.isFinite(result));
        return result;
    }

    private static void assertMatchesReference(Model model, double[] sizes, boolean sampledSizes) {
        double result = 0.0;
        for (Coalescent law : model.laws()) {
            double expected = referenceLogP(law, sizes, TIMES);
            assertEquals(expected, law.getCurrentLogP(), TOLERANCE);
            result += expected;
        }
        if (sampledSizes)
            for (double size : sizes) {
                double log = Math.log(size);
                result += -0.5 * Math.log(2.0 * Math.PI) - 0.5 * log * log - log;
            }
        assertEquals(result, model.posterior().getCurrentLogP(), TOLERANCE);
    }

    private static Model state(BEASTState graph, List<Coalescent> laws) {
        List<Distribution> terms = new ArrayList<>(graph.priorDistributions.values());
        terms.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", terms);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        for (Coalescent law : laws) {
            Tree tree = law.treeIntervalsInput.get().treeInput.get();
            if (!graph.stateNodes.containsKey(tree)) tree.setEverythingDirty(false);
        }
        return new Model(state, posterior, laws);
    }

    private static void finish(Model model, boolean accept) {
        if (accept) model.state().acceptCalculationNodes();
        else {
            model.state().restore();
            model.state().restoreCalculationNodes();
        }
        model.state().setEverythingDirty(false);
    }

    private static double[] cached(Model model) {
        double[] result = new double[model.laws().size() + 1];
        for (int i = 0; i < model.laws().size(); i++)
            result[i] = model.laws().get(i).getCurrentLogP();
        result[result.length - 1] = model.posterior().getCurrentLogP();
        return result;
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("skyline-coalescent"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }

    private record Model(State state, CompoundDistribution posterior, List<Coalescent> laws) {}
}
