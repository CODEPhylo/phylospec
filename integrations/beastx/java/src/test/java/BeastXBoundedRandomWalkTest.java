import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tiling.BeastXState;
import tiling.operators.OperatorBuilder;
import tiling.runner.RunMode;
import tiling.runner.RunnerOptions;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real parameters bounded on both sides are moved by a random walk on the logit scale. A reflecting
 * walk is accepted every time under a flat prior, so its adaptive window grew without limit: Uniform
 * samples became NaN, and Beta and two-sided Truncated samples had too small a variance.
 */
public class BeastXBoundedRandomWalkTest {

    @Test
    public void onlyParametersBoundedOnBothSidesUseTheLogitWalk() throws Exception {
        String source = """
                Probability p ~ Beta(alpha=2.0, beta=2.0)
                Real h ~ Truncated(base=Normal(mean=0.0, sd=1.0), lower=0.0)
                Real m ~ Normal(mean=0.0, sd=1.0)
                """;
        BeastXState state = new PhyloSpecRunner(source).buildState("boundedOperators");

        List<String> operators = new OperatorBuilder().summarize(state);

        assertTrue(operators.contains(
                "RandomWalkOperator(parameter=p, weight=1.0, windowSize=1.0, boundary=logit)"), operators.toString());
        assertTrue(operators.contains(
                "RandomWalkOperator(parameter=h, weight=1.0, windowSize=1.0, boundary=reflecting)"), operators.toString());
        assertTrue(operators.contains(
                "RandomWalkOperator(parameter=m, weight=1.0, windowSize=1.0, boundary=reflecting)"), operators.toString());

        Path xmlPath = XmlTestSupport.xmlPath("boundedOperators");
        XmlTestSupport.prepare(xmlPath);
        String xml = XmlTestSupport.buildAndWriteXml("boundedOperators", source + """

                mcmc {
                    Integer chainLength = 1
                    Logger screenLogger = screenLogger(logEvery=1, parameters=[p])
                }
                """, xmlPath);

        XmlTestSupport.assertXmlContains(xml,
                "<randomWalkOperator id=\"p_randomWalk\" windowSize=\"1.0\" weight=\"1.0\" boundaryCondition=\"logit\">");
        XmlTestSupport.assertXmlContains(xml,
                "<randomWalkOperator id=\"h_randomWalk\" windowSize=\"1.0\" weight=\"1.0\" boundaryCondition=\"reflecting\">");
    }

    static Stream<Arguments> boundedPriors() {
        return Stream.of(
                Arguments.of("Uniform(0, 2)", "Real x ~ Uniform(lower=0.0, upper=2.0)", 1.0, 1.0 / 3.0),
                Arguments.of("Beta(2, 2)", "Probability x ~ Beta(alpha=2.0, beta=2.0)", 0.5, 0.05),
                // Most of the mass is near the bounds, which checks the logit Hastings ratio.
                Arguments.of("Beta(0.5, 0.5)", "Probability x ~ Beta(alpha=0.5, beta=0.5)", 0.5, 0.125),
                // Normal(0, 1) truncated to [-1, 1]: variance 1 - 2 phi(1) / (Phi(1) - Phi(-1)).
                Arguments.of("Truncated Normal on [-1, 1]",
                        "Real x ~ Truncated(base=Normal(mean=0.0, sd=1.0), lower=-1.0, upper=1.0)",
                        0.0, 0.29112),
                // A sampled limit is left open, so x is moved by a reflecting walk with only the
                // lower bound: mean E[h] / 2, variance E[h^2] / 3 - (E[h] / 2)^2.
                Arguments.of("Uniform(0, h), h sampled",
                        "Real h ~ Normal(mean=5.0, sd=0.1)\nReal x ~ Uniform(lower=0.0, upper=h)",
                        2.5, 25.01 / 3.0 - 6.25)
        );
    }

    /** Prior-only MCMC, in memory and from XML: the samples must match the prior's mean and variance. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("boundedPriors")
    public void boundedParameterSamplesItsPrior(String name, String prior, double expectedMean, double expectedVariance)
            throws Exception {
        for (boolean viaXml : new boolean[]{false, true}) {
            String runName = "bounded" + (viaXml ? "Xml" : "InMemory");
            Path logPath = XmlTestSupport.logPath(runName);
            XmlTestSupport.prepare(logPath);

            PhyloSpecRunner runner = new PhyloSpecRunner(prior + """

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

            List<Double> samples = readColumn(logPath, "x").stream().skip(1000).toList();
            assertFalse(samples.stream().anyMatch(value -> value.isNaN()), name + " " + runName + " has NaN samples");

            double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double variance = samples.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (samples.size() - 1);

            assertEquals(expectedMean, mean, 0.02, name + " " + runName + " mean of x");
            assertEquals(expectedVariance, variance, 0.1 * expectedVariance, name + " " + runName + " variance of x");
        }
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
