package tiles.trees;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.operator.ScaleTreeOperator;
import beast.base.spec.evolution.speciation.BirthDeathGernhard08Model;
import beast.base.spec.evolution.speciation.YuleModel;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiling.LiveBirthDeathGernhard08Model;
import tiling.LiveYuleModel;
import tiling.RootAgeOperator;

class RootAgeBindingTest {
    private static final double TOLERANCE = 1e-10;
    private static final double LOG_ROOT_MEAN = Math.log(5.0);
    private static final String TAXA =
            "Taxa samples = [taxon(name=\"a\"),taxon(name=\"b\"),taxon(name=\"c\"),taxon(name=\"d\")]\n";
    private static final String NEWICK = "((a:1.0,b:1.0):4.0,(c:2.0,d:2.0):3.0);";
    private static final String OBSERVED = "Tree fixed = fromNewick(newickString=\"" + NEWICK + "\")\n";
    private static final String RANDOM_AGE = "PositiveReal age ~ LogNormal(logMean=1.6094379124341003,logSd=0.5)\n";

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"yule", "rates", "diversification"})
    void fixedNonNegativeAgeBindsTheFinalTreeAndUsesRootConditioning(String family) throws Exception {
        BEASTState graph = build(TAXA + "NonNegativeReal age = 5.0\nTree tree ~ " + generator(family, "age"));
        Model model = state(graph);
        YuleModel law = model.laws().getFirst();
        assertEquals(5.0, tree(law).getRoot().getHeight(), TOLERANCE);
        assertEquals(5.0, rootAge(law).get(), TOLERANCE);
        assertTrue(law.conditionalOnRootInput.get());
        assertNull(law.originHeightParameterInput.get());
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertMatchesReference(model, false);
        assertNotEquals(
                nativeDensity(law, false),
                law.getCurrentLogP(),
                TOLERANCE,
                "Root conditioning must not silently retain the old origin-conditioned density.");
        assertTrue(
                graph.operators.stream().noneMatch(operator -> delegate(operator) instanceof ScaleTreeOperator),
                "A fixed root must not retain a scaler which always changes that root.");
        for (Operator operator : graph.operators) {
            assertTrue(operator.listStateNodes().stream().allMatch(graph.stateNodes::containsKey));
        }

        // A direct unsupported tree-root change must also be caught by the density guard,
        // rather than relying only on the configured operator wrappers.
        model.state().store(1);
        tree(law).getRoot().setHeight(5.25);
        model.state().storeCalculationNodes();
        model.state().checkCalculationNodesDirtiness();
        assertEquals(Double.NEGATIVE_INFINITY, model.posterior().calculateLogP());
        finish(model, false);
        assertEquals(5.0, tree(law).getRoot().getHeight(), TOLERANCE);
        assertMatchesReference(model, false);
    }

    private static Stream<Arguments> jointProposals() {
        return Stream.of("yule", "rates", "diversification")
                .flatMap(family -> Stream.of("parameter", "tree").flatMap(direction -> Stream.of(false, true)
                        .map(acceptFirst -> Arguments.of(family, direction, acceptFirst))));
    }

    @ParameterizedTest
    @MethodSource("jointProposals")
    void sharedRandomRootMovesInBothDirectionsAndRestoresEveryState(
            String family, String direction, boolean acceptFirst) throws Exception {
        BEASTState graph = build(TAXA + RANDOM_AGE
                + "Tree first ~ " + generator(family, "age") + "\n"
                + "Tree second ~ " + generator(family, "age") + "\n");
        Model model = state(graph);
        RealScalarParam<?> age = age(graph);
        assertEquals(3, graph.stateNodes.size());
        assertEquals(3, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertInstanceOf(LogNormal.class, graph.priorDistributions.get(age));
        assertEquals(5.0, age.get(), TOLERANCE);
        for (YuleModel law : model.laws()) assertSame(age, rootAge(law));
        RootAgeOperator operator = graph.operators.stream()
                .filter(RootAgeOperator.class::isInstance)
                .map(RootAgeOperator.class::cast)
                .filter(candidate -> direction.equals("parameter")
                        ? candidate.delegateInput.get() instanceof ScaleOperator
                        : candidate.delegateInput.get() instanceof ScaleTreeOperator)
                .findFirst()
                .orElseThrow();
        assertEquals(operator.delegateInput.get().getWeight(), operator.getWeight());
        assertTrue(operator.listStateNodes().contains(age));
        for (YuleModel law : model.laws()) assertTrue(operator.listStateNodes().contains(tree(law)));
        assertMatchesReference(model, true);

        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        for (int step = 0; step < decisions.length; step++) {
            Snapshot previous = snapshot(model, age);
            double logHastings = proposeFinite(model, operator, step * 100);
            Snapshot proposed = snapshot(model, age);
            assertNotEquals(previous.age(), proposed.age(), TOLERANCE);
            double logScale = Math.log(proposed.age() / previous.age());
            double expectedHastings = direction.equals("parameter")
                    ? logScale
                    : tree(model.laws().getFirst()).getInternalNodeCount() * logScale;
            assertEquals(
                    expectedHastings,
                    logHastings,
                    TOLERANCE,
                    "Synchronizing a duplicated root coordinate must retain the native proposal ratio.");
            for (YuleModel law : model.laws()) {
                assertEquals(age.get(), tree(law).getRoot().getHeight(), TOLERANCE);
            }
            assertMatchesReference(model, true);
            finish(model, decisions[step]);
            assertSnapshot(decisions[step] ? proposed : previous, model, age);
            assertMatchesReference(model, true);
            assertEquals(model.posterior().getCurrentLogP(), model.posterior().calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yule", "rates", "diversification"})
    void fixedObservedRootRetainsTheDataAndRejectsMismatches(String family) throws Exception {
        BEASTState graph = build(TAXA + OBSERVED + "Tree tree ~ " + generator(family, "5.0") + " observed as fixed\n");
        Model model = state(graph);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(5.0, tree(model.laws().getFirst()).getRoot().getHeight(), TOLERANCE);
        assertNotNull(tree(model.laws().getFirst()).getTaxonset());
        assertMatchesReference(model, false);
        rejects(TAXA + OBSERVED + "Tree tree ~ " + generator(family, "6.0") + " observed as fixed\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"yule", "rates", "diversification"})
    void observedFixedRootTracksSampledRatesAndRestoresAcceptedAndRejectedChanges(String family) throws Exception {
        BEASTState graph = build(TAXA + OBSERVED
                + "PositiveReal rate ~ LogNormal(logMean=0.0,logSd=0.5)\n"
                + "Tree tree ~ " + generator(family, "5.0", "rate") + " observed as fixed\n");
        Model model = state(graph);
        RealScalarParam<?> rate = assertInstanceOf(
                RealScalarParam.class, graph.stateNodes.keySet().iterator().next());
        assertEquals("rate", rate.getID());
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertInstanceOf(LogNormal.class, graph.priorDistributions.get(rate));
        String fixedTree = tree(model.laws().getFirst()).getRoot().toNewick();
        assertObservedRateReference(model, rate);

        boolean[] decisions = {false, true, false};
        // Keep birthRate above the fixed deathRate=0.5: critical/subcritical
        // BirthDeath parameterizations are outside this supported path.
        double[] proposedRates = {2.0, 3.0, 1.5};
        for (int step = 0; step < decisions.length; step++) {
            Snapshot previous = snapshot(model, rate);
            model.state().store(step);
            rate.set(proposedRates[step]);
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            assertTrue(Double.isFinite(model.posterior().calculateLogP()));
            Snapshot proposed = snapshot(model, rate);
            assertNotEquals(previous.age(), proposed.age(), TOLERANCE);
            assertNotEquals(previous.densities()[0], proposed.densities()[0], TOLERANCE);
            assertEquals(fixedTree, tree(model.laws().getFirst()).getRoot().toNewick());
            assertObservedRateReference(model, rate);
            finish(model, decisions[step]);
            assertSnapshot(decisions[step] ? proposed : previous, model, rate);
            assertObservedRateReference(model, rate);
            assertEquals(model.posterior().getCurrentLogP(), model.posterior().calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yule", "rates", "diversification"})
    void zeroAgesAndUnsupportedStochasticCombinationsFailExplicitly(String family) {
        rejects(TAXA + "NonNegativeReal age = 0.0\nTree tree ~ " + generator(family, "age"));
        rejects(TAXA + OBSERVED + RANDOM_AGE + "Tree tree ~ " + generator(family, "age") + " observed as fixed\n");
        rejects(TAXA + "Real logAge ~ Normal(mean=0.0,sd=1.0)\nPositiveReal age = exp(x=logAge)\n" + "Tree tree ~ "
                + generator(family, "age"));
    }

    @Test
    void aTwoTaxonRootConditionedYuleHasNoRemainingNodeTimeDensity() throws Exception {
        BEASTState graph = build("""
                Taxa samples = [taxon(name="a"),taxon(name="b")]
                Tree tree ~ Yule(birthRate=2.0,rootAge=5.0,taxa=samples)
                """);
        Model model = state(graph);
        assertEquals(0.0, model.laws().getFirst().getCurrentLogP(), TOLERANCE);
        assertEquals(5.0, tree(model.laws().getFirst()).getRoot().getHeight(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yule", "rates", "diversification"})
    void runnerShortChainMovesTheSharedRootAndKeepsEveryLoggedStateConsistent(String family) throws Exception {
        String source = TAXA + RANDOM_AGE
                + "Tree first ~ " + generator(family, "age") + "\n"
                + "Tree second ~ " + generator(family, "age") + "\n"
                + """
                mcmc {
                    Integer chainLength = 200
                    Integer defaultLogEvery = 1
                    Integer randomSeed = 41235
                }
                """;
        Path runDirectory = directory.resolve("root-age-run-" + family);
        new PhyloSpecRunner(source, List.of("beast2")).runManaged("root-age", runDirectory, warning -> {});
        List<String> lines = Files.readAllLines(runDirectory.resolve("output/root-age.log")).stream()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .toList();
        assertEquals(202, lines.size(), "Header plus samples 0 through 200.");
        List<String> headers = List.of(lines.getFirst().trim().split("\\s+"));
        for (String name : List.of("age", "first.height", "second.height", "posterior")) {
            assertTrue(headers.contains(name), headers.toString());
        }
        List<Double> ages = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.trim().split("\\s+");
            double age = Double.parseDouble(values[headers.indexOf("age")]);
            assertTrue(Double.isFinite(age) && age > 0.0);
            assertEquals(age, Double.parseDouble(values[headers.indexOf("first.height")]), TOLERANCE);
            assertEquals(age, Double.parseDouble(values[headers.indexOf("second.height")]), TOLERANCE);
            assertTrue(Double.isFinite(Double.parseDouble(values[headers.indexOf("posterior")])));
            ages.add(age);
        }
        long distinctAges = ages.stream().distinct().count();
        assertTrue(distinctAges > 1, "The short chain must move rootAge, not merely remain on a valid initial state.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"yule", "rates", "diversification"})
    void runnerShortChainMovesTheTreeWhilePreservingItsFixedRoot(String family) throws Exception {
        String source = TAXA + "Tree tree ~ " + generator(family, "5.0") + "\n" + """
                mcmc {
                    Integer chainLength = 100
                    Integer defaultLogEvery = 1
                    Integer randomSeed = 41235
                }
                """;
        Path runDirectory = directory.resolve("fixed-root-" + family);
        new PhyloSpecRunner(source, List.of("beast2")).runManaged("fixed-root", runDirectory, warning -> {});
        List<String> lines = Files.readAllLines(runDirectory.resolve("output/fixed-root.log")).stream()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .toList();
        assertEquals(102, lines.size(), "Header plus samples 0 through 100.");
        List<String> headers = List.of(lines.getFirst().trim().split("\\s+"));
        for (String name : List.of("tree.height", "posterior")) {
            assertTrue(headers.contains(name), headers.toString());
        }
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.trim().split("\\s+");
            assertEquals(5.0, Double.parseDouble(values[headers.indexOf("tree.height")]), TOLERANCE);
            assertTrue(Double.isFinite(Double.parseDouble(values[headers.indexOf("posterior")])));
        }
        List<Path> treeLogs;
        try (Stream<Path> files = Files.list(runDirectory.resolve("output"))) {
            treeLogs = files.filter(path -> path.getFileName().toString().endsWith(".trees"))
                    .toList();
        }
        assertEquals(1, treeLogs.size());
        List<String> trees = Files.readAllLines(treeLogs.getFirst()).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("tree STATE_"))
                .map(line -> line.substring(line.indexOf('=') + 1).trim())
                .toList();
        assertEquals(101, trees.size(), "One tree for every logged sample.");
        assertTrue(
                trees.stream().distinct().count() > 1,
                "Fixing the root must still permit accepted changes elsewhere in the tree.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"yule", "rates", "diversification"})
    void runnerIncludesAConstantObservedRootDensityUnrelatedToTheSampledState(String family) throws Exception {
        String source = TAXA + OBSERVED
                + "Tree tree ~ " + generator(family, "5.0") + " observed as fixed\n"
                + "PositiveReal nuisance ~ LogNormal(logMean=0.0,logSd=0.5)\n"
                + """
                mcmc {
                    Integer chainLength = 20
                    Integer defaultLogEvery = 1
                    Integer randomSeed = 41235
                }
                """;
        Path runDirectory = directory.resolve("constant-observation-" + family);
        // Exercise the real runner from source, with no test-harness initialization of its graph.
        new PhyloSpecRunner(source, List.of("beast2")).runManaged("observed", runDirectory, warning -> {});
        List<String> lines = Files.readAllLines(runDirectory.resolve("output/observed.log")).stream()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .toList();
        assertEquals(22, lines.size(), "Header plus samples 0 through 20.");
        List<String> headers = List.of(lines.getFirst().trim().split("\\s+"));
        for (String name : List.of("nuisance", "prior", "likelihood", "posterior")) {
            assertTrue(headers.contains(name), headers.toString());
        }
        double nativeDensity = nativeObservedDensity(family);
        assertTrue(Double.isFinite(nativeDensity));
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.trim().split("\\s+");
            double nuisance = Double.parseDouble(values[headers.indexOf("nuisance")]);
            assertTrue(Double.isFinite(nuisance) && nuisance > 0.0);
            double z = Math.log(nuisance) / 0.5;
            double prior = -0.5 * Math.log(2.0 * Math.PI) - Math.log(0.5) - Math.log(nuisance) - 0.5 * z * z;
            assertEquals(prior, Double.parseDouble(values[headers.indexOf("prior")]), TOLERANCE);
            assertEquals(nativeDensity, Double.parseDouble(values[headers.indexOf("likelihood")]), TOLERANCE);
            assertEquals(prior + nativeDensity, Double.parseDouble(values[headers.indexOf("posterior")]), TOLERANCE);
        }
    }

    private static double proposeFinite(Model model, Operator operator, int offset) {
        for (int attempt = 0; attempt < 100; attempt++) {
            model.state().store(offset + attempt);
            double hastings = operator.proposal();
            model.state().storeCalculationNodes();
            model.state().checkCalculationNodesDirtiness();
            if (Double.isFinite(hastings) && Double.isFinite(model.posterior().calculateLogP())) return hastings;
            finish(model, false);
        }
        fail("The configured joint proposal could not produce a valid move.");
        return Double.NaN;
    }

    private static void rejects(String source) {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> build(source));
        assertTrue(failure.getMessage().contains("rootAge"), failure::getMessage);
    }

    private static String generator(String family, String age) {
        return generator(family, age, family.equals("rates") ? "2.0" : "1.5");
    }

    private static String generator(String family, String age, String rate) {
        String arguments =
                switch (family) {
                    case "yule" -> "Yule(birthRate=" + rate;
                    case "rates" -> "BirthDeath(birthRate=" + rate + ",deathRate=0.5,samplingProbability=0.8";
                    case "diversification" ->
                        "BirthDeath(diversificationRate=" + rate + ",turnover=0.25,samplingProbability=0.8";
                    default -> throw new IllegalArgumentException(family);
                };
        return arguments + ",rootAge=" + age + ",taxa=samples)";
    }

    private static Operator delegate(Operator operator) {
        return operator instanceof RootAgeOperator joint ? joint.delegateInput.get() : operator;
    }

    private static Tree tree(YuleModel law) {
        return (Tree) law.treeInput.get();
    }

    private static RealScalar<?> rootAge(YuleModel law) {
        if (law instanceof LiveYuleModel yule) return yule.rootAgeParameterInput.get();
        return assertInstanceOf(LiveBirthDeathGernhard08Model.class, law)
                .rootAgeParameterInput
                .get();
    }

    private static RealScalarParam<?> age(BEASTState graph) {
        return assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(node -> "age".equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static double nativeDensity(YuleModel law, boolean rootConditioned) {
        YuleModel reference =
                law instanceof BirthDeathGernhard08Model ? new BirthDeathGernhard08Model() : new YuleModel();
        reference.treeInput.setValue(new Tree(tree(law).getRoot().copy()), reference);
        reference.birthDiffRateParameterInput.setValue(
                new RealScalarParam<>(law.birthDiffRateParameterInput.get().get(), PositiveReal.INSTANCE), reference);
        if (rootConditioned) reference.conditionalOnRootInput.setValue(true, reference);
        else
            reference.originHeightParameterInput.setValue(
                    new RealScalarParam<>(rootAge(law).get(), PositiveReal.INSTANCE), reference);
        if (reference instanceof BirthDeathGernhard08Model bd) {
            BirthDeathGernhard08Model original = (BirthDeathGernhard08Model) law;
            bd.relativeDeathRateParameterInput.setValue(
                    new RealScalarParam<>(
                            original.relativeDeathRateParameterInput.get().get(), PositiveReal.INSTANCE),
                    bd);
            bd.sampleProbabilityInput.setValue(
                    new RealScalarParam<>(original.sampleProbabilityInput.get().get(), UnitInterval.INSTANCE), bd);
        }
        // Direct native initialization avoids its advisory MRCAPrior validation: the reference
        // tree's root is explicitly fixed here, and the density flag is the subject of comparison.
        reference.initAndValidate();
        return reference.calculateLogP();
    }

    private static double nativeObservedDensity(String family) {
        YuleModel reference = family.equals("yule") ? new YuleModel() : new BirthDeathGernhard08Model();
        reference.treeInput.setValue(new TreeParser(NEWICK), reference);
        reference.birthDiffRateParameterInput.setValue(new RealScalarParam<>(1.5, PositiveReal.INSTANCE), reference);
        reference.conditionalOnRootInput.setValue(true, reference);
        if (reference instanceof BirthDeathGernhard08Model bd) {
            bd.relativeDeathRateParameterInput.setValue(new RealScalarParam<>(0.25, PositiveReal.INSTANCE), bd);
            bd.sampleProbabilityInput.setValue(new RealScalarParam<>(0.8, UnitInterval.INSTANCE), bd);
        }
        reference.initAndValidate();
        return reference.calculateLogP();
    }

    private static void assertMatchesReference(Model model, boolean randomAge) {
        double expected = 0.0;
        for (YuleModel law : model.laws()) {
            double density = nativeDensity(law, true);
            assertTrue(Double.isFinite(density));
            assertEquals(density, law.getCurrentLogP(), TOLERANCE);
            expected += density;
        }
        if (randomAge) {
            double value = rootAge(model.laws().getFirst()).get();
            double z = (Math.log(value) - LOG_ROOT_MEAN) / 0.5;
            expected += -0.5 * Math.log(2.0 * Math.PI) - Math.log(0.5) - Math.log(value) - 0.5 * z * z;
        }
        assertEquals(expected, model.posterior().getCurrentLogP(), TOLERANCE);
    }

    private static void assertObservedRateReference(Model model, RealScalarParam<?> rate) {
        YuleModel law = model.laws().getFirst();
        double nativeTreeDensity = nativeDensity(law, true);
        assertTrue(Double.isFinite(nativeTreeDensity));
        assertEquals(nativeTreeDensity, law.getCurrentLogP(), TOLERANCE);
        assertEquals(5.0, tree(law).getRoot().getHeight(), TOLERANCE);
        double z = Math.log(rate.get()) / 0.5;
        double prior = -0.5 * Math.log(2.0 * Math.PI) - Math.log(0.5) - Math.log(rate.get()) - 0.5 * z * z;
        assertEquals(nativeTreeDensity + prior, model.posterior().getCurrentLogP(), TOLERANCE);
    }

    private static Model state(BEASTState graph) {
        List<Distribution> terms = new ArrayList<>(graph.priorDistributions.values());
        terms.addAll(graph.likelihoodDistributions);
        List<YuleModel> laws = terms.stream()
                .filter(YuleModel.class::isInstance)
                .map(YuleModel.class::cast)
                .toList();
        assertFalse(laws.isEmpty());
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setID("posterior");
        posterior.initByName("distribution", terms);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        // With no sampled nodes, State has no dependency path on which to dirty these
        // constant terms. Initialize their caches explicitly before summing them.
        if (graph.stateNodes.isEmpty()) terms.forEach(Distribution::calculateLogP);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        return new Model(state, posterior, laws);
    }

    private static Snapshot snapshot(Model model, RealScalarParam<?> age) {
        return new Snapshot(
                age.get(),
                model.laws().stream().map(law -> tree(law).getRoot().toNewick()).toList(),
                model.laws().stream().mapToDouble(Distribution::getCurrentLogP).toArray(),
                model.posterior().getCurrentLogP());
    }

    private static void assertSnapshot(Snapshot expected, Model model, RealScalarParam<?> age) {
        Snapshot actual = snapshot(model, age);
        assertEquals(expected.age(), actual.age(), TOLERANCE);
        assertEquals(expected.trees(), actual.trees());
        assertArrayEquals(expected.densities(), actual.densities(), TOLERANCE);
        assertEquals(expected.posterior(), actual.posterior(), TOLERANCE);
    }

    private static void finish(Model model, boolean accept) {
        if (accept) model.state().acceptCalculationNodes();
        else {
            model.state().restore();
            model.state().restoreCalculationNodes();
        }
        model.state().setEverythingDirty(false);
    }

    private static BEASTState build(String source) throws Exception {
        Randomizer.setSeed(7211);
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("root-age-binding"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }

    private record Model(State state, CompoundDistribution posterior, List<YuleModel> laws) {}

    private record Snapshot(double age, List<String> trees, double[] densities, double posterior) {}
}
