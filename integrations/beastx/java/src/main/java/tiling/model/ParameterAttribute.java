package tiling.model;

import dr.inference.model.Parameter;
import dr.util.Attribute;

public class ParameterAttribute implements Attribute<double[]> {

    private final Parameter parameter;

    public ParameterAttribute(Parameter parameter) {
        this.parameter = parameter;
    }

    @Override
    public String getAttributeName() {
        return this.parameter.getParameterName();
    }

    @Override
    public double[] getAttributeValue() {
        return this.parameter.getParameterValues();
    }
}
