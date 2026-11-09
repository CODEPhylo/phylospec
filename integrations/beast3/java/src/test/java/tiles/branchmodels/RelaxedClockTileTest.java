package tiles.branchmodels;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.likelihood.BeerLikelihoodCore4;
import beast.base.evolution.tree.Tree;
import beast.base.inference.CalculationNode;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.parser.NexusParser;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.branchratemodel.UCRelaxedClockModel;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.evolution.substitutionmodel.JukesCantor;
import beast.base.spec.inference.distribution.Gamma;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.IntRandomWalkOperator;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.operator.SwapOperator;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.phylospec.workspace.Workspace;
import tiling.ExpValue;

class RelaxedClockTileTest {
    private static final Path DATA = Path.of("src/test/java/resources/primate-mtDNA.nex")
            .toAbsolutePath()
            .normalize();
    private static final double PRIOR_TOLERANCE = 1e-12;
    private static final double LIKELIHOOD_TOLERANCE = 1e-8;
    private static final String FIXED_TREE = "(((((Homo_sapiens:0.1,Pan:0.1):0.1,Gorilla:0.2):0.1,"
            + "(Pongo:0.15,Hylobates:0.15):0.15):0.3,"
            + "(((Macaca_fuscata:0.1,M._mulatta:0.1):0.1,"
            + "(M._fascicularis:0.1,M._sylvanus:0.1):0.1):0.2,Saimiri_sciureus:0.4):0.2):0.4,"
            + "(Tarsius_syrichta:0.5,Lemur_catta:0.5):0.5);";

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 2.0})
    void fixedClockRatesKeepTheExistingNativeModel(double rate) throws Exception {
        Model model = build("PositiveReal rate = " + rate + "\n", null, 1.0);
        assertConfiguration(model);
        assertInstanceOf(RealScalarParam.class, model.clock().meanRateInput.get());
        assertEquals(rate, model.clock().getMeanRate().get(), 0.0);
        assertTrue(model.graph().priorDistributions.isEmpty());
        assertNativeMatches(model);
    }

    @ParameterizedTest
    @CsvSource({"gamma, false", "gamma, true", "derived, false", "derived, true"})
    void sampledAndDerivedRatesUpdateLikelihoodsAndRestoreState(String family, boolean acceptFirst) throws Exception {
        String declaration = family.equals("gamma")
                ? "PositiveReal rate ~ Gamma(shape=2.0, rate=1.0)\n"
                : "Real logRate ~ Normal(mean=0.0, sd=1.0)\nPositiveReal rate = exp(x=logRate)\n";
        Model model = build(declaration, family.equals("gamma") ? "rate" : "logRate", 1.0);
        assertConfiguration(model);
        RealScalarParam<?> source = model.source();
        if (family.equals("gamma")) {
            assertSame(source, model.clock().meanRateInput.get());
            assertSame(PositiveReal.INSTANCE, source.getDomain());
            assertEquals(1.0, source.get(), 0.0);
            Gamma prior = assertInstanceOf(Gamma.class, model.prior());
            assertSame(source, prior.paramInput.get());
            assertEquals(2.0, prior.alphaInput.get().get(), 0.0);
            assertEquals(1.0, prior.betaInput.get().get(), 0.0);
        } else {
            ExpValue rate =
                    assertInstanceOf(ExpValue.class, model.clock().meanRateInput.get());
            assertSame(source, rate.xInput.get());
            assertSame(Real.INSTANCE, source.getDomain());
            assertEquals(0.1, source.get(), 0.0);
            assertTrue(source.getOutputs().contains(rate));
            assertTrue(rate.getOutputs().contains(model.clock()));
            assertTrue(model.graph().calculationNodes.containsKey(rate));
            Normal prior = assertInstanceOf(Normal.class, model.prior());
            assertSame(source, prior.paramInput.get());
            assertEquals(0.0, prior.meanInput.get().get(), 0.0);
            assertEquals(1.0, prior.sdInput.get().get(), 0.0);
        }
        assertNativeMatches(model);
        int[] initialCategories = categories(model);
        double[] proposals = family.equals("gamma") ? new double[] {1.5, 0.6, 2.0} : new double[] {-0.5, 0.0, 0.7};
        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        for (int step = 0; step < proposals.length; step++) {
            double previousSource = source.get();
            double previousMeanRate = model.clock().getMeanRate().get();
            double[] previousBranchRates = branchRates(model);
            Values previous = currentValues(model);
            model.state().store(step + 1);
            source.set(proposals[step]);
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            assertFalse(model.tree().somethingIsDirty(), "The fixed tree must not drive likelihood updates.");
            assertTrue(model.clock().somethingIsDirty());
            assertTrue(model.likelihood().somethingIsDirty());
            assertTrue(Double.isFinite(model.posterior().calculateLogP()));
            Values proposed = currentValues(model);
            double expectedMeanRate = family.equals("gamma") ? proposals[step] : Math.exp(proposals[step]);
            assertEquals(expectedMeanRate, model.clock().getMeanRate().get(), 0.0);
            assertNotEquals(previous.likelihood(), proposed.likelihood(), LIKELIHOOD_TOLERANCE);
            double[] proposedBranchRates = branchRates(model);
            for (int i = 0; i < proposedBranchRates.length; i++) {
                assertEquals(
                        expectedMeanRate / previousMeanRate,
                        proposedBranchRates[i] / previousBranchRates[i],
                        PRIOR_TOLERANCE);
            }
            assertArrayEquals(initialCategories, categories(model));
            // Compare independent native objects; do not reevaluate this still-dirty likelihood.
            assertNativeMatches(model);

            if (decisions[step]) {
                model.state().acceptCalculationNodes();
            } else {
                model.state().restore();
                model.state().restoreCalculationNodes();
            }
            model.state().setEverythingDirty(false);
            assertEquals(decisions[step] ? proposals[step] : previousSource, source.get(), 0.0);
            assertEquals(family.equals("gamma") ? "rate" : "logRate", source.getID());
            assertArrayEquals(initialCategories, categories(model));
            // Inspect the saved densities before any recomputation could hide restoration errors.
            assertValues(decisions[step] ? proposed : previous, currentValues(model));
            assertArrayEquals(
                    decisions[step] ? proposedBranchRates : previousBranchRates, branchRates(model), PRIOR_TOLERANCE);
            assertNativeMatches(model);
        }
    }

    @Test
    void baseDistributionMustStillHaveMeanOne() {
        assertThrows(TileApplicationError.class, () -> build("PositiveReal rate = 1.0\n", null, 2.0));
    }

    private static void assertConfiguration(Model model) {
        BEASTState graph = model.graph();
        boolean sampledRate = model.source() != null;
        assertEquals(sampledRate ? 2 : 1, graph.stateNodes.size());
        assertEquals(sampledRate ? 1 : 0, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(sampledRate ? 3 : 2, graph.operators.size());
        assertFalse(model.tree().isEstimatedInput.get());
        assertFalse(model.tree().somethingIsDirty());
        assertFalse(graph.stateNodes.containsKey(model.tree()));
        assertSame(model.tree(), model.clock().treeInput.get());
        assertSame(model.clock(), model.likelihood().branchRateModelInput.get());
        assertSame(model.categories(), model.clock().categoryInput.get());
        assertSame(model.clock().meanRateInput.get(), model.clock().getMeanRate());
        assertTrue(assertInstanceOf(CalculationNode.class, model.clock().getMeanRate())
                .getOutputs()
                .contains(model.clock()));
        assertTrue(model.clock().getOutputs().contains(model.likelihood()));
        assertSame(PositiveReal.INSTANCE, model.clock().getMeanRate().getDomain());
        assertSame(NonNegativeInt.INSTANCE, model.categories().getDomain());
        assertEquals(model.tree().getNodeCount() - 1, model.categories().size());
        assertFalse(model.clock().normalizeInput.get());
        assertEquals(-1, model.clock().numberOfDiscreteRates.get());
        for (int category : categories(model))
            assertTrue(category >= 0 && category < model.categories().size());
        LogNormal base =
                assertInstanceOf(LogNormal.class, model.clock().rateDistInput.get());
        assertEquals(1.0, base.MParameterInput.get().get(), 0.0);
        assertEquals(0.5, base.SParameterInput.get().get(), 0.0);
        assertTrue(base.hasMeanInRealSpaceInput.get());
        assertEquals(1.0, base.getMean(), PRIOR_TOLERANCE);
        assertNull(base.paramInput.get());
        assertFalse(graph.priorDistributions.containsValue(base));

        IntRandomWalkOperator walk = graph.operators.stream()
                .filter(IntRandomWalkOperator.class::isInstance)
                .map(IntRandomWalkOperator.class::cast)
                .findFirst()
                .orElseThrow();
        SwapOperator swap = graph.operators.stream()
                .filter(SwapOperator.class::isInstance)
                .map(SwapOperator.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(model.categories()), walk.listStateNodes());
        assertEquals(List.of(model.categories()), swap.listStateNodes());
        assertEquals(1, walk.windowSizeInput.get());
        assertEquals(10.0, walk.m_pWeight.get(), 0.0);
        assertEquals(10.0, swap.m_pWeight.get(), 0.0);
        if (sampledRate) {
            var operator = graph.operators.stream()
                    .filter(candidate -> candidate.listStateNodes().contains(model.source()))
                    .findFirst()
                    .orElseThrow();
            if (model.prior() instanceof Gamma) {
                assertSame(
                        model.source(),
                        assertInstanceOf(ScaleOperator.class, operator)
                                .parameterInput
                                .get());
            } else {
                assertSame(
                        model.source(),
                        assertInstanceOf(RealRandomWalkOperator.class, operator)
                                .scalarInput
                                .get());
            }
            assertEquals(List.of(model.source()), operator.listStateNodes());
            assertEquals(5.0, operator.m_pWeight.get(), 0.0);
        }
        assertInstanceOf(BeerLikelihoodCore4.class, model.likelihood().getLikelihoodCore());
    }

    private static void assertNativeMatches(Model model) throws Exception {
        NexusParser parser = new NexusParser();
        parser.parseFile(DATA.toFile());
        Alignment alignment = parser.m_alignment;
        Tree tree = new Tree(model.tree().getRoot().copy());
        tree.isEstimatedInput.setValue(false, tree);
        LogNormal base = new LogNormal();
        base.initByName(
                "M",
                new RealScalarParam<>(1.0, Real.INSTANCE),
                "S",
                new RealScalarParam<>(0.5, PositiveReal.INSTANCE),
                "meanInRealSpace",
                true);
        IntVectorParam<NonNegativeInt> categories = new IntVectorParam<>(categories(model), NonNegativeInt.INSTANCE);
        RealScalarParam<PositiveReal> meanRate =
                new RealScalarParam<>(model.clock().getMeanRate().get(), PositiveReal.INSTANCE);
        UCRelaxedClockModel clock = new UCRelaxedClockModel();
        clock.initByName("distr", base, "clock.rate", meanRate, "tree", tree, "rateCategories", categories);
        // Native initialization randomizes categories. Restore the matching state before any rate calculation.
        int[] currentCategories = categories(model);
        for (int i = 0; i < currentCategories.length; i++) categories.set(i, currentCategories[i]);
        assertNotSame(model.tree().getRoot(), tree.getRoot());
        assertNotSame(model.categories(), categories);
        assertNotSame(model.clock().getMeanRate(), meanRate);
        assertNotSame(model.clock().rateDistInput.get(), base);
        assertNotSame(model.likelihood().dataInput.get(), alignment);
        JukesCantor substitution = new JukesCantor();
        substitution.initAndValidate();
        SiteModel siteModel = new SiteModel();
        siteModel.initByName("substModel", substitution);
        TreeLikelihood likelihood = new TreeLikelihood();
        likelihood.initByName(
                "data",
                alignment,
                "tree",
                tree,
                "siteModel",
                siteModel,
                "branchRateModel",
                clock,
                "implementation",
                TreeLikelihood.class.getName(),
                "useAmbiguities",
                false,
                "useTipLikelihoods",
                false);
        assertInstanceOf(BeerLikelihoodCore4.class, likelihood.getLikelihoodCore());
        assertArrayEquals(branchRates(model), branchRates(tree, clock), PRIOR_TOLERANCE);
        assertEquals(1.0, model.clock().getRateForBranch(model.tree().getRoot()), 0.0);
        double nativeLikelihood = likelihood.calculateLogP();
        double nativePrior = nativePrior(model);
        assertValues(new Values(nativePrior, nativeLikelihood, nativePrior + nativeLikelihood), currentValues(model));
    }

    private static double nativePrior(Model model) {
        if (model.source() == null) return 0.0;
        double value = model.source().get();
        Distribution prior;
        double expected;
        if (model.prior() instanceof Gamma) {
            Gamma gamma = new Gamma();
            gamma.initByName(
                    "param",
                    new RealScalarParam<>(value, PositiveReal.INSTANCE),
                    "alpha",
                    new RealScalarParam<>(2.0, PositiveReal.INSTANCE),
                    "lambda",
                    new RealScalarParam<>(1.0, PositiveReal.INSTANCE));
            prior = gamma;
            expected = Math.log(value) - value;
        } else {
            prior = new Normal(
                    new RealScalarParam<>(value, Real.INSTANCE),
                    new RealScalarParam<>(0.0, Real.INSTANCE),
                    new RealScalarParam<>(1.0, PositiveReal.INSTANCE));
            expected = -0.5 * Math.log(2.0 * Math.PI) - 0.5 * value * value;
        }
        double actual = prior.calculateLogP();
        assertEquals(expected, actual, PRIOR_TOLERANCE);
        return actual;
    }

    private static void assertValues(Values expected, Values actual) {
        assertTrue(Double.isFinite(expected.posterior()));
        assertTrue(Double.isFinite(actual.posterior()));
        assertEquals(expected.prior(), actual.prior(), PRIOR_TOLERANCE);
        assertEquals(expected.likelihood(), actual.likelihood(), LIKELIHOOD_TOLERANCE);
        assertEquals(expected.posterior(), actual.posterior(), LIKELIHOOD_TOLERANCE);
        assertEquals(actual.prior() + actual.likelihood(), actual.posterior(), LIKELIHOOD_TOLERANCE);
    }

    private static Values currentValues(Model model) {
        return new Values(
                model.prior() == null ? 0.0 : model.prior().getCurrentLogP(),
                model.likelihood().getCurrentLogP(),
                model.posterior().getCurrentLogP());
    }

    private static int[] categories(Model model) {
        return model.categories().getElements().stream()
                .mapToInt(Integer::intValue)
                .toArray();
    }

    private static double[] branchRates(Model model) {
        return branchRates(model.tree(), model.clock());
    }

    private static double[] branchRates(Tree tree, UCRelaxedClockModel clock) {
        return Arrays.stream(tree.getNodesAsArray())
                .filter(node -> !node.isRoot())
                .mapToDouble(clock::getRateForBranch)
                .toArray();
    }

    private static Model build(String rateDeclaration, String sourceName, double baseMean) throws Exception {
        String source = "Alignment data = fromNexus(file=\"" + DATA.getFileName() + "\")\n"
                + "Tree tree = fromNewick(newickString=\"" + FIXED_TREE + "\")\n"
                + rateDeclaration
                + "Vector<PositiveReal> branchRates ~ RelaxedClock(base=LogNormal(mean=" + baseMean
                + ", logSd=0.5), clockRate=rate, tree=tree)\n"
                + "Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=jc69(), branchRates=branchRates) observed as data\n";
        Randomizer.setSeed(12345);
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver(), new Workspace(DATA.getParent())).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("relaxed-clock-test", DATA.getParent()));
        catalog.configureState(graph);
        TreeLikelihood likelihood = assertInstanceOf(TreeLikelihood.class, graph.likelihoodDistributions.getFirst());
        likelihood.implementationInput.setValue(TreeLikelihood.class.getName(), likelihood);
        graph.initializeBEASTObjects();
        UCRelaxedClockModel clock = assertInstanceOf(UCRelaxedClockModel.class, likelihood.branchRateModelInput.get());
        Tree tree = clock.treeInput.get();
        IntVectorParam<?> categories = assertInstanceOf(IntVectorParam.class, clock.categoryInput.get());
        RealScalarParam<?> sampled = sourceName == null
                ? null
                : assertInstanceOf(
                        RealScalarParam.class,
                        graph.stateNodes.keySet().stream()
                                .filter(node -> sourceName.equals(node.getID()))
                                .findFirst()
                                .orElseThrow());
        Distribution prior = sampled == null ? null : graph.priorDistributions.get(sampled);
        List<Distribution> terms = new ArrayList<>(graph.priorDistributions.values());
        terms.add(likelihood);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", terms);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        // This fixed tree is not in State, so State cannot clear its initial dirty flags.
        tree.setEverythingDirty(false);
        return new Model(graph, tree, clock, categories, sampled, prior, likelihood, posterior, state);
    }

    private record Values(double prior, double likelihood, double posterior) {}

    private record Model(
            BEASTState graph,
            Tree tree,
            UCRelaxedClockModel clock,
            IntVectorParam<?> categories,
            RealScalarParam<?> source,
            Distribution prior,
            TreeLikelihood likelihood,
            CompoundDistribution posterior,
            State state) {}
}
