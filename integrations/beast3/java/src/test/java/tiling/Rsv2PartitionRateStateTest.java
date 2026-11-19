package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.alignment.FilteredAlignment;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.distribution.Dirichlet;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import beastlabs.math.distributions.WeightedDirichlet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;

class Rsv2PartitionRateStateTest {
    private static final Path RSV2 = Path.of("src/test/resources/rsv2").toAbsolutePath();
    private static final int[] LENGTHS = {209, 210, 210};
    private static final int[] FIRST_COLUMNS = {2, 0, 1}; // native zero-based indices for codon positions 3, 1, 2
    private static final double TOLERANCE = 1e-8;

    @ParameterizedTest(name = "{0}, proposal on {1}")
    @CsvSource({
        "partition-rates.phylospec, mu", "partition-rates.phylospec, weights",
        "time-stamped-data.phylospec, mu", "time-stamped-data.phylospec, relativeRate"
    })
    void eachPartitionTracksAcceptedAndRejectedProposals(String script, String parameter, @TempDir Path project)
            throws Exception {
        boolean original = script.equals("time-stamped-data.phylospec");
        if (original) {
            Files.createDirectory(project.resolve("data"));
            Files.copy(RSV2.resolve("RSV2.nex"), project.resolve("data/RSV2.nex"));
        }
        Model model = buildModel(Files.readString(RSV2.resolve(script)), original ? project : RSV2, original);
        boolean moveClock = parameter.equals("mu");
        checkAgainstNative(model);

        // The last proposal starts from a restored state and exposes stale internal caches.
        for (int step = 0; step < 3; step++) {
            double oldMu = model.mu.get();
            List<Double> oldCoordinates = List.copyOf(model.coordinates.getElements());
            double[] previous = logLikelihoods(model);
            double previousPosterior = model.posterior.getCurrentLogP();

            model.state.store(step + 1);
            if (moveClock) {
                model.mu.set(oldMu * (1.20 + step * 0.1));
            } else if (original) {
                assertTrue(Double.isFinite(model.weightedExchange.proposal()));
            } else {
                double delta = 0.02 * (step + 1);
                model.coordinates.set(0, oldCoordinates.get(0) + delta);
                model.coordinates.set(1, oldCoordinates.get(1) - delta);
            }
            model.state.storeCalculationNodes();
            model.state.checkCalculationNodesDirtiness();
            double proposedPosterior = model.posterior.calculateLogP();
            assertTrue(Double.isFinite(proposedPosterior));
            double[] proposed = logLikelihoods(model);
            checkAgainstNative(model);
            int changedPartitions = 0;
            for (int i = 0; i < proposed.length; i++) {
                if (moveClock
                        || model.coordinates.get(i) != oldCoordinates.get(i).doubleValue()) {
                    changedPartitions++;
                    assertNotEquals(previous[i], proposed[i], TOLERANCE, "partition " + (i + 1));
                } else {
                    assertEquals(previous[i], proposed[i], TOLERANCE, "unchanged partition " + (i + 1));
                }
            }
            assertEquals(moveClock ? 3 : 2, changedPartitions);

            boolean accept = step != 1;
            double proposedMu = model.mu.get();
            List<Double> proposedCoordinates = List.copyOf(model.coordinates.getElements());
            if (accept) {
                model.state.acceptCalculationNodes();
            } else {
                model.state.restore();
                model.state.restoreCalculationNodes();
            }
            model.state.setEverythingDirty(false);
            assertEquals(accept ? proposedMu : oldMu, model.mu.get());
            assertEquals(accept ? proposedCoordinates : oldCoordinates, model.coordinates.getElements());
            assertArrayEquals(accept ? proposed : previous, logLikelihoods(model), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, model.posterior.getCurrentLogP(), TOLERANCE);
            checkAgainstNative(model);
        }
    }

    private static double[] logLikelihoods(Model model) {
        return model.partitions.stream()
                .mapToDouble(Distribution::getCurrentLogP)
                .toArray();
    }

    private static void checkAgainstNative(Model model) {
        double weightedSum = 0.0;
        for (int i = 0; i < LENGTHS.length; i++) {
            TreeLikelihood actual = model.partitions.get(i);
            SiteModel site = (SiteModel) actual.siteModelInput.get();
            boolean perPartitionClocks = model.weightedExchange != null;
            double r = perPartitionClocks ? model.coordinates.get(i) : 629.0 * model.coordinates.get(i) / LENGTHS[i];
            var branch = actual.treeInput.get().getRoot().getChild(0);
            double clockRate = perPartitionClocks ? model.mu.get() * r : model.mu.get();
            assertEquals(clockRate, actual.branchRateModelInput.get().getRateForBranch(branch), 1e-14);
            assertEquals(perPartitionClocks ? 1.0 : r, site.getRateForCategory(0, branch), 1e-14);
            weightedSum += LENGTHS[i] * r;
            double reference = nativeLogLikelihood(actual, model.mu.get() * r);
            assertTrue(Double.isFinite(reference));
            assertEquals(reference, actual.getCurrentLogP(), TOLERANCE, actual.getID());
        }
        assertEquals(629.0, weightedSum, 1e-10);
    }

    // Same observed partition, tree and HKY parameter values. Independently put the whole
    // product on a native clock and leave the new native site model at unit mutation rate.
    private static double nativeLogLikelihood(TreeLikelihood actual, double effectiveRate) {
        HKY original = (HKY) ((SiteModel) actual.siteModelInput.get()).substModelInput.get();
        HKY hky = new HKY();
        hky.initByName(
                "kappa", new RealScalarParam<>(original.kappaInput.get().get(), PositiveReal.INSTANCE),
                "frequencies",
                        new Frequencies(new SimplexParam(
                                original.frequenciesInput.get().getFreqs().clone())));
        SiteModel site = new SiteModel();
        site.initByName("substModel", hky, "mutationRate", new RealScalarParam<>(1.0, PositiveReal.INSTANCE));
        StrictClockModel clock = new StrictClockModel();
        clock.initByName("clock.rate", new RealScalarParam<>(effectiveRate, PositiveReal.INSTANCE));
        TreeLikelihood nativeLikelihood = new TreeLikelihood();
        nativeLikelihood.initByName(
                "tree",
                new Tree(actual.treeInput.get().getRoot().copy()),
                "data",
                actual.dataInput.get(),
                "siteModel",
                site,
                "branchRateModel",
                clock,
                "implementation",
                TreeLikelihood.class.getName());
        return nativeLikelihood.calculateLogP();
    }

    private static Model buildModel(String source, Path project, boolean original) throws Exception {
        Randomizer.setSeed(12345L);
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver(), new Workspace(project)).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        var graph = evaluator.applyBestTiling(new BEASTState("rsv2", project));
        catalog.configureState(graph);
        List<TreeLikelihood> partitions = graph.likelihoodDistributions.stream()
                .map(likelihood -> assertInstanceOf(TreeLikelihood.class, likelihood))
                .sorted(Comparator.comparing(Distribution::getID))
                .toList();
        for (TreeLikelihood partition : partitions) {
            partition.implementationInput.setValue(TreeLikelihood.class.getName(), partition);
        }
        graph.initializeBEASTObjects();
        assertEquals(3, partitions.size());
        assertEquals(
                3,
                partitions.stream().map(p -> p.siteModelInput.get()).distinct().count());
        Tree tree = assertInstanceOf(Tree.class, namedNode(graph, "tree"));
        StrictClockModel clock = assertInstanceOf(
                StrictClockModel.class,
                partitions.getFirst().branchRateModelInput.get());
        RealScalarParam<?> mu =
                assertInstanceOf(RealScalarParam.class, namedNode(graph, original ? "meanClockRate" : "mu"));
        if (original) {
            assertEquals(
                    3,
                    partitions.stream()
                            .map(p -> p.branchRateModelInput.get())
                            .distinct()
                            .count());
        } else {
            assertSame(mu, clock.meanRateInput.get());
        }
        for (int i = 0; i < partitions.size(); i++) {
            TreeLikelihood partition = partitions.get(i);
            assertEquals((original ? "alignment_" : "a") + (i + 1) + "_likelihood", partition.getID());
            assertEquals(129, partition.dataInput.get().getTaxonCount());
            var data = assertInstanceOf(FilteredAlignment.class, partition.dataInput.get());
            assertEquals(LENGTHS[i], data.getSiteCount());
            int[] columns = data.indices();
            for (int j = 0; j < columns.length; j++) assertEquals(FIRST_COLUMNS[i] + 3 * j, columns[j]);
            if (!original) assertSame(clock, partition.branchRateModelInput.get());
            assertSame(tree, partition.treeInput.get());
            HKY hky = (HKY) ((SiteModel) partition.siteModelInput.get()).substModelInput.get();
            assertSame(namedNode(graph, (original ? "kappa_" : "kappa") + (i + 1)), hky.kappaInput.get());
            assertSame(
                    namedNode(graph, (original ? "baseFrequencies_" : "pi") + (i + 1)),
                    hky.frequenciesInput.get().frequenciesInput.get());
        }
        RealVectorParam<?> coordinates;
        DeltaExchangeOperator exchange = null;
        if (original) {
            coordinates = assertInstanceOf(RealVectorParam.class, namedNode(graph, "relativeRate"));
            var prior = assertInstanceOf(WeightedDirichlet.class, graph.priorDistributions.get(coordinates));
            assertSame(coordinates, prior.paramInput.get());
            assertEquals(
                    1,
                    graph.priorDistributions.values().stream()
                            .filter(WeightedDirichlet.class::isInstance)
                            .count());
            assertEquals(List.of(209.0, 210.0, 210.0), prior.weightsInput.get().getElements());
            assertEquals(List.of(1.0, 1.0, 1.0), prior.alphaInput.get().getElements());
            var rateMoves = graph.operators.stream()
                    .filter(op -> op.listStateNodes().contains(coordinates))
                    .toList();
            assertEquals(1, rateMoves.size());
            exchange = assertInstanceOf(DeltaExchangeOperator.class, rateMoves.getFirst());
            assertEquals(
                    List.of(209, 210, 210), exchange.parameterWeightsInput.get().getElements());
            // Six partition-specific HKY parameters plus the tree, mean clock, population size and joint rates.
            assertEquals(10, graph.stateNodes.size());
        } else {
            coordinates = assertInstanceOf(SimplexParam.class, namedNode(graph, "weights"));
            Dirichlet prior = assertInstanceOf(Dirichlet.class, graph.priorDistributions.get(coordinates));
            assertEquals(List.of(1.0, 1.0, 1.0), prior.alphaInput.get().getElements());
        }

        List<Distribution> terms = new ArrayList<>(graph.priorDistributions.values());
        terms.addAll(partitions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", terms);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        mu.set(0.008);
        double[] simplex = {0.2, 0.3, 0.5};
        for (int i = 0; i < simplex.length; i++) {
            coordinates.set(i, original ? 629.0 * simplex[i] / LENGTHS[i] : simplex[i]);
        }
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        return new Model(state, posterior, partitions, mu, coordinates, exchange);
    }

    private static StateNode namedNode(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(n -> id.equals(n.getID()))
                .findFirst()
                .orElseThrow();
    }

    private record Model(
            State state,
            CompoundDistribution posterior,
            List<TreeLikelihood> partitions,
            RealScalarParam<?> mu,
            RealVectorParam<?> coordinates,
            DeltaExchangeOperator weightedExchange) {}
}
