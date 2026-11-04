package tiling;

import beast.base.core.Input;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import java.util.ArrayList;
import java.util.List;

/** A fixed-length, read-only vector that retains its scalar dependencies. */
public final class RealVectorFromScalars extends CalculationNode implements RealVector<Real> {

    public final Input<List<RealScalar<? extends Real>>> valuesInput = new Input<>(
            "values", "Scalar elements read from the current state.", new ArrayList<>(), Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (valuesInput.get().isEmpty() || valuesInput.get().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("A nonempty list of real scalar elements is required.");
        }
    }

    @Override
    public int size() {
        return valuesInput.get().size();
    }

    @Override
    public double get(int index) {
        return valuesInput.get().get(index).get();
    }

    @Override
    public List<Double> getElements() {
        return valuesInput.get().stream().map(RealScalar::get).toList();
    }

    @Override
    public Real getDomain() {
        return Real.INSTANCE;
    }
}
