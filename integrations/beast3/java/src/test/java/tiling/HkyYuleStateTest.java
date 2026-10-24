package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.likelihood.BeerLikelihoodCore4;
import beast.base.evolution.operator.Exchange;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.parser.NexusParser;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.evolution.speciation.YuleModel;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.distribution.Dirichlet;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
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
import org.phylospec.workspace.Workspace;
import runner.PhyloSpecRunner;

class HkyYuleStateTest {
    private static final Path SOURCE = Path.of("src/main/distribution/examples/hky-yule.phylospec")
            .toAbsolutePath().normalize();
    private static final Path DATA = SOURCE.getParent().resolve("data/primate-mtDNA.nex");
    private static final double PRIOR_TOLERANCE = 1e-10;
    private static final double LIKELIHOOD_TOLERANCE = 1e-8;
    // An explicit test state, not a starting-tree constraint in the user's model.
    private static final String FIXED_TREE = "(((((Homo_sapiens:0.1,Pan:0.1):0.1,Gorilla:0.2):0.1,"
            + "(Pongo:0.15,Hylobates:0.15):0.15):0.3,"
            + "(((Macaca_fuscata:0.1,M._mulatta:0.1):0.1,"
            + "(M._fascicularis:0.1,M._sylvanus:0.1):0.1):0.2,Saimiri_sciureus:0.4):0.2):0.4,"
            + "(Tarsius_syrichta:0.5,Lemur_catta:0.5):0.5);";

    @Test
    void userExamplePreservesDataModelPriorsAndSampledState() throws Exception {
        assertNotNull(new PhyloSpecRunner(SOURCE, List.of("beast2")).check());
        Model model = buildExample();
        assertArrayEquals(Files.readAllBytes(Path.of("src/test/java/resources/primate-mtDNA.nex")),
                Files.readAllBytes(DATA));
        assertEquals(12, model.likelihood().dataInput.get().getTaxonCount());
        assertEquals(898, model.likelihood().dataInput.get().getSiteCount());
        assertEquals(Set.of("tree", "kappa", "baseFrequencies"),
                model.graph().stateNodes.keySet().stream().map(StateNode::getID).collect(Collectors.toSet()));
        assertEquals(3, model.graph().stateNodes.size());
        assertEquals(3, model.graph().priorDistributions.size());
        assertEquals(1, model.graph().likelihoodDistributions.size());
        assertEquals(Set.of("tree_prior", "kappa_prior", "baseFrequencies_prior"),
                model.graph().priorDistributions.values().stream()
                        .map(Distribution::getID).collect(Collectors.toSet()));
        assertEquals("alignment_likelihood", model.likelihood().getID());
        assertEquals(30, model.graph().chainLength);
        assertEquals(1, model.graph().defaultLogEvery);
        assertEquals(Long.valueOf(12345), model.graph().randomSeed);

        assertEquals(Math.exp(1.0), model.kappa().get());
        assertArrayEquals(new double[] {0.25, 0.25, 0.25, 0.25}, frequencies(model));
        assertSame(PositiveReal.INSTANCE, model.kappa().getDomain());
        assertSame(UnitInterval.INSTANCE, model.frequencies().getDomain());
        assertSame(model.tree(), model.yule().treeInput.get());
        assertEquals(1.0, model.yule().birthDiffRateParameterInput.get().get());
        assertFalse(model.yule().conditionalOnRootInput.get());
        assertNull(model.yule().originHeightParameterInput.get());
        assertSame(model.kappa(), model.kappaPrior().paramInput.get());
        assertEquals(1.0, model.kappaPrior().MParameterInput.get().get());
        assertEquals(0.5, model.kappaPrior().SParameterInput.get().get());
        assertFalse(model.kappaPrior().hasMeanInRealSpaceInput.get());
        assertSame(model.frequencies(), model.frequenciesPrior().paramInput.get());
        assertEquals(List.of(1.0, 1.0, 1.0, 1.0), model.frequenciesPrior().alphaInput.get().getElements());

        SiteModel siteModel = assertInstanceOf(SiteModel.class, model.likelihood().siteModelInput.get());
        HKY hky = assertInstanceOf(HKY.class, siteModel.substModelInput.get());
        assertSame(model.kappa(), hky.kappaInput.get());
        assertSame(model.frequencies(), hky.frequenciesInput.get().frequenciesInput.get());
        assertSame(model.tree(), model.likelihood().treeInput.get());
        StrictClockModel clock = assertInstanceOf(StrictClockModel.class,
                model.likelihood().branchRateModelInput.get());
        assertEquals(1.0, clock.meanRateInput.get().get());
        assertEquals(1, siteModel.getCategoryCount());
        assertEquals(1.0, siteModel.getRateForCategory(0, model.tree().getRoot()));
        assertEquals(0.0, siteModel.getProportionInvariant());
        assertNull(siteModel.shapeParameterInput.get());
        assertFalse(model.likelihood().m_useAmbiguities.get());
        assertFalse(model.likelihood().m_useTipLikelihoods.get());
        assertNull(model.likelihood().rootFrequenciesInput.get());

        for (StateNode node : model.graph().stateNodes.keySet()) {
            assertTrue(model.graph().operators.stream().filter(operator -> operator.getWeight() > 0)
                    .flatMap(operator -> operator.listStateNodes().stream()).anyMatch(sampled -> sampled == node),
                    node.getID() + " must be connected to an active operator.");
        }
        assertNativeMatches(model);
    }

    @ParameterizedTest(name = "nonuniform frequencies={0}")
    @ValueSource(booleans = {false, true})
    void matchesEachNativeProbabilityAtExplicitFixedStates(boolean nonuniform) throws Exception {
        Model model = buildExample();
        setFixedState(model, nonuniform);
        assertNativeMatches(model);
    }

    static Stream<Arguments> proposals() {
        return Stream.of("tree", "treeTopology", "kappa", "baseFrequencies")
                .flatMap(id -> Stream.of(Arguments.of(id, false), Arguments.of(id, true)));
    }

    @ParameterizedTest(name = "{0}: acceptFirst={1}")
    @MethodSource("proposals")
    void followsAcceptedAndRejectedProposals(String id, boolean acceptFirst) throws Exception {
        Model model = buildExample();
        setFixedState(model, true);
        assertNativeMatches(model);

        // Exercise both orders, including the first proposal after a rejection.
        for (int proposal = 0; proposal < 2; proposal++) {
            boolean accept = proposal == 0 ? acceptFirst : !acceptFirst;
            Values previous = currentValues(model);
            double previousKappa = model.kappa().get();
            double[] previousFrequencies = frequencies(model);
            String previousTree = model.tree().getRoot().toNewick();

            model.state().store(proposal + 1);
            switch (id) {
                case "tree" -> model.tree().getRoot().setHeight(model.tree().getRoot().getHeight() * 1.1);
                case "treeTopology" -> {
                    Node first = model.tree().getExternalNodes().stream()
                            .filter(node -> node.getID().equals("Homo_sapiens")).findFirst().orElseThrow();
                    Node second = model.tree().getExternalNodes().stream()
                            .filter(node -> node.getID().equals("Macaca_fuscata")).findFirst().orElseThrow();
                    Node firstParent = first.getParent();
                    Node secondParent = second.getParent();
                    // Use the native exchange helper to mark the affected tree nodes dirty.
                    Exchange exchange = new Exchange();
                    exchange.replace(firstParent, first, second);
                    exchange.replace(secondParent, second, first);
                }
                case "kappa" -> model.kappa().set(model.kappa().get() * 1.2);
                case "baseFrequencies" -> {
                    // A sum-preserving proposal, evaluated only after both elements are set.
                    model.frequencies().set(0, previousFrequencies[0] + 0.025);
                    model.frequencies().set(1, previousFrequencies[1] - 0.025);
                }
                default -> throw new IllegalArgumentException(id);
            }
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            assertTrue(Double.isFinite(model.posterior().calculateLogP()));
            Values proposed = currentValues(model);
            assertNotEquals(previous.likelihood(), proposed.likelihood(), LIKELIHOOD_TOLERANCE,
                    "Each sampled state component must affect the sequence likelihood.");
            if (id.equals("tree")) {
                assertNotEquals(previous.yule(), proposed.yule(), PRIOR_TOLERANCE);
            } else if (id.equals("kappa")) {
                assertNotEquals(previous.kappa(), proposed.kappa(), PRIOR_TOLERANCE);
            } else if (id.equals("baseFrequencies")) {
                // The uniform Dirichlet density is constant over the valid simplex.
                assertClose(Math.log(6.0), proposed.frequencies(), PRIOR_TOLERANCE, "Dirichlet");
            } else {
                // Exchanging tips preserves every node height, and therefore this Yule density.
                assertClose(previous.yule(), proposed.yule(), PRIOR_TOLERANCE, "Yule after topology exchange");
            }
            // A live MCMC proposal is evaluated once before accepting or rejecting it.
            // Re-evaluating a still-dirty native likelihood can flip its double buffers
            // again and overwrite the state that rejection must restore.
            assertNativeMatches(model, false);
            double proposedKappa = model.kappa().get();
            double[] proposedFrequencies = frequencies(model);
            String proposedTree = model.tree().getRoot().toNewick();

            if (accept) {
                model.state().acceptCalculationNodes();
            } else {
                model.state().restore();
                model.state().restoreCalculationNodes();
            }
            model.state().setEverythingDirty(false);
            assertEquals(accept ? proposedKappa : previousKappa, model.kappa().get());
            assertArrayEquals(accept ? proposedFrequencies : previousFrequencies, frequencies(model));
            assertEquals(accept ? proposedTree : previousTree, model.tree().getRoot().toNewick());

            // Check cached log densities BEFORE any recomputation or native comparison.
            assertValues(accept ? proposed : previous, currentValues(model));
            // Fresh native objects check the accepted/restored state without forcing it dirty.
            assertNativeMatches(model);
        }
    }

    private static void setFixedState(Model model, boolean nonuniform) {
        TreeParser fixed = new TreeParser();
        fixed.initByName("taxa", model.likelihood().dataInput.get(), "newick", FIXED_TREE,
                "IsLabelledNewick", true, "adjustTipHeights", false);
        fixed.setID(model.tree().getID());
        // TreeLikelihood has already cached tip partials by node number. Preserve that mapping.
        for (int i = 0; i < fixed.getLeafNodeCount(); i++) {
            assertEquals(model.tree().getNode(i).getID(), fixed.getNode(i).getID());
            assertEquals(0.0, fixed.getNode(i).getHeight(), 1e-12);
        }
        model.tree().assignFrom(fixed);
        model.kappa().set(nonuniform ? 4.25 : Math.exp(1.0));
        double[] values = nonuniform ? new double[] {0.30, 0.20, 0.15, 0.35}
                : new double[] {0.25, 0.25, 0.25, 0.25};
        for (int i = 0; i < values.length; i++) model.frequencies().set(i, values[i]);
        if (nonuniform) model.tree().getRoot().setHeight(1.15);
        // Only establishment of a test's initial state uses a full dirty recalculation.
        assertTrue(Double.isFinite(model.state().robustlyCalcPosterior(model.posterior())));
    }

    private static void assertNativeMatches(Model model) throws Exception {
        assertNativeMatches(model, true);
    }

    private static void assertNativeMatches(Model model, boolean recalculateCleanGraph) throws Exception {
        // Reparse the data and create all native objects independently, without any Tiles or
        // shared parameters, tree nodes, substitution models, alignment objects or caches.
        NexusParser parser = new NexusParser();
        parser.parseFile(DATA.toFile());
        Alignment alignment = parser.m_alignment;
        Tree tree = new Tree(model.tree().getRoot().copy());
        RealScalarParam<PositiveReal> kappa = new RealScalarParam<>(model.kappa().get(), PositiveReal.INSTANCE);
        SimplexParam frequencies = new SimplexParam(frequencies(model));
        Frequencies equilibrium = new Frequencies(frequencies);
        HKY hky = new HKY();
        hky.initByName("kappa", kappa, "frequencies", equilibrium);
        SiteModel siteModel = new SiteModel();
        siteModel.initByName("substModel", hky, "gammaCategoryCount", 0,
                "mutationRate", new RealScalarParam<>(1.0, PositiveReal.INSTANCE),
                "proportionInvariant", new RealScalarParam<>(0.0, UnitInterval.INSTANCE));
        StrictClockModel clock = new StrictClockModel();
        clock.initByName("clock.rate", new RealScalarParam<>(1.0, PositiveReal.INSTANCE));
        TreeLikelihood likelihood = new TreeLikelihood();
        likelihood.initByName("data", alignment, "tree", tree, "siteModel", siteModel,
                "branchRateModel", clock, "implementation", TreeLikelihood.class.getName(),
                "useAmbiguities", false, "useTipLikelihoods", false);
        assertInstanceOf(BeerLikelihoodCore4.class, likelihood.getLikelihoodCore());
        assertNotSame(model.likelihood().dataInput.get(), alignment);
        assertNotSame(model.tree().getRoot(), tree.getRoot());
        assertNotSame(model.kappa(), kappa);
        assertNotSame(model.frequencies(), frequencies);

        YuleModel yule = new YuleModel();
        yule.initByName("tree", tree, "birthDiffRate", new RealScalarParam<>(1.0, PositiveReal.INSTANCE),
                "conditionalOnRoot", false);
        LogNormal kappaPrior = new LogNormal(kappa, new RealScalarParam<>(1.0, Real.INSTANCE),
                new RealScalarParam<>(0.5, PositiveReal.INSTANCE), false);
        Dirichlet frequenciesPrior = new Dirichlet(frequencies,
                new RealVectorParam<>(new double[] {1, 1, 1, 1}, PositiveReal.INSTANCE));
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(yule, kappaPrior, frequenciesPrior, likelihood));
        // Fresh native distributions are not attached to an MCMC State. Populate their logP
        // explicitly; CompoundDistribution otherwise reads their uninitialized clean caches.
        yule.calculateLogP();
        kappaPrior.calculateLogP();
        frequenciesPrior.calculateLogP();
        likelihood.calculateLogP();
        double posteriorValue = posterior.calculateLogP();
        Values expected = new Values(yule.getCurrentLogP(), kappaPrior.getCurrentLogP(),
                frequenciesPrior.getCurrentLogP(), likelihood.getCurrentLogP(), posteriorValue);
        assertClose(Math.log(6.0), expected.frequencies(), PRIOR_TOLERANCE, "Dirichlet normalization");
        double yuleByHeights = -tree.getRoot().getHeight();
        for (var node : tree.getInternalNodes()) yuleByHeights -= node.getHeight();
        assertClose(yuleByHeights, expected.yule(), PRIOR_TOLERANCE, "Unconditioned Yule at birth rate 1");
        double logKappa = Math.log(kappa.get());
        double logNormalByFormula = -logKappa - Math.log(0.5) - 0.5 * Math.log(2.0 * Math.PI)
                - 0.5 * Math.pow((logKappa - 1.0) / 0.5, 2);
        assertClose(logNormalByFormula, expected.kappa(), PRIOR_TOLERANCE, "LogNormal parameterization");

        assertValues(expected, currentValues(model));
        if (!recalculateCleanGraph) return;
        // Recalculate through the existing graph without marking it dirty: this also checks
        // the restored HKY/frequency/tree-likelihood caches, not just stored logP values.
        assertClose(expected.yule(), model.yule().calculateLogP(), PRIOR_TOLERANCE, "Yule recalculation");
        assertClose(expected.kappa(), model.kappaPrior().calculateLogP(), PRIOR_TOLERANCE, "LogNormal recalculation");
        assertClose(expected.frequencies(), model.frequenciesPrior().calculateLogP(),
                PRIOR_TOLERANCE, "Dirichlet recalculation");
        assertClose(expected.likelihood(), model.likelihood().calculateLogP(),
                LIKELIHOOD_TOLERANCE, "Likelihood recalculation");
        assertClose(expected.posterior(), model.posterior().calculateLogP(),
                LIKELIHOOD_TOLERANCE, "Posterior recalculation");
    }

    private static void assertValues(Values expected, Values actual) {
        assertClose(expected.yule(), actual.yule(), PRIOR_TOLERANCE, "Yule");
        assertClose(expected.kappa(), actual.kappa(), PRIOR_TOLERANCE, "LogNormal");
        assertClose(expected.frequencies(), actual.frequencies(), PRIOR_TOLERANCE, "Dirichlet");
        assertClose(expected.prior(), actual.prior(), PRIOR_TOLERANCE, "Prior sum");
        assertClose(expected.likelihood(), actual.likelihood(), LIKELIHOOD_TOLERANCE, "Likelihood");
        assertClose(expected.posterior(), actual.posterior(), LIKELIHOOD_TOLERANCE, "Posterior");
        assertClose(actual.prior() + actual.likelihood(), actual.posterior(),
                LIKELIHOOD_TOLERANCE, "Posterior composition");
    }

    private static void assertClose(double expected, double actual, double tolerance, String label) {
        assertTrue(Double.isFinite(expected), label + " reference must be finite.");
        assertTrue(Double.isFinite(actual), label + " must be finite.");
        assertEquals(expected, actual, tolerance, label);
    }

    private static double[] frequencies(Model model) {
        return model.frequencies().getElements().stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static Values currentValues(Model model) {
        return new Values(model.yule().getCurrentLogP(), model.kappaPrior().getCurrentLogP(),
                model.frequenciesPrior().getCurrentLogP(), model.likelihood().getCurrentLogP(),
                model.posterior().getCurrentLogP());
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream().filter(node -> id.equals(node.getID())).findFirst().orElseThrow();
    }

    private Model buildExample() throws Exception {
        String source = Files.readString(SOURCE);
        Randomizer.setSeed(12345);
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver(), new Workspace(SOURCE.getParent())).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("hky-yule-state", SOURCE.getParent()));
        catalog.configureState(graph);
        TreeLikelihood likelihood = assertInstanceOf(TreeLikelihood.class, graph.likelihoodDistributions.getFirst());
        configureLikelihood(likelihood);
        graph.initializeBEASTObjects();
        assertBackend(likelihood);

        Tree tree = assertInstanceOf(Tree.class, node(graph, "tree"));
        RealScalarParam<?> kappa = assertInstanceOf(RealScalarParam.class, node(graph, "kappa"));
        SimplexParam frequencies = assertInstanceOf(SimplexParam.class, node(graph, "baseFrequencies"));
        YuleModel yule = assertInstanceOf(YuleModel.class, graph.priorDistributions.get(tree));
        LogNormal kappaPrior = assertInstanceOf(LogNormal.class, graph.priorDistributions.get(kappa));
        Dirichlet frequenciesPrior = assertInstanceOf(Dirichlet.class, graph.priorDistributions.get(frequencies));
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(yule, kappaPrior, frequenciesPrior, likelihood));
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        return new Model(graph, tree, kappa, frequencies, yule, kappaPrior, frequenciesPrior, likelihood, posterior, state);
    }

    void configureLikelihood(TreeLikelihood likelihood) {
        // The default suite compares the mapped graph with the independent Java reference.
        likelihood.implementationInput.setValue(TreeLikelihood.class.getName(), likelihood);
    }

    void assertBackend(TreeLikelihood likelihood) {
        assertInstanceOf(BeerLikelihoodCore4.class, likelihood.getLikelihoodCore());
    }

    private record Model(BEASTState graph, Tree tree, RealScalarParam<?> kappa, SimplexParam frequencies,
            YuleModel yule, LogNormal kappaPrior, Dirichlet frequenciesPrior, TreeLikelihood likelihood,
            CompoundDistribution posterior, State state) {}

    private record Values(double yule, double kappa, double frequencies, double likelihood, double posterior) {
        double prior() { return yule + kappa + frequencies; }
    }
}
