package tiling.xml.builders;

import dr.inference.distribution.BetaDistributionModel;
import dr.math.distributions.Distribution;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.ExponentialDistributionModel;
import dr.inference.distribution.GammaDistributionModel;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.distribution.NormalDistributionModel;
import dr.inference.distribution.PoissonDistributionModel;
import dr.inference.distribution.UniformDistributionModel;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import tiles.distributions.BernoulliTile;
import tiles.distributions.BinomialTile;
import tiles.distributions.CauchyTile;
import tiles.distributions.DiscreteUniformTile;
import tiles.distributions.GeometricTile;
import tiles.distributions.OffsetTile;
import tiling.BeastXState;
import tiling.model.BeastXTruncatedLikelihood;
import tiling.params.BeastXExponentialMean;
import tiling.params.BeastXLogNormalMu;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds XML elements for supported scalar parameter priors.
 *
 * <p>Distribution parameters that are MCMC state nodes are written as references,
 * so the XML prior follows them during the run. Other parameters are written inline.</p>
 */
public class ScalarPriorXmlBuilder {

    public boolean supports(Distribution distribution) {
        if (distribution instanceof OffsetTile.OffsetDistribution offsetDistribution) {
            return supports(offsetDistribution.getBase());
        }

        return distribution instanceof NormalDistributionModel
                || distribution instanceof LogNormalDistributionModel
                || distribution instanceof GammaDistributionModel
                || distribution instanceof ExponentialDistributionModel
                || distribution instanceof UniformDistributionModel
                || distribution instanceof BetaDistributionModel
                || distribution instanceof CauchyTile.Cauchy
                || distribution instanceof PoissonDistributionModel
                || distribution instanceof BinomialTile.BinomialCountDistribution
                || distribution instanceof BernoulliTile.BernoulliDistribution
                || distribution instanceof GeometricTile.GeometricCountDistribution
                || distribution instanceof DiscreteUniformTile.DiscreteUniform;
    }

    public XmlElement buildPrior(
            BeastXState state,
            Parameter parameter,
            DistributionLikelihood likelihood
    ) {
        XmlElement data =
                parameterReference(parameter);

        int dimension =
                parameter.getDimension();

        if (likelihood instanceof BeastXTruncatedLikelihood truncated) {
            return truncatedPrior(state, data, dimension, truncated);
        }

        return prior(state, data, dimension, likelihood.getId(), likelihood.getDistribution());
    }

    /**
     * Writes the prior of {@code data}, an element that refers to the parameter or to a value
     * derived from it, with {@code dimension} values.
     */
    private XmlElement prior(
            BeastXState state,
            XmlElement data,
            int dimension,
            String priorId,
            Distribution distribution
    ) {
        // Offset(base, offset) is the base distribution of x - offset, so any base can be shifted.
        // Nested offsets are all subtracted in one element.
        if (distribution instanceof OffsetTile.OffsetDistribution) {
            List<Parameter> offsets = new ArrayList<>();
            Distribution base = distribution;

            while (base instanceof OffsetTile.OffsetDistribution offsetDistribution) {
                offsets.add(offsetDistribution.getOffset());
                base = offsetDistribution.getBase();
            }

            return prior(state, shiftedData(state, data, dimension, priorId, offsets), dimension, priorId, base);
        }

        if (distribution instanceof NormalDistributionModel normalDistribution) {
            return normalPrior(state, data, priorId, normalDistribution);
        }

        if (distribution instanceof LogNormalDistributionModel logNormalDistribution) {
            return prior(
                    data,
                    priorId,
                    logNormalDistributionModel(state, priorId + "_distribution", logNormalDistribution)
            );
        }

        if (distribution instanceof GammaDistributionModel gammaDistribution) {
            return gammaPrior(state, data, priorId, gammaDistribution);
        }

        if (distribution instanceof ExponentialDistributionModel exponentialDistribution) {
            return exponentialPrior(state, data, priorId, exponentialDistribution);
        }

        if (distribution instanceof UniformDistributionModel uniformDistribution) {
            return uniformPrior(state, data, priorId, uniformDistribution);
        }

        if (distribution instanceof BetaDistributionModel betaDistribution) {
            return betaPrior(state, data, priorId, betaDistribution);
        }

        if (distribution instanceof CauchyTile.Cauchy cauchy) {
            return XmlElement.element("cauchyPrior")
                    .withId(priorId)
                    .withAttribute("median", format(cauchy.getLocation()))
                    .withAttribute("scale", format(cauchy.getScale()))
                    .withChild(data);
        }

        if (distribution instanceof DiscreteUniformTile.DiscreteUniform discreteUniform) {
            return XmlElement.element("discreteUniformPrior")
                    .withId(priorId)
                    .withAttribute("lower", format(discreteUniform.getLower()))
                    .withAttribute("upper", format(discreteUniform.getUpper()))
                    .withChild(data);
        }

        if (distribution instanceof PoissonDistributionModel poisson) {
            return prior(
                    data,
                    priorId,
                    XmlElement.element("poissonDistributionModel")
                            .withId(priorId + "_distribution")
                            .withChild(
                                    distributionParameter(
                                            state,
                                            "mean",
                                            priorId + "_mean",
                                            poisson.getLocationVariable(),
                                            poisson.mean()
                                    )
                            )
            );
        }

        if (distribution instanceof BinomialTile.BinomialCountDistribution binomial) {
            return binomialPrior(state, data, dimension, priorId, binomial.getNumTrials(), binomial.getP());
        }

        // Bernoulli(p) is Binomial(1, p) on 0 and 1.
        if (distribution instanceof BernoulliTile.BernoulliDistribution bernoulli) {
            return binomialPrior(state, data, dimension, priorId, 1, bernoulli.getP());
        }

        if (distribution instanceof GeometricTile.GeometricCountDistribution geometric) {
            return geometricPrior(state, data, priorId, geometric.getP());
        }

        throw new UnsupportedOperationException(
                "This scalar prior is not supported in BEAST X XML."
        );
    }

    /**
     * Writes Binomial(numTrials, p) as BEAST X's binomialLikelihood, which includes the binomial
     * coefficient. Every count gets its own number of trials, so IID counts are also covered.
     */
    private XmlElement binomialPrior(
            BeastXState state,
            XmlElement data,
            int dimension,
            String priorId,
            int numTrials,
            Parameter p
    ) {
        StringBuilder trials = new StringBuilder();
        for (int i = 0; i < dimension; i++) {
            trials.append(i == 0 ? "" : " ").append(numTrials);
        }

        return XmlElement.element("binomialLikelihood")
                .withId(priorId)
                .withChild(
                        XmlElement.element("trials")
                                .withChild(
                                        XmlElement.element("parameter")
                                                .withId(priorId + "_trials")
                                                .withAttribute("value", trials.toString())
                                )
                )
                .withChild(XmlElement.element("counts").withChild(data))
                .withChild(distributionParameter(state, "proportion", priorId + "_proportion", p, p.getParameterValue(0)));
    }

    /**
     * Writes Geometric(p), the number of failures before the first success, as a negative binomial
     * with mean (1 - p) / p and alpha 1. A sampled p is written as p ^ -1 + (-1), so the mean
     * follows p during the run.
     */
    private XmlElement geometricPrior(
            BeastXState state,
            XmlElement data,
            String priorId,
            Parameter p
    ) {
        String meanId = priorId + "_mean";

        XmlElement mean =
                state.stateNodes.containsKey(p)
                        ? XmlElement.element("sumParameter")
                                .withId(meanId)
                                .withChild(
                                        XmlElement.element("transformedParameter")
                                                .withId(meanId + "_inverseP")
                                                .withChild(parameterReference(p))
                                                .withChild(powerTransform(-1.0))
                                )
                                .withChild(inlineParameterDefinition(meanId + "_minusOne", -1.0))
                        : inlineParameterDefinition(meanId, (1.0 - p.getParameterValue(0)) / p.getParameterValue(0));

        return prior(
                data,
                priorId,
                XmlElement.element("negativeBinomialDistributionModel")
                        .withId(priorId + "_distribution")
                        .withChild(XmlElement.element("mean").withChild(mean))
                        .withChild(XmlElement.element("alpha").withChild(inlineParameterDefinition(priorId + "_alpha", 1.0)))
        );
    }

    private static XmlElement powerTransform(double power) {
        return XmlElement.element("powerTransform")
                .withAttribute("power", Double.toString(power))
                .withChild(XmlElement.element("transform").withAttribute("type", "power"));
    }

    /**
     * Writes the base prior inside a truncatedDistributionLikelihood, which divides its density
     * by the probability of the base distribution between the bounds. A plain base prior would
     * leave that out, and the bounds would only remain on the parameter.
     */
    private XmlElement truncatedPrior(
            BeastXState state,
            XmlElement data,
            int dimension,
            BeastXTruncatedLikelihood truncated
    ) {
        String priorId =
                truncated.getId();

        // BEAST X checks the bounds against the data of the base prior. With an Offset base,
        // that data is x - offset, so the bounds are shifted the same way.
        double shift = 0.0;

        for (Distribution base = truncated.getDistribution();
             base instanceof OffsetTile.OffsetDistribution offsetDistribution;
             base = offsetDistribution.getBase()) {
            if (state.stateNodes.containsKey(offsetDistribution.getOffset())) {
                throw new UnsupportedOperationException(
                        "Truncated over an Offset with a sampled offset is not supported in BEAST X XML: "
                                + "the truncation bounds would have to move with the offset."
                );
            }

            shift += offsetDistribution.getOffset().getParameterValue(0);
        }

        XmlElement element =
                XmlElement.element("truncatedDistributionLikelihood")
                        .withId(priorId)
                        .withChild(prior(state, data, dimension, priorId + "_base", truncated.getDistribution()));

        // A missing low or high element means no bound on that side.
        if (Double.isFinite(truncated.getLower())) {
            element = element.withChild(
                    XmlElement.element("low")
                            .withChild(inlineParameterDefinition(priorId + "_low", truncated.getLower() - shift))
            );
        }

        if (Double.isFinite(truncated.getUpper())) {
            element = element.withChild(
                    XmlElement.element("high")
                            .withChild(inlineParameterDefinition(priorId + "_high", truncated.getUpper() - shift))
            );
        }

        return element;
    }

    /**
     * Builds a logNormalDistributionModel element in the same parameterization as the
     * in-memory model. Also used for relaxed-clock base distributions.
     *
     * <p>BEAST X parses {@code meanInRealSpace} with mean and stdev children through the same
     * constructor the tiles use: "true" reads them as the real-space mean and standard deviation,
     * and "false" reads them as mu and sigma on the log scale.</p>
     */
    public XmlElement logNormalDistributionModel(
            BeastXState state,
            String id,
            LogNormalDistributionModel distribution
    ) {
        boolean meanInRealSpace =
                distribution.getParameterization() == LogNormalDistributionModel.Parameterization.MEAN_STDEV;

        Parameter location =
                meanInRealSpace ? distribution.getMeanParameter() : distribution.getMuParameter();

        Parameter scale =
                meanInRealSpace ? distribution.getStdevParameter() : distribution.getSigmaParameter();

        XmlElement locationElement =
                location instanceof BeastXLogNormalMu mu
                        ? XmlElement.element("mean").withChild(logNormalMu(state, id + "_mean", mu))
                        : distributionParameter(
                                state,
                                "mean",
                                id + "_mean",
                                location,
                                meanInRealSpace ? distribution.getMean() : distribution.getMu()
                        );

        return XmlElement.element("logNormalDistributionModel")
                .withId(id)
                .withAttribute("meanInRealSpace", Boolean.toString(meanInRealSpace))
                .withChild(locationElement)
                .withChild(
                        distributionParameter(
                                state,
                                "stdev",
                                id + "_stdev",
                                scale,
                                meanInRealSpace ? distribution.getStdev() : distribution.getSigma()
                        )
                );
    }

    private XmlElement normalPrior(
            BeastXState state,
            XmlElement data,
            String priorId,
            NormalDistributionModel distribution
    ) {
        return prior(
                data,
                priorId,
                XmlElement.element("normalDistributionModel")
                        .withId(priorId + "_distribution")
                        .withChild(
                                distributionParameter(
                                        state,
                                        "mean",
                                        priorId + "_mean",
                                        distribution.getVariable(0),
                                        distribution.mean()
                                )
                        )
                        .withChild(
                                distributionParameter(
                                        state,
                                        "stdev",
                                        priorId + "_stdev",
                                        distribution.getVariable(1),
                                        distribution.getStdev()
                                )
                        )
        );
    }

    private XmlElement gammaPrior(
            BeastXState state,
            XmlElement data,
            String priorId,
            GammaDistributionModel distribution
    ) {
        return prior(
                data,
                priorId,
                XmlElement.element("gammaDistributionModel")
                        .withId(priorId + "_distribution")
                        .withChild(
                                distributionParameter(
                                        state,
                                        "shape",
                                        priorId + "_shape",
                                        distribution.getVariable(0),
                                        distribution.getShape()
                                )
                        )
                        .withChild(
                                distributionParameter(
                                        state,
                                        "rate",
                                        priorId + "_rate",
                                        distribution.getVariable(1),
                                        1.0 / distribution.getScale()
                                )
                        )
        );
    }

    private XmlElement exponentialPrior(
            BeastXState state,
            XmlElement data,
            String priorId,
            ExponentialDistributionModel distribution
    ) {
        return prior(
                data,
                priorId,
                XmlElement.element("exponentialDistributionModel")
                        .withId(priorId + "_distribution")
                        .withChild(
                                XmlElement.element("mean")
                                        .withChild(exponentialMean(state, priorId + "_mean", distribution))
                        )
        );
    }

    /**
     * Writes the mean {@code 1 / rate} as {@code rate ^ -1} when the rate is sampled, so that the
     * mean follows the rate during the run. A fixed mean is written inline.
     */
    private XmlElement exponentialMean(
            BeastXState state,
            String id,
            ExponentialDistributionModel distribution
    ) {
        if (distribution.getLocationVariable() instanceof BeastXExponentialMean mean
                && state.stateNodes.containsKey(mean.getRate())) {
            return XmlElement.element("transformedParameter")
                    .withId(id)
                    .withChild(parameterReference(mean.getRate()))
                    .withChild(powerTransform(-1.0));
        }

        return inlineParameterDefinition(id, distribution.mean());
    }

    private XmlElement uniformPrior(
            BeastXState state,
            XmlElement data,
            String priorId,
            UniformDistributionModel distribution
    ) {
        return prior(
                data,
                priorId,
                XmlElement.element("uniformDistributionModel")
                        .withId(priorId + "_distribution")
                        .withChild(
                                distributionParameter(
                                        state,
                                        "lower",
                                        priorId + "_lower",
                                        distribution.getVariable(0),
                                        distribution.getLower()
                                )
                        )
                        .withChild(
                                distributionParameter(
                                        state,
                                        "upper",
                                        priorId + "_upper",
                                        distribution.getVariable(1),
                                        distribution.getUpper()
                                )
                        )
        );
    }

    private XmlElement betaPrior(
            BeastXState state,
            XmlElement data,
            String priorId,
            BetaDistributionModel distribution
    ) {
        Parameter alpha =
                betaDistributionVariable(distribution, 0);

        Parameter beta =
                betaDistributionVariable(distribution, 1);

        return prior(
                data,
                priorId,
                XmlElement.element("betaDistributionModel")
                        .withId(priorId + "_distribution")
                        .withChild(
                                distributionParameter(
                                        state,
                                        "alpha",
                                        priorId + "_alpha",
                                        alpha,
                                        alpha.getParameterValue(0)
                                )
                        )
                        .withChild(
                                distributionParameter(
                                        state,
                                        "beta",
                                        priorId + "_beta",
                                        beta,
                                        beta.getParameterValue(0)
                                )
                        )
        );
    }

    private XmlElement prior(
            XmlElement data,
            String priorId,
            XmlElement distributionModel
    ) {
        return XmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(distributionModel)
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(data)
                );
    }

    /**
     * Writes {@code data - offset_1 - offset_2 ...}, with each offset repeated for every value of
     * data. A sampled offset is written as a reference times -1, so the shifted value follows it
     * during the run.
     */
    private XmlElement shiftedData(
            BeastXState state,
            XmlElement data,
            int dimension,
            String priorId,
            List<Parameter> offsets
    ) {
        String id =
                priorId + "_shifted";

        XmlElement shifted =
                XmlElement.element("sumParameter")
                        .withId(id)
                        .withChild(data);

        for (int i = 0; i < offsets.size(); i++) {
            Parameter offset =
                    offsets.get(i);

            String offsetId =
                    id + "_negativeOffset" + (i + 1);

            if (!state.stateNodes.containsKey(offset)) {
                shifted = shifted.withChild(repeatedParameterDefinition(offsetId, -offset.getParameterValue(0), dimension));
                continue;
            }

            XmlElement sampledOffset =
                    dimension == 1
                            ? parameterReference(offset)
                            : XmlElement.element("duplicatedParameter")
                                    .withId(offsetId + "_copies")
                                    .withChild(parameterReference(offset))
                                    .withChild(
                                            XmlElement.element("copies")
                                                    .withChild(inlineParameterDefinition(offsetId + "_copyCount", dimension))
                                    );

            shifted = shifted.withChild(
                    XmlElement.element("productParameter")
                            .withId(offsetId)
                            .withChild(repeatedParameterDefinition(offsetId + "_minusOne", -1.0, dimension))
                            .withChild(sampledOffset)
            );
        }

        return shifted;
    }


    /**
     * Writes a distribution parameter as a reference when it is an MCMC state node,
     * and as an inline constant otherwise.
     */
    private XmlElement distributionParameter(
            BeastXState state,
            String elementName,
            String inlineId,
            Variable<?> variable,
            double value
    ) {
        XmlElement child;

        if (variable instanceof Parameter parameter && state.stateNodes.containsKey(parameter)) {
            child =
                    parameterReference(parameter);
        } else {
            child =
                    inlineParameterDefinition(inlineId, value);
        }

        return XmlElement.element(elementName)
                .withChild(child);
    }

    /**
     * Writes {@code mu = log(mean) - logSd^2 / 2} with BEAST X parameter elements, so that mu
     * follows a sampled mean or logSd during the run. Fixed values are written inline.
     */
    private XmlElement logNormalMu(BeastXState state, String id, BeastXLogNormalMu mu) {
        boolean sampledMean = state.stateNodes.containsKey(mu.getMean());
        boolean sampledLogSd = state.stateNodes.containsKey(mu.getLogSd());

        if (!sampledMean && !sampledLogSd) {
            return inlineParameterDefinition(id, mu.getParameterValue(0));
        }

        XmlElement logMean =
                sampledMean
                        ? XmlElement.element("transformedParameter")
                                .withId(id + "_logMean")
                                .withChild(parameterReference(mu.getMean()))
                                .withChild(XmlElement.element("transform").withAttribute("type", "log"))
                        : inlineParameterDefinition(id + "_logMean", Math.log(mu.getMean().getParameterValue(0)));

        double logSdValue = mu.getLogSd().getParameterValue(0);

        XmlElement negativeHalfLogVariance =
                sampledLogSd
                        ? XmlElement.element("productParameter")
                                .withId(id + "_negativeHalfLogVariance")
                                .withChild(inlineParameterDefinition(id + "_negativeHalf", -0.5))
                                .withChild(parameterReference(mu.getLogSd()))
                                .withChild(parameterReference(mu.getLogSd()))
                        : inlineParameterDefinition(id + "_negativeHalfLogVariance", -0.5 * logSdValue * logSdValue);

        return XmlElement.element("sumParameter")
                .withId(id)
                .withChild(logMean)
                .withChild(negativeHalfLogVariance);
    }

    private XmlElement parameterReference(Parameter parameter) {
        return XmlElement.ref("parameter", parameterId(parameter));
    }

    private XmlElement inlineParameterDefinition(
            String id,
            double value
    ) {
        return XmlElement.element("parameter")
                .withId(id)
                .withAttribute("value", format(value));
    }

    private XmlElement repeatedParameterDefinition(
            String id,
            double value,
            int dimension
    ) {
        StringBuilder values = new StringBuilder();

        for (int i = 0; i < dimension; i++) {
            values.append(i == 0 ? "" : " ").append(format(value));
        }

        return XmlElement.element("parameter")
                .withId(id)
                .withAttribute("value", values.toString());
    }

    private String parameterId(Parameter parameter) {
        String id =
                parameter.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X parameter to XML.");
        }

        return id;
    }

    private static Parameter betaDistributionVariable(
            BetaDistributionModel distribution,
            int variableIndex
    ) {
        if (variableIndex >= distribution.getVariableCount()) {
            throw new UnsupportedOperationException("Beta XML export requires alpha and beta parameters.");
        }

        Object variable =
                distribution.getVariable(variableIndex);

        if (variable instanceof Parameter parameter) {
            return parameter;
        }

        throw new UnsupportedOperationException("Beta XML export requires alpha and beta Parameter variables.");
    }

    private static String format(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Cannot serialize NaN as a BEAST X XML number.");
        }

        if (value == Double.POSITIVE_INFINITY) {
            return "Infinity";
        }

        if (value == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        }

        return Double.toString(value);
    }
}
