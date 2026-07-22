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
import tiling.xml.XmlElement;

/**
 * Builds XML elements for supported scalar parameter priors.
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
            Parameter parameter,
            DistributionLikelihood likelihood
    ) {
        Distribution distribution =
                likelihood.getDistribution();

        if (distribution instanceof NormalDistributionModel normalDistribution) {
            return normalPrior(parameter, likelihood, normalDistribution);
        }

        if (distribution instanceof LogNormalDistributionModel logNormalDistribution) {
            return logNormalPrior(parameter, likelihood, logNormalDistribution);
        }

        if (distribution instanceof GammaDistributionModel gammaDistribution) {
            return gammaPrior(parameter, likelihood, gammaDistribution);
        }

        if (distribution instanceof ExponentialDistributionModel exponentialDistribution) {
            return exponentialPrior(parameter, likelihood, exponentialDistribution);
        }

        if (distribution instanceof UniformDistributionModel uniformDistribution) {
            return uniformPrior(parameter, likelihood, uniformDistribution);
        }

        if (distribution instanceof BetaDistributionModel betaDistribution) {
            return betaPrior(parameter, likelihood, betaDistribution);
        }

        throw new UnsupportedOperationException(
                "Only Normal, LogNormal, Gamma, Exponential, Uniform, and Beta scalar priors are supported."
        );
    }

    private XmlElement normalPrior(
            Parameter parameter,
            DistributionLikelihood likelihood,
            NormalDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return XmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(
                                        XmlElement.element("normalDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withChild(
                                                        XmlElement.element("mean")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_mean",
                                                                                distribution.mean(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                                .withChild(
                                                        XmlElement.element("stdev")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_stdev",
                                                                                distribution.getStdev(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                )
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
    }

    private XmlElement logNormalPrior(
            Parameter parameter,
            DistributionLikelihood likelihood,
            LogNormalDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return XmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(
                                        XmlElement.element("logNormalDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withChild(
                                                        XmlElement.element("mu")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_mu",
                                                                                distribution.getMu(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                                .withChild(
                                                        XmlElement.element("precision")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_precision",
                                                                                distribution.getPrecision(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                )
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
    }

    private XmlElement gammaPrior(
            Parameter parameter,
            DistributionLikelihood likelihood,
            GammaDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        double shape =
                distribution.getShape();

        double rate =
                1.0 / distribution.getScale();

        return XmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(
                                        XmlElement.element("gammaDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withAttribute("offset", "0.0")
                                                .withChild(
                                                        XmlElement.element("shape")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_shape",
                                                                                shape,
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                                .withChild(
                                                        XmlElement.element("rate")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_rate",
                                                                                rate,
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                )
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
    }

    private XmlElement exponentialPrior(
            Parameter parameter,
            DistributionLikelihood likelihood,
            ExponentialDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return XmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(
                                        XmlElement.element("exponentialDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withAttribute("offset", "0.0")
                                                .withChild(
                                                        XmlElement.element("mean")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_mean",
                                                                                distribution.mean(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                )
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
    }

    private XmlElement uniformPrior(
            Parameter parameter,
            DistributionLikelihood likelihood,
            UniformDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return XmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(
                                        XmlElement.element("uniformDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withChild(
                                                        XmlElement.element("lower")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_lower",
                                                                                distribution.getLower(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                                .withChild(
                                                        XmlElement.element("upper")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_upper",
                                                                                distribution.getUpper(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                )
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
    }

    private XmlElement betaPrior(
            Parameter parameter,
            DistributionLikelihood likelihood,
            BetaDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return XmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(
                                        XmlElement.element("betaDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withChild(
                                                        betaShapeParameter(
                                                                "alpha",
                                                                priorId + "_alpha",
                                                                betaDistributionVariable(distribution, 0)
                                                        )
                                                )
                                                .withChild(
                                                        betaShapeParameter(
                                                                "beta",
                                                                priorId + "_beta",
                                                                betaDistributionVariable(distribution, 1)
                                                        )
                                                )
                                )
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
    }

    private XmlElement betaShapeParameter(
            String elementName,
            String parameterId,
            Parameter parameter
    ) {
        return XmlElement.element(elementName)
                .withChild(
                        inlineParameterDefinition(
                                parameterId,
                                parameter.getParameterValue(0),
                                null,
                                null
                        )
                );
    }

    private XmlElement parameterReference(Parameter parameter) {
        return XmlElement.ref("parameter", parameterId(parameter));
    }

    private XmlElement inlineParameterDefinition(
            String id,
            double value,
            Double lower,
            Double upper
    ) {
        XmlElement element =
                XmlElement.element("parameter")
                        .withId(id)
                        .withAttribute("value", format(value));

        if (lower != null) {
            element =
                    element.withAttribute("lower", format(lower));
        }

        if (upper != null) {
            element =
                    element.withAttribute("upper", format(upper));
        }

        return element;
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
