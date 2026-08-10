package tiling.xml.builders;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import tiling.BeastXState;
import tiling.operators.OperatorSelector;
import tiling.operators.OperatorSpec;
import tiling.xml.XmlElement;

import java.util.List;

/** Materializes the shared BEAST X operator selection as XML. */
public final class OperatorXmlBuilder {

    public List<XmlElement> buildOperators(BeastXState state) {
        return new OperatorSelector().select(state).stream()
                .filter(spec -> spec.weight() > 0.0)
                .map(this::build)
                .toList();
    }

    private XmlElement build(OperatorSpec spec) {
        return switch (spec.family()) {
            case SCALE -> parameterOperator(
                    "scaleOperator", spec, "scale",
                    "scaleFactor", format(spec.tuning()));
            case RANDOM_WALK -> parameterOperator(
                    "randomWalkOperator", spec, "randomWalk",
                    "windowSize", format(spec.tuning()))
                    .withAttribute("boundaryCondition", "reflecting");
            case DELTA_EXCHANGE -> parameterOperator(
                    "deltaExchange", spec, "deltaExchange",
                    "delta", format(spec.tuning()));
            case INTEGER_RANDOM_WALK -> parameterOperator(
                    "randomWalkIntegerOperator", spec, "randomWalk",
                    "windowSize", Integer.toString((int) spec.tuning()));
            case INTEGER_SWAP -> parameterOperator(
                    "swapOperator", spec, "swap",
                    "size", Integer.toString((int) spec.tuning()))
                    .withAttribute("autoOptimize", "false");
            case INTEGER_UNIFORM -> parameterOperator(
                    "uniformIntegerOperator", spec, "uniform",
                    "count", Integer.toString((int) spec.tuning()));
            case TREE_SCALE -> treeScaleOperator(spec, false);
            case TREE_ROOT_SCALE -> treeScaleOperator(spec, true);
            case TREE_UNIFORM_HEIGHT -> treeOperator(
                    "nodeHeightOperator", spec, "uniformNodeHeight");
            case TREE_SUBTREE_SLIDE -> treeOperator(
                    "subtreeSlide", spec, "subtreeSlide")
                    .withAttribute("size", format(spec.tuning()))
                    .withAttribute("gaussian", "true");
            case TREE_NARROW_EXCHANGE -> treeOperator(
                    "narrowExchange", spec, "narrowExchange");
            case TREE_WIDE_EXCHANGE -> treeOperator(
                    "wideExchange", spec, "wideExchange");
            case TREE_WILSON_BALDING -> treeOperator(
                    "wilsonBalding", spec, "wilsonBalding");
            case TREE_CLOCK_UP_DOWN -> treeClockUpDownOperator(spec);
        };
    }

    private XmlElement parameterOperator(
            String element,
            OperatorSpec spec,
            String suffix,
            String tuningName,
            String tuningValue
    ) {
        return XmlElement.element(element)
                .withId(parameterId(spec.parameter()) + "_" + suffix)
                .withAttribute(tuningName, tuningValue)
                .withAttribute("weight", format(spec.weight()))
                .withChild(parameterReference(spec.parameter()));
    }

    private XmlElement treeScaleOperator(OperatorSpec spec, boolean rootOnly) {
        String treeId = treeId(spec.tree());
        return XmlElement.element("scaleOperator")
                .withId(treeId + (rootOnly ? "_rootScale" : "_scale"))
                .withAttribute("scaleFactor", format(spec.tuning()))
                .withAttribute("weight", format(spec.weight()))
                .withAttribute("scaleAll", Boolean.toString(!rootOnly))
                .withAttribute("ignoreBounds", "true")
                .withChild(XmlElement.ref(
                        "parameter",
                        treeId + (rootOnly ? ".rootHeight" : ".allInternalNodeHeights")));
    }

    private XmlElement treeOperator(
            String element,
            OperatorSpec spec,
            String suffix
    ) {
        return XmlElement.element(element)
                .withId(treeId(spec.tree()) + "_" + suffix)
                .withAttribute("weight", format(spec.weight()))
                .withChild(treeReference(spec.tree()));
    }

    private XmlElement treeClockUpDownOperator(OperatorSpec spec) {
        String treeId = treeId(spec.tree());
        return XmlElement.element("upDownOperator")
                .withId(treeId + "_" + parameterId(spec.parameter()) + "_upDown")
                .withAttribute("scaleFactor", format(spec.tuning()))
                .withAttribute("weight", format(spec.weight()))
                .withChild(XmlElement.element("up")
                        .withChild(parameterReference(spec.parameter())))
                .withChild(XmlElement.element("down")
                        .withChild(XmlElement.ref(
                                "parameter", treeId + ".allInternalNodeHeights")));
    }

    private XmlElement parameterReference(Parameter parameter) {
        return XmlElement.ref("parameter", parameterId(parameter));
    }

    private XmlElement treeReference(TreeModel tree) {
        return XmlElement.ref("treeModel", treeId(tree));
    }

    private static String parameterId(Parameter parameter) {
        String id = parameter.getId();
        if (id == null || id.isBlank()) {
            id = parameter.getParameterName();
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize an operator for an unnamed parameter.");
        }
        return id;
    }

    private static String treeId(TreeModel tree) {
        String id = tree.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize an operator for an unnamed tree.");
        }
        return id;
    }

    private static String format(double value) {
        return Double.toString(value);
    }
}
