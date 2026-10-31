package tiling;

import beast.base.core.Input;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;

/** A live, read-only reciprocal of a positive rate. */
public final class ReciprocalValue extends CalculationNode implements RealScalar<PositiveReal> {

    public final Input<RealScalar<PositiveReal>> rateInput =
            new Input<>("rate", "Positive rate whose reciprocal is returned.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (rateInput.get() == null) {
            throw new IllegalArgumentException("A positive rate is required.");
        }
    }

    @Override
    public double get() {
        return 1.0 / rateInput.get().get();
    }

    @Override
    public PositiveReal getDomain() {
        return PositiveReal.INSTANCE;
    }
}
