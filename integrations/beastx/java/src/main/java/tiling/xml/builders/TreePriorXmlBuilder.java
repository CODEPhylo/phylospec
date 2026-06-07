package tiling.xml.builders;

import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
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
            BirthDeathGernhard08Model birthDeathModel =
                    getBirthDeathModel(treePrior);

            if (isYuleCompatible(birthDeathModel)) {
                return yuleModelDefinition(state, treePrior, birthDeathModel);
            }

            return birthDeathModelDefinition(state, treePrior, birthDeathModel);
        }

        if (treePrior instanceof CoalescentLikelihood) {
            return constantPopulationModelDefinition(state, treePrior);
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
                .withAttribute("type", "LABELED")
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

    private XmlElement constantPopulationModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        ConstantPopulationModel constantPopulationModel =
                getConstantPopulationModel(treePrior);

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

    private XmlElement speciationTreePrior(
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        BirthDeathGernhard08Model birthDeathModel =
                getBirthDeathModel(treePrior);

        String modelTag =
                isYuleCompatible(birthDeathModel)
                        ? "yuleModel"
                        : "birthDeathModel";

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
        return XmlElement.element("coalescentLikelihood")
                .withId(priorId(treePrior))
                .withChild(
                        XmlElement.element("model")
                                .withChild(
                                        XmlElement.ref("constantSize", treePriorModelId(treePrior))
                                )
                )
                .withChild(
                        XmlElement.element("populationTree")
                                .withChild(treeReference(treeModel))
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

    private static BirthDeathGernhard08Model getBirthDeathModel(AbstractModelLikelihood treePrior) {
        if (!(treePrior instanceof SpeciationLikelihood speciationLikelihood)) {
            throw unsupported("Only SpeciationLikelihood tree priors can be serialized as Yule or BirthDeath XML.");
        }

        SpeciationModel speciationModel =
                speciationLikelihood.getSpeciationModel();

        if (!(speciationModel instanceof BirthDeathGernhard08Model birthDeathModel)) {
            throw unsupported("Only Yule and BirthDeath tree priors are supported.");
        }

        return birthDeathModel;
    }

    private static ConstantPopulationModel getConstantPopulationModel(AbstractModelLikelihood treePrior) {
        if (!(treePrior instanceof CoalescentLikelihood coalescentLikelihood)) {
            throw unsupported("Only CoalescentLikelihood tree priors can be serialized as coalescent XML.");
        }

        DemographicModel demographicModel =
                coalescentLikelihood.getDemoModel();

        if (!(demographicModel instanceof ConstantPopulationModel constantPopulationModel)) {
            throw unsupported("Only constant-population Coalescent tree priors are supported.");
        }

        return constantPopulationModel;
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

    private static Parameter constantPopulationVariable(ConstantPopulationModel populationModel) {
        if (populationModel.getVariableCount() < 1) {
            throw unsupported("Constant-population Coalescent XML export requires a population size parameter.");
        }

        Variable<?> variable =
                populationModel.getVariable(0);

        if (!(variable instanceof Parameter parameter)) {
            throw unsupported("Constant-population Coalescent XML export requires a population size parameter.");
        }

        return parameter;
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

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend TreePriorXmlBuilder before exporting this tree prior to XML."
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