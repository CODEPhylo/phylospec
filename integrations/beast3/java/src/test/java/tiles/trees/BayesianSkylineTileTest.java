package tiles.trees;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.inference.parameter.RealParameter;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.BayesianSkyline;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.parameter.IntSimplexParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import tiles.distributions.UniformComposition;

class BayesianSkylineTileTest {
    @ParameterizedTest
    @CsvSource({"tree,false", "tree,true", "population,false", "population,true", "groups,false", "groups,true"})
    void matchesNativePriorAcrossProposals(String target, boolean accept) throws Exception {
        BEASTState graph = build(source());
        for (int i = 0; i < 4; i++) {
            ((RealScalarParam<?>) named(graph, "theta" + i)).set(0.2 * (i + 1));
        }
        assertEquals(6, graph.stateNodes.size(), "Four populations, one group vector and one tree.");
        assertEquals(6, graph.priorDistributions.size(), "Each sampled variable owns its prior exactly once.");
        assertTrue(graph.likelihoodDistributions.isEmpty());
        Tree tree = (Tree) named(graph, "tree");
        BayesianSkyline skyline = (BayesianSkyline) graph.priorDistributions.get(tree);
        assertSame(tree, skyline.treeIntervalsInput.get().treeInput.get());
        assertNull(skyline.treeInput.get());
        assertTrue(tree.getOutputs().contains(skyline.treeIntervalsInput.get()));
        assertTrue(skyline.treeIntervalsInput.get().getOutputs().contains(skyline));
        var groups = (IntSimplexParam<?>) named(graph, "groups");
        assertSame(groups, skyline.groupSizeParamInput.get());
        var groupOperators = graph.operators.stream()
                .filter(o -> o.listStateNodes().contains(groups))
                .toList();
        assertEquals(1, groupOperators.size());
        DeltaExchangeOperator exchange = assertInstanceOf(DeltaExchangeOperator.class, groupOperators.getFirst());
        assertTrue(exchange.integerOperatorInput.get());
        assertEquals(2.0, exchange.deltaInput.get());

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", new ArrayList<>(graph.priorDistributions.values()));
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        double initial = state.robustlyCalcPosterior(posterior);
        assertReference(graph, posterior);
        state.store(1);
        change(graph, target);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        double proposed = posterior.calculateLogP();
        assertNotEquals(initial, proposed, 1e-10);
        assertReference(graph, posterior);
        if (accept) state.acceptCalculationNodes();
        else {
            state.restore();
            state.restoreCalculationNodes();
        }
        state.setEverythingDirty(false);
        assertEquals(accept ? proposed : initial, posterior.getCurrentLogP(), 1e-10);
        assertReference(graph, posterior);
        state.store(2);
        change(graph, target);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        posterior.calculateLogP();
        assertReference(graph, posterior);
    }

    @ParameterizedTest
    @ValueSource(strings = {"total=10,num=4", "total=11,num=3", "total=11,num=12"})
    void rejectsInvalidGrouping(String arguments) {
        Exception error = assertThrows(Exception.class, () -> build(source().replace("total=11,num=4", arguments)));
        String message = error.getMessage();
        assertNotNull(message);
        if (arguments.startsWith("total=10")) {
            assertTrue(message.contains("Group sizes must sum to taxa.num - 1"), message);
        } else if (arguments.endsWith("num=3")) {
            assertTrue(message.contains("populationSizes") || message.contains("same positive dimension"), message);
        } else {
            assertTrue(message.contains("num") && message.contains("total"), message);
        }
    }

    @Test
    void runsConditionalPopulationAndGroupUpdates(@TempDir Path directory) throws Exception {
        Path script = directory.resolve("skyline.phylospec");
        String prefix = directory.resolve("skyline").toString();
        Files.writeString(script, source() + """
                mcmc {
                    Integer chainLength = 100
                    Integer defaultLogEvery = 1
                    Integer randomSeed = 34891
                    String outputPrefix = "%s"
                }
                """.formatted(prefix));
        new PhyloSpecRunner(script, List.of("beast2")).runPhyloSpec(prefix);
        var trace = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(102, trace.size());
        for (String line : trace.subList(1, trace.size())) {
            for (String value : line.trim().split("\\s+")) assertTrue(Double.isFinite(Double.parseDouble(value)), line);
        }
        assertTrue(Files.size(Path.of(prefix + ".trees")) > 0);
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    @SuppressWarnings("unchecked")
    private static void change(BEASTState graph, String target) {
        switch (target) {
            case "tree" -> {
                Tree tree = (Tree) named(graph, "tree");
                tree.getRoot().setHeight(tree.getRoot().getHeight() + 0.4);
            }
            case "population" -> {
                var population = (RealScalarParam<PositiveReal>) named(graph, "theta0");
                population.set(population.get() * 1.4);
            }
            case "groups" -> {
                var groups = (IntSimplexParam<PositiveInt>) named(graph, "groups");
                groups.set(0, groups.get(0) - 1);
                groups.set(1, groups.get(1) + 1);
            }
            default -> throw new AssertionError(target);
        }
    }

    private static void assertReference(BEASTState graph, CompoundDistribution posterior) {
        Tree tree = (Tree) named(graph, "tree");
        BayesianSkyline actual = (BayesianSkyline) graph.priorDistributions.get(tree);
        Double[] values = new Double[4];
        for (int i = 0; i < values.length; i++) values[i] = ((RealScalarParam<?>) named(graph, "theta" + i)).get();
        Integer[] groups = new Integer[4];
        for (int i = 0; i < groups.length; i++)
            groups[i] = actual.groupSizeParamInput.get().get(i);
        var nativeSkyline = new beast.base.evolution.tree.coalescent.BayesianSkyline();
        var intervals = new TreeIntervals();
        intervals.initByName("tree", new Tree(tree.getRoot().copy()));
        nativeSkyline.initByName(
                "popSizes",
                new RealParameter(values),
                "groupSizes",
                new IntegerParameter(groups),
                "treeIntervals",
                intervals);
        double treePrior = nativeSkyline.calculateLogP();
        assertTrue(Double.isFinite(treePrior));
        assertEquals(treePrior, actual.getCurrentLogP(), 1e-10);
        var markov = new beast.base.inference.distribution.MarkovChainDistribution();
        markov.initByName("parameter", new RealParameter(values));
        var first = new beast.base.inference.distribution.LogNormalDistributionModel();
        first.initByName("M", "-2.3", "S", "1.8");
        double populationPrior = first.logDensity(values[0]) + markov.calculateLogP();
        double actualPopulationPrior = 0;
        for (int i = 0; i < values.length; i++)
            actualPopulationPrior +=
                    graph.priorDistributions.get(named(graph, "theta" + i)).getCurrentLogP();
        assertEquals(
                populationPrior,
                actualPopulationPrior,
                1e-10,
                "Separate conditional exponentials must reproduce the original Markov-chain factor.");
        // Native XML has no group prior: normalized uniform adds only a fixed constant.
        double groupPrior = -Math.log(120); // choose(11-1,4-1)
        assertInstanceOf(UniformComposition.class, graph.priorDistributions.get(named(graph, "groups")));
        assertEquals(
                groupPrior, graph.priorDistributions.get(named(graph, "groups")).getCurrentLogP(), 1e-12);
        assertEquals(treePrior + populationPrior + groupPrior, posterior.getCurrentLogP(), 1e-9);
    }

    private static StateNode named(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static String source() {
        return """
                Alignment data = fromNexus(file="%s")
                PositiveReal theta0 ~ LogNormal(logMean=-2.3,logSd=1.8)
                PositiveReal theta1 ~ Exponential(mean=theta0)
                PositiveReal theta2 ~ Exponential(mean=theta1)
                PositiveReal theta3 ~ Exponential(mean=theta2)
                Vector<PositiveReal> populations = [theta0,theta1,theta2,theta3]
                Vector<PositiveInteger> groups ~ UniformComposition(total=11,num=4)
                Tree tree ~ BayesianSkyline(populationSizes=populations,groupSizes=groups,taxa=taxa(data))
                """.formatted(
                        Path.of("src/test/java/resources/primate-mtDNA.nex").toAbsolutePath());
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
        var graph = evaluator.applyBestTiling(new BEASTState("skyline"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
