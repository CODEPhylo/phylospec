package tiling.xml.builders;

import dr.inference.distribution.MultivariateDistributionLikelihood;
import dr.inference.model.Parameter;
import dr.math.distributions.DirichletDistribution;
import tiling.xml.XmlElement;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class DirichletPriorXmlBuilder {

    public XmlElement buildPrior(
            Parameter parameter,
            MultivariateDistributionLikelihood likelihood,
            DirichletDistribution distribution
    ) {
        String priorId =
                likelihood.getId();

        return XmlElement.element("dirichletParameterPrior")
                .withId(priorId)
                .withAttribute("sumToNumberOfElements", Boolean.toString(sumToNumberOfElements(distribution)))
                .withChild(
                        XmlElement.element("countsParameter")
                                .withChild(
                                        inlineVectorParameterDefinition(
                                                priorId + "_counts",
                                                counts(distribution),
                                                0.0,
                                                null
                                        )
                                )
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
    }

    public double[] counts(DirichletDistribution distribution) {
        return ((double[]) readPrivateField(distribution, "counts")).clone();
    }

    private boolean sumToNumberOfElements(DirichletDistribution distribution) {
        return (boolean) readPrivateField(distribution, "sumToNumberOfElements");
    }

    private XmlElement inlineVectorParameterDefinition(
            String id,
            double[] values,
            Double lower,
            Double upper
    ) {
        XmlElement element =
                XmlElement.element("parameter")
                        .withId(id)
                        .withAttribute("value", formatValues(values));

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

    private static String formatValues(double[] values) {
        List<String> formatted =
                new ArrayList<>();

        for (double value : values) {
            formatted.add(format(value));
        }

        return String.join(" ", formatted);
    }

    private static Object readPrivateField(Object object, String fieldName) {
        try {
            Field field =
                    object.getClass().getDeclaredField(fieldName);

            field.setAccessible(true);

            return field.get(object);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not read BEAST X field '" + fieldName + "' from " + object.getClass().getName() + ".",
                    exception
            );
        }
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