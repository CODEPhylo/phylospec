package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import operators.popfunc.ModelIndicatorOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;
import popfunc.beast.evolution.populationmodel.StochasticVariableSelection;

/** Checks the native example's I_na semantics using existing AGT-generated population objects. */
class AncestralSelectionTest {
    private static final Path EXAMPLE = Path.of("examples/ancestral-selection.phylospec").toAbsolutePath();
    private static final double TOLERANCE = 1e-9;

    @Test
    void sharesParametersAndCountsEveryPriorOnce() throws Exception {
        BEASTState graph = build();
        var selection = selection(graph);
        var without = (GompertzGrowth_f0) selection.modelsInput.get().get(0);
        var with = (GompertzGrowth_f0) selection.modelsInput.get().get(1);
        assertSame(without.f0Input.get(), with.f0Input.get());
        assertSame(without.bInput.get(), with.bInput.get());
        assertSame(without.N0Input.get(), with.N0Input.get());
        assertNull(without.NAInput.get());
        assertSame(parameter(graph, "NA"), with.NAInput.get());
        assertEquals(0, without.indicatorParameterInput.get().get());
        assertEquals(1, with.indicatorParameterInput.get().get());
        assertEquals(Set.of("f0", "b", "N0", "NA", "I_na", "tree"), graph.stateNodes.keySet().stream()
                .map(node -> node.getID()).collect(Collectors.toSet()));
        assertEquals(graph.stateNodes.keySet(), graph.priorDistributions.keySet());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertTrue(graph.priorDistributions.containsKey(parameter(graph, "NA")),
                "The inactive ancestral parameter retains its original prior.");

        var indicator = (IntScalarParam<?>) selection.indicatorInput.get();
        var prior = assertInstanceOf(IntUniform.class, graph.priorDistributions.get(indicator));
        assertEquals(0, prior.getLowerBoundOfParameter());
        assertEquals(1, prior.getUpperBoundOfParameter());
        for (int value : new int[]{0, 1}) {
            indicator.set(value);
            assertEquals(-Math.log(2), prior.calculateLogP(), TOLERANCE);
        }
        var operators = graph.operators.stream().filter(ModelIndicatorOperator.class::isInstance)
                .map(ModelIndicatorOperator.class::cast).toList();
        assertEquals(1, operators.size());
        assertSame(indicator, operators.getFirst().indicatorInput.get());
    }

    @ParameterizedTest
    @CsvSource({"0,true", "0,false", "1,true", "1,false"})
    void switchingAndRestoringMatchTheNativeIndicator(int initial, boolean accept) throws Exception {
        BEASTState graph = build();
        Coalescent coalescent = coalescent(graph);
        var selection = selection(graph);
        var indicator = (IntScalarParam<?>) selection.indicatorInput.get();
        indicator.set(initial);
        // Compare at an interior point valid for both native branches, without changing priors.
        parameter(graph, "N0").set(150.0);
        parameter(graph, "NA").set(20.0);
        parameter(graph, "b").set(0.4);
        parameter(graph, "f0").set(0.7);
        var posterior = new CompoundDistribution();
        var distributions = new ArrayList<>(graph.priorDistributions.values());
        distributions.addAll(graph.likelihoodDistributions);
        posterior.initByName("distribution", distributions);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        double before = state.robustlyCalcPosterior(posterior);
        assertTrue(Double.isFinite(before));
        assertNativeValues(graph, coalescent);

        state.store(1);
        var operator = graph.operators.stream().filter(ModelIndicatorOperator.class::isInstance)
                .map(ModelIndicatorOperator.class::cast).findFirst().orElseThrow();
        assertEquals(0.0, operator.proposal());
        assertEquals(1 - initial, indicator.get());
        // Also change shared inputs: the selected model must retain live references.
        double originalSize = parameter(graph, "N0").get();
        double originalAncestralSize = parameter(graph, "NA").get();
        parameter(graph, "N0").set(originalSize * 1.1);
        parameter(graph, "NA").set(originalAncestralSize * 1.2);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        double proposed = posterior.calculateLogP();
        assertTrue(Double.isFinite(proposed));
        assertNativeValues(graph, coalescent);
        if (accept) {
            state.acceptCalculationNodes();
        } else {
            state.restore();
            state.restoreCalculationNodes();
        }
        state.setEverythingDirty(false);
        assertEquals(accept ? 1 - initial : initial, indicator.get());
        assertEquals(accept ? originalSize * 1.1 : originalSize, parameter(graph, "N0").get());
        assertEquals(accept ? originalAncestralSize * 1.2 : originalAncestralSize, parameter(graph, "NA").get());
        assertEquals(accept ? proposed : before, posterior.getCurrentLogP(), TOLERANCE);
        assertNativeValues(graph, coalescent);
        assertEquals(accept ? proposed : before, state.robustlyCalcPosterior(posterior), TOLERANCE);
    }

    private static void assertNativeValues(BEASTState graph, Coalescent coalescent) {
        var selection = selection(graph);
        // A fresh native object uses one I_na input, not two population objects or our Tile.
        var nativeModel = new GompertzGrowth_f0();
        nativeModel.initByName("f0", scalar(parameter(graph, "f0").get()),
                "b", scalar(parameter(graph, "b").get()), "N0", scalar(parameter(graph, "N0").get()),
                "NA", scalar(parameter(graph, "NA").get()),
                "I_na", new IntScalarParam<>(selection.indicatorInput.get().get(), NonNegativeInt.INSTANCE));
        for (double time : new double[]{0.0, 0.5, 2.0, 5.0}) {
            assertEquals(nativeModel.getPopSize(time), selection.getPopSize(time), TOLERANCE);
            assertEquals(nativeModel.getIntensity(time), selection.getIntensity(time), TOLERANCE);
        }
        Tree tree = coalescent.treeIntervalsInput.get().treeInput.get();
        Tree referenceTree = new TreeParser(tree.getRoot().toNewick() + ";");
        Coalescent reference = new Coalescent();
        reference.initByName("tree", referenceTree, "populationModel", nativeModel);
        assertEquals(tree.getRoot().getHeight(), referenceTree.getRoot().getHeight(), TOLERANCE);
        assertEquals(reference.calculateLogP(), coalescent.getCurrentLogP(), TOLERANCE);
    }

    private static RealScalarParam<PositiveReal> scalar(double value) {
        return new RealScalarParam<>(value, PositiveReal.INSTANCE);
    }

    private static RealScalarParam<?> parameter(BEASTState graph, String name) {
        return graph.stateNodes.keySet().stream().filter(node -> name.equals(node.getID()))
                .map(RealScalarParam.class::cast).findFirst().orElseThrow();
    }

    private static Coalescent coalescent(BEASTState graph) {
        return graph.priorDistributions.values().stream().filter(Coalescent.class::isInstance)
                .map(Coalescent.class::cast).findFirst().orElseThrow();
    }

    private static StochasticVariableSelection selection(BEASTState graph) {
        return assertInstanceOf(StochasticVariableSelection.class, coalescent(graph).popSizeInput.get());
    }

    private static BEASTState build() throws Exception {
        Randomizer.setSeed(12345);
        String source = Files.readString(EXAMPLE);
        var catalog = TileCatalog.select(BEASTState.class, List.of("popfunc", "beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions().transform(new EvaluateLiterals().transform(
                new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        var graph = evaluator.applyBestTiling(new BEASTState("ancestral-selection", EXAMPLE.getParent()));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
