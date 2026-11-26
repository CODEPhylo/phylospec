import dr.evolution.tree.NodeRef;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.math.distributions.GammaDistribution;
import dr.math.distributions.NormalDistribution;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PhyloCTMC, site rates and clocks of BEAST X, checked by comparing the tree likelihood with
 * Felsenstein pruning written here for JC69 on a three-taxon alignment.
 *
 * <p>The expected likelihood is computed on the tree that BEAST X holds, with each branch length
 * multiplied by its branch rate and the partition rate, and averaged over the site-rate
 * categories. Each row is checked in memory and after an XML round trip, before and after one of
 * its sampled parameters changes, so a frozen parameter also fails.</p>
 */
class BeastXLikelihoodCoverageTest {

    private static final double TOLERANCE = 1e-8;

    /** The sequences of three-taxa.nex. */
    private static final Map<String, String> SEQUENCES = Map.of(
            "A", "ACGTACGA",
            "B", "ACGTTCGA",
            "C", "AGGTACCA"
    );

    private static final String TREE = """
            Alignment data = fromNexus("src/test/java/resources/three-taxa.nex")
            Taxa taxa = taxa(data)
            Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
            """;

    private static final double[][] ONE_CATEGORY = JukesCantorPruning.ONE_CATEGORY;

    static Stream<Arguments> rows() {
        return Stream.of(
                row("PhyloCTMC", "", "",
                        (tree, p) -> jc69(tree, node -> 1.0, ONE_CATEGORY), null),
                row("PhyloCTMC fixed rate", "", ", rate=3.0",
                        (tree, p) -> jc69(tree, node -> 3.0, ONE_CATEGORY), null),
                row("PhyloCTMC sampled rate", "PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.5)", ", rate=r",
                        (tree, p) -> jc69(tree, node -> value(p, "r"), ONE_CATEGORY), "r"),
                row("StrictClock default rate", "Vector<Rate> br ~ StrictClock(tree=tree)", ", branchRates=br",
                        (tree, p) -> jc69(tree, node -> 1.0, ONE_CATEGORY), null),
                row("StrictClock",
                        """
                        PositiveReal c ~ LogNormal(logMean=0.0, logSd=0.5)
                        Vector<Rate> br ~ StrictClock(clockRate=c, tree=tree)""",
                        ", branchRates=br",
                        (tree, p) -> jc69(tree, node -> value(p, "c"), ONE_CATEGORY), "c"),
                row("RelaxedClock",
                        """
                        PositiveReal c ~ LogNormal(logMean=0.0, logSd=0.5)
                        Vector<Rate> br ~ RelaxedClock(clockRate=c, base=LogNormal(logMean=-0.08, logSd=0.4), tree=tree)""",
                        ", branchRates=br",
                        (tree, p) -> jc69(tree, relaxedClockRates(tree, p, -0.08, 0.4), ONE_CATEGORY), "c"),
                row("DiscreteGammaInv",
                        """
                        PositiveReal shape ~ LogNormal(logMean=0.0, logSd=0.5)
                        Vector<Rate> sr ~ DiscreteGammaInv(shape=shape, numCategories=4, numSites=numSites(data))""",
                        ", siteRates=sr",
                        (tree, p) -> jc69(tree, node -> 1.0, gammaCategories(value(p, "shape"), 4)), "shape"),
                row("DiscreteGammaInv with sampled rate",
                        """
                        PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.5)
                        Vector<Rate> sr ~ DiscreteGammaInv(shape=0.5, numCategories=4, numSites=numSites(data))""",
                        ", siteRates=sr, rate=r",
                        (tree, p) -> jc69(tree, node -> value(p, "r"), gammaCategories(0.5, 4)), "r"),
                // BEAST X adds a zero-weight invariant category for an explicit 0 and then gets the mean
                // rate wrong (#152); a proportion of 0 is the same model as none.
                row("DiscreteGammaInv with invariantProportion 0",
                        """
                        PositiveReal shape ~ LogNormal(logMean=0.0, logSd=0.5)
                        Vector<Rate> sr ~ DiscreteGammaInv(shape=shape, numCategories=4, invariantProportion=0.0, numSites=numSites(data))""",
                        ", siteRates=sr",
                        (tree, p) -> jc69(tree, node -> 1.0, gammaCategories(value(p, "shape"), 4)), "shape")
        );
    }

    /** BEAST X gets +I+G wrong (#152), so a fixed non-zero or a sampled proportion is rejected. */
    @ParameterizedTest
    @MethodSource("invariantProportions")
    void nonZeroInvariantProportionIsRejected(String prior, String invariantProportion) {
        Exception error = assertThrows(Exception.class, () -> buildModel(TREE + prior + "\n"
                + "Vector<Rate> sr ~ DiscreteGammaInv(shape=1.0, numCategories=4, invariantProportion="
                + invariantProportion + ", numSites=numSites(data))\n"
                + "Alignment x ~ PhyloCTMC(tree=tree, qMatrix=jc69(), siteRates=sr) observed as data\n"));
        assertTrue(error.getMessage().contains("non-zero invariantProportion"), error.getMessage());
    }

    static Stream<Arguments> invariantProportions() {
        return Stream.of(
                Arguments.of("", "0.2"),
                Arguments.of("Probability pInv ~ Beta(alpha=1.0, beta=4.0)", "pInv")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void likelihoodMatchesPruning(String name, String source, Expected expected, String changed) throws Exception {
        assertLikelihood(name + " in memory", findTreeLikelihood(buildModel(source).likelihood), expected, changed);
        assertLikelihood(name + " from XML", findTreeLikelihood(parseXml(name, source)), expected, changed);
    }

    @Test
    void rateComputedFromSampledValuesIsRejected() {
        Exception error = assertThrows(Exception.class, () -> buildModel(TREE + """
                PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.5)
                Alignment x ~ PhyloCTMC(tree=tree, qMatrix=jc69(), rate=2.0 * r) observed as data
                """));
        assertTrue(error.getMessage().contains("'rate'"), error.getMessage());
    }

    private static void assertLikelihood(String name, Likelihood likelihood, Expected expected, String changed) {
        TreeModel tree = findModel(likelihood.getModel(), TreeModel.class);
        Function<String, Parameter> parameters = id -> findParameter(likelihood.getModel(), id);

        assertEquals(expected.logLikelihood(tree, parameters), likelihood.getLogLikelihood(), TOLERANCE, name);

        if (changed != null) {
            parameters.apply(changed).setParameterValue(0, 1.7);
            assertEquals(expected.logLikelihood(tree, parameters), likelihood.getLogLikelihood(), TOLERANCE,
                    name + " after " + changed + " changed");
        }
    }

    private static double jc69(TreeModel tree, ToDoubleFunction<NodeRef> branchRate, double[][] siteCategories) {
        return JukesCantorPruning.logLikelihood(tree, SEQUENCES, branchRate, siteCategories);
    }

    /**
     * Gamma site rates by the median of each of k equal-probability categories, scaled to mean 1
     * (Yang 1994; BEAST X and BEAST 3 both discretize this way).
     */
    private static double[][] gammaCategories(double shape, int k) {
        double[] rates = new double[k];
        double[] weights = new double[k];
        double sum = 0.0;
        for (int i = 0; i < k; i++) {
            rates[i] = GammaDistribution.quantile((2.0 * i + 1.0) / (2.0 * k), shape, 1.0 / shape);
            sum += rates[i];
        }
        for (int i = 0; i < k; i++) {
            rates[i] *= k / sum;
            weights[i] = 1.0 / k;
        }
        return new double[][]{rates, weights};
    }

    /**
     * Relaxed-clock rates: clockRate times the base log-normal quantile of each branch's category,
     * without rescaling on the tree (as in BEAST 3).
     */
    private static ToDoubleFunction<NodeRef> relaxedClockRates(TreeModel tree, Function<String, Parameter> parameters,
                                                               double logMean, double logSd) {
        Parameter categories = parameters.apply("branchRateCategories");
        int branchCount = tree.getNodeCount() - 1;
        int rootNumber = tree.getRoot().getNumber();
        double clockRate = value(parameters, "c");
        return node -> {
            // Categories are indexed by node number, skipping the root.
            int number = node.getNumber();
            double category = categories.getParameterValue(number < rootNumber ? number : number - 1);
            return clockRate * Math.exp(logMean + logSd * NormalDistribution.quantile((category + 0.5) / branchCount, 0.0, 1.0));
        };
    }

    private static double value(Function<String, Parameter> parameters, String id) {
        return parameters.apply(id).getParameterValue(0);
    }

    private static Arguments row(String name, String priors, String phyloCtmcArguments, Expected expected,
                                 String changed) {
        String source = TREE + priors + "\n"
                + "Alignment x ~ PhyloCTMC(tree=tree, qMatrix=jc69()" + phyloCtmcArguments + ") observed as data\n";
        return Arguments.of(name, source, expected, changed);
    }

    private static tiling.BeastXModel buildModel(String source) throws Exception {
        try {
            return new PhyloSpecRunner(source).buildMaterializedModel("likelihood");
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(XmlTestSupport.isMissingBeagleLibrary(exception), "BEAGLE native library is not available.");
            throw exception;
        }
    }

    private static Likelihood parseXml(String name, String source) throws Exception {
        String fileName = "likelihood-" + name.replaceAll("\\W+", "-");
        Path xmlPath = XmlTestSupport.xmlPath(fileName);
        Path treeLogPath = XmlTestSupport.treeLogPath(fileName);
        XmlTestSupport.prepare(xmlPath, treeLogPath);

        try {
            return new PhyloSpecRunner(source + """

                    mcmc {
                        Integer chainLength = 1
                        Logger treeLogger = treeLogger(logEvery=1, file="%s", trees=[tree])
                    }
                    """.formatted(XmlTestSupport.unixPath(treeLogPath)))
                    .buildXmlRun(XmlRunnerOptions.builder("likelihood", xmlPath).build())
                    .mcmc()
                    .getLikelihood();
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(XmlTestSupport.isMissingBeagleLibrary(exception), "BEAGLE native library is not available.");
            throw exception;
        }
    }

    /** Finds the tree likelihood of the alignment x among nested compound likelihoods. */
    private static Likelihood findTreeLikelihood(Likelihood likelihood) {
        Likelihood found = findLikelihood(likelihood, "x_likelihood");
        if (found == null) {
            throw new AssertionError("No tree likelihood found.");
        }
        return found;
    }

    private static Likelihood findLikelihood(Likelihood likelihood, String id) {
        if (id.equals(likelihood.getId())) {
            return likelihood;
        }
        if (likelihood instanceof CompoundLikelihood compound) {
            for (Likelihood child : compound.getLikelihoods()) {
                Likelihood found = findLikelihood(child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T findModel(Model model, Class<T> type) {
        if (type.isInstance(model)) {
            return type.cast(model);
        }
        for (int i = 0; i < model.getModelCount(); i++) {
            T found = findModel(model.getModel(i), type);
            if (found != null) {
                return found;
            }
        }
        return null;
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

    /** The expected log-likelihood, from the tree and the current parameter values. */
    @FunctionalInterface
    interface Expected {
        double logLikelihood(TreeModel tree, Function<String, Parameter> parameters);
    }
}
