import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tiling.BeastXState;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks that the XML export keeps sampled distribution parameters live: after BEAST X
 * parses the XML, the child density must follow the hyperparameter, as it does in memory.
 */
public class BeastXXmlLiveDistributionParametersTest {

    private static final double TOLERANCE = 1e-10;

    private static final String NUCLEOTIDE_DATA = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Taxa taxa = taxa(data)
            Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
            """;

    /**
     * Each case compares the parsed XML of a hierarchical model, with the hyperparameter
     * set to a new value, against the in-memory model written with that value as a constant.
     */
    static Stream<Arguments> hierarchicalModels() {
        return Stream.of(
                Arguments.of("Normal mean",
                        "Real h ~ Normal(mean=0.0, sd=1.0)\nReal x ~ Normal(mean=h, sd=1.0)",
                        "Real x ~ Normal(mean=3.0, sd=1.0)",
                        new double[]{3.0}, new double[]{0.5}),
                Arguments.of("Normal sd",
                        "PositiveReal h ~ LogNormal(logMean=0.0, logSd=1.0)\nReal x ~ Normal(mean=0.0, sd=h)",
                        "Real x ~ Normal(mean=0.0, sd=2.5)",
                        new double[]{2.5}, new double[]{0.5}),
                Arguments.of("LogNormal logMean",
                        "Real h ~ Normal(mean=0.0, sd=1.0)\nPositiveReal x ~ LogNormal(logMean=h, logSd=1.0)",
                        "PositiveReal x ~ LogNormal(logMean=2.0, logSd=1.0)",
                        new double[]{2.0}, new double[]{1.5}),
                Arguments.of("LogNormal logSd",
                        "PositiveReal h ~ LogNormal(logMean=0.0, logSd=1.0)\nPositiveReal x ~ LogNormal(logMean=0.0, logSd=h)",
                        "PositiveReal x ~ LogNormal(logMean=0.0, logSd=2.5)",
                        new double[]{2.5}, new double[]{1.5}),
                Arguments.of("LogNormal real-space mean, logSd",
                        "PositiveReal h ~ LogNormal(logMean=0.0, logSd=1.0)\nPositiveReal x ~ LogNormal(mean=2.0, logSd=h)",
                        "PositiveReal x ~ LogNormal(mean=2.0, logSd=2.5)",
                        new double[]{2.5}, new double[]{1.5}),
                Arguments.of("Gamma shape",
                        "PositiveReal h ~ LogNormal(logMean=0.0, logSd=1.0)\nPositiveReal x ~ Gamma(shape=h, rate=1.0)",
                        "PositiveReal x ~ Gamma(shape=6.0, rate=1.0)",
                        new double[]{6.0}, new double[]{2.0}),
                Arguments.of("Gamma rate",
                        "PositiveReal h ~ LogNormal(logMean=0.0, logSd=1.0)\nPositiveReal x ~ Gamma(shape=2.0, rate=h)",
                        "PositiveReal x ~ Gamma(shape=2.0, rate=4.0)",
                        new double[]{4.0}, new double[]{2.0}),
                Arguments.of("Beta alpha",
                        "PositiveReal h ~ LogNormal(logMean=0.0, logSd=1.0)\nProbability x ~ Beta(alpha=h, beta=2.0)",
                        "Probability x ~ Beta(alpha=7.0, beta=2.0)",
                        new double[]{7.0}, new double[]{0.3}),
                Arguments.of("Beta beta",
                        "PositiveReal h ~ LogNormal(logMean=0.0, logSd=1.0)\nProbability x ~ Beta(alpha=2.0, beta=h)",
                        "Probability x ~ Beta(alpha=2.0, beta=7.0)",
                        new double[]{7.0}, new double[]{0.3}),
                Arguments.of("Uniform upper",
                        "Real h ~ Normal(mean=5.0, sd=0.1)\nReal x ~ Uniform(lower=0.0, upper=h)",
                        "Real x ~ Uniform(lower=0.0, upper=10.0)",
                        new double[]{10.0}, new double[]{2.5}),
                Arguments.of("Uniform lower",
                        "Real h ~ Normal(mean=0.0, sd=0.1)\nReal x ~ Uniform(lower=h, upper=10.0)",
                        "Real x ~ Uniform(lower=-5.0, upper=10.0)",
                        new double[]{-5.0}, new double[]{2.5}),
                Arguments.of("Dirichlet concentration",
                        "Vector<PositiveReal> h ~ IID(base=LogNormal(logMean=0.0, logSd=1.0), num=3)\n"
                                + "Simplex x ~ Dirichlet(concentration=h)",
                        "Simplex x ~ Dirichlet(concentration=[2.0, 3.0, 4.0])",
                        new double[]{2.0, 3.0, 4.0}, new double[]{0.2, 0.3, 0.5})
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hierarchicalModels")
    public void parsedXmlDensityFollowsSampledHyperparameter(
            String name,
            String hierarchicalSource,
            String constantSource,
            double[] hyperValue,
            double[] childValue
    ) throws Exception {
        List<Likelihood> xmlLikelihoods = parseXml(name, hierarchicalSource, "x");

        Parameter xmlChild = dataParameter(xmlLikelihoods, "x");
        Parameter xmlHyper = dataParameter(xmlLikelihoods, "h");
        Likelihood xmlChildPrior = priorOf(xmlLikelihoods, "x");

        setValues(xmlChild, childValue);
        double atStart = logDensity(xmlChildPrior);

        setValues(xmlHyper, hyperValue);
        double afterProposal = logDensity(xmlChildPrior);

        BeastXState constant = new PhyloSpecRunner(constantSource).buildState("xmlLiveConstant");
        Parameter constantChild = constant.stateNodesByPhyloSpecName.get("x");
        setValues(constantChild, childValue);
        double expected = logDensity(constant.priorDistributions.get(constantChild));

        assertNotEquals(atStart, afterProposal, TOLERANCE, name + " XML density did not react to the hyperparameter.");
        assertEquals(expected, afterProposal, TOLERANCE, name + " XML density uses a stale hyperparameter.");
    }

    @Test
    public void sampledUniformBoundIsNotWrittenAsFixedParameterBound() throws Exception {
        String sampled = writeXml(
                "sampledUniformBound",
                "Real h ~ Normal(mean=5.0, sd=0.1)\nReal x ~ Uniform(lower=0.0, upper=h)",
                "x"
        );
        XmlTestSupport.assertXmlContains(sampled, "<parameter id=\"x\" value=\"2.5\" lower=\"0.0\"/>");

        String fixed = writeXml(
                "fixedUniformBound",
                "Real x ~ Uniform(lower=0.0, upper=5.0)",
                "x"
        );
        XmlTestSupport.assertXmlContains(fixed, "<parameter id=\"x\" value=\"2.5\" lower=\"0.0\" upper=\"5.0\"/>");
    }

    @Test
    @Tag("beagle")
    public void relaxedClockLogSdStaysLiveInXml() throws Exception {
        List<Likelihood> likelihoods;

        try {
            likelihoods = parseXml(
                    "relaxedClockLogSd",
                    NUCLEOTIDE_DATA + """
                            PositiveReal s ~ Exponential(rate=3.0)
                            Vector<Rate> branchRates ~ RelaxedClock(clockRate=1.0, base=LogNormal(mean=1.0, logSd=s), tree=tree)
                            Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=jc69(), branchRates=branchRates) observed as data
                            """,
                    "s"
            );
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
                    "Skipping relaxed-clock XML check because BEAGLE native library is not available."
            );

            throw exception;
        }

        Parameter s = dataParameter(likelihoods, "s");
        Likelihood ownPrior = priorOf(likelihoods, "s");

        double atStart = otherLogDensity(likelihoods, ownPrior);
        setValues(s, new double[]{2.0});
        double afterProposal = otherLogDensity(likelihoods, ownPrior);

        assertNotEquals(atStart, afterProposal, TOLERANCE, "Relaxed-clock rates did not react to logSd.");
    }

    /**
     * End-to-end XML run: with mu ~ Normal(0, 1) and x ~ Normal(mu, 1), the prior gives
     * Var(x) = 2 and corr(mu, x) = 1 / sqrt(2). A frozen mean gives Var(x) = 1 and corr = 0.
     */
    @Test
    public void hierarchicalNormalXmlMcmcMatchesAnalyticMoments() throws Exception {
        Path xmlPath = XmlTestSupport.xmlPath("hierarchicalNormal");
        Path logPath = XmlTestSupport.logPath("hierarchicalNormal");
        XmlTestSupport.prepare(xmlPath, logPath);

        String source = """
                Real mu ~ Normal(mean=0.0, sd=1.0)
                Real x ~ Normal(mean=mu, sd=1.0)

                mcmc {
                    Integer chainLength = 1000000
                    Integer randomSeed = 42
                    Logger fileLogger = fileLogger(logEvery=100, file="%s", parameters=[mu, x])
                }
                """.formatted(XmlTestSupport.unixPath(logPath));

        new PhyloSpecRunner(source).executeXmlRun(
                XmlRunnerOptions.builder("hierarchicalNormalXml", xmlPath).build()
        );

        List<double[]> samples = readSamples(logPath);
        samples = samples.subList(samples.size() / 10, samples.size());

        double varianceMu = covariance(samples, 0, 0);
        double varianceX = covariance(samples, 1, 1);
        double correlation = covariance(samples, 0, 1) / Math.sqrt(varianceMu * varianceX);

        assertEquals(1.0, varianceMu, 0.15);
        assertEquals(2.0, varianceX, 0.25);
        assertEquals(1.0 / Math.sqrt(2.0), correlation, 0.1);
    }

    /** Writes the model to XML, parses it with BEAST X, and returns all leaf likelihoods. */
    private static List<Likelihood> parseXml(String name, String source, String loggedParameter) throws Exception {
        Path xmlPath = XmlTestSupport.xmlPath(name.replaceAll("\\W", ""));
        Path logPath = XmlTestSupport.logPath(name.replaceAll("\\W", ""));
        XmlTestSupport.prepare(xmlPath, logPath);

        Likelihood posterior = new PhyloSpecRunner(withLogger(source, logPath, loggedParameter))
                .buildXmlRun(XmlRunnerOptions.builder("xmlLive", xmlPath).build())
                .mcmc()
                .getLikelihood();

        List<Likelihood> leaves = new ArrayList<>();
        collectLeaves(posterior, leaves);
        return leaves;
    }

    private static String writeXml(String name, String source, String loggedParameter) throws Exception {
        Path xmlPath = XmlTestSupport.xmlPath(name);
        Path logPath = XmlTestSupport.logPath(name);
        XmlTestSupport.prepare(xmlPath, logPath);

        new PhyloSpecRunner(withLogger(source, logPath, loggedParameter))
                .buildXmlRun(XmlRunnerOptions.builder(name, xmlPath).build());

        return Files.readString(xmlPath);
    }

    private static String withLogger(String source, Path logPath, String loggedParameter) {
        return source + """

                mcmc {
                    Integer chainLength = 10
                    Logger fileLogger = fileLogger(logEvery=1, file="%s", parameters=[%s])
                }
                """.formatted(XmlTestSupport.unixPath(logPath), loggedParameter);
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

    /** Finds the prior whose data is the parameter with the given id. */
    private static Likelihood priorOf(List<Likelihood> likelihoods, String id) {
        for (Likelihood likelihood : likelihoods) {
            if (likelihood instanceof AbstractDistributionLikelihood prior) {
                for (Attribute<double[]> data : prior.getDataList()) {
                    if (data instanceof Parameter parameter && id.equals(parameter.getId())) {
                        return likelihood;
                    }
                }
            }
        }
        throw new AssertionError("No prior on parameter " + id + " in the parsed XML.");
    }

    private static Parameter dataParameter(List<Likelihood> likelihoods, String id) {
        for (Attribute<double[]> data : ((AbstractDistributionLikelihood) priorOf(likelihoods, id)).getDataList()) {
            if (data instanceof Parameter parameter && id.equals(parameter.getId())) {
                return parameter;
            }
        }
        throw new AssertionError("Parameter " + id + " not found.");
    }

    /** Sum of all log densities except the parameter's own prior. */
    private static double otherLogDensity(List<Likelihood> likelihoods, Likelihood ownPrior) {
        double total = 0.0;
        for (Likelihood likelihood : likelihoods) {
            if (likelihood != ownPrior) {
                total += logDensity(likelihood);
            }
        }
        return total;
    }

    private static void setValues(Parameter parameter, double[] values) {
        assertNotNull(parameter);
        for (int i = 0; i < values.length; i++) {
            parameter.setParameterValue(i, values[i]);
        }
    }

    private static double logDensity(Likelihood likelihood) {
        likelihood.makeDirty();
        return likelihood.getLogLikelihood();
    }

    /** Reads the mu and x columns of a BEAST X log file. */
    private static List<double[]> readSamples(Path logPath) throws Exception {
        List<double[]> samples = new ArrayList<>();
        int muColumn = -1;
        int xColumn = -1;

        for (String line : Files.readAllLines(logPath)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            List<String> columns = List.of(line.trim().split("\t"));

            if (muColumn < 0) {
                muColumn = columns.indexOf("mu");
                xColumn = columns.indexOf("x");
                continue;
            }

            samples.add(new double[]{
                    Double.parseDouble(columns.get(muColumn)),
                    Double.parseDouble(columns.get(xColumn))
            });
        }

        return samples;
    }

    private static double covariance(List<double[]> samples, int first, int second) {
        double meanFirst = samples.stream().mapToDouble(sample -> sample[first]).average().orElseThrow();
        double meanSecond = samples.stream().mapToDouble(sample -> sample[second]).average().orElseThrow();

        double sum = 0.0;
        for (double[] sample : samples) {
            sum += (sample[first] - meanFirst) * (sample[second] - meanSecond);
        }

        return sum / (samples.size() - 1);
    }
}
