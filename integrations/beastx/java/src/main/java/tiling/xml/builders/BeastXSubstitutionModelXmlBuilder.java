package tiling.xml.builders;

import dr.evolution.datatype.DataType;
import dr.evomodel.substmodel.BaseSubstitutionModel;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.evomodel.substmodel.nucleotide.HKY;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import tiling.xml.BeastXXmlElement;

import java.util.ArrayList;
import java.util.List;

public class BeastXSubstitutionModelXmlBuilder {

    public List<BeastXXmlElement> buildSubstitutionModel(
            SubstitutionModel substitutionModel,
            String substitutionModelId
    ) {
        if (substitutionModel instanceof HKY hky) {
            return buildHKYModel(hky, substitutionModelId);
        }

        throw unsupported(
                "Only HKY-compatible nucleotide substitution models are supported for XML export at this stage."
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

        BeastXXmlElement element =
                BeastXXmlElement.element("parameter")
                        .withId(id == null || id.isBlank() ? fallbackId : id)
                        .withAttribute("value", vectorValue(parameter));

        return element;
    }

    private String vectorValue(Parameter parameter) {
        List<String> values =
                new ArrayList<>();

        for (int i = 0; i < parameter.getDimension(); i++) {
            values.add(format(parameter.getParameterValue(i)));
        }

        return String.join(" ", values);
    }

    private FrequencyModel frequencyModel(HKY hky) {
        if (hky instanceof BaseSubstitutionModel baseSubstitutionModel) {
            return baseSubstitutionModel.getFrequencyModel();
        }

        throw unsupported("Cannot extract frequency model from HKY substitution model.");
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