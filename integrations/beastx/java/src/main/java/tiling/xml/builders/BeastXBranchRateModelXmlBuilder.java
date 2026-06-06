package tiling.xml.builders;

import dr.evomodel.branchratemodel.DiscretizedBranchRates;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.distribution.ParametricDistributionModel;
import dr.inference.model.Parameter;
import tiling.BeastXState;
import tiling.xml.BeastXXmlElement;

public class BeastXBranchRateModelXmlBuilder {

    public BeastXXmlElement buildStrictClockBranchRates(
            BeastXState state,
            TreeModel treeModel,
            Parameter clockRateParameter
    ) {
        String id =
                treeId(treeModel) + "_strictClockBranchRates";

        return BeastXXmlElement.element("strictClockBranchRates")
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

    public BeastXXmlElement buildRelaxedClockBranchRates(
            TreeModel treeModel,
            BeastXState.RelaxedClockSpec spec
    ) {
        String id =
                relaxedClockBranchRateModelId(treeModel, spec);

        return BeastXXmlElement.element("discretizedBranchRates")
                .withId(id)
                .withAttribute("overSampling", "1")
                .withAttribute("normalize", "true")
                .withAttribute("normalizeBranchRateTo", format(spec.normalizeBranchRateTo()))
                .withAttribute("randomizeRates", "false")
                .withAttribute("keepRates", "true")
                .withChild(treeReference(treeModel))
                .withChild(
                        BeastXXmlElement.element("distribution")
                                .withChild(
                                        parametricDistributionDefinition(
                                                id + "_distribution",
                                                spec.distributionModel()
                                        )
                                )
                )
                .withChild(
                        BeastXXmlElement.element("rateCategories")
                                .withChild(
                                        parameterReference(spec.rateCategoriesParameter())
                                )
                );
    }

    public String relaxedClockBranchRateModelId(
            TreeModel treeModel,
            BeastXState.RelaxedClockSpec spec
    ) {
        DiscretizedBranchRates relaxedClock =
                spec.relaxedClock();

        String relaxedClockId =
                relaxedClock.getId();

        if (relaxedClockId != null && !relaxedClockId.isBlank()) {
            return relaxedClockId;
        }

        return treeId(treeModel) + "_relaxedClockBranchRates";
    }

    private BeastXXmlElement parametricDistributionDefinition(
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

    private BeastXXmlElement logNormalDistributionModelDefinition(
            String id,
            LogNormalDistributionModel distribution
    ) {
        return BeastXXmlElement.element("logNormalDistributionModel")
                .withId(id)
                .withChild(
                        BeastXXmlElement.element("mu")
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
                        BeastXXmlElement.element("precision")
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

    private BeastXXmlElement parameterElement(
            BeastXState state,
            String elementName,
            Parameter parameter,
            String fallbackId,
            double fallbackValue,
            Double lower,
            Double upper
    ) {
        BeastXXmlElement child;

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

        return BeastXXmlElement.element(elementName)
                .withChild(child);
    }

    private BeastXXmlElement inlineParameterDefinition(
            String id,
            double value,
            Double lower,
            Double upper
    ) {
        BeastXXmlElement element =
                BeastXXmlElement.element("parameter")
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

    private BeastXXmlElement parameterReference(Parameter parameter) {
        return BeastXXmlElement.ref("parameter", parameterId(parameter));
    }

    private BeastXXmlElement treeReference(TreeModel treeModel) {
        return BeastXXmlElement.ref("treeModel", treeId(treeModel));
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
                message + " Extend BeastXBranchRateModelXmlBuilder before exporting this branch-rate model to XML."
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