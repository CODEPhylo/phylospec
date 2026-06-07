package tiling.xml.builders;

import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.List;

public class StateParameterXmlBuilder {

    public XmlElement buildParameter(Parameter parameter) {
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

            if (Double.isFinite(lower)) {
                element =
                        element.withAttribute("lower", format(lower));
            }

            if (Double.isFinite(upper)) {
                element =
                        element.withAttribute("upper", format(upper));
            }
        }

        return element;
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
