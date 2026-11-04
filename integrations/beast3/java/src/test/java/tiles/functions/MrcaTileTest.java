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
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import beastconfig.LoggerSelector;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.MrcaAgeValue;

class MrcaTileTest {
    private static final String NEWICK = "((A:1.0,B:1.0):2.0,(C:1.5,D:1.5):1.5);";
    private static final double OBSERVED = -0.4;
    private static final double SD = 0.5;
    private static final double TOLERANCE = 1e-12;

    private static Stream<Arguments> fixedSelections() {
        return Stream.of(
                Arguments.of("[\"A\"]", 0.0),
                Arguments.of("[\"A\", \"B\"]", 1.0),
                Arguments.of("[\"C\", \"D\"]", 1.5),
                Arguments.of("[\"A\", \"C\"]", 3.0),
                Arguments.of("[\"A\", \"B\", \"C\", \"D\"]", 3.0),
                Arguments.of("[\"B\", \"A\", \"B\"]", 1.0));
    }

    @ParameterizedTest
    @MethodSource("fixedSelections")
    void fixedTreesSupportSingletonsInternalNodesRootAndRepeatedNames(String clade, double expected) throws Exception {
        Model model = build(fixedTreeSource(NEWICK), clade);

        assertTrue(model.graph().stateNodes.isEmpty());
        assertTrue(model.graph().priorDistributions.isEmpty());
        assertTrue(model.graph().likelihoodDistributions.isEmpty());
        assertTrue(model.graph().operators.isEmpty());
        assertEquals(1, model.graph().calculationNodes.size());
        assertSame(NonNegativeReal.INSTANCE, model.value().getDomain());
        assertValue(model, expected);
    }

    @Test
    void zeroAgeInternalNodeAndDatedSingletonAreValid() throws Exception {
        assertValue(build(fixedTreeSource("((A:0.0,B:0.0):0.0,C:0.0);"), "[\"A\", \"B\"]"), 0.0);
        assertValue(build(fixedTreeSource("(A:1.0,B:2.0);"), "[\"A\"]"), 1.0);
    }

    @Test
    void fixedTreeValueFollowsHeightChangesAndReplacementNodes() throws Exception {
        Model model = build(fixedTreeSource(NEWICK), "[\"A\", \"B\"]");
        Node oldAncestor = leaf(model.tree(), "A").getParent();
        oldAncestor.setHeight(1.25);
        assertValue(model, 1.25);

        model.tree().assignFrom(new TreeParser("((A:2.0,B:2.0):2.0,(C:1.0,D:1.0):3.0);"));
        assertNotSame(oldAncestor, leaf(model.tree(), "A").getParent());
        assertValue(model, 2.0);
    }

    @Test
    void assignedCladeAndRepeatedConsumersShareOneLiveValue() throws Exception {
        BEASTState graph = compile(fixedTreeSource(NEWICK) + """

                Vector<String> selected = ["A", "B"]
                NonNegativeReal age = mrca(clade=selected, tree=tree)
                NonNegativeReal alias = age
                mcmc {
                    Logger trace = fileLogger(logEvery=1, file="mrca.log", parameters=[age, alias])
                }
                """);

        assertEquals(1, graph.calculationNodes.size());
        List<?> parameters = graph.fileLoggerSpecs.getFirst().parameters();
        assertEquals(2, parameters.size());
        MrcaAgeValue value = assertInstanceOf(MrcaAgeValue.class, parameters.getFirst());
        assertSame(value, parameters.get(1));
        leaf(value.treeInput.get(), "A").getParent().setHeight(1.25);
        assertEquals(1.25, loggedValue(value), 0.0);
    }

    @Test
    void defaultLoggersIncludeTheDerivedValue() throws Exception {
        BEASTState graph = compile(fixedTreeSource(NEWICK) + """

                NonNegativeReal age = mrca(clade=["A", "B"], tree=tree)
                """);
        MrcaAgeValue value = assertInstanceOf(
                MrcaAgeValue.class, graph.calculationNodes.keySet().iterator().next());

        LoggerSelector.addMissingLoggerSpecs(graph);

        assertTrue(graph.fileLoggerSpecs.getFirst().parameters().contains(value));
        assertTrue(graph.screenLoggerSpecs.getFirst().parameters().contains(value));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            value.init(out);
            value.close(out);
        }
        assertEquals(value.getID(), bytes.toString(StandardCharsets.UTF_8).trim());
        assertEquals(1.0, loggedValue(value), 0.0);
    }

    private static Stream<Arguments> invalidSelections() {
        return Stream.of(
                Arguments.of(List.of()),
                Arguments.of(List.of("")),
                Arguments.of(List.of("  ")),
                Arguments.of(Arrays.asList("A", null)),
                Arguments.of(List.of("missing")),
                Arguments.of(List.of("A", "missing")),
                Arguments.of(List.of("a")),
                Arguments.of(List.of("A ")),
                Arguments.of(List.of("inner")));
    }

    @ParameterizedTest
    @MethodSource("invalidSelections")
    void rejectsEmptyBlankNullMissingAndNonTipNames(List<String> names) {
        MrcaAgeValue value = new MrcaAgeValue();
        value.treeInput.setValue(new TreeParser("((A:1.0,B:1.0)inner:2.0,C:3.0);"), value);
        value.cladeInput.setValue(names, value);
        assertThrows(IllegalArgumentException.class, value::initAndValidate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", " ", "a", "A "})
    void invalidScriptSelectionsProduceATileDiagnostic(String name) {
        assertThrows(TileApplicationError.class, () -> build(fixedTreeSource(NEWICK), "[\"A\", \"" + name + "\"]"));
    }

    @Test
    void rejectsAmbiguousDuplicateTipIdentifiers() {
        Tree tree = new TreeParser(NEWICK);
        leaf(tree, "B").setID("A");
        MrcaAgeValue value = new MrcaAgeValue();
        value.treeInput.setValue(tree, value);
        value.cladeInput.setValue(List.of("A"), value);
        assertThrows(IllegalArgumentException.class, value::initAndValidate);
    }

    @Test
    void selectedNamesAreCopiedAtInitialization() {
        MrcaAgeValue value = new MrcaAgeValue();
        value.treeInput.setValue(new TreeParser(NEWICK), value);
        value.cladeInput.setValue(List.of("A", "B"), value);
        value.initAndValidate();

        value.cladeInput.get().clear();
        value.cladeInput.get().addAll(List.of("C", "D"));

        assertEquals(1.0, value.get(), 0.0);
    }

    @Test
    void replacementTreeCannotSilentlyLoseASelectedTaxon() throws Exception {
        Model model = build(fixedTreeSource(NEWICK), "[\"A\", \"B\"]");
        model.tree().assignFrom(new TreeParser("((A:1.0,X:1.0):2.0,(C:1.5,D:1.5):1.5);"));
        assertThrows(IllegalArgumentException.class, model.value()::get);
    }

    @Test
    void stochasticTaxonSelectionIsRejectedAtTheCladeBoundary() {
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> compile(fixedTreeSource(NEWICK) + """

                Vector<String> labels = ["A", "B"]
                NonNegativeInteger index ~ Poisson(rate=1.0)
                NonNegativeReal age = mrca(clade=[labels[index], "C"], tree=tree)
                """));
        assertTrue(error.getMessage().contains("clade"), error.getMessage());
        assertTrue(error.getMessage().contains("random variable"), error.getMessage());
    }

    private static Stream<Arguments> treeProposals() {
        return Stream.of("Yule(birthRate=1.0, taxa=taxa(data))", "Coalescent(populationSize=10.0, taxa=taxa(data))")
                .flatMap(prior -> Stream.of(
                        Arguments.of(prior, false, false),
                        Arguments.of(prior, false, true),
                        Arguments.of(prior, true, false),
                        Arguments.of(prior, true, true)));
    }

    @ParameterizedTest
    @MethodSource("treeProposals")
    void sampledTreeUpdatesReachLikelihoodAndRestoreCachedValues(
            String treePrior, boolean topologyProposal, boolean acceptFirst, @TempDir Path directory) throws Exception {
        String treeSource = sampledTreeSource(directory, treePrior);
        BEASTState baseline = compile(treeSource);
        Model model = build(
                treeSource,
                "[\"A\", \"B\"]",
                "Real y ~ Normal(mean=age, sd=" + SD + ") observed as " + OBSERVED + "\n");
        BEASTState graph = model.graph();
        Tree tree = model.tree();

        assertEquals(1, graph.stateNodes.size());
        assertTrue(graph.stateNodes.containsKey(tree));
        assertFalse(graph.stateNodes.containsKey(model.value()));
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(1, graph.calculationNodes.size());
        assertTrue(graph.calculationNodes.containsKey(model.value()));
        assertTrue(tree.getOutputs().contains(model.value()));
        assertEquals(
                baseline.operators.stream().map(Object::getClass).toList(),
                graph.operators.stream().map(Object::getClass).toList());

        // Start every proposal sequence with known ages and the same rooted topology.
        String treeId = tree.getID();
        tree.assignFrom(new TreeParser(NEWICK));
        tree.setID(treeId);
        Distribution prior = graph.priorDistributions.get(tree);
        assertNotNull(prior);

        Normal likelihood = assertInstanceOf(Normal.class, graph.likelihoodDistributions.getFirst());
        RealScalarParam<?> observation = assertInstanceOf(RealScalarParam.class, likelihood.paramInput.get());
        assertSame(model.value(), likelihood.meanInput.get());
        assertEquals(OBSERVED, observation.get(), 0.0);
        assertSame(Real.INSTANCE, observation.getDomain());
        assertFalse(graph.stateNodes.containsKey(observation));
        assertEquals(SD, likelihood.sdInput.get().get(), 0.0);
        assertTrue(model.value().getOutputs().contains(likelihood));
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(prior, likelihood));
        State state = new State();
        state.initByName("stateNode", List.of(tree));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        assertValue(model, 1.0);
        assertEquals(normalLogDensity(1.0), likelihood.getCurrentLogP(), TOLERANCE);

        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            double previousAge = model.value().get();
            double previousPrior = prior.getCurrentLogP();
            double previousLikelihood = likelihood.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            Node previousRoot = tree.getRoot();
            double rootHeight = previousRoot.getHeight();
            double proposedAge = topologyProposal
                    ? (previousAge == rootHeight ? leaf(tree, "A").getParent().getHeight() : rootHeight)
                    : previousAge + 0.25;

            state.store(step + 1);
            if (topologyProposal) {
                swapTips(tree, "B", "C");
                assertEquals(rootHeight, tree.getRoot().getHeight(), 0.0);
            } else {
                leaf(tree, "A").getParent().setHeight(proposedAge);
            }
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            double proposedPosterior = posterior.calculateLogP();
            double proposedPrior = prior.getCurrentLogP();
            double expectedLikelihood = normalLogDensity(proposedAge);
            assertTrue(Double.isFinite(proposedPosterior));
            assertNotEquals(previousAge, proposedAge, 0.0);
            assertNotEquals(previousLikelihood, expectedLikelihood, TOLERANCE);
            assertValue(model, proposedAge);
            assertEquals(expectedLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(proposedPrior + expectedLikelihood, proposedPosterior, TOLERANCE);

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
                assertNotSame(previousRoot, tree.getRoot());
            }
            state.setEverythingDirty(false);

            double currentAge = accept ? proposedAge : previousAge;
            assertValue(model, currentAge);
            assertSame(model.value(), likelihood.meanInput.get());
            // Inspect restored caches before calling any calculateLogP method.
            assertEquals(accept ? proposedPrior : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedLikelihood : previousLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);

            assertEquals(
                    (accept ? proposedPrior : previousPrior) + normalLogDensity(currentAge),
                    posterior.calculateLogP(),
                    TOLERANCE);
            assertEquals(normalLogDensity(currentAge), likelihood.calculateLogP(), TOLERANCE);
        }
    }

    private static void swapTips(Tree tree, String first, String second) {
        Node a = leaf(tree, first);
        Node b = leaf(tree, second);
        Node parentA = a.getParent();
        Node parentB = b.getParent();
        assertNotSame(parentA, parentB);
        parentA.removeChild(a);
        parentB.removeChild(b);
        parentA.addChild(b);
        parentB.addChild(a);
    }

    private static Node leaf(Tree tree, String name) {
        return tree.getExternalNodes().stream()
                .filter(node -> name.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static double normalLogDensity(double mean) {
        double z = (OBSERVED - mean) / SD;
        return -Math.log(SD) - 0.5 * Math.log(2.0 * Math.PI) - 0.5 * z * z;
    }

    private static String fixedTreeSource(String newick) {
        return "Tree tree = fromNewick(newickString=\"" + newick + "\")";
    }

    private static String sampledTreeSource(Path directory, String prior) throws Exception {
        Path data = directory.resolve("four-taxa.nex");
        Files.writeString(data, """
                #NEXUS
                Begin data;
                    Dimensions ntax=4 nchar=4;
                    Format datatype=dna missing=? gap=-;
                    Matrix
                    A ACGT
                    B ACGT
                    C ACGT
                    D ACGT
                    ;
                End;
                """);
        return "Alignment data = fromNexus(file=\"" + data.toAbsolutePath() + "\")\nTree tree ~ " + prior;
    }

    private static Model build(String treeSource, String clade) throws Exception {
        return build(treeSource, clade, "");
    }

    private static Model build(String treeSource, String clade, String consumer) throws Exception {
        BEASTState graph =
                compile(treeSource + "\nNonNegativeReal age = mrca(clade=" + clade + ", tree=tree)\n" + consumer + """
                mcmc {
                    Logger trace = fileLogger(logEvery=1, file="mrca.log", parameters=[age])
                }
                """);
        MrcaAgeValue value = assertInstanceOf(
                MrcaAgeValue.class,
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("mrca-test"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }

    private static void assertValue(Model model, double expected) {
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

    private record Model(BEASTState graph, Tree tree, MrcaAgeValue value) {}
}
