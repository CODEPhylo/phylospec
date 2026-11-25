package tiling.xml.builders;

import dr.evomodel.tree.TMRCAStatistic;
import dr.evomodel.tree.TreeHeightStatistic;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import tiling.BeastXState;
import tiling.rpn.BeastXRPNStatistic;
import tiling.rpn.BeastXSumStatistic;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes values computed from other values, such as {@code Real s = sum(vector=v)} or
 * {@code Real m = 2.0 * r}, as BEAST X RPNcalculator and sumStatistic elements, so that the XML
 * computes them from the sampled values, as in memory.
 */
public class DerivedValueXmlBuilder {

    private final CalibrationPriorXmlBuilder calibrationPriorXmlBuilder =
            new CalibrationPriorXmlBuilder();

    /**
     * The definitions of a computed value and of the values it reads, each before it is used.
     * Statistics whose id is in {@code emittedIds} are already defined and only referenced.
     */
    public List<XmlElement> buildDefinitions(
            BeastXState state,
            BeastXRPNStatistic statistic,
            Set<String> emittedIds
    ) {
        List<XmlElement> definitions = new ArrayList<>();
        define(state, statistic, emittedIds, definitions);
        return definitions;
    }

    /** Adds the definition of a statistic after those it reads, and returns a reference to it. */
    private XmlElement define(
            BeastXState state,
            Statistic statistic,
            Set<String> emittedIds,
            List<XmlElement> definitions
    ) {
        if (statistic instanceof Parameter parameter && state.stateNodes.containsKey(parameter)) {
            return XmlElement.ref("parameter", parameter.getId());
        }

        String id = statistic.getId();
        XmlElement reference = XmlElement.ref(elementName(statistic), id);

        if (emittedIds.add(id)) {
            definitions.add(definition(state, statistic, emittedIds, definitions));
        }

        return reference;
    }

    private XmlElement definition(
            BeastXState state,
            Statistic statistic,
            Set<String> emittedIds,
            List<XmlElement> definitions
    ) {
        String id = statistic.getId();

        if (statistic instanceof BeastXRPNStatistic rpn) {
            XmlElement element = XmlElement.element("RPNcalculator").withId(id);

            for (Map.Entry<String, Statistic> variable : rpn.getVariables().entrySet()) {
                element = element.withChild(
                        XmlElement.element("variable")
                                .withAttribute("name", variable.getKey())
                                .withChild(define(state, variable.getValue(), emittedIds, definitions))
                );
            }

            return element.withChild(
                    XmlElement.element("expression")
                            .withAttribute("name", rpn.getName())
                            .withText(rpn.getExpression())
            );
        }

        if (statistic instanceof BeastXSumStatistic sum) {
            return XmlElement.element("sumStatistic")
                    .withId(id)
                    .withAttribute("elementwise", "true")
                    .withChild(vector(state, sum, id + "_vector"));
        }

        if (statistic instanceof TreeHeightStatistic height) {
            return XmlElement.element("treeHeightStatistic")
                    .withId(id)
                    .withAttribute("name", id)
                    .withChild(XmlElement.ref("treeModel", ((TreeModel) height.getTree()).getId()));
        }

        if (statistic instanceof TMRCAStatistic mrca) {
            return calibrationPriorXmlBuilder.tmrcaStatistic(id, id, mrca);
        }

        // A fixed number in the expression, such as 2.0 in 2.0 * r.
        return constant(id, (Parameter) statistic);
    }

    /** The vector a sum adds up: a reference when it is sampled, its values when it is fixed. */
    private XmlElement vector(BeastXState state, BeastXSumStatistic sum, String inlineId) {
        Statistic vector = sum.getVector();

        if (vector instanceof Parameter parameter && state.stateNodes.containsKey(parameter)) {
            return XmlElement.ref("parameter", parameter.getId());
        }

        if (isConstant(vector)) {
            return constant(inlineId, (Parameter) vector);
        }

        throw unsupported(vector);
    }

    private static String elementName(Statistic statistic) {
        if (statistic instanceof BeastXRPNStatistic) {
            return "RPNcalculator";
        }

        if (statistic instanceof BeastXSumStatistic) {
            return "sumStatistic";
        }

        if (statistic instanceof TreeHeightStatistic) {
            return "treeHeightStatistic";
        }

        if (statistic instanceof TMRCAStatistic) {
            return "tmrcaStatistic";
        }

        if (isConstant(statistic)) {
            return "parameter";
        }

        throw unsupported(statistic);
    }

    /** A plain parameter that is not sampled holds fixed values. */
    private static boolean isConstant(Statistic statistic) {
        return statistic.getClass() == Parameter.Default.class;
    }

    private static XmlElement constant(String id, Parameter parameter) {
        List<String> values = new ArrayList<>();

        for (int i = 0; i < parameter.getDimension(); i++) {
            values.add(Double.toString(parameter.getParameterValue(i)));
        }

        return XmlElement.element("parameter")
                .withId(id)
                .withAttribute("value", String.join(" ", values));
    }

    private static RuntimeException unsupported(Statistic statistic) {
        return new UnsupportedOperationException(
                "A computed value reads a " + statistic.getClass().getSimpleName()
                        + ", which cannot be written to BEAST X XML yet."
        );
    }
}
