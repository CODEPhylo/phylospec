package tiling;

import beast.base.core.Input;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.Int;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.IntVector;
import java.util.ArrayList;
import java.util.List;

/** A fixed-length, read-only vector that retains its scalar dependencies. */
public final class IntVectorFromScalars extends CalculationNode implements IntVector<Int> {

    public final Input<List<IntScalar<? extends Int>>> valuesInput = new Input<>(
            "values", "Scalar elements read from the current state.", new ArrayList<>(), Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (valuesInput.get().isEmpty() || valuesInput.get().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("A nonempty list of integer scalar elements is required.");
        }
    }

    @Override
    public int size() {
        return valuesInput.get().size();
    }

    @Override
    public int get(int index) {
        return valuesInput.get().get(index).get();
    }

    @Override
    public List<Integer> getElements() {
        return valuesInput.get().stream().map(IntScalar::get).toList();
    }

    @Override
    public Int getDomain() {
        return Int.INSTANCE;
    }
}
