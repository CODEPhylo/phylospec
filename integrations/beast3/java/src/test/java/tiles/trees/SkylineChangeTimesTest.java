package tiles.trees;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.IntervalType;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealVector;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.List;
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
import tiling.PiecewiseConstantPopulation;
import tiling.RealVectorFromScalars;

class SkylineChangeTimesTest {
    private static final double TOLERANCE = 1e-11;
    private static final double[] LIST_TIME_LOG_MEANS = {Math.log(0.5), Math.log(1.5)};
    private static final String TREES = """
            Taxa samples = [taxon(name="a"),taxon(name="b"),taxon(name="c"),taxon(name="d")]
            Tree firstTree = fromNewick(newickString="((a:1.0,b:1.0):1.0,(c:1.5,d:1.5):0.5);")
            Tree secondTree = fromNewick(newickString="((a:0.75,b:0.75):1.5,(c:1.0,d:1.0):1.25);")
            """;

    @ParameterizedTest
    @CsvSource({
        "vector,vector,true", "vector,vector,false", "vector,list,true", "vector,list,false",
        "list,vector,true", "list,vector,false", "list,list,true", "list,list,false"
    })
    void sharedTimeUpdatesRecalculateBothTreesAndRestoreTheFullPosterior(
            String populationFamily, String timeFamily, boolean acceptFirst) throws Exception {
        Model model = model(populationFamily, timeFamily);
        BEASTState graph = model.graph();
        int populations = timeFamily.equals("vector") ? 2 : 3;
        int expectedStateNodes =
                (populationFamily.equals("vector") ? 1 : populations) + (timeFamily.equals("vector") ? 1 : 2);
        assertEquals(expectedStateNodes, graph.stateNodes.size());
        assertEquals(expectedStateNodes, graph.priorDistributions.size());
        assertEquals(expectedStateNodes, graph.operators.size());
        for (var operator : graph.operators) {
            assertEquals(5.0, operator.m_pWeight.get());
            assertTrue(operator.listStateNodes().stream().allMatch(graph.stateNodes::containsKey));
        }
        for (Coalescent law : model.laws()) {
            PiecewiseConstantPopulation population = population(law);
            if (timeFamily.equals("vector")) {
                assertSame(node(graph, "times"), population.changeTimesInput.get());
                assertTrue(law.getConditions().contains("times"));
            } else {
                RealVectorFromScalars times =
                        assertInstanceOf(RealVectorFromScalars.class, population.changeTimesInput.get());
                assertSame(node(graph, "t0"), times.valuesInput.get().get(0));
                assertSame(node(graph, "t1"), times.valuesInput.get().get(1));
                assertTrue(law.getConditions().containsAll(List.of("t0", "t1")));
            }
        }
        assertReference(model, timeFamily);
        // The second proposal crosses an observed coalescent event, changing which
        // population size contributes to the event density as well as the integral.
        double[] proposals = timeFamily.equals("vector") ? new double[] {0.8, 1.6, 1.1} : new double[] {1.3, 1.7, 1.4};
        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        int index = timeFamily.equals("vector") ? 0 : 1;
        for (int step = 0; step < proposals.length; step++) {
            double[] previousTimes = times(model.laws().getFirst());
            double[] previous = cached(model);
            model.state().store(step + 1);
            setTime(graph, timeFamily, index, proposals[step]);
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            for (Coalescent law : model.laws()) {
                assertFalse(law.treeIntervalsInput.get().treeInput.get().somethingIsDirty());
                assertTrue(law.somethingIsDirty(), "A time-only proposal must invalidate the tree density.");
            }
            model.posterior().calculateLogP();
            double[] proposed = cached(model);
            assertNotEquals(previous[0], proposed[0], TOLERANCE);
            assertNotEquals(previous[1], proposed[1], TOLERANCE);
            assertReference(model, timeFamily);
            finish(model, decisions[step]);
            assertEquals(
                    decisions[step] ? proposals[step] : previousTimes[index],
                    times(model.laws().getFirst())[index]);
            assertArrayEquals(decisions[step] ? proposed : previous, cached(model), TOLERANCE);
            assertReference(model, timeFamily);
            assertEquals(model.posterior().getCurrentLogP(), model.posterior().calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.5, 1.75})
    void equalOrReversedTimesAreRejectedWithoutChangingTheirPriorsAndRecover(double invalidFirstTime) throws Exception {
        Model model = model("vector", "list");
        RealScalarParam<?> first = assertInstanceOf(RealScalarParam.class, node(model.graph(), "t0"));
        Distribution prior = model.graph().priorDistributions.get(first);
        double[] previous = cached(model);
        double original = first.get();
        model.state().store(1);
        first.set(invalidFirstTime);
        model.state().storeCalculationNodes();
        model.state().checkCalculationNodesDirtiness();
        assertTrue(Double.isFinite(prior.calculateLogP()), "The original prior allows this positive proposal.");
        for (Coalescent law : model.laws()) {
            assertFalse(population(law).hasValidChangeTimes());
            assertEquals(Double.NEGATIVE_INFINITY, law.calculateLogP());
        }
        assertEquals(Double.NEGATIVE_INFINITY, model.posterior().calculateLogP());
        finish(model, false);
        assertEquals(original, first.get());
        assertArrayEquals(previous, cached(model), TOLERANCE);
        assertReference(model, "list");

        model.state().store(2);
        first.set(0.75);
        model.state().storeCalculationNodes();
        model.state().checkCalculationNodesDirtiness();
        assertTrue(Double.isFinite(model.posterior().calculateLogP()));
        finish(model, true);
        assertEquals(0.75, first.get());
        assertReference(model, "list");
    }

    private static Model model(String populationFamily, String timeFamily) throws Exception {
        int count = timeFamily.equals("vector") ? 2 : 3;
        String populations;
        if (populationFamily.equals("vector")) {
            populations = "Vector<PositiveReal> sizes ~ IID(base=LogNormal(logMean=0.0,logSd=1.0),num=" + count + ")\n";
        } else {
            StringBuilder declarations = new StringBuilder();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String name = "n" + i;
                names.add(name);
                declarations.append("PositiveReal ").append(name).append(" ~ LogNormal(logMean=0.0,logSd=1.0)\n");
            }
            declarations
                    .append("Vector<PositiveReal> sizes = [")
                    .append(String.join(",", names))
                    .append("]\n");
            populations = declarations.toString();
        }
        String times = timeFamily.equals("vector")
                ? "Vector<PositiveReal> times ~ IID(base=LogNormal(logMean=0.0,logSd=1.0),num=1)\n"
                : """
                        PositiveReal t0 ~ LogNormal(logMean=-0.6931471805599453,logSd=1.0)
                        PositiveReal t1 ~ LogNormal(logMean=0.4054651081081644,logSd=1.0)
                        Vector<PositiveReal> times = [t0,t1]
                        """;
        BEASTState graph = build(TREES + populations + times + """
                Tree first ~ SkylineCoalescent(populationSizes=sizes,changeTimes=times,taxa=samples) observed as firstTree
                Tree second ~ SkylineCoalescent(populationSizes=sizes,changeTimes=times,taxa=samples) observed as secondTree
                """);
        // Choose a nonconstant population state for the numerical comparison; do not
        // change the generator's initialization or its prior/operator configuration.
        for (int i = 0; i < count; i++) {
            if (populationFamily.equals("vector")) {
                assertInstanceOf(RealVectorParam.class, node(graph, "sizes")).set(i, Math.pow(2, i + 1));
            } else {
                assertInstanceOf(RealScalarParam.class, node(graph, "n" + i)).set(Math.pow(2, i + 1));
            }
        }
        List<Coalescent> laws = graph.likelihoodDistributions.stream()
                .map(distribution -> assertInstanceOf(Coalescent.class, distribution))
                .toList();
        assertEquals(2, laws.size());
        List<Distribution> terms = new ArrayList<>(graph.priorDistributions.values());
        terms.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", terms);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        for (Coalescent law : laws) law.treeIntervalsInput.get().treeInput.get().setEverythingDirty(false);
        return new Model(graph, state, posterior, laws);
    }

    private static void assertReference(Model model, String timeFamily) {
        double[] times = times(model.laws().getFirst());
        double[] sizes =
                values(population(model.laws().getFirst()).populationSizesInput.get());
        double expected = 0.0;
        for (Coalescent law : model.laws()) {
            double density = referenceLogP(law, sizes, times);
            assertEquals(density, law.getCurrentLogP(), TOLERANCE);
            expected += density;
        }
        for (double size : sizes) expected += logNormal(size, 0.0);
        for (int i = 0; i < times.length; i++) {
            expected += logNormal(times[i], timeFamily.equals("vector") ? 0.0 : LIST_TIME_LOG_MEANS[i]);
        }
        assertEquals(expected, model.posterior().getCurrentLogP(), TOLERANCE);
    }

    private static double logNormal(double value, double logMean) {
        double delta = Math.log(value) - logMean;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(value) - 0.5 * delta * delta;
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
        return result;
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(value -> id.equals(value.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static void setTime(BEASTState graph, String family, int index, double value) {
        if (family.equals("vector"))
            assertInstanceOf(RealVectorParam.class, node(graph, "times")).set(index, value);
        else assertInstanceOf(RealScalarParam.class, node(graph, "t" + index)).set(value);
    }

    private static PiecewiseConstantPopulation population(Coalescent law) {
        return assertInstanceOf(PiecewiseConstantPopulation.class, law.popSizeInput.get());
    }

    private static double[] values(RealVector<?> values) {
        return values.getElements().stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double[] times(Coalescent law) {
        return values(population(law).changeTimesInput.get());
    }

    private static double[] cached(Model model) {
        double[] values = new double[model.laws().size() + 1];
        for (int i = 0; i < model.laws().size(); i++)
            values[i] = model.laws().get(i).getCurrentLogP();
        values[values.length - 1] = model.posterior().getCurrentLogP();
        return values;
    }

    private static void finish(Model model, boolean accept) {
        if (accept) model.state().acceptCalculationNodes();
        else {
            model.state().restore();
            model.state().restoreCalculationNodes();
        }
        model.state().setEverythingDirty(false);
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("skyline-change-times"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }

    private record Model(BEASTState graph, State state, CompoundDistribution posterior, List<Coalescent> laws) {}
}
