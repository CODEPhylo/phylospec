package tiling.xml.builders;

import dr.inference.distribution.BetaDistributionModel;
import dr.math.distributions.Distribution;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.ExponentialDistributionModel;
import dr.inference.distribution.GammaDistributionModel;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.distribution.NormalDistributionModel;
import dr.inference.distribution.UniformDistributionModel;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import tiling.BeastXState;
import tiling.params.BeastXLogNormalMu;
import tiling.xml.XmlElement;

/**
 * Builds XML elements for supported scalar parameter priors.
 *
 * <p>Distribution parameters that are MCMC state nodes are written as references,
 * so the XML prior follows them during the run. Other parameters are written inline.</p>
 */
public class ScalarPriorXmlBuilder {

    public boolean supports(Distribution distribution) {
        return distribution instanceof NormalDistributionModel
                || distribution instanceof LogNormalDistributionModel
                || distribution instanceof GammaDistributionModel
                || distribution instanceof ExponentialDistributionModel
                || distribution instanceof UniformDistributionModel
                || distribution instanceof BetaDistributionModel;
    }

    public XmlElement buildPrior(
            BeastXState state,
            Parameter parameter,
            DistributionLikelihood likelihood
    ) {
        Distribution distribution =
                likelihood.getDistribution();

        if (distribution instanceof NormalDistributionModel normalDistribution) {
            return normalPrior(state, parameter, likelihood, normalDistribution);
        }

        if (distribution instanceof LogNormalDistributionModel logNormalDistribution) {
            return prior(
                    parameter,
                    likelihood,
                    logNormalDistributionModel(state, likelihood.getId() + "_distribution", logNormalDistribution)
            );
        }

        if (distribution instanceof GammaDistributionModel gammaDistribution) {
            return gammaPrior(state, parameter, likelihood, gammaDistribution);
        }

        if (distribution instanceof ExponentialDistributionModel exponentialDistribution) {
            return exponentialPrior(parameter, likelihood, exponentialDistribution);
        }

        if (distribution instanceof UniformDistributionModel uniformDistribution) {
            return uniformPrior(state, parameter, likelihood, uniformDistribution);
        }

        if (distribution instanceof BetaDistributionModel betaDistribution) {
            return betaPrior(state, parameter, likelihood, betaDistribution);
        }

        throw new UnsupportedOperationException(
                "Only Normal, LogNormal, Gamma, Exponential, Uniform, and Beta scalar priors are supported."
        );
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
            Parameter parameter,
            DistributionLikelihood likelihood,
            NormalDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return prior(
                parameter,
                likelihood,
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
            Parameter parameter,
            DistributionLikelihood likelihood,
            GammaDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return prior(
                parameter,
                likelihood,
                XmlElement.element("gammaDistributionModel")
                        .withId(priorId + "_distribution")
                        .withAttribute("offset", "0.0")
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
            Parameter parameter,
            DistributionLikelihood likelihood,
            ExponentialDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        // The Exponential tile only accepts fixed rates, so the mean is always written inline.
        return prior(
                parameter,
                likelihood,
                XmlElement.element("exponentialDistributionModel")
                        .withId(priorId + "_distribution")
                        .withAttribute("offset", "0.0")
                        .withChild(
                                XmlElement.element("mean")
                                        .withChild(
                                                inlineParameterDefinition(
                                                        priorId + "_mean",
                                                        distribution.mean()
                                                )
                                        )
                        )
        );
    }

    private XmlElement uniformPrior(
            BeastXState state,
            Parameter parameter,
            DistributionLikelihood likelihood,
            UniformDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return prior(
                parameter,
                likelihood,
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
            Parameter parameter,
            DistributionLikelihood likelihood,
            BetaDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        Parameter alpha =
                betaDistributionVariable(distribution, 0);

        Parameter beta =
                betaDistributionVariable(distribution, 1);

        return prior(
                parameter,
                likelihood,
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
            Parameter parameter,
            DistributionLikelihood likelihood,
            XmlElement distributionModel
    ) {
        return XmlElement.element("distributionLikelihood")
                .withId(likelihood.getId())
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(distributionModel)
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
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
