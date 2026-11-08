package tiles.sitemodels;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.likelihood.BeerLikelihoodCore4;
import beast.base.evolution.tree.Tree;
import beast.base.inference.CalculationNode;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.parser.NexusParser;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.evolution.substitutionmodel.JukesCantor;
import beast.base.spec.inference.distribution.Gamma;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;
import runner.PhyloSpecDiagnostic;
import runner.PhyloSpecException;
import runner.PhyloSpecRunner;
import tiling.ExpValue;

class SiteModelTileTest {
    private static final Path DATA = Path.of("src/test/java/resources/primate-mtDNA.nex")
            .toAbsolutePath()
            .normalize();
    private static final double PARAMETER_TOLERANCE = 1e-12;
    private static final double LIKELIHOOD_TOLERANCE = 1e-8;
    private static final String FIXED_TREE = "(((((Homo_sapiens:0.1,Pan:0.1):0.1,Gorilla:0.2):0.1,"
            + "(Pongo:0.15,Hylobates:0.15):0.15):0.3,"
            + "(((Macaca_fuscata:0.1,M._mulatta:0.1):0.1,"
            + "(M._fascicularis:0.1,M._sylvanus:0.1):0.1):0.2,Saimiri_sciureus:0.4):0.2):0.4,"
            + "(Tarsius_syrichta:0.5,Lemur_catta:0.5):0.5);";

    @ParameterizedTest
    @CsvSource({"1.0, 0.0", "2.0, 0.0", "1.0, 0.2", "2.0, 0.2"})
    void fixedShapesKeepTheExistingNativeSiteModel(double shape, double invariant) throws Exception {
        Model model = build("PositiveReal shape = " + shape + "\n", null, invariant);
        assertConfiguration(model, invariant);
        assertInstanceOf(
                RealScalarParam.class, model.siteModel().shapeParameterInput.get());
        assertEquals(shape, model.siteModel().shapeParameterInput.get().get(), 0.0);
        assertNativeMatches(model);
    }

    @ParameterizedTest
    @CsvSource({
        "gamma, false, 0.0", "gamma, true, 0.0", "gamma, false, 0.2", "gamma, true, 0.2",
        "derived, false, 0.0", "derived, true, 0.0", "derived, false, 0.2", "derived, true, 0.2"
    })
    void sampledAndDerivedShapesUpdateLikelihoodsAndRestoreState(String family, boolean acceptFirst, double invariant)
            throws Exception {
        String declaration = family.equals("gamma")
                ? "PositiveReal shape ~ Gamma(shape=2.0, rate=1.0)\n"
                : "Real logShape ~ Normal(mean=0.0, sd=1.0)\nPositiveReal shape = exp(x=logShape)\n";
        Model model = build(declaration, family.equals("gamma") ? "shape" : "logShape", invariant);
        assertConfiguration(model, invariant);
        RealScalarParam<?> source = model.source();
        if (family.equals("gamma")) {
            assertSame(source, model.siteModel().shapeParameterInput.get());
            assertSame(PositiveReal.INSTANCE, source.getDomain());
            assertEquals(1.0, source.get(), 0.0);
            Gamma prior = assertInstanceOf(Gamma.class, model.prior());
            assertSame(source, prior.paramInput.get());
            assertEquals(2.0, prior.alphaInput.get().get(), 0.0);
            assertEquals(1.0, prior.betaInput.get().get(), 0.0);
        } else {
            ExpValue shape = assertInstanceOf(
                    ExpValue.class, model.siteModel().shapeParameterInput.get());
            assertSame(source, shape.xInput.get());
            assertSame(Real.INSTANCE, source.getDomain());
            assertEquals(0.1, source.get(), 0.0);
            assertTrue(source.getOutputs().contains(shape));
            assertTrue(shape.getOutputs().contains(model.siteModel()));
            assertTrue(model.graph().calculationNodes.containsKey(shape));
            Normal prior = assertInstanceOf(Normal.class, model.prior());
            assertSame(source, prior.paramInput.get());
            assertEquals(0.0, prior.meanInput.get().get(), 0.0);
            assertEquals(1.0, prior.sdInput.get().get(), 0.0);
        }
        assertNativeMatches(model);
        double[] initialProportions = proportions(model);
        double[] proposals = family.equals("gamma") ? new double[] {1.5, 0.6, 2.0} : new double[] {-0.5, 0.0, 0.7};
        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        for (int step = 0; step < proposals.length; step++) {
            double previousSource = source.get();
            double[] previousRates = rates(model);
            Values previous = currentValues(model);
            model.state().store(step + 1);
            source.set(proposals[step]);
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            assertFalse(model.tree().somethingIsDirty(), "The fixed tree must not drive likelihood updates.");
            assertTrue(model.siteModel().somethingIsDirty());
            assertTrue(model.likelihood().somethingIsDirty());
            assertTrue(Double.isFinite(model.posterior().calculateLogP()));
            Values proposed = currentValues(model);
            double expectedShape = family.equals("gamma") ? proposals[step] : Math.exp(proposals[step]);
            assertEquals(
                    expectedShape, model.siteModel().shapeParameterInput.get().get(), 0.0);
            assertNotEquals(previous.likelihood(), proposed.likelihood(), LIKELIHOOD_TOLERANCE);
            double[] proposedRates = rates(model);
            assertFalse(Arrays.equals(previousRates, proposedRates), "Shape changes must update category rates.");
            assertArrayEquals(initialProportions, proportions(model), PARAMETER_TOLERANCE);
            // Use a fresh native graph; never evaluate this still-dirty likelihood a second time.
            assertNativeMatches(model);

            if (decisions[step]) {
                model.state().acceptCalculationNodes();
            } else {
                model.state().restore();
                model.state().restoreCalculationNodes();
            }
            model.state().setEverythingDirty(false);
            assertEquals(decisions[step] ? proposals[step] : previousSource, source.get(), 0.0);
            assertEquals(family.equals("gamma") ? "shape" : "logShape", source.getID());
            // First inspect cached densities. SiteModel may then lazily regenerate its restored rates.
            assertValues(decisions[step] ? proposed : previous, currentValues(model));
            assertArrayEquals(decisions[step] ? proposedRates : previousRates, rates(model), PARAMETER_TOLERANCE);
            assertArrayEquals(initialProportions, proportions(model), PARAMETER_TOLERANCE);
            assertEquals(invariant, model.siteModel().invarParameterInput.get().get(), 0.0);
            assertNativeMatches(model);
        }
    }

    @Test
    void unrestrictedRandomRealIsStillRejectedAsShape() {
        assertThrows(TypeError.class, () -> build("Real shape ~ Normal(mean=0.0, sd=1.0)\n", "shape", 0.0));
    }

    private static void assertConfiguration(Model model, double invariant) {
        BEASTState graph = model.graph();
        boolean sampled = model.source() != null;
        assertEquals(sampled ? 1 : 0, graph.stateNodes.size());
        assertEquals(sampled ? 1 : 0, graph.priorDistributions.size());
        assertEquals(sampled ? 1 : 0, graph.operators.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertFalse(model.tree().isEstimatedInput.get());
        assertFalse(model.tree().somethingIsDirty());
        assertFalse(graph.stateNodes.containsKey(model.tree()));
        assertSame(model.siteModel(), model.likelihood().siteModelInput.get());
        assertSame(
                PositiveReal.INSTANCE,
                model.siteModel().shapeParameterInput.get().getDomain());
        assertEquals(4, model.siteModel().gammaCategoryCount.get());
        assertEquals(invariant, model.siteModel().invarParameterInput.get().get(), 0.0);
        assertSame(
                UnitInterval.INSTANCE,
                model.siteModel().invarParameterInput.get().getDomain());
        assertTrue(assertInstanceOf(
                        CalculationNode.class,
                        model.siteModel().shapeParameterInput.get())
                .getOutputs()
                .contains(model.siteModel()));
        assertTrue(model.siteModel().getOutputs().contains(model.likelihood()));
        assertInstanceOf(BeerLikelihoodCore4.class, model.likelihood().getLikelihoodCore());
        StrictClockModel clock = assertInstanceOf(
                StrictClockModel.class, model.likelihood().branchRateModelInput.get());
        assertEquals(1.0, clock.meanRateInput.get().get(), 0.0);
        if (sampled) {
            ScaleOperator operator = assertInstanceOf(ScaleOperator.class, graph.operators.getFirst());
            assertEquals(List.of(model.source()), operator.listStateNodes());
            assertEquals(5.0, operator.m_pWeight.get(), 0.0);
        }
    }

    private static void assertNativeMatches(Model model) throws Exception {
        NexusParser parser = new NexusParser();
        parser.parseFile(DATA.toFile());
        Alignment alignment = parser.m_alignment;
        Tree tree = new Tree(model.tree().getRoot().copy());
        tree.isEstimatedInput.setValue(false, tree);
        JukesCantor substitution = new JukesCantor();
        substitution.initAndValidate();
        RealScalarParam<PositiveReal> shape = new RealScalarParam<>(
                model.siteModel().shapeParameterInput.get().get(), PositiveReal.INSTANCE);
        RealScalarParam<UnitInterval> invariant = new RealScalarParam<>(
                model.siteModel().invarParameterInput.get().get(), UnitInterval.INSTANCE);
        SiteModel siteModel = new SiteModel();
        // Preserve the native gamma-discretization algorithm and the mapped category settings.
        siteModel.initByName(
                "substModel", substitution, "shape", shape, "gammaCategoryCount", 4, "proportionInvariant", invariant);
        StrictClockModel clock = new StrictClockModel();
        clock.initByName("clock.rate", new RealScalarParam<>(1.0, PositiveReal.INSTANCE));
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
        assertNotSame(model.tree().getRoot(), tree.getRoot());
        assertNotSame(model.likelihood().dataInput.get(), alignment);
        assertNotSame(model.siteModel(), siteModel);
        assertNotSame(model.siteModel().shapeParameterInput.get(), shape);
        assertNotSame(model.siteModel().invarParameterInput.get(), invariant);
        assertInstanceOf(BeerLikelihoodCore4.class, likelihood.getLikelihoodCore());
        // TreeLikelihood controls whether invariant sites have a separate category; compare after its initialization.
        assertEquals(siteModel.getCategoryCount(), model.siteModel().getCategoryCount());
        assertArrayEquals(siteModel.getCategoryRates(null), rates(model), PARAMETER_TOLERANCE);
        assertArrayEquals(siteModel.getCategoryProportions(null), proportions(model), PARAMETER_TOLERANCE);
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
        assertEquals(expected, actual, PARAMETER_TOLERANCE);
        return actual;
    }

    private static void assertValues(Values expected, Values actual) {
        assertTrue(Double.isFinite(expected.posterior()));
        assertTrue(Double.isFinite(actual.posterior()));
        assertEquals(expected.prior(), actual.prior(), PARAMETER_TOLERANCE);
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

    private static double[] rates(Model model) {
        return model.siteModel().getCategoryRates(null);
    }

    private static double[] proportions(Model model) {
        // Native SiteModel returns its internal proportions array, which is overwritten on recalculation.
        return model.siteModel().getCategoryProportions(null).clone();
    }

    @Test
    void sharedSiteRatesKeepDistinctPartitionModelsAndLiveRateChecks(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("input.fa"), ">A\nA\n>B\nC\n");
        String source = """
                Alignment data = fromFasta(file="input.fa")
                Tree tree = fromNewick(newickString="(A:1.0,B:1.0);")
                Vector<PositiveReal> categories ~ DiscreteGammaInv(shape=2.0,numCategories=2,numSites=1)
                Simplex w ~ Dirichlet(concentration=[1.0,1.0])
                Alignment a ~ PhyloCTMC(tree=tree,qMatrix=jc69(),siteRates=categories,rate=2.0*w[1]) observed as data
                Alignment b ~ PhyloCTMC(tree=tree,qMatrix=jc69(),siteRates=categories,rate=1.5) observed as data
                """;
        BEASTState graph = compile(source, directory);
        var likelihoods = graph.likelihoodDistributions.stream()
                .map(TreeLikelihood.class::cast)
                .sorted(java.util.Comparator.comparing(TreeLikelihood::getID))
                .toList();
        var first = (SiteModel) likelihoods.get(0).siteModelInput.get();
        var second = (SiteModel) likelihoods.get(1).siteModelInput.get();
        assertNotSame(first, second);
        assertSame(first.shapeParameterInput.get(), second.shapeParameterInput.get());
        assertSame(first.invarParameterInput.get(), second.invarParameterInput.get());
        assertEquals(1.0, first.muParameterInput.get().get());
        assertEquals(1.5, second.muParameterInput.get().get());
        assertTrue(Double.isFinite(likelihoods.get(0).calculateLogP()));
        var w = assertInstanceOf(
                beast.base.spec.inference.parameter.SimplexParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(node -> "w".equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
        w.set(0, 0.0);
        w.set(1, 1.0);
        var error = assertThrows(IllegalArgumentException.class, () -> first.getRateForCategory(0, null));
        assertTrue(error.getMessage().contains("strictly positive PhyloCTMC rate; received 0.0"));
        assertEquals(1.5, second.muParameterInput.get().get());
    }

    @Test
    void zeroPartitionRateIsReportedDuringConstruction(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("input.fa"), ">A\nA\n>B\nC\n");
        String source = """
                Alignment data = fromFasta(file="input.fa")
                Tree tree = fromNewick(newickString="(A:1.0,B:1.0);")
                Alignment a ~ PhyloCTMC(tree=tree,qMatrix=jc69(),rate=0.0) observed as data
                """;
        var error =
                assertThrows(org.phylospec.tiling.errors.TileApplicationError.class, () -> compile(source, directory));
        assertTrue(error.getMessage().contains("strictly positive PhyloCTMC rate; received 0.0"), error::getMessage);
    }

    @Test
    void sampledSimplexIndexIsRejectedInsteadOfFrozenAtInitialization() {
        String source = """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Tree tree ~ Yule(birthRate=1.0,taxa=taxa(data))
                Simplex w ~ Dirichlet(concentration=[1.0,1.0])
                NonNegativeInteger i ~ Poisson(rate=1.0)
                Alignment a ~ PhyloCTMC(tree=tree,qMatrix=jc69(),rate=2.0*w[i]) observed as data
                """;
        var failure =
                assertThrows(PhyloSpecException.class, () -> new PhyloSpecRunner(source, List.of("beast2")).check());
        assertEquals(PhyloSpecDiagnostic.Stage.MAPPING, failure.diagnostic().stage());
    }

    private static BEASTState compile(String source, Path directory) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver(), new Workspace(directory)).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("site-model-test", directory));
        catalog.configureState(graph);
        for (Distribution distribution : graph.likelihoodDistributions) {
            TreeLikelihood likelihood = assertInstanceOf(TreeLikelihood.class, distribution);
            likelihood.implementationInput.setValue(TreeLikelihood.class.getName(), likelihood);
        }
        graph.initializeBEASTObjects();
        return graph;
    }

    private static Model build(String shapeDeclaration, String sourceName, double invariant) throws Exception {
        String invariantArgument = invariant == 0.0 ? "" : ", invariantProportion=" + invariant;
        String source = "Alignment data = fromNexus(file=\"" + DATA.getFileName() + "\")\n"
                + "Tree tree = fromNewick(newickString=\"" + FIXED_TREE + "\")\n"
                + shapeDeclaration
                + "Vector<PositiveReal> siteRates ~ DiscreteGammaInv(shape=shape, numCategories=4, "
                + "numSites=numSites(alignment=data)" + invariantArgument + ")\n"
                + "Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=jc69(), siteRates=siteRates) observed as data\n";
        BEASTState graph = compile(source, DATA.getParent());
        TreeLikelihood likelihood = assertInstanceOf(TreeLikelihood.class, graph.likelihoodDistributions.getFirst());
        SiteModel siteModel = assertInstanceOf(SiteModel.class, likelihood.siteModelInput.get());
        Tree tree = assertInstanceOf(Tree.class, likelihood.treeInput.get());
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
        State state = null;
        if (sampled != null) {
            state = new State();
            state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
            state.initialise();
            state.setPosterior(posterior);
            assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        } else {
            // With no sampled nodes, explicitly initialize the likelihood cache for CompoundDistribution.
            assertTrue(Double.isFinite(likelihood.calculateLogP()));
            assertTrue(Double.isFinite(posterior.calculateLogP()));
        }
        // A fixed tree is outside State, so its initial dirty flags need clearing explicitly.
        tree.setEverythingDirty(false);
        return new Model(graph, tree, siteModel, sampled, prior, likelihood, posterior, state);
    }

    private record Values(double prior, double likelihood, double posterior) {}

    private record Model(
            BEASTState graph,
            Tree tree,
            SiteModel siteModel,
            RealScalarParam<?> source,
            Distribution prior,
            TreeLikelihood likelihood,
            CompoundDistribution posterior,
            State state) {}
}
