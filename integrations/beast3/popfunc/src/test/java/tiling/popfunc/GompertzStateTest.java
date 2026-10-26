package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Beta;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.phylospec.workspace.Workspace;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;

class GompertzStateTest {
    private static final List<String> PARAMETERS = List.of(
            "initialPopulationSize", "growthRate", "initialProportion", "ancestralPopulationSize");

    @Test
    void exampleConnectsAllFourPopulationParametersToTheSampledState() throws Exception {
        Model model = buildExample();
        BEASTState graph = model.graph();
        assertEquals(5, graph.stateNodes.size());
        assertEquals(5, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(30, graph.chainLength);
        assertEquals(1, graph.defaultLogEvery);
        assertEquals(Long.valueOf(12345), graph.randomSeed);

        assertSame(parameter(model, "initialPopulationSize"), model.population().N0Input.get());
        assertSame(parameter(model, "growthRate"), model.population().bInput.get());
        assertSame(parameter(model, "initialProportion"), model.population().f0Input.get());
        assertSame(parameter(model, "ancestralPopulationSize"), model.population().NAInput.get());
        assertSame(UnitInterval.INSTANCE, parameter(model, "initialProportion").getDomain());
        assertEquals(1, model.population().indicatorParameterInput.get().get());
        assertSame(model.coalescent(), graph.priorDistributions.get(model.tree()));
        assertNull(model.coalescent().treeInput.get());
        assertSame(model.tree(), model.coalescent().treeIntervalsInput.get().treeInput.get());

        // Check the actual script defaults, before any proposal or test-side parameter edits.
        assertEquals(Math.exp(5.0), parameter(model, "initialPopulationSize").get());
        assertEquals(Math.exp(-0.95), parameter(model, "growthRate").get());
        assertEquals(0.5, parameter(model, "initialProportion").get());
        assertEquals(Math.exp(3.0), parameter(model, "ancestralPopulationSize").get());
        assertClose(parameter(model, "initialPopulationSize").get(), model.population().getPopSize(0.0));
        assertReferenceMatches(model);

        assertLogNormalPrior(model, "initialPopulationSize", 5.0, 0.5);
        assertLogNormalPrior(model, "growthRate", -0.95, 0.2);
        assertLogNormalPrior(model, "ancestralPopulationSize", 3.0, 0.3);
        Beta proportionPrior = assertInstanceOf(Beta.class,
                graph.priorDistributions.get(parameter(model, "initialProportion")));
        assertSame(parameter(model, "initialProportion"), proportionPrior.paramInput.get());
        assertEquals(20.0, proportionPrior.alphaInput.get().get());
        assertEquals(7.0, proportionPrior.betaInput.get().get());

        for (String id : PARAMETERS) {
            RealScalarParam<?> parameter = parameter(model, id);
            assertNotNull(graph.priorDistributions.get(parameter), id + " must have its own prior.");
            assertTrue(graph.operators.stream()
                    .filter(operator -> operator.getWeight() > 0.0)
                    .flatMap(operator -> operator.listStateNodes().stream())
                    .anyMatch(node -> node == parameter), id + " must be reachable by an active operator.");
        }
    }

    private static Stream<Arguments> proposals() {
        return Stream.concat(PARAMETERS.stream(), Stream.of("tree"))
                .flatMap(id -> Stream.of(Arguments.of(id, false), Arguments.of(id, true)));
    }

    @ParameterizedTest(name = "{0}: acceptFirst={1}")
    @MethodSource("proposals")
    void followsAcceptedAndRejectedProposals(String id, boolean acceptFirst) throws Exception {
        Model model = buildExample();
        // Exercise the unmodified script's real initial state, with no test-side reset.
        StateNode changedNode = node(model, id);
        Distribution changedPrior = model.graph().priorDistributions.get(changedNode);
        assertReferenceMatches(model);

        // Exercise both orders so that a proposal after rollback is checked as well.
        for (int proposal = 0; proposal < 2; proposal++) {
            boolean accept = proposal == 0 ? acceptFirst : !acceptFirst;
            double previousValue = value(model, id);
            double previousPosterior = model.posterior().getCurrentLogP();
            double previousPrior = changedPrior.getCurrentLogP();
            double previousCoalescent = model.coalescent().getCurrentLogP();
            double previousPopulationSize = model.population().getPopSize(0.5);
            double proposedValue = id.equals("initialProportion")
                    ? previousValue * 0.9
                    : previousValue * 1.1;

            // This is the store/propose/calculate/accept-or-restore order used by BEAST MCMC.
            model.state().store(proposal + 1);
            if (changedNode instanceof Tree tree) {
                tree.getRoot().setHeight(proposedValue);
            } else {
                parameter(model, id).set(proposedValue);
            }
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            double proposedPosterior = model.posterior().calculateLogP();
            assertTrue(Double.isFinite(proposedPosterior));
            double proposedPrior = changedPrior.getCurrentLogP();
            double proposedCoalescent = model.coalescent().getCurrentLogP();
            assertNotEquals(previousPrior, proposedPrior, 1e-12, "The proposed node's prior must change.");
            assertNotEquals(previousCoalescent, proposedCoalescent, 1e-12,
                    "The coalescent must respond to every population parameter and the tree.");
            if (!id.equals("tree")) {
                assertNotEquals(previousPopulationSize, model.population().getPopSize(0.5), 1e-12,
                        "A population parameter proposal must alter the population function.");
            }
            assertReferenceMatches(model);

            if (accept) {
                model.state().acceptCalculationNodes();
            } else {
                model.state().restore();
                model.state().restoreCalculationNodes();
            }
            model.state().setEverythingDirty(false);
            assertEquals(accept ? proposedValue : previousValue, value(model, id));
            assertClose(accept ? proposedPosterior : previousPosterior, model.posterior().getCurrentLogP());
            assertClose(accept ? proposedPrior : previousPrior, changedPrior.getCurrentLogP());
            assertClose(accept ? proposedCoalescent : previousCoalescent, model.coalescent().getCurrentLogP());
            if (!accept) {
                assertClose(previousPopulationSize, model.population().getPopSize(0.5));
            }

            // Do not force anything dirty: validate the accepted/restored function and interval
            // caches, not only Distribution.logP, against entirely new native objects.
            assertReferenceMatches(model);
            assertClose(accept ? proposedPrior : previousPrior, changedPrior.calculateLogP());
            assertClose(accept ? proposedPosterior : previousPosterior, model.posterior().calculateLogP());
        }
    }

    private static void assertReferenceMatches(Model model) {
        GompertzGrowth_f0 referencePopulation = new GompertzGrowth_f0();
        referencePopulation.initByName(
                "N0", new RealScalarParam<>(parameter(model, "initialPopulationSize").get(), PositiveReal.INSTANCE),
                "b", new RealScalarParam<>(parameter(model, "growthRate").get(), PositiveReal.INSTANCE),
                "f0", new RealScalarParam<>(parameter(model, "initialProportion").get(), UnitInterval.INSTANCE),
                "NA", new RealScalarParam<>(parameter(model, "ancestralPopulationSize").get(), PositiveReal.INSTANCE),
                "I_na", new IntScalarParam<>(1, NonNegativeInt.INSTANCE));
        for (double time : new double[] {0.0, 0.5, 2.0, 5.0}) {
            double populationSize = model.population().getPopSize(time);
            assertTrue(populationSize > 0.0, "Population size must be positive at time " + time);
            assertClose(referencePopulation.getPopSize(time), populationSize);
            assertClose(referencePopulation.getIntensity(time), model.population().getIntensity(time));
        }

        Tree copy = new Tree(model.tree().getRoot().copy());
        TreeIntervals intervals = new TreeIntervals();
        intervals.initByName("tree", copy);
        Coalescent reference = new Coalescent();
        reference.initByName("treeIntervals", intervals, "populationModel", referencePopulation);
        double expected = reference.calculateLogP();
        assertClose(expected, model.coalescent().getCurrentLogP());
        assertClose(expected, model.coalescent().calculateLogP());
    }

    private static void assertLogNormalPrior(Model model, String id, double logMean, double logSd) {
        RealScalarParam<?> parameter = parameter(model, id);
        LogNormal prior = assertInstanceOf(LogNormal.class, model.graph().priorDistributions.get(parameter));
        assertSame(parameter, prior.paramInput.get());
        assertSame(PositiveReal.INSTANCE, parameter.getDomain());
        assertEquals(logMean, prior.MParameterInput.get().get());
        assertEquals(logSd, prior.SParameterInput.get().get());
        assertFalse(prior.hasMeanInRealSpaceInput.get());
    }

    private static void assertClose(double expected, double actual) {
        assertTrue(Double.isFinite(expected));
        assertTrue(Double.isFinite(actual));
        assertEquals(expected, actual, Math.max(1e-12, Math.abs(expected) * 1e-10));
    }

    private static double value(Model model, String id) {
        return id.equals("tree") ? model.tree().getRoot().getHeight() : parameter(model, id).get();
    }

    private static RealScalarParam<?> parameter(Model model, String id) {
        return assertInstanceOf(RealScalarParam.class, node(model, id));
    }

    private static StateNode node(Model model, String id) {
        return model.graph().stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static Model buildExample() throws Exception {
        Path sourceFile = Path.of("examples/gompertz.phylospec").toAbsolutePath().normalize();
        String source = Files.readString(sourceFile);
        Randomizer.setSeed(12345);
        var catalog = TileCatalog.select(BEASTState.class, List.of("popfunc", "beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver(), new Workspace(sourceFile.getParent()))
                .visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("gompertz-state", sourceFile.getParent()));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();

        Tree tree = graph.stateNodes.keySet().stream()
                .filter(Tree.class::isInstance)
                .map(Tree.class::cast)
                .findFirst()
                .orElseThrow();
        Coalescent coalescent = assertInstanceOf(Coalescent.class, graph.priorDistributions.get(tree));
        GompertzGrowth_f0 population = assertInstanceOf(GompertzGrowth_f0.class, coalescent.popSizeInput.get());
        List<Distribution> distributions = new ArrayList<>(graph.priorDistributions.values());
        distributions.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", distributions);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        return new Model(graph, tree, coalescent, population, posterior, state);
    }

    private record Model(
            BEASTState graph,
            Tree tree,
            Coalescent coalescent,
            GompertzGrowth_f0 population,
            CompoundDistribution posterior,
            State state) {}
}
