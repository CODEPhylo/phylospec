import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tiling.BeastXState;
import tiling.runner.RunMode;
import tiling.runner.RunnerOptions;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage matrix for BEAST X generators: one row per overload and configuration.
 *
 * <p>Each row evaluates the density of {@code x} at fixed states and compares it with a value
 * computed from the distribution's formula, not from BEAST X. Every row runs both in memory and
 * after an XML round trip through BEAST X. Rows with sampled hyperparameters change them between
 * states, so a frozen hyperparameter also fails.</p>
 */
public class BeastXGeneratorCoverageTest {

    private static final double TOLERANCE = 1e-9;

    /** Values for named state nodes and the expected log density of {@code x} there. */
    record State(Map<String, Double> values, double expectedLogDensity) {
    }

    record Row(String source, List<State> states) {
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                row("LogNormal(logMean, logSd), fixed",
                        "PositiveReal x ~ LogNormal(logMean=0.5, logSd=0.8)",
                        state(Map.of("x", 1.5), logNormal(1.5, 0.5, 0.8)),
                        state(Map.of("x", 0.3), logNormal(0.3, 0.5, 0.8))),
                row("LogNormal(logMean, logSd), sampled logMean and logSd",
                        """
                        Real m ~ Normal(mean=0.0, sd=1.0)
                        PositiveReal s ~ LogNormal(logMean=0.0, logSd=0.5)
                        PositiveReal x ~ LogNormal(logMean=m, logSd=s)
                        """,
                        state(Map.of("x", 1.5, "m", 0.5, "s", 0.8), logNormal(1.5, 0.5, 0.8)),
                        state(Map.of("x", 1.5, "m", -0.3, "s", 1.4), logNormal(1.5, -0.3, 1.4))),
                row("LogNormal(mean, logSd), fixed",
                        "PositiveReal x ~ LogNormal(mean=2.0, logSd=0.5)",
                        state(Map.of("x", 1.5), logNormalWithMean(1.5, 2.0, 0.5)),
                        state(Map.of("x", 4.0), logNormalWithMean(4.0, 2.0, 0.5))),
                row("LogNormal(mean, logSd), sampled logSd",
                        """
                        PositiveReal s ~ LogNormal(logMean=0.0, logSd=0.5)
                        PositiveReal x ~ LogNormal(mean=2.0, logSd=s)
                        """,
                        state(Map.of("x", 1.5, "s", 0.5), logNormalWithMean(1.5, 2.0, 0.5)),
                        state(Map.of("x", 1.5, "s", 1.2), logNormalWithMean(1.5, 2.0, 1.2))),
                row("LogNormal(mean, logSd), sampled mean and logSd",
                        """
                        PositiveReal m ~ LogNormal(logMean=0.5, logSd=0.5)
                        PositiveReal s ~ LogNormal(logMean=0.0, logSd=0.5)
                        PositiveReal x ~ LogNormal(mean=m, logSd=s)
                        """,
                        state(Map.of("x", 1.5, "m", 2.0, "s", 0.5), logNormalWithMean(1.5, 2.0, 0.5)),
                        state(Map.of("x", 1.5, "m", 3.5, "s", 1.2), logNormalWithMean(1.5, 3.5, 1.2)))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    public void inMemoryDensityMatchesFormula(String name, Row row) throws Exception {
        BeastXState state = new PhyloSpecRunner(row.source()).buildState("coverage");
        Likelihood prior = state.priorDistributions.get(state.stateNodesByPhyloSpecName.get("x"));

        for (State fixedState : row.states()) {
            fixedState.values().forEach((id, value) -> state.stateNodesByPhyloSpecName.get(id).setParameterValue(0, value));

            assertEquals(fixedState.expectedLogDensity(), logDensity(prior), TOLERANCE, name + " at " + fixedState.values());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    public void xmlDensityMatchesFormula(String name, Row row) throws Exception {
        List<Likelihood> priors = parseXmlPriors(name, row.source());
        Likelihood prior = priorOf(priors, "x");

        for (State fixedState : row.states()) {
            fixedState.values().forEach((id, value) -> dataParameter(priors, id).setParameterValue(0, value));

            assertEquals(fixedState.expectedLogDensity(), logDensity(prior), TOLERANCE, name + " at " + fixedState.values());
        }
    }

    /**
     * RelaxedClock uses LogNormal(mean=1, logSd) as the distribution of the relative branch rates,
     * so its log-scale sigma must equal the sampled logSd, and its mean must stay 1.
     */
    @Test
    public void relaxedClockLogNormalBaseUsesLogSd() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                PositiveReal s ~ Exponential(rate=3.0)
                Vector<Rate> branchRates ~ RelaxedClock(clockRate=1.0, base=LogNormal(mean=1.0, logSd=s), tree=tree)
                """).buildState("relaxedClock");

        LogNormalDistributionModel base =
                (LogNormalDistributionModel) state.treeRelaxedClockModels.values().iterator().next().distributionModel();
        Parameter s = state.stateNodesByPhyloSpecName.get("s");

        for (double logSd : new double[]{0.3, 1.0}) {
            s.setParameterValue(0, logSd);

            assertEquals(logSd, base.getSigma(), TOLERANCE);
            assertEquals(-0.5 * logSd * logSd, base.getMu(), TOLERANCE);
            assertEquals(1.0, base.mean(), TOLERANCE);
        }
    }

    /** The relaxed-clock XML must compute mu from the sampled logSd rather than writing a fixed value. */
    @Test
    public void relaxedClockXmlDerivesMuFromSampledLogSd() throws Exception {
        Path xmlPath = XmlTestSupport.xmlPath("relaxedClockLogNormalMu");
        XmlTestSupport.prepare(xmlPath);

        String xml = XmlTestSupport.buildAndWriteXml("relaxedClockLogNormalMu", """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                PositiveReal s ~ Exponential(rate=3.0)
                Vector<Rate> branchRates ~ RelaxedClock(clockRate=1.0, base=LogNormal(mean=1.0, logSd=s), tree=tree)
                Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=jc69(), branchRates=branchRates) observed as data

                mcmc {
                    Integer chainLength = 10
                    Logger fileLogger = fileLogger(logEvery=1, file="%s", parameters=[s])
                }
                """.formatted(XmlTestSupport.unixPath(XmlTestSupport.logPath("relaxedClockLogNormalMu"))), xmlPath);

        String branchRates = xml.substring(xml.indexOf("<discretizedBranchRates"), xml.indexOf("</discretizedBranchRates>"));

        XmlTestSupport.assertXmlContains(branchRates, "meanInRealSpace=\"false\"");
        XmlTestSupport.assertXmlContains(branchRates, "<sumParameter");
        XmlTestSupport.assertXmlContains(branchRates, "<productParameter");
        XmlTestSupport.assertXmlContains(branchRates, "<parameter idref=\"s\"/>");
    }

    /**
     * Prior sampling of LogNormal(mean=2, logSd=0.5), in memory and from XML: log(x) must have
     * mean log(2) - 0.5^2 / 2 and variance 0.5^2.
     */
    @Test
    public void logNormalWithMeanSamplesTheIntendedDistribution() throws Exception {
        double expectedLogMean = Math.log(2.0) - 0.125;

        for (boolean viaXml : new boolean[]{false, true}) {
            String runName = viaXml ? "logNormalMeanXml" : "logNormalMeanInMemory";
            Path logPath = XmlTestSupport.logPath(runName);
            XmlTestSupport.prepare(logPath);

            PhyloSpecRunner runner = new PhyloSpecRunner("""
                    PositiveReal x ~ LogNormal(mean=2.0, logSd=0.5)

                    mcmc {
                        Integer chainLength = 1000000
                        Integer randomSeed = 42
                        Logger fileLogger = fileLogger(logEvery=100, file="%s", parameters=[x])
                    }
                    """.formatted(XmlTestSupport.unixPath(logPath)));

            if (viaXml) {
                Path xmlPath = XmlTestSupport.xmlPath(runName);
                XmlTestSupport.prepare(xmlPath);
                runner.executeXmlRun(XmlRunnerOptions.builder(runName, xmlPath).build());
            } else {
                runner.run(RunnerOptions.builder(runName).mode(RunMode.EXECUTE_MCMC).build());
            }

            List<Double> logSamples = readColumn(logPath, "x").stream().skip(1000).map(Math::log).toList();
            double mean = logSamples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double variance = logSamples.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (logSamples.size() - 1);

            assertEquals(expectedLogMean, mean, 0.05, runName + " mean of log(x)");
            assertEquals(0.25, variance, 0.03, runName + " variance of log(x)");
        }
    }

    /* reference densities */

    private static double logNormal(double x, double mu, double sigma) {
        double z = (Math.log(x) - mu) / sigma;
        return -Math.log(x * sigma * Math.sqrt(2.0 * Math.PI)) - 0.5 * z * z;
    }

    /** LogNormal with a real-space mean and a log-space standard deviation. */
    private static double logNormalWithMean(double x, double mean, double logSd) {
        return logNormal(x, Math.log(mean) - 0.5 * logSd * logSd, logSd);
    }

    /* helpers */

    private static Arguments row(String name, String source, State... states) {
        return Arguments.of(name, new Row(source, List.of(states)));
    }

    private static State state(Map<String, Double> values, double expectedLogDensity) {
        return new State(values, expectedLogDensity);
    }

    private static double logDensity(Likelihood likelihood) {
        likelihood.makeDirty();
        return likelihood.getLogLikelihood();
    }

    /** Exports the model to XML, parses it with BEAST X, and returns its prior terms. */
    private static List<Likelihood> parseXmlPriors(String name, String source) throws Exception {
        String fileName = "coverage-" + name.replaceAll("\\W+", "-");
        Path xmlPath = XmlTestSupport.xmlPath(fileName);
        Path logPath = XmlTestSupport.logPath(fileName);
        XmlTestSupport.prepare(xmlPath, logPath);

        Likelihood posterior = new PhyloSpecRunner(source + """

                mcmc {
                    Integer chainLength = 10
                    Logger fileLogger = fileLogger(logEvery=1, file="%s", parameters=[x])
                }
                """.formatted(XmlTestSupport.unixPath(logPath)))
                .buildXmlRun(XmlRunnerOptions.builder("coverage", xmlPath).build())
                .mcmc()
                .getLikelihood();

        List<Likelihood> leaves = new ArrayList<>();
        collectLeaves(posterior, leaves);
        return leaves;
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
    private static AbstractDistributionLikelihood priorOf(List<Likelihood> leaves, String id) {
        for (Likelihood likelihood : leaves) {
            if (likelihood instanceof AbstractDistributionLikelihood prior && dataOf(prior, id) != null) {
                return prior;
            }
        }
        throw new AssertionError("No prior on parameter " + id + " in the parsed XML.");
    }

    private static Parameter dataParameter(List<Likelihood> leaves, String id) {
        return dataOf(priorOf(leaves, id), id);
    }

    private static Parameter dataOf(AbstractDistributionLikelihood prior, String id) {
        for (Attribute<double[]> data : prior.getDataList()) {
            if (data instanceof Parameter parameter && id.equals(parameter.getId())) {
                return parameter;
            }
        }
        return null;
    }

    private static List<Double> readColumn(Path logPath, String column) throws Exception {
        List<Double> values = new ArrayList<>();
        int index = -1;

        for (String line : Files.readAllLines(logPath)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            List<String> columns = List.of(line.trim().split("\t"));

            if (index < 0) {
                index = columns.indexOf(column);
                assertTrue(index >= 0, "Column " + column + " not found in " + logPath);
                continue;
            }

            values.add(Double.parseDouble(columns.get(index)));
        }

        return values;
    }
}
