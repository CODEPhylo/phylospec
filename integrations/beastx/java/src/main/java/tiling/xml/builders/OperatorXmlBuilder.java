package tiling.xml.builders;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import tiling.BeastXState;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OperatorXmlBuilder {

    private static final double DEFAULT_CLOCK_TREE_UP_DOWN_WEIGHT = 5.0;
    private static final double DEFAULT_TREE_NODE_HEIGHT_SCALE_WEIGHT = 5.0;

    public List<XmlElement> buildOperators(BeastXState state) {
        List<XmlElement> operators =
                new ArrayList<>();

        operators.addAll(buildParameterOperators(state));
        operators.addAll(buildClockTreeUpDownOperators(state));
        operators.addAll(buildTreeOperators(state));

        return operators;
    }

    private List<XmlElement> buildParameterOperators(BeastXState state) {
        List<XmlElement> operators =
                new ArrayList<>();

        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.removeIf(parameter -> isRelaxedClockRateCategoriesParameter(state, parameter));

        parameters.sort(Comparator.comparing(OperatorXmlBuilder::parameterId));

        for (Parameter parameter : parameters) {
            if (isSimplexParameter(parameter)) {
                operators.add(deltaExchangeOperator(state, parameter));
            } else if (hasFiniteLowerAndUpperBounds(parameter)) {
                operators.add(randomWalkOperator(state, parameter));
            } else if (supportsScaleOperator(parameter)) {
                operators.add(scaleOperator(state, parameter));
            } else {
                operators.add(randomWalkOperator(state, parameter));
            }
        }

        return operators;
    }

    private List<XmlElement> buildClockTreeUpDownOperators(BeastXState state) {
        List<XmlElement> operators =
                new ArrayList<>();

        Parameter clockRate =
                findClockRateParameter(state);

        if (clockRate == null) {
            return operators;
        }

        List<TreeModel> trees =
                new ArrayList<>(state.treePriorDistributions.keySet());

        trees.sort(Comparator.comparing(OperatorXmlBuilder::treeId));

        for (TreeModel treeModel : trees) {
            if (hasRelaxedClockBranchRateModel(state, treeModel)) {
                continue;
            }

            operators.add(
                    clockTreeUpDownOperator(
                            state,
                            clockRate,
                            treeModel
                    )
            );
        }

        return operators;
    }

    private List<XmlElement> buildTreeOperators(BeastXState state) {
        List<XmlElement> operators =
                new ArrayList<>();

        List<TreeModel> trees =
                new ArrayList<>(state.treePriorDistributions.keySet());

        trees.sort(Comparator.comparing(OperatorXmlBuilder::treeId));

        for (TreeModel treeModel : trees) {
            if (hasRelaxedClockBranchRateModel(state, treeModel)) {
                continue;
            }

            String id =
                    treeId(treeModel);
/*
            operators.add(
                    nodeHeightScaleOperator(
                            state,
                            treeModel
                    )
            );
*/
            operators.add(
                    treeOperator(
                            "narrowExchange",
                            id + "_narrowExchange",
                            state.operatorConfig.treeNarrowExchangeWeight,
                            treeModel
                    )
            );

            operators.add(
                    treeOperator(
                            "wideExchange",
                            id + "_wideExchange",
                            state.operatorConfig.treeWideExchangeWeight,
                            treeModel
                    )
            );

            operators.add(
                    subtreeSlideOperator(state, treeModel)
            );

            operators.add(
                    treeOperator(
                            "wilsonBalding",
                            id + "_wilsonBalding",
                            state.operatorConfig.treeWilsonBaldingWeight,
                            treeModel
                    )
            );
        }

        return operators;
    }

    private boolean isRelaxedClockRateCategoriesParameter(
            BeastXState state,
            Parameter parameter
    ) {
        String parameterId =
                parameter.getId();

        for (BeastXState.RelaxedClockSpec spec : state.treeRelaxedClockModels.values()) {
            Parameter rateCategoriesParameter =
                    spec.rateCategoriesParameter();

            if (rateCategoriesParameter == parameter) {
                return true;
            }

            String rateCategoriesParameterId =
                    rateCategoriesParameter.getId();

            if (
                    parameterId != null
                            && rateCategoriesParameterId != null
                            && parameterId.equals(rateCategoriesParameterId)
            ) {
                return true;
            }
        }

        return false;
    }

    private boolean hasRelaxedClockBranchRateModel(
            BeastXState state,
            TreeModel treeModel
    ) {
        if (state.treeRelaxedClockModels.containsKey(treeModel)) {
            return true;
        }

        String treeModelId =
                treeId(treeModel);

        for (TreeModel registeredTreeModel : state.treeRelaxedClockModels.keySet()) {
            if (treeModelId.equals(treeId(registeredTreeModel))) {
                return true;
            }
        }

        return false;
    }

    private XmlElement scaleOperator(
            BeastXState state,
            Parameter parameter
    ) {
        String id =
                parameterId(parameter);

        return XmlElement.element("scaleOperator")
                .withId(id + "_scale")
                .withAttribute("scaleFactor", format(state.operatorConfig.parameterScaleFactor))
                .withAttribute("weight", format(state.operatorConfig.parameterOperatorWeight))
                .withChild(parameterReference(parameter));
    }

    private XmlElement randomWalkOperator(
            BeastXState state,
            Parameter parameter
    ) {
        String id =
                parameterId(parameter);

        return XmlElement.element("randomWalkOperator")
                .withId(id + "_randomWalk")
                .withAttribute("windowSize", format(state.operatorConfig.randomWalkWindowSize))
                .withAttribute("weight", format(state.operatorConfig.parameterOperatorWeight))
                .withAttribute("boundaryCondition", "reflecting")
                .withChild(parameterReference(parameter));
    }

    private XmlElement deltaExchangeOperator(
            BeastXState state,
            Parameter parameter
    ) {
        String id =
                parameterId(parameter);

        return XmlElement.element("deltaExchange")
                .withId(id + "_deltaExchange")
                .withAttribute("delta", "0.01")
                .withAttribute("weight", format(state.operatorConfig.parameterOperatorWeight))
                .withChild(parameterReference(parameter));
    }

    private XmlElement clockTreeUpDownOperator(
            BeastXState state,
            Parameter clockRate,
            TreeModel treeModel
    ) {
        String treeId =
                treeId(treeModel);

        String clockRateId =
                parameterId(clockRate);

        return XmlElement.element("upDownOperator")
                .withId(treeId + "_" + clockRateId + "_upDown")
                .withAttribute("scaleFactor", format(state.operatorConfig.parameterScaleFactor))
                .withAttribute("weight", format(DEFAULT_CLOCK_TREE_UP_DOWN_WEIGHT))
                .withChild(
                        XmlElement.element("up")
                                .withChild(parameterReference(clockRate))
                )
                .withChild(
                        XmlElement.element("down")
                                .withChild(treeAllInternalNodeHeightsReference(treeModel))
                );
    }

    private XmlElement nodeHeightScaleOperator(
            BeastXState state,
            TreeModel treeModel
    ) {
        String id =
                treeId(treeModel);

        return XmlElement.element("nodeHeightScaleOperator")
                .withId(id + "_nodeHeightScale")
                .withAttribute("scaleFactor", format(state.operatorConfig.parameterScaleFactor))
                .withAttribute("weight", format(DEFAULT_TREE_NODE_HEIGHT_SCALE_WEIGHT))
                .withChild(treeReference(treeModel));
    }

    private XmlElement treeOperator(
            String elementName,
            String id,
            double weight,
            TreeModel treeModel
    ) {
        return XmlElement.element(elementName)
                .withId(id)
                .withAttribute("weight", format(weight))
                .withChild(treeReference(treeModel));
    }

    private XmlElement subtreeSlideOperator(
            BeastXState state,
            TreeModel treeModel
    ) {
        String id =
                treeId(treeModel);

        return XmlElement.element("subtreeSlide")
                .withId(id + "_subtreeSlide")
                .withAttribute("weight", format(state.operatorConfig.treeSubtreeSlideWeight))
                .withAttribute("size", format(state.operatorConfig.treeSubtreeSlideSize))
                .withAttribute("gaussian", "true")
                .withChild(treeReference(treeModel));
    }

    private Parameter findClockRateParameter(BeastXState state) {
        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.removeIf(parameter -> isRelaxedClockRateCategoriesParameter(state, parameter));

        parameters.sort(Comparator.comparing(OperatorXmlBuilder::parameterId));

        for (Parameter parameter : parameters) {
            String id =
                    parameterId(parameter);

            if ("clockRate".equals(id)) {
                return parameter;
            }
        }

        for (Parameter parameter : parameters) {
            String id =
                    parameterId(parameter);

            if ("clock.rate".equals(id)) {
                return parameter;
            }
        }

        for (Parameter parameter : parameters) {
            String id =
                    parameterId(parameter)
                            .toLowerCase();

            if (id.contains("clockrate") || id.contains("clock.rate")) {
                return parameter;
            }
        }

        return null;
    }

    private XmlElement parameterReference(Parameter parameter) {
        return XmlElement.ref("parameter", parameterId(parameter));
    }

    private XmlElement treeReference(TreeModel treeModel) {
        return XmlElement.ref("treeModel", treeId(treeModel));
    }

    private XmlElement treeAllInternalNodeHeightsReference(TreeModel treeModel) {
        return XmlElement.ref(
                "parameter",
                treeId(treeModel) + ".allInternalNodeHeights"
        );
    }

    private static boolean supportsScaleOperator(Parameter parameter) {
        Bounds<Double> bounds =
                parameter.getBounds();

        if (bounds == null) {
            return false;
        }

        boolean strictlyPositive =
                true;

        boolean strictlyNegative =
                true;

        for (int i = 0; i < parameter.getDimension(); i++) {
            double lower =
                    bounds.getLowerLimit(i);

            double upper =
                    bounds.getUpperLimit(i);

            double value =
                    parameter.getParameterValue(i);

            if (!(lower >= 0.0 && value > 0.0)) {
                strictlyPositive =
                        false;
            }

            if (!(upper <= 0.0 && value < 0.0)) {
                strictlyNegative =
                        false;
            }
        }

        return strictlyPositive || strictlyNegative;
    }

    private static boolean hasFiniteLowerAndUpperBounds(Parameter parameter) {
        Bounds<Double> bounds =
                parameter.getBounds();

        if (bounds == null) {
            return false;
        }

        double lower =
                bounds.getLowerLimit(0);

        double upper =
                bounds.getUpperLimit(0);

        return Double.isFinite(lower) && Double.isFinite(upper);
    }

    private static boolean isSimplexParameter(Parameter parameter) {
        if (parameter.getDimension() <= 1) {
            return false;
        }

        Bounds<Double> bounds =
                parameter.getBounds();

        if (bounds == null) {
            return false;
        }

        double sum =
                0.0;

        for (int i = 0; i < parameter.getDimension(); i++) {
            double lower =
                    bounds.getLowerLimit(i);

            double upper =
                    bounds.getUpperLimit(i);

            if (!approximatelyZero(lower) || !approximatelyOne(upper)) {
                return false;
            }

            sum += parameter.getParameterValue(i);
        }

        return approximatelyOne(sum);
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