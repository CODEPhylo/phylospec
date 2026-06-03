package tiling.xml.builders;

import dr.evomodel.siteratemodel.GammaSiteRateModel;
import dr.evomodel.siteratemodel.SiteRateModel;
import dr.inference.model.Parameter;
import tiling.xml.BeastXXmlElement;

import java.lang.reflect.Field;

public class BeastXSiteModelXmlBuilder {

    public BeastXXmlElement buildSiteRateModel(
            SiteRateModel siteRateModel,
            String siteRateModelId,
            String substitutionModelId
    ) {
        if (siteRateModel instanceof GammaSiteRateModel gammaSiteRateModel) {
            return buildGammaSiteRateModel(
                    gammaSiteRateModel,
                    siteRateModelId,
                    substitutionModelId
            );
        }

        throw unsupported(
                "Only GammaSiteRateModel is supported for PhyloCTMC XML export at this stage."
        );
    }

    private BeastXXmlElement buildGammaSiteRateModel(
            GammaSiteRateModel siteRateModel,
            String siteRateModelId,
            String substitutionModelId
    ) {
        BeastXXmlElement element =
                BeastXXmlElement.element("gammaSiteRateModel")
                        .withId(siteRateModelId)
                        .withAttribute("categories", siteRateModel.getCategoryCount())
                        .withChild(
                                BeastXXmlElement.element("substitutionModel")
                                        .withChild(
                                                BeastXXmlElement.ref("hkyModel", substitutionModelId)
                                        )
                        );

        Parameter relativeRateParameter =
                getParameterField(
                        siteRateModel,
                        "nuParameter",
                        false
                );

        if (relativeRateParameter != null) {
            element =
                    element.withChild(
                            BeastXXmlElement.element("relativeRate")
                                    .withChild(
                                            parameterOrInlineDefinition(
                                                    siteRateModelId + "_relativeRate",
                                                    relativeRateParameter
                                            )
                                    )
                    );
        }

        Parameter shapeParameter =
                getParameterField(
                        siteRateModel,
                        "shapeParameter",
                        false
                );

        if (shapeParameter != null) {
            element =
                    element.withChild(
                            BeastXXmlElement.element("gammaShape")
                                    .withChild(
                                            parameterOrInlineDefinition(
                                                    siteRateModelId + "_shape",
                                                    shapeParameter
                                            )
                                    )
                    );
        }

        Parameter invariantParameter =
                getParameterField(
                        siteRateModel,
                        "invarParameter",
                        false
                );

        if (invariantParameter != null) {
            element =
                    element.withChild(
                            BeastXXmlElement.element("proportionInvariant")
                                    .withChild(
                                            parameterOrInlineDefinition(
                                                    siteRateModelId + "_proportionInvariant",
                                                    invariantParameter
                                            )
                                    )
                    );
        }

        return element;
    }

    private Parameter getParameterField(
            GammaSiteRateModel siteRateModel,
            String fieldName,
            boolean required
    ) {
        try {
            Field field =
                    GammaSiteRateModel.class.getDeclaredField(fieldName);

            field.setAccessible(true);

            Object value =
                    field.get(siteRateModel);

            if (value == null) {
                if (required) {
                    throw unsupported(
                            "GammaSiteRateModel field '" + fieldName + "' is required for XML export."
                    );
                }

                return null;
            }

            if (value instanceof Parameter parameter) {
                return parameter;
            }

            throw unsupported(
                    "GammaSiteRateModel field '" + fieldName + "' is not a Parameter."
            );
        } catch (NoSuchFieldException exception) {
            throw unsupported(
                    "Cannot find GammaSiteRateModel field '" + fieldName + "' for XML export."
            );
        } catch (IllegalAccessException exception) {
            throw unsupported(
                    "Cannot access GammaSiteRateModel field '" + fieldName + "' for XML export."
            );
        }
    }

    private BeastXXmlElement parameterOrInlineDefinition(
            String fallbackId,
            Parameter parameter
    ) {
        String id =
                parameter.getId();

        if (id != null && !id.isBlank()) {
            return BeastXXmlElement.ref("parameter", id);
        }

        return BeastXXmlElement.element("parameter")
                .withId(fallbackId)
                .withAttribute("value", format(parameter.getParameterValue(0)));
    }

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend BeastXSiteModelXmlBuilder before exporting this site model to XML."
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
