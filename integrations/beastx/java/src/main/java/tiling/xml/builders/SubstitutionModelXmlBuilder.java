package tiling.xml.builders;

import dr.evolution.datatype.DataType;
import dr.evolution.datatype.Codons;
import dr.evomodel.substmodel.codon.GY94CodonModel;
import dr.evomodel.substmodel.BaseSubstitutionModel;
import dr.evomodel.substmodel.GeneralSubstitutionModel;
import dr.evomodel.substmodel.EmpiricalRateMatrix;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.evomodel.substmodel.aminoacid.AminoAcidModelType;
import dr.evomodel.substmodel.aminoacid.EmpiricalAminoAcidModel;
import dr.evomodel.substmodel.nucleotide.GTR;
import dr.evomodel.substmodel.nucleotide.HKY;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.inference.model.VectorSliceParameter;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

/**
 * Builds XML substitution and frequency model definitions for PhyloCTMC export.
 */
public class SubstitutionModelXmlBuilder {

    private static final List<String> GTR_RATE_NAMES =
            List.of("rateAC", "rateAG", "rateAT", "rateCG", "rateCT", "rateGT");

    public List<XmlElement> buildSubstitutionModel(
            SubstitutionModel substitutionModel,
            String substitutionModelId
    ) {
        if (substitutionModel instanceof HKY hky) {
            return buildHKYModel(hky, substitutionModelId);
        }

        if (substitutionModel instanceof GTR gtr) {
            return buildGTRModel(gtr, substitutionModelId);
        }

        if (substitutionModel instanceof EmpiricalAminoAcidModel aminoAcidModel) {
            return buildEmpiricalAminoAcidModel(aminoAcidModel, substitutionModelId);
        }

        if (substitutionModel instanceof GeneralSubstitutionModel generalSubstitutionModel) {
            return buildGeneralSubstitutionModel(generalSubstitutionModel, substitutionModelId);
        }

        if (substitutionModel instanceof GY94CodonModel gy94CodonModel) {
            return buildGY94CodonModel(gy94CodonModel, substitutionModelId);
        }

        throw unsupported(
                "Only HKY-compatible, GTR nucleotide, empirical amino-acid, and GY94 codon substitution models are supported for XML export at this stage."
        );
    }

    public String substitutionModelTag(SubstitutionModel substitutionModel) {
        if (substitutionModel instanceof HKY) {
            return "hkyModel";
        }

        if (substitutionModel instanceof GTR) {
            return "gtrModel";
        }

        if (substitutionModel instanceof EmpiricalAminoAcidModel) {
            return "aminoAcidModel";
        }

        if (substitutionModel instanceof GeneralSubstitutionModel) {
            return "generalSubstitutionModel";
        }

        if (substitutionModel instanceof GY94CodonModel) {
            return "yangCodonModel";
        }

        throw unsupported(
                "Cannot determine XML tag for unsupported substitution model: "
                        + substitutionModel.getClass().getName()
        );
    }

    private List<XmlElement> buildHKYModel(
            HKY hky,
            String substitutionModelId
    ) {
        List<XmlElement> elements =
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

    private List<XmlElement> buildGTRModel(
            GTR gtr,
            String substitutionModelId
    ) {
        List<XmlElement> elements =
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

    private List<XmlElement> buildEmpiricalAminoAcidModel(
            EmpiricalAminoAcidModel aminoAcidModel,
            String substitutionModelId
    ) {
        List<XmlElement> elements =
                new ArrayList<>();

        FrequencyModel frequencyModel =
                frequencyModel(aminoAcidModel);

        String frequencyModelId =
                substitutionModelId + "_frequencies";

        elements.add(
                frequencyModelDefinition(
                        frequencyModel,
                        frequencyModelId
                )
        );

        elements.add(
                aminoAcidModelDefinition(
                        aminoAcidModel,
                        substitutionModelId,
                        frequencyModelId
                )
        );

        return elements;
    }

    private List<XmlElement> buildGeneralSubstitutionModel(
            GeneralSubstitutionModel generalSubstitutionModel,
            String substitutionModelId
    ) {
        List<XmlElement> elements =
                new ArrayList<>();

        FrequencyModel frequencyModel =
                frequencyModel(generalSubstitutionModel);

        String frequencyModelId =
                substitutionModelId + "_frequencies";

        elements.add(
                frequencyModelDefinition(
                        frequencyModel,
                        frequencyModelId
                )
        );

        elements.add(
                generalSubstitutionModelDefinition(
                        generalSubstitutionModel,
                        substitutionModelId,
                        frequencyModelId
                )
        );

        return elements;
    }

    private XmlElement generalSubstitutionModelDefinition(
            GeneralSubstitutionModel generalSubstitutionModel,
            String substitutionModelId,
            String frequencyModelId
    ) {
        Parameter ratesParameter =
                generalSubstitutionModelRatesParameter(generalSubstitutionModel);

        return XmlElement.element("generalSubstitutionModel")
                .withId(substitutionModelId)
                .withAttribute(
                        "dataType",
                        dataTypeName(generalSubstitutionModel.getDataType())
                )
                .withAttribute("normalized", "false")
                .withChild(
                        XmlElement.element("frequencies")
                                .withChild(
                                        XmlElement.ref("frequencyModel", frequencyModelId)
                                )
                )
                .withChild(
                        XmlElement.element("rates")
                                .withChild(
                                        parameterOrInlineDefinition(
                                                substitutionModelId + "_rates",
                                                ratesParameter
                                        )
                                )
                );
    }

    private List<XmlElement> buildGY94CodonModel(
            GY94CodonModel gy94CodonModel,
            String substitutionModelId
    ) {
        List<XmlElement> elements =
                new ArrayList<>();

        FrequencyModel frequencyModel =
                frequencyModel(gy94CodonModel);

        String frequencyModelId =
                substitutionModelId + "_frequencies";

        elements.add(
                frequencyModelDefinition(
                        frequencyModel,
                        frequencyModelId
                )
        );

        elements.add(
                yangCodonModelDefinition(
                        gy94CodonModel,
                        substitutionModelId,
                        frequencyModelId
                )
        );

        return elements;
    }

    private XmlElement frequencyModelDefinition(
            FrequencyModel frequencyModel,
            String frequencyModelId
    ) {
        Parameter frequencies =
                frequencyModel.getFrequencyParameter();

        return XmlElement.element("frequencyModel")
                .withId(frequencyModelId)
                .withAttribute("dataType", dataTypeName(frequencyModel.getDataType()))
                .withChild(
                        XmlElement.element("frequencies")
                                .withChild(
                                        vectorParameterOrInlineDefinition(
                                                frequencyModelId + "_parameter",
                                                frequencies
                                        )
                                )
                );
    }

    private XmlElement hkyModelDefinition(
            HKY hky,
            String substitutionModelId,
            String frequencyModelId
    ) {
        Parameter kappa =
                kappaParameter(hky);

        return XmlElement.element("hkyModel")
                .withId(substitutionModelId)
                .withChild(
                        XmlElement.element("frequencies")
                                .withChild(
                                        XmlElement.ref("frequencyModel", frequencyModelId)
                                )
                )
                .withChild(
                        XmlElement.element("kappa")
                                .withChild(
                                        parameterOrInlineDefinition(
                                                substitutionModelId + "_kappa",
                                                kappa
                                        )
                                )
                );
    }

    private XmlElement gtrModelDefinition(
            GTR gtr,
            String substitutionModelId,
            String frequencyModelId
    ) {
        XmlElement model =
                XmlElement.element("gtrModel")
                        .withId(substitutionModelId)
                        .withChild(
                                XmlElement.element("frequencies")
                                        .withChild(
                                                XmlElement.ref("frequencyModel", frequencyModelId)
                                        )
                        );

        List<Parameter> rateParameters =
                gtrRateParameters(gtr);

        Parameter relativeRates =
                jointGTRRatesParameter(rateParameters);

        if (relativeRates != null) {
            return model.withChild(
                    XmlElement.element("rates")
                            .withChild(
                                    parameterOrInlineVectorDefinition(
                                            substitutionModelId + "_rates",
                                            relativeRates
                                    )
                            )
            );
        }

        if (allGTRRatesAreInline(rateParameters)) {
            return model.withChild(
                    XmlElement.element("rates")
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
                            XmlElement.element(GTR_RATE_NAMES.get(i))
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

    private XmlElement aminoAcidModelDefinition(
            EmpiricalAminoAcidModel aminoAcidModel,
            String substitutionModelId,
            String frequencyModelId
    ) {
        return XmlElement.element("aminoAcidModel")
                .withId(substitutionModelId)
                .withAttribute("type", aminoAcidModelType(aminoAcidModel))
                .withChild(
                        XmlElement.element("frequencies")
                                .withChild(
                                        XmlElement.ref("frequencyModel", frequencyModelId)
                                )
                );
    }

    private XmlElement yangCodonModelDefinition(
            GY94CodonModel gy94CodonModel,
            String substitutionModelId,
            String frequencyModelId
    ) {
        Parameter omega =
                gy94ParameterField(
                        gy94CodonModel,
                        "omegaParameter"
                );

        Parameter kappa =
                gy94ParameterField(
                        gy94CodonModel,
                        "kappaParameter"
                );

        return XmlElement.element("yangCodonModel")
                .withId(substitutionModelId)
                .withChild(
                        XmlElement.element("omega")
                                .withChild(
                                        parameterOrInlineDefinition(
                                                substitutionModelId + "_omega",
                                                omega
                                        )
                                )
                )
                .withChild(
                        XmlElement.element("kappa")
                                .withChild(
                                        parameterOrInlineDefinition(
                                                substitutionModelId + "_kappa",
                                                kappa
                                        )
                                )
                )
                .withChild(
                        XmlElement.ref("frequencyModel", frequencyModelId)
                );
    }

    private String aminoAcidModelType(EmpiricalAminoAcidModel aminoAcidModel) {
        EmpiricalRateMatrix rateMatrix =
                aminoAcidModel.getEmpiricalRateMatrix();

        for (AminoAcidModelType type : AminoAcidModelType.values()) {
            EmpiricalRateMatrix candidate =
                    type.getRateMatrixInstance();

            if (
                    rateMatrix == candidate
                            || rateMatrix.getClass().equals(candidate.getClass())
            ) {
                return type.getXMLName();
            }
        }

        throw unsupported(
                "Unsupported empirical amino-acid rate matrix: "
                        + rateMatrix.getClass().getName()
        );
    }

    private XmlElement fixedGTRRatesParameter(
            List<Parameter> rateParameters,
            String fallbackId
    ) {
        List<String> values =
                new ArrayList<>();

        for (Parameter parameter : rateParameters) {
            values.add(format(parameter.getParameterValue(0)));
        }

        return XmlElement.element("parameter")
                .withId(fallbackId)
                .withAttribute("value", String.join(" ", values));
    }

    private XmlElement parameterOrInlineVectorDefinition(
            String fallbackId,
            Parameter parameter
    ) {
        if (hasId(parameter)) {
            return XmlElement.ref("parameter", parameter.getId());
        }

        List<String> values =
                new ArrayList<>();

        for (double value : parameter.getParameterValues()) {
            values.add(format(value));
        }

        return XmlElement.element("parameter")
                .withId(fallbackId)
                .withAttribute("value", String.join(" ", values));
    }

    private Parameter jointGTRRatesParameter(List<Parameter> rateParameters) {
        Parameter relativeRates =
                null;

        for (int i = 0; i < rateParameters.size(); i++) {
            Parameter rate =
                    rateParameters.get(i);

            if (!(rate instanceof VectorSliceParameter slice)
                    || slice.getParameterCount() != 1) {
                return null;
            }

            Parameter candidate =
                    slice.getParameter(0);

            if (candidate.getDimension() != 6
                    || (relativeRates != null && relativeRates != candidate)
                    || !approximatelyEqual(rate.getParameterValue(0), candidate.getParameterValue(i))) {
                return null;
            }

            relativeRates =
                    candidate;
        }

        return relativeRates;
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

    private XmlElement parameterOrInlineDefinition(
            String fallbackId,
            Parameter parameter
    ) {
        String id =
                parameter.getId();

        if (id != null && !id.isBlank()) {
            return XmlElement.ref("parameter", id);
        }

        return XmlElement.element("parameter")
                .withId(fallbackId)
                .withAttribute("value", format(parameter.getParameterValue(0)));
    }

    private XmlElement vectorParameterDefinition(
            String fallbackId,
            Parameter parameter
    ) {
        String id =
                parameter.getId();

        return XmlElement.element("parameter")
                .withId(id == null || id.isBlank() ? fallbackId : id)
                .withAttribute("value", vectorValue(parameter));
    }

    private XmlElement vectorParameterOrInlineDefinition(
            String fallbackId,
            Parameter parameter
    ) {
        String id =
                parameter.getId();

        if (id != null && !id.isBlank()) {
            return XmlElement.ref("parameter", id);
        }

        return vectorParameterDefinition(fallbackId, parameter);
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

    private Parameter generalSubstitutionModelRatesParameter(
            GeneralSubstitutionModel generalSubstitutionModel
    ) {
        try {
            Field field =
                    GeneralSubstitutionModel.class.getDeclaredField("ratesParameter");

            field.setAccessible(true);

            Object value =
                    field.get(generalSubstitutionModel);

            if (value instanceof Parameter parameter) {
                return parameter;
            }

            throw unsupported(
                    "GeneralSubstitutionModel field 'ratesParameter' is not a Parameter."
            );
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw unsupported(
                    "Cannot extract ratesParameter field from GeneralSubstitutionModel."
            );
        }
    }

    private Parameter gy94ParameterField(
            GY94CodonModel gy94CodonModel,
            String fieldName
    ) {
        try {
            Field field =
                    GY94CodonModel.class.getDeclaredField(fieldName);

            field.setAccessible(true);

            Object value =
                    field.get(gy94CodonModel);

            if (value instanceof Parameter parameter) {
                return parameter;
            }

            throw unsupported(
                    "GY94 field '" + fieldName + "' is not a Parameter."
            );
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw unsupported(
                    "Cannot extract GY94 parameter field '" + fieldName + "'."
            );
        }
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
            case "amino acid", "amino acids", "aminoacid", "aminoacids", "protein" -> "amino acid";
            case "codon", "codons", "universal codons", "codon-universal" -> "codon-universal";
            case "two states", "binary", "boolean" -> "binary";
            default -> {
                if (dataType instanceof Codons) {
                    yield "codon-universal";
                }

                yield description;
            }
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

    private static boolean approximatelyEqual(
            double left,
            double right
    ) {
        return Math.abs(left - right) < 1.0e-12;
    }

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend SubstitutionModelXmlBuilder before exporting this substitution model to XML."
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
