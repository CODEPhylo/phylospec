package tiling.xml.builders;

import dr.evolution.datatype.DataType;
import dr.evomodel.substmodel.BaseSubstitutionModel;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.evomodel.substmodel.nucleotide.GTR;
import dr.evomodel.substmodel.nucleotide.HKY;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import tiling.xml.BeastXXmlElement;

import java.util.ArrayList;
import java.util.List;

public class BeastXSubstitutionModelXmlBuilder {

    private static final List<String> GTR_RATE_NAMES =
            List.of("rateAC", "rateAG", "rateAT", "rateCG", "rateCT", "rateGT");

    public List<BeastXXmlElement> buildSubstitutionModel(
            SubstitutionModel substitutionModel,
            String substitutionModelId
    ) {
        if (substitutionModel instanceof HKY hky) {
            return buildHKYModel(hky, substitutionModelId);
        }

        if (substitutionModel instanceof GTR gtr) {
            return buildGTRModel(gtr, substitutionModelId);
        }

        throw unsupported(
                "Only HKY-compatible and GTR nucleotide substitution models are supported for XML export at this stage."
        );
    }

    public String substitutionModelTag(SubstitutionModel substitutionModel) {
        if (substitutionModel instanceof HKY) {
            return "hkyModel";
        }

        if (substitutionModel instanceof GTR) {
            return "gtrModel";
        }

        throw unsupported(
                "Cannot determine XML tag for unsupported substitution model: "
                        + substitutionModel.getClass().getName()
        );
    }

    private List<BeastXXmlElement> buildHKYModel(
            HKY hky,
            String substitutionModelId
    ) {
        List<BeastXXmlElement> elements =
                new ArrayList<>();

        FrequencyModel frequencyModel =
                frequencyModel(hky);

        String frequencyModelId =
                substitutionModelId + "_frequencies";

        elements.add(
                frequencyModelDefinition(
                        frequencyModel,
                        frequencyModelId
                )
        );

        elements.add(
                hkyModelDefinition(
                        hky,
                        substitutionModelId,
                        frequencyModelId
                )
        );

        return elements;
    }

    private List<BeastXXmlElement> buildGTRModel(
            GTR gtr,
            String substitutionModelId
    ) {
        List<BeastXXmlElement> elements =
                new ArrayList<>();

        FrequencyModel frequencyModel =
                frequencyModel(gtr);

        String frequencyModelId =
                substitutionModelId + "_frequencies";

        elements.add(
                frequencyModelDefinition(
                        frequencyModel,
                        frequencyModelId
                )
        );

        elements.add(
                gtrModelDefinition(
                        gtr,
                        substitutionModelId,
                        frequencyModelId
                )
        );

        return elements;
    }

    private BeastXXmlElement frequencyModelDefinition(
            FrequencyModel frequencyModel,
            String frequencyModelId
    ) {
        Parameter frequencies =
                frequencyModel.getFrequencyParameter();

        return BeastXXmlElement.element("frequencyModel")
                .withId(frequencyModelId)
                .withAttribute("dataType", dataTypeName(frequencyModel.getDataType()))
                .withChild(
                        BeastXXmlElement.element("frequencies")
                                .withChild(
                                        vectorParameterDefinition(
                                                frequencyModelId + "_parameter",
                                                frequencies
                                        )
                                )
                );
    }

    private BeastXXmlElement hkyModelDefinition(
            HKY hky,
            String substitutionModelId,
            String frequencyModelId
    ) {
        Parameter kappa =
                kappaParameter(hky);

        return BeastXXmlElement.element("hkyModel")
                .withId(substitutionModelId)
                .withChild(
                        BeastXXmlElement.element("frequencies")
                                .withChild(
                                        BeastXXmlElement.ref("frequencyModel", frequencyModelId)
                                )
                )
                .withChild(
                        BeastXXmlElement.element("kappa")
                                .withChild(
                                        parameterOrInlineDefinition(
                                                substitutionModelId + "_kappa",
                                                kappa
                                        )
                                )
                );
    }

    private BeastXXmlElement gtrModelDefinition(
            GTR gtr,
            String substitutionModelId,
            String frequencyModelId
    ) {
        BeastXXmlElement model =
                BeastXXmlElement.element("gtrModel")
                        .withId(substitutionModelId)
                        .withChild(
                                BeastXXmlElement.element("frequencies")
                                        .withChild(
                                                BeastXXmlElement.ref("frequencyModel", frequencyModelId)
                                        )
                        );

        List<Parameter> rateParameters =
                gtrRateParameters(gtr);

        if (allGTRRatesAreInline(rateParameters)) {
            return model.withChild(
                    BeastXXmlElement.element("rates")
                            .withChild(
                                    fixedGTRRatesParameter(
                                            rateParameters,
                                            substitutionModelId + "_rates"
                                    )
                            )
            );
        }

        int impliedRateIndex =
                impliedReferenceRateIndex(rateParameters);

        for (int i = 0; i < rateParameters.size(); i++) {
            if (i == impliedRateIndex) {
                continue;
            }

            model =
                    model.withChild(
                            BeastXXmlElement.element(GTR_RATE_NAMES.get(i))
                                    .withChild(
                                            parameterOrInlineDefinition(
                                                    substitutionModelId + "_" + GTR_RATE_NAMES.get(i),
                                                    rateParameters.get(i)
                                            )
                                    )
                    );
        }

        return model;
    }

    private BeastXXmlElement fixedGTRRatesParameter(
            List<Parameter> rateParameters,
            String fallbackId
    ) {
        List<String> values =
                new ArrayList<>();

        for (Parameter parameter : rateParameters) {
            values.add(format(parameter.getParameterValue(0)));
        }

        return BeastXXmlElement.element("parameter")
                .withId(fallbackId)
                .withAttribute("value", String.join(" ", values));
    }

    private List<Parameter> gtrRateParameters(GTR gtr) {
        List<Parameter> parameters =
                new ArrayList<>();

        for (int i = 0; i < gtr.getVariableCount(); i++) {
            Variable<?> variable =
                    gtr.getVariable(i);

            if (variable instanceof Parameter parameter && parameter.getDimension() == 1) {
                parameters.add(parameter);
            }
        }

        if (parameters.size() != 6) {
            throw unsupported(
                    "GTR XML export requires six scalar rate parameters in AC, AG, AT, CG, CT, GT order."
            );
        }

        return parameters;
    }

    private boolean allGTRRatesAreInline(List<Parameter> rateParameters) {
        for (Parameter parameter : rateParameters) {
            if (hasId(parameter)) {
                return false;
            }
        }

        return true;
    }

    private int impliedReferenceRateIndex(List<Parameter> rateParameters) {
        int impliedRateIndex =
                -1;

        for (int i = 0; i < rateParameters.size(); i++) {
            Parameter parameter =
                    rateParameters.get(i);

            if (!hasId(parameter) && approximatelyOne(parameter.getParameterValue(0))) {
                if (impliedRateIndex != -1) {
                    throw unsupported(
                            "Stochastic GTR XML export requires exactly one fixed rate equal to 1.0 "
                                    + "so BEAST X can use it as the implied reference rate."
                    );
                }

                impliedRateIndex =
                        i;
            }
        }

        if (impliedRateIndex == -1) {
            throw unsupported(
                    "Stochastic GTR XML export requires exactly one fixed rate equal to 1.0. "
                            + "BEAST X XML accepts either a six-dimensional rates parameter, or exactly five named rates "
                            + "with one implied reference rate."
            );
        }

        return impliedRateIndex;
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

    private BeastXXmlElement vectorParameterDefinition(
            String fallbackId,
            Parameter parameter
    ) {
        String id =
                parameter.getId();

        return BeastXXmlElement.element("parameter")
                .withId(id == null || id.isBlank() ? fallbackId : id)
                .withAttribute("value", vectorValue(parameter));
    }

    private String vectorValue(Parameter parameter) {
        List<String> values =
                new ArrayList<>();

        for (int i = 0; i < parameter.getDimension(); i++) {
            values.add(format(parameter.getParameterValue(i)));
        }

        return String.join(" ", values);
    }

    private FrequencyModel frequencyModel(SubstitutionModel substitutionModel) {
        if (substitutionModel instanceof BaseSubstitutionModel baseSubstitutionModel) {
            return baseSubstitutionModel.getFrequencyModel();
        }

        throw unsupported(
                "Cannot extract frequency model from substitution model: "
                        + substitutionModel.getClass().getName()
        );
    }

    private Parameter kappaParameter(HKY hky) {
        for (int i = 0; i < hky.getVariableCount(); i++) {
            Variable<?> variable =
                    hky.getVariable(i);

            if (variable instanceof Parameter parameter && parameter.getDimension() == 1) {
                return parameter;
            }
        }

        throw unsupported("Cannot extract kappa parameter from HKY substitution model.");
    }

    private String dataTypeName(DataType dataType) {
        if (dataType == null) {
            throw new IllegalArgumentException(
                    "Cannot serialize substitution-model frequency model without a data type."
            );
        }

        String description =
                dataType.getDescription();

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot serialize substitution-model frequency model with unnamed data type."
            );
        }

        return switch (description.toLowerCase()) {
            case "nucleotide", "nucleotides", "dna" -> "nucleotide";
            case "amino acid", "amino acids", "aminoacid", "aminoacids", "protein" -> "aminoacid";
            case "two states", "binary", "boolean" -> "twoState";
            default -> description;
        };
    }

    private static boolean hasId(Parameter parameter) {
        String id =
                parameter.getId();

        return id != null && !id.isBlank();
    }

    private static boolean approximatelyOne(double value) {
        return Math.abs(value - 1.0) < 1.0e-12;
    }

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend BeastXSubstitutionModelXmlBuilder before exporting this substitution model to XML."
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