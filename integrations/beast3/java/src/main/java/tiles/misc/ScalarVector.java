package tiles.misc;

import beast.base.core.Input;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import java.util.ArrayList;
import java.util.List;

/** Read-only, live vector of scalars; its elements own all state and priors. */
public final class ScalarVector<D extends Real> extends CalculationNode implements RealVector<D> {
    public final Input<List<RealScalar<D>>> valuesInput =
            new Input<>("values", "Scalars in vector order", new ArrayList<>(), Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (valuesInput.get().isEmpty()) throw new IllegalArgumentException("A scalar vector must not be empty.");
    }

    @Override
    public double get(int index) {
        return valuesInput.get().get(index).get();
    }

    @Override
    public int size() {
        return valuesInput.get().size();
    }

    @Override
    public List<Double> getElements() {
        return valuesInput.get().stream().map(RealScalar::get).toList();
    }

    @Override
    public D getDomain() {
        return valuesInput.get().getFirst().getDomain();
    }
}
