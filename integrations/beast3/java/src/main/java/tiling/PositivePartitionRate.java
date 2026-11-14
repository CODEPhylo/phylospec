package tiling;

import beast.base.core.Input;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;

/** A live, checked view for BEAST's strictly positive SiteModel mutation rate. */
public final class PositivePartitionRate extends CalculationNode implements RealScalar<PositiveReal> {
    public final Input<RealScalar<? extends NonNegativeReal>> rateInput =
            new Input<>("rate", "Partition rate supplied to PhyloCTMC.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        get();
    }

    @Override
    public double get() {
        double rate = rateInput.get().get();
        if (!(rate > 0.0) || !Double.isFinite(rate)) {
            throw new IllegalArgumentException(
                    "The BEAST 3 backend requires a finite, strictly positive PhyloCTMC rate; received " + rate + ".");
        }
        return rate;
    }

    @Override
    public PositiveReal getDomain() {
        return PositiveReal.INSTANCE;
    }
}
