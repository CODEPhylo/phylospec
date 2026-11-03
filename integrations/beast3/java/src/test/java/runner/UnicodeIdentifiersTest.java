package runner;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.inference.util.RPNcalculator;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

class UnicodeIdentifiersTest {
    private static final double TOLERANCE = 1e-8;
    // The same fixed primate tree used by HkyYuleStateTest's independent native comparison.
    private static final String FIXED_TREE = "(((((Homo_sapiens:0.1,Pan:0.1):0.1,Gorilla:0.2):0.1,"
            + "(Pongo:0.15,Hylobates:0.15):0.15):0.3,"
            + "(((Macaca_fuscata:0.1,M._mulatta:0.1):0.1,"
            + "(M._fascicularis:0.1,M._sylvanus:0.1):0.1):0.2,Saimiri_sciureus:0.4):0.2):0.4,"
            + "(Tarsius_syrichta:0.5,Lemur_catta:0.5):0.5);";

    // Explicitly named equivalents of the packaged HKY/Yule example's inline priors.
    static String greekHkyModel(String data) {
        return """
                Alignment data = fromNexus("%s")
                Tree θ ~ Yule(birthRate=1.0, taxa=taxa(data))
                PositiveReal κ ~ LogNormal(logMean=1.0, logSd=0.5)
                Simplex π ~ Dirichlet(repeat(value=1.0, num=4))
                PositiveReal μ = 1.0
                Alignment alignment ~ PhyloCTMC(
                    tree=θ,
                    branchRates~StrictClock(clockRate=μ, tree=θ),
                    qMatrix=hky(kappa=κ, baseFrequencies=π)
                ) observed as data
                mcmc {
                    Integer chainLength = 30
                    Integer defaultLogEvery = 1
                    Integer randomSeed = 12345
                    String outputPrefix = "unicode-hky"
                }
                """.formatted(data);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void greekAndAsciiHkyModelsHaveIdenticalBindingsAndProbabilities(boolean nonuniform) throws Exception {
        String greekSource = greekHkyModel("src/main/distribution/examples/data/primate-mtDNA.nex");
        String asciiSource = greekSource
                .replace("θ", "tree")
                .replace("κ", "kappa")
                .replace("π", "baseFrequencies")
                .replace("μ", "clockRate");
        assertNotNull(new PhyloSpecRunner(greekSource, List.of("beast2")).check());
        Model greek = build(greekSource);
        Model ascii = build(asciiSource);
        assertEquals(Set.of("θ", "κ", "π"), ids(greek.graph()));
        assertEquals(Set.of("tree", "kappa", "baseFrequencies"), ids(ascii.graph()));
        setFixedState(greek, "θ", "κ", "π", nonuniform);
        setFixedState(ascii, "tree", "kappa", "baseFrequencies", nonuniform);

        TreeLikelihood likelihood = assertInstanceOf(
                TreeLikelihood.class, greek.graph().likelihoodDistributions.getFirst());
        SiteModel siteModel = assertInstanceOf(SiteModel.class, likelihood.siteModelInput.get());
        HKY hky = assertInstanceOf(HKY.class, siteModel.substModelInput.get());
        assertSame(node(greek, "θ"), likelihood.treeInput.get());
        assertSame(node(greek, "κ"), hky.kappaInput.get());
        assertSame(node(greek, "π"), hky.frequenciesInput.get().frequenciesInput.get());
        StrictClockModel clock = assertInstanceOf(StrictClockModel.class, likelihood.branchRateModelInput.get());
        assertEquals(1.0, clock.meanRateInput.get().get());
        assertEquals(12, likelihood.dataInput.get().getTaxonCount());
        assertEquals(898, likelihood.dataInput.get().getSiteCount());
        assertEquals(3, greek.graph().priorDistributions.size());
        assertEquals(1, greek.graph().likelihoodDistributions.size());
        for (String[] names : List.of(
                new String[] {"θ", "tree"}, new String[] {"κ", "kappa"}, new String[] {"π", "baseFrequencies"})) {
            Distribution prior = greek.graph().priorDistributions.get(node(greek, names[0]));
            assertEquals(names[0] + "_prior", prior.getID());
            assertEquals(
                    ascii.graph().priorDistributions.get(node(ascii, names[1])).getCurrentLogP(),
                    prior.getCurrentLogP(),
                    TOLERANCE);
        }
        assertEquals(
                ascii.graph().likelihoodDistributions.getFirst().getCurrentLogP(),
                likelihood.getCurrentLogP(),
                TOLERANCE);
        assertEquals(ascii.posterior().getCurrentLogP(), greek.posterior().getCurrentLogP(), TOLERANCE);
        double prior = greek.graph().priorDistributions.values().stream()
                .mapToDouble(Distribution::getCurrentLogP)
                .sum();
        assertEquals(prior + likelihood.getCurrentLogP(), greek.posterior().getCurrentLogP(), TOLERANCE);
    }

    @Test
    void greekRpnAndDownstreamBindingsFollowProposalsAndRestoration() throws Exception {
        Model model = build("""
                Real μ ~ Normal(mean=1.0, sd=2.0)
                Real κ ~ Normal(mean=1.0, sd=2.0)
                Real ρ = μ + κ * μ
                Real ψ ~ Normal(mean=μ, sd=0.5) observed as -0.4
                """);
        RealScalarParam<?> mean = assertInstanceOf(RealScalarParam.class, node(model, "μ"));
        RealScalarParam<?> kappa = assertInstanceOf(RealScalarParam.class, node(model, "κ"));
        RPNcalculator calculation = assertInstanceOf(
                RPNcalculator.class,
                model.graph().calculationNodes.keySet().stream()
                        .filter(node -> "ρ".equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
        Normal likelihood = assertInstanceOf(
                Normal.class, model.graph().likelihoodDistributions.getFirst());
        assertSame(mean, likelihood.meanInput.get());
        assertEquals(Set.of("μ", "κ"), Set.copyOf(calculation.getArguments()));
        mean.set(0.5);
        kappa.set(1.5);
        model.state().robustlyCalcPosterior(model.posterior());
        assertEquals(1.25, calculation.getStatisticValue(0), TOLERANCE);

        for (boolean accept : new boolean[] {false, true}) {
            double previousMean = mean.get();
            double previousLikelihood = likelihood.getCurrentLogP();
            double previousPosterior = model.posterior().getCurrentLogP();
            model.state().store(1);
            mean.set(0.75);
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            double proposedPosterior = model.posterior().calculateLogP();
            double proposedLikelihood = likelihood.getCurrentLogP();
            assertTrue(Double.isFinite(proposedPosterior));
            assertNotEquals(previousLikelihood, proposedLikelihood, TOLERANCE);
            assertEquals(1.875, calculation.getStatisticValue(0), TOLERANCE);
            assertEquals(0.75, likelihood.meanInput.get().get());
            if (accept) {
                model.state().acceptCalculationNodes();
            } else {
                model.state().restore();
                model.state().restoreCalculationNodes();
            }
            model.state().setEverythingDirty(false);
            assertEquals(accept ? 0.75 : previousMean, mean.get());
            assertEquals(mean.get() * (1.0 + kappa.get()), calculation.getStatisticValue(0), TOLERANCE);
            assertSame(mean, likelihood.meanInput.get());
            assertEquals(accept ? proposedLikelihood : previousLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    accept ? proposedPosterior : previousPosterior,
                    model.posterior().getCurrentLogP(),
                    TOLERANCE);
        }
    }

    @Test
    void greekScopedIndicesBindEachElementToItsOwnParameter() throws Exception {
        Model model = build("""
                Real μ[ι] ~ Normal(mean=1.0, sd=1.0) for ι in 1:2
                Real θ[ι] ~ Normal(mean=μ[ι], sd=1.0) for ι in 1:2
                """);
        assertEquals(Set.of("μ_1", "μ_2", "θ_1", "θ_2"), ids(model.graph()));
        for (int index = 1; index <= 2; index++) {
            RealScalarParam<?> upstream = assertInstanceOf(RealScalarParam.class, node(model, "μ_" + index));
            Normal downstream = assertInstanceOf(
                    Normal.class, model.graph().priorDistributions.get(node(model, "θ_" + index)));
            assertSame(upstream, downstream.meanInput.get());
            upstream.set(index + 0.25);
            assertEquals(index + 0.25, downstream.meanInput.get().get());
        }
        assertEquals(
                1.25,
                assertInstanceOf(RealScalarParam.class, node(model, "μ_1")).get());
        assertTrue(Double.isFinite(model.state().robustlyCalcPosterior(model.posterior())));
    }

    private static Set<String> ids(BEASTState graph) {
        return graph.stateNodes.keySet().stream().map(StateNode::getID).collect(Collectors.toSet());
    }

    private static StateNode node(Model model, String id) {
        return model.graph().stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static void setFixedState(
            Model model, String treeId, String kappaId, String frequenciesId, boolean nonuniform) {
        TreeLikelihood likelihood = assertInstanceOf(
                TreeLikelihood.class, model.graph().likelihoodDistributions.getFirst());
        Tree tree = assertInstanceOf(Tree.class, node(model, treeId));
        TreeParser fixed = new TreeParser();
        fixed.initByName(
                "taxa",
                likelihood.dataInput.get(),
                "newick",
                FIXED_TREE,
                "IsLabelledNewick",
                true,
                "adjustTipHeights",
                false);
        fixed.setID(treeId);
        for (int index = 0; index < fixed.getLeafNodeCount(); index++) {
            assertEquals(tree.getNode(index).getID(), fixed.getNode(index).getID());
        }
        tree.assignFrom(fixed);
        assertInstanceOf(RealScalarParam.class, node(model, kappaId)).set(nonuniform ? 4.25 : Math.exp(1.0));
        SimplexParam frequencies = assertInstanceOf(SimplexParam.class, node(model, frequenciesId));
        double[] values = nonuniform ? new double[] {0.30, 0.20, 0.15, 0.35} : new double[] {0.25, 0.25, 0.25, 0.25};
        for (int index = 0; index < values.length; index++) frequencies.set(index, values[index]);
        if (nonuniform) tree.getRoot().setHeight(1.15);
        assertTrue(Double.isFinite(model.state().robustlyCalcPosterior(model.posterior())));
    }

    private static Model build(String source) throws Exception {
        Randomizer.setSeed(12345);
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("unicode-identifiers", Path.of("")));
        catalog.configureState(graph);
        for (Distribution distribution : graph.likelihoodDistributions) {
            if (distribution instanceof TreeLikelihood likelihood) {
                likelihood.implementationInput.setValue(TreeLikelihood.class.getName(), likelihood);
            }
        }
        graph.initializeBEASTObjects();
        var distributions = new ArrayList<>(graph.priorDistributions.values());
        distributions.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", distributions);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        return new Model(graph, posterior, state);
    }

    private record Model(BEASTState graph, CompoundDistribution posterior, State state) {}
}
