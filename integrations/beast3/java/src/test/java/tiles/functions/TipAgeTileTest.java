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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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

class TipAgeTileTest {
    private static final String NEWICK = "((A:0.5,B:1.0)inner:2.0,(C:1.5,D:1.5):1.5);";
    private static final double OBSERVED = -0.4;
    private static final double SD = 0.5;
    private static final double TOLERANCE = 1e-12;

    @ParameterizedTest
    @CsvSource({"A, 0.5", "B, 0.0"})
    void fixedTreesReturnDatedAndZeroAgeTips(String name, double expected) throws Exception {
        Model model = build(fixedTreeSource(NEWICK), name);

        assertTrue(model.graph().stateNodes.isEmpty());
        assertTrue(model.graph().priorDistributions.isEmpty());
        assertTrue(model.graph().likelihoodDistributions.isEmpty());
        assertTrue(model.graph().operators.isEmpty());
        assertEquals(1, model.graph().calculationNodes.size());
        assertSame(NonNegativeReal.INSTANCE, model.value().getDomain());
        assertEquals(List.of(name), model.value().cladeInput.get());
        assertValue(model, expected);
    }

    @Test
    void importedAccessorNamespaceResolvesTheTreeOverload() throws Exception {
        assertEquals(
                "phylospec.functions.accessor", new TipAgeTile().getNamespace().orElseThrow());
        // Scripts import a namespace with 'use'; dotted function calls are not DSL syntax.
        BEASTState graph = compile("use phylospec.functions.accessor\n" + fixedTreeSource(NEWICK) + """

                NonNegativeReal tipAge = age(node="A", tree=tree)
                """);
        assertEquals(1, graph.calculationNodes.size());
        MrcaAgeValue value = assertInstanceOf(
                MrcaAgeValue.class, graph.calculationNodes.keySet().iterator().next());
        assertEquals(0.5, value.get(), 0.0);
    }

    @Test
    void taxonAndTreeAgeOverloadsRemainDistinctInTheSameScript() throws Exception {
        BEASTState graph = compile(fixedTreeSource(NEWICK) + """

                Taxon sample = taxon(name="A", age=2.5)
                NonNegativeReal sampleAge = age(taxon=sample)
                NonNegativeReal tipAge = age(node="A", tree=tree)
                mcmc {
                    Logger sampleTrace = fileLogger(logEvery=1, file="sample-age.log", parameters=[sampleAge])
                    Logger treeTrace = fileLogger(logEvery=1, file="tree-age.log", parameters=[tipAge])
                }
                """);

        // Keep this overload test independent of mixed parameter/calculation-node list binding.
        assertEquals(2, graph.fileLoggerSpecs.size());
        List<?> parameters = graph.fileLoggerSpecs.stream()
                .flatMap(logger -> logger.parameters().stream())
                .toList();
        assertEquals(2, parameters.size());
        RealScalarParam<?> sampleAge = parameters.stream()
                .filter(RealScalarParam.class::isInstance)
                .map(RealScalarParam.class::cast)
                .findFirst()
                .orElseThrow();
        MrcaAgeValue tipAge = parameters.stream()
                .filter(MrcaAgeValue.class::isInstance)
                .map(MrcaAgeValue.class::cast)
                .findFirst()
                .orElseThrow();
        assertSame(NonNegativeReal.INSTANCE, sampleAge.getDomain());
        assertSame(NonNegativeReal.INSTANCE, tipAge.getDomain());
        assertEquals(2.5, sampleAge.get(), 0.0);
        assertEquals(0.5, tipAge.get(), 0.0);
        assertEquals(1, graph.calculationNodes.size());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());

        leaf(tipAge.treeInput.get(), "A").setHeight(0.75);
        assertEquals(0.75, tipAge.get(), 0.0);
        assertEquals(2.5, sampleAge.get(), 0.0);
    }

    @Test
    void deterministicNameAndRepeatedConsumersShareOneLiveValue() throws Exception {
        BEASTState graph = compile(fixedTreeSource(NEWICK) + """

                Vector<String> labels = ["B", "A"]
                NonNegativeInteger index = num(vector=labels)
                String selected = labels[index]
                NonNegativeReal tipAge = age(node=selected, tree=tree)
                NonNegativeReal alias = tipAge
                mcmc {
                    Logger trace = fileLogger(logEvery=1, file="tip-age.log", parameters=[tipAge, alias])
                }
                """);

        assertEquals(1, graph.calculationNodes.size());
        List<?> parameters = graph.fileLoggerSpecs.getFirst().parameters();
        assertEquals(2, parameters.size());
        MrcaAgeValue value = assertInstanceOf(MrcaAgeValue.class, parameters.getFirst());
        assertSame(value, parameters.get(1));
        assertEquals(List.of("A"), value.cladeInput.get());
        leaf(value.treeInput.get(), "A").setHeight(0.75);
        assertEquals(0.75, value.get(), 0.0);
        assertEquals(0.75, loggedValue(value), 0.0);
    }

    @Test
    void defaultLoggersIncludeTheTipAge() throws Exception {
        BEASTState graph = compile(fixedTreeSource(NEWICK) + """

                NonNegativeReal tipAge = age(node="A", tree=tree)
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
        assertEquals(0.5, loggedValue(value), 0.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "", " ", "inner", "a", "A "})
    void onlyExactNonBlankTipNamesAreAccepted(String name) {
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> build(fixedTreeSource(NEWICK), name));
        assertTrue(error.getMessage().contains("Invalid tip-age selection"), error.getMessage());
    }

    @Test
    void stochasticNameSelectionIsRejectedAtTheNodeBoundary() {
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> compile(fixedTreeSource(NEWICK) + """

                Vector<String> labels = ["A", "B"]
                NonNegativeInteger index ~ Poisson(rate=1.0)
                NonNegativeReal tipAge = age(node=labels[index], tree=tree)
                """));
        assertTrue(error.getMessage().contains("node"), error.getMessage());
        assertTrue(error.getMessage().contains("random variable"), error.getMessage());
    }

    @Test
    void valueFollowsTipHeightChangesAndReplacementNodes() throws Exception {
        Model model = build(fixedTreeSource(NEWICK), "A");
        Node originalTip = leaf(model.tree(), "A");
        originalTip.setHeight(0.75);
        assertValue(model, 0.75);

        model.tree().assignFrom(new TreeParser("((A:0.5,B:2.0):2.0,(C:1.0,D:1.0):3.0);"));
        assertNotSame(originalTip, leaf(model.tree(), "A"));
        assertValue(model, 1.5);
    }

    private static Stream<Arguments> treeProposals() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(false, true),
                Arguments.of(true, false),
                Arguments.of(true, true));
    }

    @ParameterizedTest
    @MethodSource("treeProposals")
    void coalescentTreeUpdatesReachLikelihoodAndRestoreCachedValues(
            boolean topologyProposal, boolean acceptFirst, @TempDir Path directory) throws Exception {
        String treeSource = sampledTreeSource(directory);
        BEASTState baseline = compile(treeSource);
        Model model =
                build(treeSource, "A", "Real y ~ Normal(mean=tipAge, sd=" + SD + ") observed as " + OBSERVED + "\n");
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

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(prior, likelihood));
        State state = new State();
        state.initByName("stateNode", List.of(tree));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        assertValue(model, 0.5);
        assertEquals(coalescentLogDensity(0.5), prior.getCurrentLogP(), TOLERANCE);
        assertEquals(normalLogDensity(0.5), likelihood.getCurrentLogP(), TOLERANCE);

        // Explicit proposals exercise the live accessor; it does not add tip-date operators.
        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            double previousAge = model.value().get();
            double previousPrior = prior.getCurrentLogP();
            double previousLikelihood = likelihood.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            Node previousTip = leaf(tree, "A");
            int previousParent = previousTip.getParent().getNr();
            double proposedAge = topologyProposal ? previousAge : previousAge + 0.1;

            state.store(step + 1);
            if (topologyProposal) {
                swapTips(tree, "A", "C");
                assertNotEquals(previousParent, leaf(tree, "A").getParent().getNr());
            } else {
                leaf(tree, "A").setHeight(proposedAge);
            }
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            double proposedPosterior = posterior.calculateLogP();
            double proposedPrior = prior.getCurrentLogP();
            double expectedLikelihood = normalLogDensity(proposedAge);
            assertTrue(Double.isFinite(proposedPosterior));
            assertValue(model, proposedAge);
            assertEquals(coalescentLogDensity(proposedAge), proposedPrior, TOLERANCE);
            assertEquals(expectedLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(proposedPrior + expectedLikelihood, proposedPosterior, TOLERANCE);
            if (topologyProposal) {
                assertEquals(previousAge, proposedAge, 0.0);
                assertEquals(previousPosterior, proposedPosterior, TOLERANCE);
            } else {
                assertNotEquals(previousLikelihood, expectedLikelihood, TOLERANCE);
            }

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
                assertNotSame(previousTip, leaf(tree, "A"));
                assertEquals(previousParent, leaf(tree, "A").getParent().getNr());
            }
            state.setEverythingDirty(false);

            double currentAge = accept ? proposedAge : previousAge;
            assertValue(model, currentAge);
            assertSame(model.value(), likelihood.meanInput.get());
            // Inspect native caches before recalculating any density after acceptance or rollback.
            assertEquals(accept ? proposedPrior : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedLikelihood : previousLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    coalescentLogDensity(currentAge) + normalLogDensity(currentAge),
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

    private static double coalescentLogDensity(double tipAge) {
        // Three tips at 0, one at tipAge < 1, and coalescences at 1, 1.5, 3.
        // The integrated number of lineage pairs is 3*a + 6*(1-a) + 3*0.5 + 1*1.5.
        return -3.0 * Math.log(10.0) - (9.0 - 3.0 * tipAge) / 10.0;
    }

    private static String fixedTreeSource(String newick) {
        return "Tree tree = fromNewick(newickString=\"" + newick + "\")";
    }

    private static String sampledTreeSource(Path directory) throws Exception {
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
        return "Alignment data = fromNexus(file=\"" + data.toAbsolutePath()
                + "\")\nTree tree ~ Coalescent(populationSize=10.0, taxa=taxa(data))";
    }

    private static Model build(String treeSource, String name) throws Exception {
        return build(treeSource, name, "");
    }

    private static Model build(String treeSource, String name, String consumer) throws Exception {
        BEASTState graph = compile(
                treeSource + "\nNonNegativeReal tipAge = age(node=\"" + name + "\", tree=tree)\n" + consumer + """
                mcmc {
                    Logger trace = fileLogger(logEvery=1, file="tip-age.log", parameters=[tipAge])
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("tip-age-test"));
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
