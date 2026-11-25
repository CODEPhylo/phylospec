package tiling.rpn;

import dr.inference.model.RPNcalculatorStatistic;
import dr.inference.model.Statistic;

import java.util.Map;

/**
 * A BEAST X RPN calculator for one PhyloSpec value. BEAST X does not expose the expression and the
 * variables of an RPN calculator, so they are kept here for XML export.
 */
public class BeastXRPNStatistic extends RPNcalculatorStatistic {

    private final String name;
    private final String expression;
    private final Map<String, Statistic> variables;

    public BeastXRPNStatistic(String name, String expression, Map<String, Statistic> variables) {
        super(name, new String[]{expression}, new String[]{name}, variables);
        this.name = name;
        this.expression = expression;
        this.variables = variables;
    }

    /** The name of the computed value, which is also its column label in logs. */
    public String getName() {
        return this.name;
    }

    public String getExpression() {
        return this.expression;
    }

    /** The statistics the expression reads, by the name used in the expression. */
    public Map<String, Statistic> getVariables() {
        return this.variables;
    }
}
