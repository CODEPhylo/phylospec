package tiling;

import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.inference.model.VariableListener;

import java.util.function.DoubleSupplier;

public class BeastXDerivedScalarParameter extends Parameter.Abstract implements VariableListener {

    private final String id;
    private final DoubleSupplier valueSupplier;
    private Bounds<Double> bounds =
            new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1);

    public BeastXDerivedScalarParameter(
            String id,
            DoubleSupplier valueSupplier,
            Parameter... dependencies
    ) {
        super(id);
        this.id = id;
        this.valueSupplier = valueSupplier;

        for (Parameter dependency : dependencies) {
            dependency.addParameterListener(this);
        }
    }

    @Override
    public int getDimension() {
        return 1;
    }

    @Override
    public double getParameterValue(int dim) {
        if (dim != 0) {
            throw new IndexOutOfBoundsException("Derived scalar parameter only has dimension 0.");
        }

        return valueSupplier.getAsDouble();
    }

    @Override
    public String getParameterName() {
        return id;
    }

    @Override
    public void setParameterValue(int dim, double value) {
        throw new UnsupportedOperationException("Derived scalar parameters cannot be set directly.");
    }

    @Override
    public void setParameterValueQuietly(int dim, double value) {
        throw new UnsupportedOperationException("Derived scalar parameters cannot be set directly.");
    }

    @Override
    public void setParameterValueNotifyChangedAll(int dim, double value) {
        throw new UnsupportedOperationException("Derived scalar parameters cannot be set directly.");
    }

    @Override
    public void setDimension(int dimension) {
        if (dimension != 1) {
            throw new UnsupportedOperationException("Derived scalar parameters must have dimension 1.");
        }
    }

    @Override
    public void addBounds(Bounds<Double> bounds) {
        this.bounds = bounds;
    }

    @Override
    public Bounds<Double> getBounds() {
        return bounds;
    }

    @Override
    public void addDimension(int index, double value) {
        throw new UnsupportedOperationException("Derived scalar parameters cannot change dimension.");
    }

    @Override
    public double removeDimension(int index) {
        throw new UnsupportedOperationException("Derived scalar parameters cannot change dimension.");
    }

    @Override
    protected void storeValues() {
        // Value is derived from dependency parameters.
    }

    @Override
    protected void restoreValues() {
        // Value is derived from dependency parameters.
    }

    @Override
    protected void acceptValues() {
        // Value is derived from dependency parameters.
    }

    @Override
    protected void adoptValues(Parameter source) {
        throw new UnsupportedOperationException("Derived scalar parameters cannot adopt values.");
    }

    @Override
    public void variableChangedEvent(
            Variable variable,
            int index,
            Variable.ChangeType type
    ) {
        fireParameterChangedEvent();
    }
}