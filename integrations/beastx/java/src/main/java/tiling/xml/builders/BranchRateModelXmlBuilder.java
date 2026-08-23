package tiling.xml.builders;

import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.distribution.ParametricDistributionModel;
import dr.inference.model.Parameter;
import tiling.BeastXState;
import tiling.xml.XmlElement;

/**
 * Builds XML elements for strict and relaxed branch rate models.
 */
public class BranchRateModelXmlBuilder {

    public XmlElement buildStrictClockBranchRates(
            BeastXState state,
            TreeModel treeModel,
            Parameter clockRateParameter
    ) {
        String id =
                treeId(treeModel) + "_strictClockBranchRates";

        return XmlElement.element("strictClockBranchRates")
                .withId(id)
                .withChild(
                        parameterElement(
                                state,
                                "rate",
                                clockRateParameter,
                                id + "_rate",
                                clockRateParameter.getParameterValue(0),
                                0.0,
                                null
                        )
                );
    }

    public XmlElement buildRelaxedClockBranchRates(
            BeastXState state,
            TreeModel treeModel,
            BeastXState.RelaxedClockSpec spec
    ) {
        String id =
                relaxedClockBranchRateModelId(treeModel, spec);

        return XmlElement.element("multiplicativeBranchRates")
                .withId(id)
                .withChild(
                        buildStrictClockBranchRates(
                                state,
                                treeModel,
                                spec.clockRateParameter()
                        )
                )
                .withChild(
                        buildRelativeRelaxedClockBranchRates(
                                treeModel,
                                spec,
                                id + "_relativeRates"
                        )
                );
    }

    public String relaxedClockBranchRateModelId(
            TreeModel treeModel,
            BeastXState.RelaxedClockSpec spec
    ) {
        return treeId(treeModel) + "_relaxedClockBranchRates";
    }

    private XmlElement buildRelativeRelaxedClockBranchRates(
            TreeModel treeModel,
            BeastXState.RelaxedClockSpec spec,
            String id
    ) {
        return XmlElement.element("discretizedBranchRates")
                .withId(id)
                .withAttribute("overSampling", "1")
                .withAttribute("normalize", "true")
                .withAttribute("normalizeBranchRateTo", "1.0")
                .withAttribute("randomizeRates", "false")
                .withAttribute("keepRates", "true")
                .withAttribute("cachedRates", "true")
                .withChild(treeReference(treeModel))
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(
                                        parametricDistributionDefinition(
                                                id + "_distribution",
                                                spec.distributionModel()
                                        )
                                )
                )
                .withChild(
                        XmlElement.element("rateCategories")
                                .withChild(
                                        parameterReference(spec.rateCategoriesParameter())
                                )
                );
    }

    private XmlElement parametricDistributionDefinition(
            String id,
            ParametricDistributionModel distribution
    ) {
        if (distribution instanceof LogNormalDistributionModel logNormalDistribution) {
            return logNormalDistributionModelDefinition(id, logNormalDistribution);
        }

        throw unsupported(
                "Only LogNormal relaxed-clock base distributions are supported for XML export at this stage."
        );
    }

    private XmlElement logNormalDistributionModelDefinition(
            String id,
            LogNormalDistributionModel distribution
    ) {
        return XmlElement.element("logNormalDistributionModel")
                .withId(id)
                .withChild(
                        XmlElement.element("mu")
                                .withChild(
                                        inlineParameterDefinition(
                                                id + "_mu",
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
                                                id + "_precision",
                                                distribution.getPrecision(),
                                                null,
                                                null
                                        )
                                )
                );
    }

    private XmlElement parameterElement(
            BeastXState state,
            String elementName,
            Parameter parameter,
            String fallbackId,
            double fallbackValue,
            Double lower,
            Double upper
    ) {
        XmlElement child;

        if (parameter != null && state.stateNodes.containsKey(parameter)) {
            child =
                    parameterReference(parameter);
        } else {
            child =
                    inlineParameterDefinition(
                            fallbackId,
                            fallbackValue,
                            lower,
                            upper
                    );
        }

        return XmlElement.element(elementName)
                .withChild(child);
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

    private XmlElement parameterReference(Parameter parameter) {
        return XmlElement.ref("parameter", parameterId(parameter));
    }

    private XmlElement treeReference(TreeModel treeModel) {
        return XmlElement.ref("treeModel", treeId(treeModel));
    }

    private static String parameterId(Parameter parameter) {
        String id =
                parameter.getId();

        if (id == null || id.isBlank()) {
            id =
                    parameter.getParameterName();
        }

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X parameter.");
        }

        return id;
    }

    private static String treeId(TreeModel treeModel) {
        String id =
                treeModel.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X tree model.");
        }

        return id;
    }

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend BranchRateModelXmlBuilder before exporting this branch-rate model to XML."
        );
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
