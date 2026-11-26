import dr.evolution.tree.NodeRef;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastXCoreTileLibrary;
import tiling.BeastXState;
import tiling.runner.RunMode;
import tiling.runner.RunnerOptions;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tree priors and population functions of BEAST X, checked on a tree with three taxa.
 *
 * <p>Coalescent densities are compared with the coalescent formula. Speciation densities are
 * compared through a property that does not depend on how a paper scales them: a density
 * conditioned on the number of taxa integrates over all trees to the same value for every rate.
 * Each check runs in memory and after an XML round trip.</p>
 */
public class BeastXTreePriorCoverageTest {

    private static final String TAXA = """
            Tree source = fromNewick("((A:1.0,B:1.0):1.0,C:2.0);")
            Taxa taxa = taxa(tree=source)
            """;

    /** Node ages (root, inner node) at which each coalescent density is checked. */
    private static final double[][] AGES = {{1.0, 0.3}, {2.5, 0.9}};

    /** A tree prior on three taxa whose node ages and sampled parameters can be set. */
    interface ThreeTaxonPrior {
        double logDensity(double rootAge, double innerAge);

        void set(String parameter, double... values);
    }

    // coalescent

    static Stream<Arguments> coalescentRows() {
        return Stream.of(
                Arguments.of("Coalescent, constant size",
                        "",
                        "Coalescent(populationSize=2.0, taxa=taxa)",
                        (DoubleUnaryOperator) t -> 2.0),
                Arguments.of("Coalescent, exponential population function",
                        "PopulationFunction f = exponentialPopulationFunction(populationSize=3.0, growthRate=0.4)\n",
                        "Coalescent(populationSize=f, taxa=taxa)",
                        (DoubleUnaryOperator) t -> 3.0 * Math.exp(-0.4 * t)),
                Arguments.of("SkylineCoalescent with change times",
                        "",
                        "SkylineCoalescent(populationSizes=[2.0, 5.0, 1.0], changeTimes=[0.5, 1.2], taxa=taxa)",
                        (DoubleUnaryOperator) t -> t < 0.5 ? 2.0 : (t < 1.2 ? 5.0 : 1.0))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coalescentRows")
    public void inMemoryCoalescentMatchesFormula(String name, String prefix, String prior, DoubleUnaryOperator size)
            throws Exception {
        assertCoalescent(name, inMemory(prefix, prior), size);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coalescentRows")
    public void xmlCoalescentMatchesFormula(String name, String prefix, String prior, DoubleUnaryOperator size)
            throws Exception {
        assertCoalescent(name, fromXml(name, prefix, prior), size);
    }

    /**
     * The pieces of a compound population function use absolute ages, and a list may mix any
     * population functions. BEAST X XML has no compound population model yet.
     */
    @Test
    public void compoundPopulationFunction() throws Exception {
        String prefix = """
                PopulationFunction a = constantPopulationFunction(populationSize=2.0)
                PopulationFunction b = exponentialPopulationFunction(populationSize=5.0, growthRate=0.3)
                PopulationFunction f = compoundPopulationFunction(functions=[a, b], changeTimes=[0.8])
                """;
        String prior = "Coalescent(populationSize=f, taxa=taxa)";

        assertCoalescent("compound", inMemory(prefix, prior), t -> t < 0.8 ? 2.0 : 5.0 * Math.exp(-0.3 * t));

        Exception error = assertThrows(Exception.class, () -> fromXml("compound", prefix, prior));
        assertTrue(error.getMessage().contains("supported"), error.getMessage());
    }

    /**
     * logisticPopulationFunction follows the PhyloSpec definition N(t) = K / (1 + exp(r (t - t50))),
     * which BEAST X XML cannot express, so XML export fails with an explicit error.
     */
    @Test
    public void logisticPopulationFunction() throws Exception {
        String prefix = "PopulationFunction f = logisticPopulationFunction(inflectionAge=1.5, carryingCapacity=4.0, growthRate=0.8)\n";
        String prior = "Coalescent(populationSize=f, taxa=taxa)";

        assertCoalescent("logistic", inMemory(prefix, prior), t -> 4.0 / (1.0 + Math.exp(0.8 * (t - 1.5))));

        Exception error = assertThrows(Exception.class, () -> fromXml("logistic", prefix, prior));
        assertTrue(error.getMessage().contains("logisticPopulationFunction"), error.getMessage());
    }

    // Bayesian skyline

    /**
     * Group i holds groupSizes[i] coalescent events, counted from the tips. The last two arguments are
     * the population sizes of the groups holding the first event (the inner node) and the second (the root).
     */
    static Stream<Arguments> bayesianSkylineRows() {
        return Stream.of(
                Arguments.of("one event per group",
                        "SkylineCoalescent(populationSizes=[2.0, 5.0], groupSizes=[1, 1], taxa=taxa)", 2.0, 5.0),
                Arguments.of("both events in one group",
                        "SkylineCoalescent(populationSizes=[3.0], groupSizes=[2], taxa=taxa)", 3.0, 3.0)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bayesianSkylineRows")
    public void bayesianSkylineMatchesFormula(String name, String prior, double firstSize, double secondSize)
            throws Exception {
        for (ThreeTaxonPrior skyline : List.of(inMemory("", prior), fromXml("Bayesian skyline " + name, "", prior))) {
            for (double[] ages : AGES) {
                assertEquals(bayesianSkyline(ages[0], ages[1], firstSize, secondSize),
                        skyline.logDensity(ages[0], ages[1]), 1e-9,
                        name + " at root " + ages[0] + ", inner node " + ages[1]);
            }
        }
    }

    /** Sampled population sizes are used at their current values, in memory and from XML. */
    @Test
    public void bayesianSkylineFollowsSampledPopulationSizes() throws Exception {
        String prefix = "Vector<PositiveReal> sizes ~ IID(base=LogNormal(logMean=0.0, logSd=1.0), num=2)\n";
        String prior = "SkylineCoalescent(populationSizes=sizes, groupSizes=[1, 1], taxa=taxa)";

        for (ThreeTaxonPrior skyline : List.of(inMemory(prefix, prior), fromXml("Bayesian skyline sampled", prefix, prior))) {
            for (double[] sizes : new double[][]{{2.0, 5.0}, {0.7, 1.3}}) {
                skyline.set("sizes", sizes);
                for (double[] ages : AGES) {
                    assertEquals(bayesianSkyline(ages[0], ages[1], sizes[0], sizes[1]),
                            skyline.logDensity(ages[0], ages[1]), 1e-9,
                            "sizes " + sizes[0] + ", " + sizes[1]);
                }
            }
        }
    }

    /**
     * Prior-only MCMC of the tree, in memory and from XML. With one event per group, the time to the
     * first coalescence is exponential with mean N1 / 3 and the time from there to the root is
     * exponential with mean N2, so the root age has mean N1 / 3 + N2 and variance (N1 / 3)^2 + N2^2.
     */
    @Test
    public void bayesianSkylineTreeSamplesItsPrior() throws Exception {
        double first = 2.0;
        double second = 0.5;
        double expectedMean = first / 3.0 + second;
        double expectedVariance = first * first / 9.0 + second * second;

        for (boolean viaXml : new boolean[]{false, true}) {
            String runName = "bayesianSkyline" + (viaXml ? "Xml" : "InMemory");
            Path logPath = XmlTestSupport.logPath(runName);
            XmlTestSupport.prepare(logPath);

            PhyloSpecRunner runner = new PhyloSpecRunner(TAXA + """
                    Tree tree ~ SkylineCoalescent(populationSizes=[%s, %s], groupSizes=[1, 1], taxa=taxa)
                    Age root = rootAge(tree=tree)

                    mcmc {
                        Integer chainLength = 1000000
                        Integer randomSeed = 42
                        Logger fileLogger = fileLogger(logEvery=100, file="%s", parameters=[root])
                    }
                    """.formatted(first, second, XmlTestSupport.unixPath(logPath)));

            if (viaXml) {
                Path xmlPath = XmlTestSupport.xmlPath(runName);
                XmlTestSupport.prepare(xmlPath);
                runner.executeXmlRun(XmlRunnerOptions.builder(runName, xmlPath).build());
            } else {
                runner.run(RunnerOptions.builder(runName).mode(RunMode.EXECUTE_MCMC).build());
            }

            List<Double> samples = XmlTestSupport.readColumn(logPath, "root").stream().skip(1000).toList();
            double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double variance = samples.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (samples.size() - 1);

            assertEquals(expectedMean, mean, 0.05, runName + " mean root age");
            assertEquals(expectedVariance, variance, 0.1 * expectedVariance, runName + " variance of the root age");
        }
    }

    static Stream<Arguments> unsupportedBayesianSkylines() {
        return Stream.of(
                // BEAST X would silently replace group sizes that are all 1 with equal groups.
                Arguments.of("SkylineCoalescent(populationSizes=[1.0], groupSizes=[1], taxa=taxa)",
                        "must sum to the number of coalescent events"),
                Arguments.of("SkylineCoalescent(populationSizes=[1.0, 2.0], groupSizes=[1, 2], taxa=taxa)",
                        "must sum to the number of coalescent events"),
                // As in BEAST 3, groupSizes does not go with changeTimes.
                Arguments.of("SkylineCoalescent(populationSizes=[1.0, 2.0], changeTimes=[0.5], groupSizes=[1, 1], taxa=taxa)",
                        "groupSizes")
        );
    }

    @ParameterizedTest
    @MethodSource("unsupportedBayesianSkylines")
    public void unsupportedBayesianSkylinesFailExplicitly(String prior, String expected) {
        Exception error = assertThrows(Exception.class,
                () -> new PhyloSpecRunner(TAXA + "Tree tree ~ " + prior + "\n").buildState("treePrior"));
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }

    // speciation

    /** Expected value of the density integrated over all trees with three taxa. */
    static Stream<Arguments> speciationRows() {
        // BEAST X's Yule model uses the UNSCALED tree type and BirthDeath uses LABELED, which differ by a
        // constant factor. Neither depends on the rates, which is what this checks.
        return Stream.of(
                Arguments.of("Yule, birth rate 0.5", "Yule(birthRate=0.5, taxa=taxa)", 0.5, 60.0),
                Arguments.of("Yule, birth rate 2", "Yule(birthRate=2.0, taxa=taxa)", 0.5, 15.0),
                Arguments.of("BirthDeath, turnover 0.5", "BirthDeath(diversificationRate=1.0, turnover=0.5, taxa=taxa)", 1.0, 40.0),
                Arguments.of("BirthDeath, turnover 0.2", "BirthDeath(diversificationRate=2.0, turnover=0.2, taxa=taxa)", 1.0, 20.0),
                Arguments.of("BirthDeath, sampling probability 0.3",
                        "BirthDeath(diversificationRate=1.0, turnover=0.5, samplingProbability=0.3, taxa=taxa)", 1.0, 40.0),
                Arguments.of("BirthDeath rates 2 and 1", "BirthDeath(birthRate=2.0, deathRate=1.0, taxa=taxa)", 1.0, 40.0),
                Arguments.of("BirthDeath rates, sampling probability 0.3",
                        "BirthDeath(birthRate=3.0, deathRate=0.5, samplingProbability=0.3, taxa=taxa)", 1.0, 20.0)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("speciationRows")
    public void inMemorySpeciationDensityDoesNotDependOnRates(String name, String prior, double expected, double maxAge)
            throws Exception {
        assertEquals(expected, integralOverTrees(inMemory("", prior), maxAge), 0.01, name);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("speciationRows")
    public void xmlSpeciationDensityDoesNotDependOnRates(String name, String prior, double expected, double maxAge)
            throws Exception {
        assertEquals(expected, integralOverTrees(fromXml(name, "", prior), maxAge), 0.01, name);
    }

    // root age

    /**
     * With rootAge, the density is conditioned on the root: with the root fixed, it integrates over the
     * inner node and the three topologies to the same value for every rate and every root age (1 for
     * LABELED BirthDeath, 3 for UNSCALED Yule, which leaves out the topology factor).
     */
    static Stream<Arguments> rootConditionedRows() {
        return Stream.of(
                Arguments.of("Yule, rootAge 1", "Yule(birthRate=0.5, rootAge=1.0, taxa=taxa)", 3.0, 1.0),
                Arguments.of("Yule, rootAge 4", "Yule(birthRate=3.0, rootAge=4.0, taxa=taxa)", 3.0, 4.0),
                Arguments.of("BirthDeath, rootAge 2",
                        "BirthDeath(diversificationRate=1.0, turnover=0.5, rootAge=2.0, taxa=taxa)", 1.0, 2.0),
                Arguments.of("BirthDeath rates, rootAge 3",
                        "BirthDeath(birthRate=2.0, deathRate=1.0, rootAge=3.0, taxa=taxa)", 1.0, 3.0)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rootConditionedRows")
    public void inMemoryRootConditionedDensityIsNormalized(String name, String prior, double expected, double rootAge)
            throws Exception {
        assertEquals(expected, integralOverInnerAge(inMemory("", prior), rootAge), 1e-4, name);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rootConditionedRows")
    public void xmlRootConditionedDensityIsNormalized(String name, String prior, double expected, double rootAge)
            throws Exception {
        assertEquals(expected, integralOverInnerAge(fromXml(name, "", prior), rootAge), 1e-4, name);
    }

    /** With rootAge, the root stays at that age during MCMC while the inner node moves. */
    @Test
    public void rootStaysAtRootAgeDuringMcmc() throws Exception {
        String source = TAXA + """
                PositiveReal lambda ~ LogNormal(logMean=0.0, logSd=0.5)
                Tree tree ~ Yule(birthRate=lambda, rootAge=4.0, taxa=taxa)
                """;
        for (boolean viaXml : new boolean[]{false, true}) {
            String fileName = viaXml ? "tree-prior-root-age-xml" : "tree-prior-root-age-memory";
            Path xmlPath = XmlTestSupport.xmlPath(fileName);
            Path treeLogPath = XmlTestSupport.treeLogPath(fileName);
            XmlTestSupport.prepare(xmlPath, treeLogPath);
            String run = source + """

                    mcmc {
                        Integer chainLength = 2000
                        Logger treeLogger = treeLogger(logEvery=500, file="%s", trees=[tree])
                    }
                    """.formatted(XmlTestSupport.unixPath(treeLogPath));

            Model model = viaXml
                    ? new PhyloSpecRunner(run)
                            .executeXmlRun(XmlRunnerOptions.builder("treePrior", xmlPath).execute(true).build())
                            .mcmc().getLikelihood().getModel()
                    : new PhyloSpecRunner(run).runMCMC("treePrior").getLikelihood().getModel();

            TreeModel tree = findTree(model);
            assertEquals(4.0, tree.getNodeHeight(tree.getRoot()), 0.0, fileName + " root");
            NodeRef inner = tree.getInternalNode(0) == tree.getRoot() ? tree.getInternalNode(1) : tree.getInternalNode(0);
            // The starting tree puts the inner node half-way; 2000 steps move it.
            assertTrue(Math.abs(tree.getNodeHeight(inner) - 2.0) > 1e-9, fileName + " inner node did not move");
        }
    }

    /**
     * A sampled rootAge is the root height of the tree. The tree density is conditioned on the root
     * and integrates to the same value for every root age, so the root age keeps exactly its own
     * prior: prior-only MCMC of LogNormal(logMean=1, logSd=0.3) must give mean exp(1.045) and
     * variance (exp(0.09) - 1) exp(2.09), in memory and from XML. In every logged row the rootAge
     * equals the root height.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "Yule(birthRate=1.0, rootAge=a, taxa=taxa)",
            "BirthDeath(diversificationRate=1.0, turnover=0.5, rootAge=a, taxa=taxa)"
    })
    public void sampledRootAgeKeepsItsPrior(String prior) throws Exception {
        double expectedMean = Math.exp(1.0 + 0.3 * 0.3 / 2.0);
        double expectedVariance = (Math.exp(0.3 * 0.3) - 1.0) * Math.exp(2.0 + 0.3 * 0.3);

        for (boolean viaXml : new boolean[]{false, true}) {
            String runName = "sampledRootAge" + (viaXml ? "Xml" : "InMemory");
            Path logPath = XmlTestSupport.logPath(runName);
            XmlTestSupport.prepare(logPath);

            PhyloSpecRunner runner = new PhyloSpecRunner(TAXA + """
                    PositiveReal a ~ LogNormal(logMean=1.0, logSd=0.3)
                    Tree tree ~ %s
                    Age root = rootAge(tree=tree)

                    mcmc {
                        Integer chainLength = 1000000
                        Integer randomSeed = 42
                        Logger fileLogger = fileLogger(logEvery=100, file="%s", parameters=[a, root])
                    }
                    """.formatted(prior, XmlTestSupport.unixPath(logPath)));

            if (viaXml) {
                Path xmlPath = XmlTestSupport.xmlPath(runName);
                XmlTestSupport.prepare(xmlPath);
                runner.executeXmlRun(XmlRunnerOptions.builder(runName, xmlPath).build());
            } else {
                runner.run(RunnerOptions.builder(runName).mode(RunMode.EXECUTE_MCMC).build());
            }

            List<Double> rootAges = XmlTestSupport.readColumn(logPath, "a");
            List<Double> rootHeights = XmlTestSupport.readColumn(logPath, "root");
            for (int row = 0; row < rootAges.size(); row++) {
                assertEquals(rootHeights.get(row), rootAges.get(row), 1e-9, runName + " row " + row);
            }

            List<Double> samples = rootAges.stream().skip(1000).toList();
            double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double variance = samples.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (samples.size() - 1);

            assertEquals(expectedMean, mean, 0.05, runName + " mean of rootAge");
            assertEquals(expectedVariance, variance, 0.1 * expectedVariance, runName + " variance of rootAge");
        }
    }

    static Stream<Arguments> unsupportedSampledRootAges() {
        String rootAge = "PositiveReal a ~ LogNormal(logMean=1.0, logSd=0.3)\n";
        return Stream.of(
                // The rootAge variable is replaced by the root height, so elsewhere it would stay fixed.
                Arguments.of(rootAge + "Tree tree ~ Yule(birthRate=1.0, rootAge=a, taxa=taxa)\n"
                        + "Real z ~ Normal(mean=0.0, sd=a)\n", "can only be used as rootAge"),
                Arguments.of(rootAge + "Tree tree ~ Yule(birthRate=1.0, rootAge=a, taxa=taxa) observed as source\n",
                        "observed tree cannot have a sampled rootAge"),
                Arguments.of("Tree other ~ Yule(birthRate=1.0, taxa=taxa)\n"
                                + "Tree tree ~ Yule(birthRate=1.0, rootAge=rootAge(tree=other), taxa=taxa)\n",
                        "not a value computed from sampled values")
        );
    }

    @ParameterizedTest
    @MethodSource("unsupportedSampledRootAges")
    public void unsupportedSampledRootAgesFailExplicitly(String source, String expected) {
        Exception error = assertThrows(Exception.class, () -> XmlTestSupport.buildModel("treePrior", TAXA + source));
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }

    /** BirthDeath(birthRate, deathRate) equals BirthDeath(birthRate - deathRate, deathRate / birthRate). */
    @Test
    public void birthDeathRatesFollowSampledRates() throws Exception {
        String prefix = """
                PositiveReal b ~ LogNormal(logMean=1.0, logSd=0.1)
                PositiveReal d ~ LogNormal(logMean=0.0, logSd=0.1)
                """;
        String prior = "BirthDeath(birthRate=b, deathRate=d, taxa=taxa)";
        for (ThreeTaxonPrior rates : List.of(inMemory(prefix, prior), fromXml("BirthDeath rates", prefix, prior))) {
            for (double[] values : new double[][]{{Math.E, 1.0}, {2.5, 0.4}}) {
                rates.set("b", values[0]);
                rates.set("d", values[1]);
                ThreeTaxonPrior reference = inMemory("", "BirthDeath(diversificationRate=" + (values[0] - values[1])
                        + ", turnover=" + (values[1] / values[0]) + ", taxa=taxa)");
                for (double[] ages : AGES) {
                    assertEquals(reference.logDensity(ages[0], ages[1]), rates.logDensity(ages[0], ages[1]), 1e-9,
                            "b=" + values[0] + ", d=" + values[1]);
                }
            }
        }
    }

    static Stream<Arguments> unsupportedRootAgeAndRates() {
        return Stream.of(
                Arguments.of("Taxa dated = [taxon(name=\"A\"), taxon(name=\"B\", age=1.0), taxon(name=\"C\")]\n"
                        + "Tree tree ~ Yule(birthRate=1.0, rootAge=4.0, taxa=dated)\n", "contemporaneous"),
                Arguments.of(TAXA + "Tree tree ~ BirthDeath(diversificationRate=1.0, turnover=0.5, "
                        + "samplingProbability=0.5, rootAge=4.0, taxa=taxa)\n", "samplingProbability"),
                Arguments.of(TAXA + "Tree tree ~ BirthDeath(birthRate=1.0, deathRate=2.0, taxa=taxa)\n",
                        "birthRate > deathRate"),
                Arguments.of(TAXA + "Tree tree ~ Yule(birthRate=1.5, rootAge=3.0, taxa=taxa) observed as source\n",
                        "root at rootAge")
        );
    }

    /**
     * As in BEAST 3, rootAge also applies to an observed tree whose root is at rootAge (the observed
     * tree is not rescaled): its density equals the sampled tree's at the same node ages.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "Yule(birthRate=1.5, rootAge=2.0, taxa=taxa)",
            "BirthDeath(birthRate=3.0, deathRate=1.0, rootAge=2.0, taxa=taxa)"
    })
    public void observedTreeWithRootAge(String prior) throws Exception {
        double sampled = inMemory("", prior).logDensity(2.0, 1.0);
        String source = TAXA + "Tree tree ~ " + prior + " observed as source\n";

        Likelihood inMemory = (Likelihood) new PhyloSpecRunner(source).buildState("treePrior")
                .observedTreeDistributions.values().iterator().next();
        assertEquals(sampled, inMemory.getLogLikelihood(), 1e-9);

        Path xmlPath = XmlTestSupport.xmlPath("tree-prior-observed-root-age");
        XmlTestSupport.prepare(xmlPath);
        Likelihood posterior = new PhyloSpecRunner("PositiveReal z ~ LogNormal(logMean=0.0, logSd=1.0)\n" + source + """

                mcmc {
                    Integer chainLength = 1
                    Logger screenLogger = screenLogger(logEvery=1, parameters=[z])
                }
                """)
                .buildXmlRun(XmlRunnerOptions.builder("treePrior", xmlPath).build())
                .mcmc()
                .getLikelihood();
        assertTrue(Files.readString(xmlPath).contains("conditionalOnRoot=\"true\""));

        List<Likelihood> leaves = new ArrayList<>();
        collectLeaves(posterior, leaves);
        Likelihood fromXml = leaves.stream()
                .filter(leaf -> "tree_likelihood".equals(leaf.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tree_likelihood in the parsed XML."));
        assertEquals(sampled, fromXml.getLogLikelihood(), 1e-9);
    }

    /**
     * As in BEAST 3: rootAge needs contemporaneous taxa and an observed tree whose root is at
     * rootAge, and BirthDeath needs birthRate > deathRate.
     */
    @ParameterizedTest
    @MethodSource("unsupportedRootAgeAndRates")
    public void unsupportedRootAgeAndRatesFailExplicitly(String source, String expected) {
        Exception error = assertThrows(Exception.class, () -> new PhyloSpecRunner(source).buildState("treePrior"));
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }

    /** Integral of the density over the inner node age and the three labelled topologies, root at rootAge. */
    private static double integralOverInnerAge(ThreeTaxonPrior prior, double rootAge) {
        int steps = 2000;
        double width = rootAge / steps;
        double sum = 0.0;
        for (int j = 0; j < steps; j++) {
            sum += Math.exp(prior.logDensity(rootAge, (j + 0.5) * width));
        }
        return 3.0 * sum * width;
    }

    /**
     * BEAST X's FBD model takes birth and death rates, so for (diversificationRate, turnover) they
     * are derived. With sampled parameters, the XML must write them as expressions: written as
     * constants they would stay at their starting values. At each value, the density must equal
     * the model written with the matching speciation and extinction rates.
     *
     * <p>Only the XML path is checked: in memory, BEAST X's FBD model needs an origin, which the
     * FBD tile does not provide yet.</p>
     */
    @Test
    public void xmlFossilizedBirthDeathRatesFollowSampledDiversificationAndTurnover() throws Exception {
        ThreeTaxonPrior sampled = fromXml(
                "FBD diversification and turnover",
                """
                PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.3)
                Rate t ~ LogNormal(logMean=-1.0, logSd=0.3)
                """,
                "FossilizedBirthDeath(diversificationRate=r, turnover=t, serialSamplingRate=0.3, taxa=taxa)"
        );

        for (double[] value : new double[][]{{1.5, 0.2}, {0.6, 0.7}}) {
            double speciationRate = value[0] / (1.0 - value[1]);
            double extinctionRate = value[1] * speciationRate;
            ThreeTaxonPrior direct = fromXml(
                    "FBD speciation and extinction",
                    "",
                    "FossilizedBirthDeath(speciationRate=%s, extinctionRate=%s, serialSamplingRate=0.3, taxa=taxa)"
                            .formatted(speciationRate, extinctionRate)
            );

            sampled.set("r", value[0]);
            sampled.set("t", value[1]);

            assertEquals(direct.logDensity(2.0, 0.7), sampled.logDensity(2.0, 0.7), 1e-9,
                    "diversificationRate " + value[0] + ", turnover " + value[1]);
        }
    }

    // population functions

    @Test
    public void populationFunctions() {
        DemographicModel constant = populationFunction("constantPopulationFunction(populationSize=7.0)");
        DemographicModel exponential = populationFunction("exponentialPopulationFunction(populationSize=10.0, growthRate=0.5)");

        for (double t : new double[]{0.0, 1.0, 4.0}) {
            assertEquals(7.0, constant.getDemographicFunction().getDemographic(t), 1e-12);
            assertEquals(10.0 * Math.exp(-0.5 * t), exponential.getDemographicFunction().getDemographic(t), 1e-12);
        }
    }

    // helpers

    private static void assertCoalescent(String name, ThreeTaxonPrior prior, DoubleUnaryOperator size) {
        for (double[] ages : AGES) {
            assertEquals(coalescent(ages[0], ages[1], size), prior.logDensity(ages[0], ages[1]), 1e-6,
                    name + " at root " + ages[0] + ", inner node " + ages[1]);
        }
    }

    /**
     * Bayesian skyline density of three tips at age 0: the interval with three lineages and the first
     * coalescence use {@code firstSize}, the interval with two lineages and the root use {@code secondSize}.
     */
    private static double bayesianSkyline(double rootAge, double innerAge, double firstSize, double secondSize) {
        return -Math.log(firstSize) - 3.0 * innerAge / firstSize
                - Math.log(secondSize) - (rootAge - innerAge) / secondSize;
    }

    /**
     * Coalescent density of three tips at age 0 that coalesce at {@code innerAge} and {@code rootAge}:
     * each coalescence contributes 1 / N(t), and each interval with k lineages contributes
     * exp(-C(k, 2) * integral of 1 / N(t)).
     */
    private static double coalescent(double rootAge, double innerAge, DoubleUnaryOperator size) {
        return -Math.log(size.applyAsDouble(innerAge)) - Math.log(size.applyAsDouble(rootAge))
                - 3.0 * integral(size, 0.0, innerAge) - integral(size, innerAge, rootAge);
    }

    /** Integral of 1 / N(t), split at the change times used above so that steps in N(t) are exact. */
    private static double integral(DoubleUnaryOperator size, double from, double to) {
        double sum = 0.0;
        double start = from;
        for (double changeTime : new double[]{0.5, 0.8, 1.2, to}) {
            double end = Math.min(changeTime, to);
            if (end > start) {
                int steps = 100_000;
                double width = (end - start) / steps;
                for (int i = 0; i < steps; i++) {
                    sum += width / size.applyAsDouble(start + (i + 0.5) * width);
                }
                start = end;
            }
        }
        return sum;
    }

    /** Integral of the density over node ages 0 < inner < root < maxAge and the three labelled topologies. */
    private static double integralOverTrees(ThreeTaxonPrior prior, double maxAge) {
        int steps = 400;
        double width = maxAge / steps;
        double sum = 0.0;
        for (int i = 0; i < steps; i++) {
            double rootAge = (i + 0.5) * width;
            for (int j = 0; j < i; j++) {
                sum += Math.exp(prior.logDensity(rootAge, (j + 0.5) * width));
            }
            // The cell on the diagonal is cut in half by inner < root.
            sum += 0.5 * Math.exp(prior.logDensity(rootAge, (i + 0.25) * width));
        }
        return 3.0 * sum * width * width;
    }

    private static ThreeTaxonPrior inMemory(String prefix, String prior) throws Exception {
        BeastXState state = new PhyloSpecRunner(TAXA + prefix + "Tree tree ~ " + prior + "\n").buildState("treePrior");
        TreeModel tree = state.treeModelsByPhyloSpecName.get("tree");
        Likelihood likelihood = (Likelihood) state.treePriorDistributions.get(tree);
        return threeTaxonPrior(tree, likelihood, id -> state.stateNodesByPhyloSpecName.get(id));
    }

    private static ThreeTaxonPrior fromXml(String name, String prefix, String prior) throws Exception {
        String fileName = "tree-prior-" + name.replaceAll("\\W+", "-");
        Path xmlPath = XmlTestSupport.xmlPath(fileName);
        Path treeLogPath = XmlTestSupport.treeLogPath(fileName);
        XmlTestSupport.prepare(xmlPath, treeLogPath);

        Likelihood posterior = new PhyloSpecRunner(TAXA + prefix + "Tree tree ~ " + prior + "\n" + """

                mcmc {
                    Integer chainLength = 1
                    Logger treeLogger = treeLogger(logEvery=1, file="%s", trees=[tree])
                }
                """.formatted(XmlTestSupport.unixPath(treeLogPath)))
                .buildXmlRun(XmlRunnerOptions.builder("treePrior", xmlPath).build())
                .mcmc()
                .getLikelihood();

        List<Likelihood> leaves = new ArrayList<>();
        collectLeaves(posterior, leaves);
        Likelihood likelihood = leaves.stream()
                .filter(leaf -> "tree_prior".equals(leaf.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tree_prior in the parsed XML."));
        TreeModel tree = findTree(likelihood.getModel());
        return threeTaxonPrior(tree, likelihood, id -> findParameter(posterior.getModel(), id));
    }

    private static ThreeTaxonPrior threeTaxonPrior(
            TreeModel tree, Likelihood likelihood, java.util.function.Function<String, Parameter> parameters) {
        return new ThreeTaxonPrior() {
            @Override
            public double logDensity(double rootAge, double innerAge) {
                return evaluate(tree, likelihood, rootAge, innerAge);
            }

            @Override
            public void set(String parameter, double... values) {
                for (int i = 0; i < values.length; i++) {
                    parameters.apply(parameter).setParameterValue(i, values[i]);
                }
            }
        };
    }

    /** Finds a parameter by id among the variables of a model and its sub-models. */
    private static Parameter findParameter(Model model, String id) {
        for (int i = 0; i < model.getVariableCount(); i++) {
            if (model.getVariable(i) instanceof Parameter parameter && id.equals(parameter.getId())) {
                return parameter;
            }
        }
        for (int i = 0; i < model.getModelCount(); i++) {
            Parameter parameter = findParameter(model.getModel(i), id);
            if (parameter != null) {
                return parameter;
            }
        }
        return null;
    }

    private static double evaluate(TreeModel tree, Likelihood likelihood, double rootAge, double innerAge) {
        NodeRef root = tree.getRoot();
        NodeRef inner = null;
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            if (tree.getInternalNode(i) != root) {
                inner = tree.getInternalNode(i);
            }
        }
        tree.beginTreeEdit();
        tree.setNodeHeight(root, Math.max(rootAge, tree.getNodeHeight(root)));
        tree.setNodeHeight(inner, innerAge);
        tree.setNodeHeight(root, rootAge);
        tree.endTreeEdit();
        likelihood.makeDirty();
        return likelihood.getLogLikelihood();
    }

    private static TreeModel findTree(Model model) {
        if (model instanceof TreeModel tree) {
            return tree;
        }
        for (int i = 0; i < model.getModelCount(); i++) {
            TreeModel tree = findTree(model.getModel(i));
            if (tree != null) {
                return tree;
            }
        }
        return null;
    }

    private static void collectLeaves(Likelihood likelihood, List<Likelihood> leaves) {
        if (likelihood instanceof CompoundLikelihood compound) {
            for (int i = 0; i < compound.getLikelihoodCount(); i++) {
                collectLeaves(compound.getLikelihood(i), leaves);
            }
        } else {
            leaves.add(likelihood);
        }
    }

    private static DemographicModel populationFunction(String call) {
        List<Stmt> statements = new Parser(new Lexer("PopulationFunction f = " + call + "\n").scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        EvaluateTiles<BeastXState> evaluateTiles = new EvaluateTiles<>(
                new BeastXCoreTileLibrary().getTiles(),
                new VariableResolver(statements),
                stochasticityResolver
        );

        BeastXState state = new BeastXState("test");
        Object last = null;
        for (Tile<?, BeastXState> tile : evaluateTiles.getBestTiling(statements)) {
            last = tile.apply(state, new IdentityHashMap<>());
        }
        return (DemographicModel) last;
    }
}
