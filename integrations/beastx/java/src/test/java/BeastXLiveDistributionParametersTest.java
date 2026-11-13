import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tiling.BeastXState;
import tiling.runner.RunMode;
import tiling.runner.RunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that distribution parameters drawn from other distributions stay live
 * in the in-memory BEAST X model, i.e. the child density follows MCMC proposals.
 */
public class BeastXLiveDistributionParametersTest {

    private static final double TOLERANCE = 1e-10;

    /**
     * Each case compares a hierarchical model, with the hyperparameter set to a new value,
     * against the same model written with that value as a constant. A frozen hyperparameter
     * would keep the density of its starting value and fail the comparison.
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
                        new double[]{2.0, 3.0, 4.0}, new double[]{0.2, 0.3, 0.5}),
                Arguments.of("Truncated base Normal mean",
                        "Real h ~ Normal(mean=0.0, sd=1.0)\n"
                                + "Real x ~ Truncated(base=Normal(mean=h, sd=1.0), lower=-10.0, upper=10.0)",
                        "Real x ~ Truncated(base=Normal(mean=3.0, sd=1.0), lower=-10.0, upper=10.0)",
                        new double[]{3.0}, new double[]{0.5})
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hierarchicalModels")
    public void childDensityFollowsSampledHyperparameter(
            String name,
            String hierarchicalSource,
            String constantSource,
            double[] hyperValue,
            double[] childValue
    ) throws Exception {
        BeastXState hierarchical = buildState(hierarchicalSource);
        BeastXState constant = buildState(constantSource);

        setValues(hierarchical, "x", childValue);
        setValues(constant, "x", childValue);

        double atStart = childLogDensity(hierarchical);

        setValues(hierarchical, "h", hyperValue);

        double afterProposal = childLogDensity(hierarchical);

        assertNotEquals(atStart, afterProposal, TOLERANCE, name + " density did not react to the hyperparameter.");
        assertEquals(childLogDensity(constant), afterProposal, TOLERANCE, name + " density uses a stale hyperparameter.");
    }

    @Test
    public void uniformStateBoundsFollowSampledUpperBound() throws Exception {
        BeastXState state = buildState("Real h ~ Normal(mean=5.0, sd=0.1)\nReal x ~ Uniform(lower=0.0, upper=h)");

        setValues(state, "h", new double[]{10.0});

        Parameter x = state.stateNodesByPhyloSpecName.get("x");
        assertEquals(10.0, x.getBounds().getUpperLimit(0), TOLERANCE);
        assertEquals(0.0, x.getBounds().getLowerLimit(0), TOLERANCE);
    }

    @Test
    public void cauchyRejectsSampledLocation() {
        assertRejected(
                "Real h ~ Normal(mean=0.0, sd=1.0)\nReal x ~ Cauchy(location=h, scale=1.0)",
                "location",
                "Cauchy"
        );
    }

    @Test
    public void cauchyRejectsSampledScale() {
        assertRejected(
                "PositiveReal h ~ LogNormal(logMean=0.0, logSd=1.0)\nReal x ~ Cauchy(location=0.0, scale=h)",
                "scale",
                "Cauchy"
        );
    }

    @Test
    public void offsetRejectsSampledOffset() {
        assertRejected(
                "Real h ~ Normal(mean=0.0, sd=1.0)\nReal x ~ Offset(base=Exponential(rate=1.0), offset=h)",
                "offset",
                "Offset"
        );
    }

    /**
     * End-to-end check with a real MCMC run, which also covers likelihood caching.
     * With mu ~ Normal(0, 1) and x ~ Normal(mu, 1), the prior gives Var(x) = 2 and
     * corr(mu, x) = 1 / sqrt(2). A frozen mean would give Var(x) = 1 and corr(mu, x) = 0.
     */
    @Test
    public void hierarchicalNormalMcmcMatchesAnalyticMoments() throws Exception {
        Path logPath = Files.createTempFile("hierarchical-normal", ".log");

        String source = """
                Real mu ~ Normal(mean=0.0, sd=1.0)
                Real x ~ Normal(mean=mu, sd=1.0)

                mcmc {
                    Integer chainLength = 1000000
                    Integer randomSeed = 42
                    Logger fileLogger = fileLogger(logEvery=100, file="%s", parameters=[mu, x])
                }
                """.formatted(logPath.toString().replace("\\", "/"));

        new PhyloSpecRunner(source).run(
                RunnerOptions.builder("hierarchicalNormal").mode(RunMode.EXECUTE_MCMC).build()
        );

        List<double[]> samples = readSamples(logPath);
        samples = samples.subList(samples.size() / 10, samples.size());

        double varianceMu = variance(samples, 0);
        double varianceX = variance(samples, 1);
        double correlation = covariance(samples, 0, 1) / Math.sqrt(varianceMu * varianceX);

        assertEquals(1.0, varianceMu, 0.15);
        assertEquals(2.0, varianceX, 0.25);
        assertEquals(1.0 / Math.sqrt(2.0), correlation, 0.1);

        Files.deleteIfExists(logPath);
    }

    private static BeastXState buildState(String source) throws Exception {
        return new PhyloSpecRunner(source).buildState("liveDistributionParameters");
    }

    private static void setValues(BeastXState state, String name, double[] values) {
        Parameter parameter = state.stateNodesByPhyloSpecName.get(name);
        for (int i = 0; i < values.length; i++) {
            parameter.setParameterValue(i, values[i]);
        }
    }

    private static double childLogDensity(BeastXState state) {
        Parameter child = state.stateNodesByPhyloSpecName.get("x");
        Likelihood prior = state.priorDistributions.get(child);
        prior.makeDirty();
        return prior.getLogLikelihood();
    }

    private static void assertRejected(String source, String... expectedMessageParts) {
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> buildState(source)
        );

        for (String expectedMessagePart : expectedMessageParts) {
            assertTrue(
                    exception.getMessage().contains(expectedMessagePart),
                    "Expected error message to contain: " + expectedMessagePart
                            + "\n\nActual message:\n" + exception.getMessage()
            );
        }
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

    private static double variance(List<double[]> samples, int column) {
        return covariance(samples, column, column);
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
