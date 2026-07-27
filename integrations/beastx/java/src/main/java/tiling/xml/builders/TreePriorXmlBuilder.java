package tiling.xml.builders;

import dr.evolution.coalescent.PiecewiseConstantPopulation;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.coalescent.demographicmodel.ExponentialGrowthModel;
import dr.evomodel.coalescent.demographicmodel.LogisticGrowthModel;
import dr.evomodel.coalescent.demographicmodel.PiecewisePopulationModel;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.BirthDeathSerialSamplingModel;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.speciation.SpeciationModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import tiling.BeastXState;
import tiling.xml.XmlElement;

public class TreePriorXmlBuilder {

    public XmlElement buildModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        if (treePrior instanceof SpeciationLikelihood) {
            SpeciationModel speciationModel =
                    getSpeciationModel(treePrior);

            if (speciationModel instanceof BirthDeathGernhard08Model birthDeathModel) {
                if (isYuleCompatible(birthDeathModel)) {
                    return yuleModelDefinition(state, treePrior, birthDeathModel);
                }

                return birthDeathModelDefinition(state, treePrior, birthDeathModel);
            }

            if (speciationModel instanceof BirthDeathSerialSamplingModel fbdModel) {
                return fossilizedBirthDeathModelDefinition(state, treePrior, fbdModel);
            }

            throw unsupported("Only Yule, BirthDeath, and FossilizedBirthDeath tree priors are supported.");
        }

        if (treePrior instanceof CoalescentLikelihood) {
            DemographicModel demographicModel =
                    getDemographicModel(treePrior);

            if (demographicModel instanceof ConstantPopulationModel constantPopulationModel) {
                return constantPopulationModelDefinition(
                        state,
                        treePrior,
                        constantPopulationModel
                );
            }

            if (demographicModel instanceof ExponentialGrowthModel exponentialGrowthModel) {
                return exponentialGrowthModelDefinition(
                        state,
                        treePrior,
                        exponentialGrowthModel
                );
            }

            if (demographicModel instanceof LogisticGrowthModel logisticGrowthModel) {
                return logisticGrowthModelDefinition(
                        state,
                        treePrior,
                        logisticGrowthModel
                );
            }

            if (demographicModel instanceof PiecewisePopulationModel piecewisePopulationModel) {
                return piecewisePopulationModelDefinition(
                        state,
                        treePrior,
                        piecewisePopulationModel
                );
            }

            throw unsupported("Only constant, exponential, logistic, and piecewise Coalescent demographic models are supported.");
        }

        throw unsupported("Only SpeciationLikelihood and CoalescentLikelihood tree priors are supported.");
    }

    public XmlElement buildPrior(
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        if (treePrior instanceof SpeciationLikelihood) {
            return speciationTreePrior(treeModel, treePrior);
        }

        if (treePrior instanceof CoalescentLikelihood) {
            return coalescentTreePrior(treeModel, treePrior);
        }

        throw unsupported("Only SpeciationLikelihood and CoalescentLikelihood tree priors are supported.");
    }

    private XmlElement yuleModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior,
            BirthDeathGernhard08Model yuleModel
    ) {
        return XmlElement.element("yuleModel")
                .withId(treePriorModelId(treePrior))
                .withAttribute("units", "years")
                .withChild(
                        parameterElement(
                                state,
                                "birthRate",
                                birthDeathVariable(yuleModel, 0),
                                priorId(treePrior) + "_birthRate",
                                yuleModel.getR(),
                                0.0,
                                null
                        )
                );
    }

    private XmlElement birthDeathModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior,
            BirthDeathGernhard08Model birthDeathModel
    ) {
        return XmlElement.element("birthDeathModel")
                .withId(treePriorModelId(treePrior))
                .withAttribute("type", "UNSCALED")
                .withAttribute("units", "years")
                .withChild(
                        parameterElement(
                                state,
                                "birthMinusDeathRate",
                                birthDeathVariable(birthDeathModel, 0),
                                priorId(treePrior) + "_birthMinusDeathRate",
                                birthDeathModel.getR(),
                                0.0,
                                null
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "relativeDeathRate",
                                birthDeathVariable(birthDeathModel, 1),
                                priorId(treePrior) + "_relativeDeathRate",
                                birthDeathModel.getA(),
                                0.0,
                                1.0
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "sampleProbability",
                                birthDeathVariable(birthDeathModel, 2),
                                priorId(treePrior) + "_sampleProbability",
                                birthDeathModel.getRho(),
                                0.0,
                                1.0
                        )
                );
    }

    private XmlElement fossilizedBirthDeathModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior,
            BirthDeathSerialSamplingModel fbdModel
    ) {
        Parameter birthRate =
                speciationVariable(fbdModel, 0, "birth rate");

        Parameter deathRate =
                speciationVariable(fbdModel, 1, "death rate");

        Parameter serialSamplingRate =
                speciationVariable(fbdModel, 2, "serial sampling rate");

        Parameter samplingProbability =
                speciationVariable(fbdModel, 3, "sampling probability");

        double origin =
                fossilizedBirthDeathOrigin(state, treePrior);

        return XmlElement.element("birthDeathSerialSampling")
                .withId(treePriorModelId(treePrior))
                .withAttribute("type", "LABELED")
                .withAttribute("units", "years")
                .withAttribute("hasFinalSample", "false")
                .withChild(
                        parameterElement(
                                state,
                                "birthRate",
                                birthRate,
                                priorId(treePrior) + "_birthRate",
                                birthRate.getParameterValue(0),
                                0.0,
                                null
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "deathRate",
                                deathRate,
                                priorId(treePrior) + "_deathRate",
                                deathRate.getParameterValue(0),
                                0.0,
                                null
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "psi",
                                serialSamplingRate,
                                priorId(treePrior) + "_psi",
                                serialSamplingRate.getParameterValue(0),
                                0.0,
                                null
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "sampleProbability",
                                samplingProbability,
                                priorId(treePrior) + "_sampleProbability",
                                samplingProbability.getParameterValue(0),
                                0.0,
                                1.0
                        )
                )
                .withChild(
                        XmlElement.element("origin")
                                .withChild(
                                        inlineParameterDefinition(
                                                priorId(treePrior) + "_origin",
                                                origin,
                                                0.0,
                                                null
                                        )
                                )
                );
    }

    private XmlElement constantPopulationModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior,
            ConstantPopulationModel constantPopulationModel
    ) {
        Parameter populationSize =
                constantPopulationVariable(constantPopulationModel);

        return XmlElement.element("constantSize")
                .withId(treePriorModelId(treePrior))
                .withAttribute("units", "years")
                .withChild(
                        parameterElement(
                                state,
                                "populationSize",
                                populationSize,
                                priorId(treePrior) + "_populationSize",
                                populationSize.getParameterValue(0),
                                0.0,
                                null
                        )
                );
    }

    private XmlElement exponentialGrowthModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior,
            ExponentialGrowthModel exponentialGrowthModel
    ) {
        Parameter populationSize =
                demographicVariable(exponentialGrowthModel, 0, "population size");

        Parameter growthRate =
                demographicVariable(exponentialGrowthModel, 1, "growth rate");

        return XmlElement.element("exponentialGrowth")
                .withId(treePriorModelId(treePrior))
                .withAttribute("units", "years")
                .withChild(
                        parameterElement(
                                state,
                                "populationSize",
                                populationSize,
                                priorId(treePrior) + "_populationSize",
                                populationSize.getParameterValue(0),
                                0.0,
                                null
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "growthRate",
                                growthRate,
                                priorId(treePrior) + "_growthRate",
                                growthRate.getParameterValue(0),
                                null,
                                null
                        )
                );
    }

    private XmlElement logisticGrowthModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior,
            LogisticGrowthModel logisticGrowthModel
    ) {
        Parameter carryingCapacity =
                demographicVariable(logisticGrowthModel, 0, "carrying capacity");

        Parameter growthRate =
                demographicVariable(logisticGrowthModel, 1, "growth rate");

        Parameter inflectionAge =
                demographicVariable(logisticGrowthModel, 2, "inflection age");

        return XmlElement.element("logisticGrowth")
                .withId(treePriorModelId(treePrior))
                .withAttribute("units", "years")
                .withChild(
                        parameterElement(
                                state,
                                "populationSize",
                                carryingCapacity,
                                priorId(treePrior) + "_populationSize",
                                carryingCapacity.getParameterValue(0),
                                0.0,
                                null
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "growthRate",
                                growthRate,
                                priorId(treePrior) + "_growthRate",
                                growthRate.getParameterValue(0),
                                null,
                                null
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "t50",
                                inflectionAge,
                                priorId(treePrior) + "_t50",
                                inflectionAge.getParameterValue(0),
                                0.0,
                                null
                        )
                );
    }

    private XmlElement piecewisePopulationModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior,
            PiecewisePopulationModel piecewisePopulationModel
    ) {
        Parameter populationSizes =
                demographicVariable(piecewisePopulationModel, 0, "population sizes");

        double[] epochWidths =
                piecewiseEpochWidths(piecewisePopulationModel, populationSizes.getDimension());

        return XmlElement.element("piecewisePopulation")
                .withId(treePriorModelId(treePrior))
                .withAttribute("units", "years")
                .withAttribute("linear", "false")
                .withChild(
                        XmlElement.element("epochSizes")
                                .withChild(
                                        state.stateNodes.containsKey(populationSizes)
                                                ? parameterReference(populationSizes)
                                                : inlineParameterDefinition(
                                                priorId(treePrior) + "_epochSizes",
                                                populationSizes,
                                                0.0,
                                                null
                                        )
                                )
                )
                .withChild(
                        XmlElement.element("epochWidths")
                                .withAttribute("widths", format(epochWidths))
                );
    }

    private XmlElement speciationTreePrior(
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        String modelTag =
                speciationModelTag(treePrior);

        return XmlElement.element("speciationLikelihood")
                .withId(priorId(treePrior))
                .withChild(
                        XmlElement.element("model")
                                .withChild(
                                        XmlElement.ref(modelTag, treePriorModelId(treePrior))
                                )
                )
                .withChild(
                        XmlElement.element("speciesTree")
                                .withChild(treeReference(treeModel))
                );
    }

    private XmlElement coalescentTreePrior(
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        String modelTag =
                coalescentModelTag(treePrior);

        return XmlElement.element("coalescentLikelihood")
                .withId(priorId(treePrior))
                .withChild(
                        XmlElement.element("model")
                                .withChild(
                                        XmlElement.ref(modelTag, treePriorModelId(treePrior))
                                )
                )
                .withChild(
                        XmlElement.element("populationTree")
                                .withChild(treeReference(treeModel))
                );
    }

    private String speciationModelTag(AbstractModelLikelihood treePrior) {
        SpeciationModel speciationModel =
                getSpeciationModel(treePrior);

        if (speciationModel instanceof BirthDeathGernhard08Model birthDeathModel) {
            return isYuleCompatible(birthDeathModel)
                    ? "yuleModel"
                    : "birthDeathModel";
        }

        if (speciationModel instanceof BirthDeathSerialSamplingModel) {
            return "birthDeathSerialSampling";
        }

        throw unsupported("Only Yule, BirthDeath, and FossilizedBirthDeath tree priors are supported.");
    }

    private String coalescentModelTag(AbstractModelLikelihood treePrior) {
        DemographicModel demographicModel =
                getDemographicModel(treePrior);

        if (demographicModel instanceof ConstantPopulationModel) {
            return "constantSize";
        }

        if (demographicModel instanceof ExponentialGrowthModel) {
            return "exponentialGrowth";
        }

        if (demographicModel instanceof LogisticGrowthModel) {
            return "logisticGrowth";
        }

        if (demographicModel instanceof PiecewisePopulationModel) {
            return "piecewisePopulation";
        }

        throw unsupported("Only constant, exponential, logistic, and piecewise Coalescent demographic models are supported.");
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

    private XmlElement inlineParameterDefinition(
            String id,
            Parameter parameter,
            Double lower,
            Double upper
    ) {
        XmlElement element =
                XmlElement.element("parameter")
                        .withId(id)
                        .withAttribute("value", format(parameterValues(parameter)));

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

    private static SpeciationModel getSpeciationModel(AbstractModelLikelihood treePrior) {
        if (!(treePrior instanceof SpeciationLikelihood speciationLikelihood)) {
            throw unsupported("Only SpeciationLikelihood tree priors can be serialized as speciation XML.");
        }

        return speciationLikelihood.getSpeciationModel();
    }

    private static DemographicModel getDemographicModel(AbstractModelLikelihood treePrior) {
        if (!(treePrior instanceof CoalescentLikelihood coalescentLikelihood)) {
            throw unsupported("Only CoalescentLikelihood tree priors can be serialized as coalescent XML.");
        }

        return coalescentLikelihood.getDemoModel();
    }

    private static Parameter birthDeathVariable(
            BirthDeathGernhard08Model birthDeathModel,
            int variableIndex
    ) {
        if (variableIndex >= birthDeathModel.getVariableCount()) {
            return null;
        }

        Variable<?> variable =
                birthDeathModel.getVariable(variableIndex);

        if (variable instanceof Parameter parameter) {
            return parameter;
        }

        return null;
    }

    private static Parameter speciationVariable(
            SpeciationModel speciationModel,
            int variableIndex,
            String label
    ) {
        if (variableIndex >= speciationModel.getVariableCount()) {
            throw unsupported("Speciation XML export requires a " + label + " parameter.");
        }

        Variable<?> variable =
                speciationModel.getVariable(variableIndex);

        if (variable instanceof Parameter parameter) {
            return parameter;
        }

        throw unsupported("Speciation XML export requires a " + label + " parameter.");
    }

    private static Parameter constantPopulationVariable(ConstantPopulationModel populationModel) {
        return demographicVariable(populationModel, 0, "population size");
    }

    private static Parameter demographicVariable(
            DemographicModel demographicModel,
            int variableIndex,
            String label
    ) {
        if (variableIndex >= demographicModel.getVariableCount()) {
            throw unsupported("Coalescent XML export requires a " + label + " parameter.");
        }

        Variable<?> variable =
                demographicModel.getVariable(variableIndex);

        if (variable instanceof Parameter parameter) {
            return parameter;
        }

        throw unsupported("Coalescent XML export requires a " + label + " parameter.");
    }

    private static double[] piecewiseEpochWidths(
            PiecewisePopulationModel piecewisePopulationModel,
            int populationSizeDimension
    ) {
        if (populationSizeDimension < 2) {
            throw unsupported("Piecewise Coalescent XML export requires at least two population sizes.");
        }

        if (!(piecewisePopulationModel.getDemographicFunction() instanceof PiecewiseConstantPopulation piecewisePopulation)) {
            throw unsupported("Piecewise Coalescent XML export requires a PiecewiseConstantPopulation demographic function.");
        }

        double[] widths =
                new double[populationSizeDimension - 1];

        for (int i = 0; i < widths.length; i++) {
            widths[i] =
                    piecewisePopulation.getEpochDuration(i);

            if (!(widths[i] > 0.0)) {
                throw unsupported("Piecewise Coalescent XML export requires positive epoch widths.");
            }
        }

        return widths;
    }

    private static double[] parameterValues(Parameter parameter) {
        double[] values =
                new double[parameter.getDimension()];

        for (int i = 0; i < values.length; i++) {
            values[i] =
                    parameter.getParameterValue(i);
        }

        return values;
    }

    private static boolean isYuleCompatible(BirthDeathGernhard08Model model) {
        return model.isYule()
                || (
                approximatelyZero(model.getA())
                        && approximatelyOne(model.getRho())
        );
    }

    private static boolean approximatelyZero(double value) {
        return Math.abs(value) < 1.0e-12;
    }

    private static boolean approximatelyOne(double value) {
        return Math.abs(value - 1.0) < 1.0e-12;
    }

    private static double fossilizedBirthDeathOrigin(
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        TreeModel treeModel =
                treeModelForPrior(state, treePrior);

        double rootHeight =
                treeModel.getNodeHeight(treeModel.getRoot());

        return rootHeight + Math.max(1.0, rootHeight * 0.25);
    }

    private static TreeModel treeModelForPrior(
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        for (java.util.Map.Entry<TreeModel, AbstractModelLikelihood> entry : state.treePriorDistributions.entrySet()) {
            if (entry.getValue() == treePrior) {
                return entry.getKey();
            }
        }

        throw unsupported("Tree prior XML export requires the tree associated with the prior.");
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

    private static String priorId(AbstractModelLikelihood prior) {
        String id =
                prior.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X prior.");
        }

        return id;
    }

    private static String treePriorModelId(AbstractModelLikelihood treePrior) {
        return priorId(treePrior) + "_model";
    }

    private static String format(double[] values) {
        StringBuilder builder =
                new StringBuilder();

        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }

            builder.append(format(values[i]));
        }

        return builder.toString();
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

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend TreePriorXmlBuilder before exporting this tree prior to XML."
        );
    }
}
