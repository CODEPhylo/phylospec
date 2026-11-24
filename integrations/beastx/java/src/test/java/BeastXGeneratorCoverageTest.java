import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.SumParameter;
import dr.util.Attribute;
import org.apache.commons.math.MathException;
import org.apache.commons.math.special.Erf;
import org.apache.commons.math.special.Gamma;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tiling.BeastXModel;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    record State(Map<String, double[]> values, double expectedLogDensity) {
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
                        state(Map.of("x", 1.5, "m", 3.5, "s", 1.2), logNormalWithMean(1.5, 3.5, 1.2))),
                row("Truncated Normal, fixed",
                        "Real x ~ Truncated(base=Normal(mean=0.5, sd=1.0), lower=0.0, upper=2.0)",
                        state(Map.of("x", 1.0), truncatedNormal(1.0, 0.5, 1.0, 0.0, 2.0)),
                        state(Map.of("x", 0.2), truncatedNormal(0.2, 0.5, 1.0, 0.0, 2.0))),
                row("Truncated Normal, sampled base mean",
                        """
                        Real m ~ Normal(mean=0.0, sd=1.0)
                        Real x ~ Truncated(base=Normal(mean=m, sd=1.0), lower=0.0, upper=2.0)
                        """,
                        state(Map.of("x", 1.0, "m", 0.5), truncatedNormal(1.0, 0.5, 1.0, 0.0, 2.0)),
                        state(Map.of("x", 1.0, "m", -1.5), truncatedNormal(1.0, -1.5, 1.0, 0.0, 2.0))),
                row("Truncated Normal, lower bound only, sampled base sd",
                        """
                        PositiveReal s ~ LogNormal(logMean=0.0, logSd=0.5)
                        Real x ~ Truncated(base=Normal(mean=1.0, sd=s), lower=0.0)
                        """,
                        state(Map.of("x", 0.5, "s", 0.5), truncatedNormal(0.5, 1.0, 0.5, 0.0, Double.POSITIVE_INFINITY)),
                        state(Map.of("x", 0.5, "s", 2.0), truncatedNormal(0.5, 1.0, 2.0, 0.0, Double.POSITIVE_INFINITY))),

                // continuous distributions
                row("Normal, fixed",
                        "Real x ~ Normal(mean=0.5, sd=2.0)",
                        state(Map.of("x", 1.0), normal(1.0, 0.5, 2.0)),
                        state(Map.of("x", -3.0), normal(-3.0, 0.5, 2.0))),
                row("Normal, sampled mean and sd",
                        """
                        Real m ~ Normal(mean=0.0, sd=1.0)
                        PositiveReal s ~ LogNormal(logMean=0.0, logSd=0.5)
                        Real x ~ Normal(mean=m, sd=s)
                        """,
                        state(Map.of("x", 1.0, "m", 0.5, "s", 2.0), normal(1.0, 0.5, 2.0)),
                        state(Map.of("x", 1.0, "m", -1.0, "s", 0.7), normal(1.0, -1.0, 0.7))),
                row("Gamma, fixed",
                        "PositiveReal x ~ Gamma(shape=2.0, rate=3.0)",
                        state(Map.of("x", 0.5), gamma(0.5, 2.0, 3.0)),
                        state(Map.of("x", 2.0), gamma(2.0, 2.0, 3.0))),
                row("Gamma, sampled shape and rate",
                        """
                        PositiveReal a ~ LogNormal(logMean=0.0, logSd=0.5)
                        PositiveReal b ~ LogNormal(logMean=0.0, logSd=0.5)
                        PositiveReal x ~ Gamma(shape=a, rate=b)
                        """,
                        state(Map.of("x", 0.5, "a", 2.0, "b", 3.0), gamma(0.5, 2.0, 3.0)),
                        state(Map.of("x", 0.5, "a", 0.7, "b", 1.5), gamma(0.5, 0.7, 1.5))),
                row("Beta, fixed",
                        "Probability x ~ Beta(alpha=2.0, beta=5.0)",
                        state(Map.of("x", 0.3), beta(0.3, 2.0, 5.0)),
                        state(Map.of("x", 0.8), beta(0.8, 2.0, 5.0))),
                row("Beta, sampled alpha and beta",
                        """
                        PositiveReal a ~ LogNormal(logMean=0.0, logSd=0.5)
                        PositiveReal b ~ LogNormal(logMean=0.0, logSd=0.5)
                        Probability x ~ Beta(alpha=a, beta=b)
                        """,
                        state(Map.of("x", 0.3, "a", 2.0, "b", 5.0), beta(0.3, 2.0, 5.0)),
                        state(Map.of("x", 0.3, "a", 0.5, "b", 1.5), beta(0.3, 0.5, 1.5))),
                row("Uniform, fixed",
                        "Real x ~ Uniform(lower=-1.0, upper=3.0)",
                        state(Map.of("x", 0.0), -Math.log(4.0)),
                        state(Map.of("x", 2.5), -Math.log(4.0))),
                row("Uniform, sampled bounds",
                        """
                        Real l ~ Normal(mean=0.0, sd=1.0)
                        Real u ~ Normal(mean=4.0, sd=1.0)
                        Real x ~ Uniform(lower=l, upper=u)
                        """,
                        state(Map.of("x", 1.0, "l", 0.0, "u", 4.0), -Math.log(4.0)),
                        state(Map.of("x", 1.0, "l", -1.0, "u", 2.0), -Math.log(3.0))),
                row("Exponential, fixed",
                        "PositiveReal x ~ Exponential(rate=2.0)",
                        state(Map.of("x", 0.5), exponential(0.5, 2.0)),
                        state(Map.of("x", 3.0), exponential(3.0, 2.0))),
                row("Exponential, sampled rate",
                        """
                        PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.5)
                        PositiveReal x ~ Exponential(rate=r)
                        """,
                        state(Map.of("x", 0.5, "r", 2.0), exponential(0.5, 2.0)),
                        state(Map.of("x", 0.5, "r", 0.4), exponential(0.5, 0.4))),

                // vector-valued distributions
                row("Dirichlet, fixed concentration",
                        "Simplex x ~ Dirichlet(concentration=[2.0, 3.0, 4.0])",
                        vectorState(Map.of("x", new double[]{0.2, 0.3, 0.5}), dirichlet(new double[]{0.2, 0.3, 0.5}, 2.0, 3.0, 4.0)),
                        vectorState(Map.of("x", new double[]{0.6, 0.3, 0.1}), dirichlet(new double[]{0.6, 0.3, 0.1}, 2.0, 3.0, 4.0))),
                row("Dirichlet, sampled concentration",
                        """
                        Vector<PositiveReal> a ~ IID(base=LogNormal(logMean=0.0, logSd=0.5), num=3)
                        Simplex x ~ Dirichlet(concentration=a)
                        """,
                        vectorState(Map.of("x", new double[]{0.2, 0.3, 0.5}, "a", new double[]{2.0, 3.0, 4.0}),
                                dirichlet(new double[]{0.2, 0.3, 0.5}, 2.0, 3.0, 4.0)),
                        vectorState(Map.of("x", new double[]{0.2, 0.3, 0.5}, "a", new double[]{0.5, 1.0, 6.0}),
                                dirichlet(new double[]{0.2, 0.3, 0.5}, 0.5, 1.0, 6.0))),
                row("IID Normal, sampled base mean",
                        """
                        Real m ~ Normal(mean=0.0, sd=1.0)
                        Vector<Real> x ~ IID(base=Normal(mean=m, sd=2.0), num=3)
                        """,
                        vectorState(Map.of("x", new double[]{1.0, -0.5, 2.0}, "m", new double[]{0.5}),
                                normal(1.0, 0.5, 2.0) + normal(-0.5, 0.5, 2.0) + normal(2.0, 0.5, 2.0)),
                        vectorState(Map.of("x", new double[]{1.0, -0.5, 2.0}, "m", new double[]{-1.0}),
                                normal(1.0, -1.0, 2.0) + normal(-0.5, -1.0, 2.0) + normal(2.0, -1.0, 2.0))),
                row("IID Gamma, fixed",
                        "Vector<PositiveReal> x ~ IID(base=Gamma(shape=2.0, rate=3.0), num=2)",
                        vectorState(Map.of("x", new double[]{0.5, 1.5}), gamma(0.5, 2.0, 3.0) + gamma(1.5, 2.0, 3.0)),
                        vectorState(Map.of("x", new double[]{2.0, 0.1}), gamma(2.0, 2.0, 3.0) + gamma(0.1, 2.0, 3.0))),
                row("IID Beta, sampled alpha",
                        """
                        PositiveReal a ~ LogNormal(logMean=0.0, logSd=0.5)
                        Vector<Probability> x ~ IID(base=Beta(alpha=a, beta=2.0), num=2)
                        """,
                        vectorState(Map.of("x", new double[]{0.3, 0.8}, "a", new double[]{2.0}), beta(0.3, 2.0, 2.0) + beta(0.8, 2.0, 2.0)),
                        vectorState(Map.of("x", new double[]{0.3, 0.8}, "a", new double[]{0.5}), beta(0.3, 0.5, 2.0) + beta(0.8, 0.5, 2.0))),

                // Cauchy, Offset and discrete distributions
                row("Cauchy, fixed",
                        "Real x ~ Cauchy(location=1.0, scale=2.0)",
                        state(Map.of("x", 0.0), cauchy(0.0, 1.0, 2.0)),
                        state(Map.of("x", 4.0), cauchy(4.0, 1.0, 2.0))),
                row("Offset Exponential, fixed",
                        "Real x ~ Offset(base=Exponential(rate=2.0), offset=1.5)",
                        state(Map.of("x", 2.0), exponential(0.5, 2.0)),
                        state(Map.of("x", 4.0), exponential(2.5, 2.0))),
                row("Poisson, fixed",
                        "NonNegativeInteger x ~ Poisson(rate=2.5)",
                        state(Map.of("x", 0.0), poisson(0, 2.5)),
                        state(Map.of("x", 4.0), poisson(4, 2.5))),
                row("Poisson, sampled rate",
                        """
                        PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.5)
                        NonNegativeInteger x ~ Poisson(rate=r)
                        """,
                        state(Map.of("x", 3.0, "r", 2.5), poisson(3, 2.5)),
                        state(Map.of("x", 3.0, "r", 0.8), poisson(3, 0.8))),
                row("Binomial, fixed",
                        "NonNegativeInteger x ~ Binomial(numTrials=10, p=0.3)",
                        state(Map.of("x", 0.0), binomial(0, 10, 0.3)),
                        state(Map.of("x", 4.0), binomial(4, 10, 0.3))),
                row("Binomial, sampled p",
                        """
                        Probability p ~ Beta(alpha=2.0, beta=2.0)
                        NonNegativeInteger x ~ Binomial(numTrials=10, p=p)
                        """,
                        state(Map.of("x", 4.0, "p", 0.3), binomial(4, 10, 0.3)),
                        state(Map.of("x", 4.0, "p", 0.75), binomial(4, 10, 0.75))),
                row("Geometric, fixed",
                        "NonNegativeInteger x ~ Geometric(p=0.3)",
                        state(Map.of("x", 0.0), geometric(0, 0.3)),
                        state(Map.of("x", 3.0), geometric(3, 0.3))),
                row("Geometric, sampled p",
                        """
                        Probability p ~ Beta(alpha=2.0, beta=2.0)
                        NonNegativeInteger x ~ Geometric(p=p)
                        """,
                        state(Map.of("x", 2.0, "p", 0.3), geometric(2, 0.3)),
                        state(Map.of("x", 2.0, "p", 0.75), geometric(2, 0.75))),
                row("Bernoulli, fixed",
                        "Boolean x ~ Bernoulli(p=0.3)",
                        state(Map.of("x", 1.0), Math.log(0.3)),
                        state(Map.of("x", 0.0), Math.log(0.7))),
                row("Bernoulli, sampled p",
                        """
                        Probability p ~ Beta(alpha=2.0, beta=2.0)
                        Boolean x ~ Bernoulli(p=p)
                        """,
                        state(Map.of("x", 1.0, "p", 0.3), Math.log(0.3)),
                        state(Map.of("x", 1.0, "p", 0.75), Math.log(0.75))),
                row("DiscreteUniform, fixed (both bounds included)",
                        "Integer x ~ DiscreteUniform(lower=2, upper=5)",
                        state(Map.of("x", 2.0), -Math.log(4.0)),
                        state(Map.of("x", 5.0), -Math.log(4.0))),
                row("IID Poisson, sampled rate",
                        """
                        PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.5)
                        Vector<NonNegativeInteger> x ~ IID(base=Poisson(rate=r), num=3)
                        """,
                        vectorState(Map.of("x", new double[]{0, 2, 5}, "r", new double[]{2.5}), poisson(0, 2.5) + poisson(2, 2.5) + poisson(5, 2.5)),
                        vectorState(Map.of("x", new double[]{0, 2, 5}, "r", new double[]{0.8}), poisson(0, 0.8) + poisson(2, 0.8) + poisson(5, 0.8))),
                row("Offset Gamma, fixed",
                        "Real x ~ Offset(base=Gamma(shape=2.0, rate=3.0), offset=-1.0)",
                        state(Map.of("x", -0.5), gamma(0.5, 2.0, 3.0)),
                        state(Map.of("x", 1.0), gamma(2.0, 2.0, 3.0))),

                // Offset with any base: the XML writes the base prior of x - offset
                row("Offset Normal, fixed",
                        "Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=1.5)",
                        state(Map.of("x", 2.0), normal(0.5, 0.0, 1.0)),
                        state(Map.of("x", 0.0), normal(-1.5, 0.0, 1.0))),
                row("Offset LogNormal, fixed",
                        "Real x ~ Offset(base=LogNormal(logMean=0.0, logSd=1.0), offset=-1.0)",
                        state(Map.of("x", 0.5), logNormal(1.5, 0.0, 1.0)),
                        state(Map.of("x", 2.0), logNormal(3.0, 0.0, 1.0))),
                row("Offset Uniform, fixed",
                        "Real x ~ Offset(base=Uniform(lower=0.0, upper=2.0), offset=1.0)",
                        state(Map.of("x", 2.5), Math.log(0.5)),
                        state(Map.of("x", 0.5), Double.NEGATIVE_INFINITY)),
                row("Offset Beta, fixed",
                        "Real x ~ Offset(base=Beta(alpha=2.0, beta=3.0), offset=1.0)",
                        state(Map.of("x", 1.4), beta(0.4, 2.0, 3.0)),
                        state(Map.of("x", 1.8), beta(0.8, 2.0, 3.0))),
                row("Offset Cauchy, fixed",
                        "Real x ~ Offset(base=Cauchy(location=0.0, scale=1.0), offset=3.0)",
                        state(Map.of("x", 4.0), cauchy(1.0, 0.0, 1.0)),
                        state(Map.of("x", 0.0), cauchy(-3.0, 0.0, 1.0))),
                row("Offset of Offset, fixed",
                        "Real x ~ Offset(base=Offset(base=Exponential(rate=2.0), offset=1.0), offset=1.0)",
                        state(Map.of("x", 2.5), exponential(0.5, 2.0)),
                        state(Map.of("x", 4.0), exponential(2.0, 2.0))),
                row("Offset, sampled offset",
                        """
                        Real o ~ Normal(mean=0.0, sd=1.0)
                        Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=o)
                        """,
                        state(Map.of("x", 2.0, "o", 1.5), normal(0.5, 0.0, 1.0)),
                        state(Map.of("x", 2.0, "o", -0.5), normal(2.5, 0.0, 1.0))),
                row("Offset Normal, sampled base mean",
                        """
                        Real m ~ Normal(mean=0.0, sd=1.0)
                        Real x ~ Offset(base=Normal(mean=m, sd=1.0), offset=1.5)
                        """,
                        state(Map.of("x", 2.0, "m", 0.0), normal(0.5, 0.0, 1.0)),
                        state(Map.of("x", 2.0, "m", 1.2), normal(0.5, 1.2, 1.0))),
                row("Offset of Truncated, fixed (the truncation is kept)",
                        "Real x ~ Offset(base=Truncated(base=Normal(mean=0.0, sd=1.0), lower=0.0), offset=2.0)",
                        state(Map.of("x", 2.5), truncatedNormal(0.5, 0.0, 1.0, 0.0, Double.POSITIVE_INFINITY)),
                        state(Map.of("x", 1.5), Double.NEGATIVE_INFINITY)),
                row("Truncated of Offset, fixed",
                        "Real x ~ Truncated(base=Offset(base=Normal(mean=0.0, sd=1.0), offset=2.0), lower=2.0)",
                        state(Map.of("x", 2.5), truncatedNormal(2.5, 2.0, 1.0, 2.0, Double.POSITIVE_INFINITY)),
                        state(Map.of("x", 1.5), Double.NEGATIVE_INFINITY)),
                row("IID Offset, sampled offset",
                        """
                        Real o ~ Normal(mean=0.0, sd=1.0)
                        Vector<Real> x ~ IID(base=Offset(base=Normal(mean=0.0, sd=1.0), offset=o), num=2)
                        """,
                        vectorState(Map.of("x", new double[]{1.5, 2.0}, "o", new double[]{1.0}),
                                normal(0.5, 0.0, 1.0) + normal(1.0, 0.0, 1.0)),
                        vectorState(Map.of("x", new double[]{1.5, 2.0}, "o", new double[]{-1.0}),
                                normal(2.5, 0.0, 1.0) + normal(3.0, 0.0, 1.0))),
                row("IID Binomial, sampled p",
                        """
                        Probability p ~ Beta(alpha=2.0, beta=2.0)
                        Vector<NonNegativeInteger> x ~ IID(base=Binomial(numTrials=10, p=p), num=3)
                        """,
                        vectorState(Map.of("x", new double[]{0, 4, 10}, "p", new double[]{0.3}),
                                binomial(0, 10, 0.3) + binomial(4, 10, 0.3) + binomial(10, 10, 0.3)),
                        vectorState(Map.of("x", new double[]{0, 4, 10}, "p", new double[]{0.75}),
                                binomial(0, 10, 0.75) + binomial(4, 10, 0.75) + binomial(10, 10, 0.75)))
        );
    }

    /**
     * Rows whose distributions BEAST X XML export does not support yet. They are checked in memory,
     * and XML export must fail with an explicit error instead of writing a wrong model.
     */
    static Stream<Arguments> xmlUnsupportedRows() {
        return Stream.of(
                // BEAST X has no XML element for a categorical distribution.
                row("Categorical, fixed (categories start at 1)",
                        "PositiveInteger x ~ Categorical(probabilities=[0.1, 0.2, 0.7])",
                        state(Map.of("x", 1.0), Math.log(0.1)),
                        state(Map.of("x", 3.0), Math.log(0.7))),
                // The truncation bounds of the shifted base would have to move with the offset.
                row("Truncated of Offset, sampled offset",
                        """
                        Real o ~ Normal(mean=0.0, sd=1.0)
                        Real x ~ Truncated(base=Offset(base=Normal(mean=0.0, sd=1.0), offset=o), lower=2.0)
                        """,
                        state(Map.of("x", 2.5, "o", 1.0), truncatedNormal(2.5, 1.0, 1.0, 2.0, Double.POSITIVE_INFINITY)),
                        state(Map.of("x", 2.5, "o", 0.0), truncatedNormal(2.5, 0.0, 1.0, 2.0, Double.POSITIVE_INFINITY)))
        );
    }

    static Stream<Arguments> allRows() {
        return Stream.concat(rows(), xmlUnsupportedRows());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allRows")
    public void inMemoryDensityMatchesFormula(String name, Row row) throws Exception {
        BeastXState state = new PhyloSpecRunner(row.source()).buildState("coverage");
        Likelihood prior = state.priorDistributions.get(state.stateNodesByPhyloSpecName.get("x"));

        for (State fixedState : row.states()) {
            fixedState.values().forEach((id, values) -> setValues(state.stateNodesByPhyloSpecName.get(id), values));

            assertEquals(fixedState.expectedLogDensity(), logDensity(prior), TOLERANCE, name + " at " + describe(fixedState));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    public void xmlDensityMatchesFormula(String name, Row row) throws Exception {
        List<Likelihood> priors = parseXmlPriors(name, row.source());
        Likelihood prior = priorOf(priors, "x");

        for (State fixedState : row.states()) {
            fixedState.values().forEach((id, values) -> setValues(dataParameter(priors, id), values));

            assertEquals(fixedState.expectedLogDensity(), logDensity(prior), TOLERANCE, name + " at " + describe(fixedState));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("xmlUnsupportedRows")
    public void xmlExportOfUnsupportedDistributionFails(String name, Row row) {
        Exception error = assertThrows(Exception.class, () -> parseXmlPriors(name, row.source()));

        assertTrue(error.getMessage().contains("supported"), name + ": " + error.getMessage());
    }

    /**
     * Priors whose density is not a BEAST X model: BEAST X restores a parameter after a rejected
     * proposal only if it is registered with a model. Before the fix these parameters kept the
     * rejected value, and the chain sampled a wrong distribution (or, from XML, stopped).
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "Real x ~ Cauchy(location=0.0, scale=1.0)",
            "Real x ~ Truncated(base=Normal(mean=0.0, sd=1.0), lower=0.0)",
            "Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=1.0)",
            "NonNegativeInteger x ~ Geometric(p=0.3)",
            "PositiveInteger x ~ Categorical(probabilities=[0.1, 0.2, 0.7])"
    })
    public void rejectedProposalIsRestored(String source) throws Exception {
        BeastXModel model = new PhyloSpecRunner(source).buildModel("restore");
        assertRestored(model.posterior.getModel(), model.beastState.stateNodesByPhyloSpecName.get("x"), source);

        if (source.contains("Categorical")) {
            return;
        }

        List<Likelihood> priors = parseXmlPriors("restore " + source, source);
        Likelihood posterior = parsedPosterior("restore " + source);
        assertRestored(posterior.getModel(), dataParameter(priors, "x"), source + " (XML)");
    }

    /**
     * Prior sampling of Offset(Truncated(Normal(0, 1), lower=0), 2), in memory and from XML:
     * x = 2 + |z| has mean 2 + sqrt(2 / pi) and variance 1 - 2 / pi.
     */
    @Test
    public void offsetOfTruncatedNormalSamplesTheIntendedDistribution() throws Exception {
        for (boolean viaXml : new boolean[]{false, true}) {
            String runName = viaXml ? "offsetTruncatedXml" : "offsetTruncatedInMemory";
            Path logPath = XmlTestSupport.logPath(runName);
            XmlTestSupport.prepare(logPath);

            PhyloSpecRunner runner = new PhyloSpecRunner("""
                    Real x ~ Offset(base=Truncated(base=Normal(mean=0.0, sd=1.0), lower=0.0), offset=2.0)

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
            double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double variance = samples.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (samples.size() - 1);

            assertEquals(2.0 + Math.sqrt(2.0 / Math.PI), mean, 0.03, runName + " mean of x");
            assertEquals(1.0 - 2.0 / Math.PI, variance, 0.03, runName + " variance of x");
        }
    }

    /** StrictClock's clockRate is optional and defaults to 1. */
    @Test
    public void strictClockRateDefaultsToOne() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                Vector<Rate> branchRates ~ StrictClock(tree=tree)
                """).buildState("strictClock");

        List<Parameter> clockRates = state.treeStrictClockRateParameters.values().iterator().next();

        assertEquals(1, clockRates.size());
        assertEquals(1.0, clockRates.get(0).getParameterValue(0), TOLERANCE);
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
                    Sampler sampler = mcmc(chainLength=10)
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
                        Sampler sampler = mcmc(chainLength=1000000)
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

    private static double normal(double x, double mean, double sd) {
        double z = (x - mean) / sd;
        return -Math.log(sd * Math.sqrt(2.0 * Math.PI)) - 0.5 * z * z;
    }

    private static double normalCdf(double x, double mean, double sd) {
        if (x == Double.POSITIVE_INFINITY) {
            return 1.0;
        }
        try {
            return 0.5 * (1.0 + Erf.erf((x - mean) / (sd * Math.sqrt(2.0))));
        } catch (MathException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static double gamma(double x, double shape, double rate) {
        return shape * Math.log(rate) - Gamma.logGamma(shape) + (shape - 1.0) * Math.log(x) - rate * x;
    }

    private static double beta(double x, double alpha, double beta) {
        double logBeta = Gamma.logGamma(alpha) + Gamma.logGamma(beta) - Gamma.logGamma(alpha + beta);
        return (alpha - 1.0) * Math.log(x) + (beta - 1.0) * Math.log(1.0 - x) - logBeta;
    }

    private static double exponential(double x, double rate) {
        return Math.log(rate) - rate * x;
    }

    private static double cauchy(double x, double location, double scale) {
        double z = (x - location) / scale;
        return -Math.log(Math.PI * scale * (1.0 + z * z));
    }

    private static double poisson(int k, double rate) {
        return k * Math.log(rate) - rate - Gamma.logGamma(k + 1.0);
    }

    private static double binomial(int k, int n, double p) {
        double logChoose = Gamma.logGamma(n + 1.0) - Gamma.logGamma(k + 1.0) - Gamma.logGamma(n - k + 1.0);
        return logChoose + k * Math.log(p) + (n - k) * Math.log(1.0 - p);
    }

    private static double dirichlet(double[] x, double... concentration) {
        double sum = 0.0;
        double logDensity = 0.0;
        for (int i = 0; i < x.length; i++) {
            sum += concentration[i];
            logDensity += (concentration[i] - 1.0) * Math.log(x[i]) - Gamma.logGamma(concentration[i]);
        }
        return logDensity + Gamma.logGamma(sum);
    }

    /** Number of failures before the first success, as in the PhyloSpec reference. */
    private static double geometric(int k, double p) {
        return Math.log(p) + k * Math.log(1.0 - p);
    }

    /** Normal restricted to [lower, upper]: the Normal density divided by the probability of that interval. */
    private static double truncatedNormal(double x, double mean, double sd, double lower, double upper) {
        return normal(x, mean, sd) - Math.log(normalCdf(upper, mean, sd) - normalCdf(lower, mean, sd));
    }

    /* helpers */

    private static Arguments row(String name, String source, State... states) {
        return Arguments.of(name, new Row(source, List.of(states)));
    }

    private static State state(Map<String, Double> values, double expectedLogDensity) {
        Map<String, double[]> vectors = new java.util.TreeMap<>();
        values.forEach((id, value) -> vectors.put(id, new double[]{value}));
        return new State(vectors, expectedLogDensity);
    }

    /** A state for vector-valued nodes, such as a Dirichlet or IID draw. */
    private static State vectorState(Map<String, double[]> values, double expectedLogDensity) {
        return new State(values, expectedLogDensity);
    }

    private static String describe(State state) {
        StringBuilder text = new StringBuilder();
        state.values().forEach((id, values) -> text.append(id).append('=').append(java.util.Arrays.toString(values)).append(' '));
        return text.toString().trim();
    }

    private static void setValues(Parameter parameter, double[] values) {
        for (int i = 0; i < values.length; i++) {
            parameter.setParameterValue(i, values[i]);
        }
    }

    private static double logDensity(Likelihood likelihood) {
        likelihood.makeDirty();
        return likelihood.getLogLikelihood();
    }

    /** Stores the model state, moves the parameter as a proposal would, and restores it as on rejection. */
    private static void assertRestored(Model model, Parameter parameter, String name) {
        double before = parameter.getParameterValue(0);

        model.storeModelState();
        parameter.setParameterValue(0, before + 1.0);
        model.restoreModelState();

        assertEquals(before, parameter.getParameterValue(0), TOLERANCE, name + ": rejected proposal was not restored");
    }

    private static final Map<String, Likelihood> PARSED_POSTERIORS = new java.util.HashMap<>();

    /** The posterior of the last XML parsed by {@link #parseXmlPriors} for this name. */
    private static Likelihood parsedPosterior(String name) {
        return PARSED_POSTERIORS.get(name);
    }

    /** Exports the model to XML, parses it with BEAST X, and returns its prior terms. */
    private static List<Likelihood> parseXmlPriors(String name, String source) throws Exception {
        String fileName = "coverage-" + name.replaceAll("\\W+", "-");
        Path xmlPath = XmlTestSupport.xmlPath(fileName);
        Path logPath = XmlTestSupport.logPath(fileName);
        XmlTestSupport.prepare(xmlPath, logPath);

        Likelihood posterior = new PhyloSpecRunner(source + """

                mcmc {
                    Sampler sampler = mcmc(chainLength=10)
                    Logger fileLogger = fileLogger(logEvery=1, file="%s", parameters=[x])
                }
                """.formatted(XmlTestSupport.unixPath(logPath)))
                .buildXmlRun(XmlRunnerOptions.builder("coverage", xmlPath).build())
                .mcmc()
                .getLikelihood();

        PARSED_POSTERIORS.put(name, posterior);

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

    /**
     * Finds the prior of the parameter with the given id: a distribution likelihood with it as data,
     * or a likelihood with id {@code <id>_prior}, such as BEAST X's binomialLikelihood.
     */
    private static Likelihood priorOf(List<Likelihood> leaves, String id) {
        for (Likelihood likelihood : leaves) {
            if (likelihood instanceof AbstractDistributionLikelihood prior && dataOf(prior, id) != null) {
                return prior;
            }
        }
        for (Likelihood likelihood : leaves) {
            if ((id + "_prior").equals(likelihood.getId())) {
                return likelihood;
            }
        }
        throw new AssertionError("No prior on parameter " + id + " in the parsed XML.");
    }

    /** Finds the parameter with the given id among the data and variables of the prior terms. */
    private static Parameter dataParameter(List<Likelihood> leaves, String id) {
        for (Likelihood likelihood : leaves) {
            if (likelihood instanceof AbstractDistributionLikelihood prior && dataOf(prior, id) != null) {
                return dataOf(prior, id);
            }
            Model model = likelihood.getModel();
            for (int i = 0; model != null && i < model.getVariableCount(); i++) {
                if (model.getVariable(i) instanceof Parameter parameter && id.equals(parameter.getId())) {
                    return parameter;
                }
            }
        }
        throw new AssertionError("No parameter " + id + " in the parsed XML.");
    }

    private static Parameter dataOf(AbstractDistributionLikelihood prior, String id) {
        for (Attribute<double[]> data : prior.getDataList()) {
            if (data instanceof Parameter parameter) {
                Parameter found = find(parameter, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Finds the parameter with the given id in a parameter or, for a shifted prior such as Offset,
     * in the parts of a sumParameter. BEAST X does not expose those parts, so they are read by reflection.
     */
    @SuppressWarnings("unchecked")
    private static Parameter find(Parameter parameter, String id) {
        if (id.equals(parameter.getId())) {
            return parameter;
        }
        if (parameter instanceof SumParameter sum) {
            try {
                java.lang.reflect.Field parts = SumParameter.class.getDeclaredField("parameterList");
                parts.setAccessible(true);
                for (Parameter part : (List<Parameter>) parts.get(sum)) {
                    Parameter found = find(part, id);
                    if (found != null) {
                        return found;
                    }
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
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
