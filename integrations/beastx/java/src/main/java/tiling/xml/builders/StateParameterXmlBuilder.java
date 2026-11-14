package tiling.xml.builders;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.UniformDistributionModel;
import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import tiling.BeastXState;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds XML parameter definitions for BEAST X MCMC state nodes.
 */
public class StateParameterXmlBuilder {

    public XmlElement buildParameter(BeastXState state, Parameter parameter) {
        XmlElement element =
                XmlElement.element("parameter")
                        .withId(parameterId(parameter))
                        .withAttribute("value", parameterValues(parameter));

        Bounds<Double> bounds =
                parameter.getBounds();

        if (bounds != null) {
            double lower =
                    bounds.getLowerLimit(0);

            double upper =
                    bounds.getUpperLimit(0);

            if (Double.isFinite(lower) && !isSampledUniformBound(state, parameter, 0)) {
                element =
                        element.withAttribute("lower", format(lower));
            }

            if (Double.isFinite(upper) && !isSampledUniformBound(state, parameter, 1)) {
                element =
                        element.withAttribute("upper", format(upper));
            }
        }

        return element;
    }

    /**
     * Returns whether the given bound (0 = lower, 1 = upper) comes from a Uniform prior whose
     * limit is itself sampled. XML can only store fixed bounds, so such a bound is left out;
     * the Uniform prior density still rejects values outside the current limits.
     */
    private static boolean isSampledUniformBound(BeastXState state, Parameter parameter, int boundIndex) {
        if (state.priorDistributions.get(parameter) instanceof DistributionLikelihood likelihood
                && likelihood.getDistribution() instanceof UniformDistributionModel uniform) {
            return uniform.getVariable(boundIndex) instanceof Parameter bound
                    && state.stateNodes.containsKey(bound);
        }

        return false;
    }

    private static String parameterValues(Parameter parameter) {
        List<String> values =
                new ArrayList<>();

        for (int i = 0; i < parameter.getDimension(); i++) {
            values.add(format(parameter.getParameterValue(i)));
        }

        return String.join(" ", values);
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
