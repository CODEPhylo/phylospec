package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.Loggable;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.evolution.tree.MRCAPrior;
import beast.base.spec.inference.distribution.Uniform;
import beastconfig.BEASTState;
import beastconfig.LoggerSelector;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import tiling.RootAgeValue;

class RootAgeTileTest {

    @Test
    void fixedTreeSupportsZeroLiveChangesAndRootReplacement() throws Exception {
        Model model = build(
                "Tree tree = fromNewick(newickString=\"(A:0.0,B:0.0);\")",
                "NonNegativeReal height = rootAge(tree=tree)");

        assertTrue(model.graph().stateNodes.isEmpty());
        assertTrue(model.graph().priorDistributions.isEmpty());
        assertTrue(model.graph().likelihoodDistributions.isEmpty());
        assertTrue(model.graph().operators.isEmpty());
        assertEquals(1, model.graph().calculationNodes.size());
        assertSame(NonNegativeReal.INSTANCE, model.value().getDomain());
        assertValue(model, 0.0);

        model.tree().getRoot().setHeight(2.0);
        assertValue(model, 2.0);

        // Replace the nodes of a fixed tree, outside an MCMC proposal.
        Node oldRoot = model.tree().getRoot();
        model.tree().assignFrom(new TreeParser("(A:3.0,B:3.0);"));

        assertNotSame(oldRoot, model.tree().getRoot());
        assertValue(model, 3.0);
    }

    @Test
    void defaultLoggersIncludeTheDerivedValue() throws Exception {
        BEASTState graph = compile("""
                Tree tree = fromNewick(newickString="(A:2.0,B:2.0);")
                NonNegativeReal height = rootAge(tree=tree)
                """);

        assertEquals(1, graph.calculationNodes.size());
        RootAgeValue value = assertInstanceOf(
                RootAgeValue.class, graph.calculationNodes.keySet().iterator().next());

        LoggerSelector.addMissingLoggerSpecs(graph);

        assertTrue(graph.fileLoggerSpecs.getFirst().parameters().contains(value));
        assertTrue(graph.screenLoggerSpecs.getFirst().parameters().contains(value));
        assertEquals(2.0, loggedValue(value), 0.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sampledTreeFollowsAcceptanceAndRejection(boolean calibrated) throws Exception {
        BEASTState baseline = compile(randomTreeSource());
        String declaration = "NonNegativeReal height = rootAge(tree=tree)"
                + (calibrated ? " observed between [0.0, 1000000.0]" : "");
        Model model = build(randomTreeSource(), declaration);
        BEASTState graph = model.graph();

        assertEquals(1, graph.stateNodes.size());
        assertTrue(graph.stateNodes.containsKey(model.tree()));
        assertFalse(graph.stateNodes.containsKey(model.value()));
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(calibrated ? 1 : 0, graph.likelihoodDistributions.size());
        assertEquals(1, graph.calculationNodes.size());
        assertTrue(graph.calculationNodes.containsKey(model.value()));
        assertTrue(model.tree().getOutputs().contains(model.value()));
        assertSame(NonNegativeReal.INSTANCE, model.value().getDomain());
        assertEquals(
                baseline.operators.stream().map(Object::getClass).toList(),
                graph.operators.stream().map(Object::getClass).toList());

        MRCAPrior calibration = null;
        if (calibrated) {
            calibration = assertInstanceOf(MRCAPrior.class, graph.likelihoodDistributions.getFirst());
            assertSame(model.tree(), calibration.treeInput.get());
            assertInstanceOf(Uniform.class, calibration.distInput.get());
            assertFalse(graph.priorDistributions.containsValue(calibration));
        }

        CompoundDistribution posterior = posterior(graph);
        State state = new State();
        state.initByName("stateNode", List.of(model.tree()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        assertValue(model, model.tree().getRoot().getHeight());

        // Exercise acceptance, rejection, and another proposal after rejection.
        boolean[] decisions = {true, false, true};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            double previousHeight = model.tree().getRoot().getHeight();
            double previousLogP = posterior.getCurrentLogP();
            Node previousRoot = model.tree().getRoot();
            double proposedHeight = previousHeight + 0.25;

            state.store(step + 1);
            model.tree().getRoot().setHeight(proposedHeight);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            double proposedLogP = posterior.calculateLogP();
            assertTrue(Double.isFinite(proposedLogP));
            assertValue(model, proposedHeight);

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
                assertNotSame(previousRoot, model.tree().getRoot());
            }
            state.setEverythingDirty(false);

            assertValue(model, accept ? proposedHeight : previousHeight);
            assertEquals(accept ? proposedLogP : previousLogP, posterior.calculateLogP(), 1e-12);
        }

        if (calibrated) {
            assertNotNull(calibration);
            assertEquals(-Math.log(1000000.0), calibration.calculateLogP(), 1e-12);

            double previousHeight = model.value().get();
            double previousLogP = posterior.getCurrentLogP();

            state.store(4);
            model.tree().getRoot().setHeight(1000001.0);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            assertValue(model, 1000001.0);
            assertEquals(Double.NEGATIVE_INFINITY, calibration.calculateLogP());
            assertEquals(Double.NEGATIVE_INFINITY, posterior.calculateLogP());

            state.restore();
            state.restoreCalculationNodes();
            state.setEverythingDirty(false);

            assertValue(model, previousHeight);
            assertEquals(previousLogP, posterior.calculateLogP(), 1e-12);
        }
    }

    private static Model build(String treeSource, String declaration) throws Exception {
        BEASTState graph = compile(treeSource + "\n" + declaration + """

                mcmc {
                    Logger trace = fileLogger(
                        logEvery=1, file="root-age.log", parameters=[height])
                }
                """);

        assertEquals(1, graph.fileLoggerSpecs.size());
        RootAgeValue value = assertInstanceOf(
                RootAgeValue.class,
                graph.fileLoggerSpecs.getFirst().parameters().getFirst());
        return new Model(graph, value.treeInput.get(), value);
    }

    private static BEASTState compile(String source) throws Exception {
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

        BEASTState graph = evaluator.applyBestTiling(new BEASTState("root-age-test"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }

    private static String randomTreeSource() {
        return """
                Alignment data = fromNexus(file="%s")
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa(data))
                """.formatted(
                        Path.of("src/test/java/resources/primate-mtDNA.nex").toAbsolutePath());
    }

    private static CompoundDistribution posterior(BEASTState graph) throws Exception {
        List<Distribution> factors = new ArrayList<>(graph.priorDistributions.values());
        factors.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        return posterior;
    }

    private static void assertValue(Model model, double expected) {
        assertEquals(expected, model.tree().getRoot().getHeight(), 0.0);
        assertEquals(expected, model.value().get(), 0.0);
        assertEquals(expected, loggedValue(model.value()), 0.0);
    }

    private static double loggedValue(Loggable value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            value.log(0, out);
        }
        return Double.parseDouble(bytes.toString(StandardCharsets.UTF_8).trim());
    }

    private record Model(BEASTState graph, Tree tree, RootAgeValue value) {}
}
