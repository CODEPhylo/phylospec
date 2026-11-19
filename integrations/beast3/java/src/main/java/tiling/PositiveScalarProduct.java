package tiling;

import beast.base.core.Input;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;

/** A read-only product. Both factors stay connected to the BEAST calculation graph. */
public final class PositiveScalarProduct extends CalculationNode implements RealScalar<PositiveReal> {
    public final Input<RealScalar<? extends PositiveReal>> leftInput =
            new Input<>("left", "Left positive factor.", Input.Validate.REQUIRED);
    public final Input<RealScalar<? extends PositiveReal>> rightInput =
            new Input<>("right", "Right positive factor.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        // Both inputs are required; there is no state or cached product to initialize.
    }

    @Override
    public double get() {
        return leftInput.get().get() * rightInput.get().get();
    }

    @Override
    public PositiveReal getDomain() {
        return PositiveReal.INSTANCE;
    }
}
