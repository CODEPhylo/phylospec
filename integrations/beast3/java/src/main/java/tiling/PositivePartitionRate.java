package tiling;

import beast.base.core.Input;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;

/**
 * A live view of a PhyloCTMC rate for BEAST's strictly positive SiteModel mutation rate.
 *
 * <p>The initial rate is checked. During MCMC the rate is passed on unchecked, so a proposal that
 * reaches a rate of 0 gives a zero likelihood and is rejected instead of ending the run.
 */
public final class PositivePartitionRate extends CalculationNode implements RealScalar<PositiveReal> {
    public final Input<RealScalar<? extends NonNegativeReal>> rateInput =
            new Input<>("rate", "Partition rate supplied to PhyloCTMC.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        double rate = get();
        if (!(rate > 0.0) || !Double.isFinite(rate)) {
            throw new IllegalArgumentException(
                    "The BEAST 3 backend requires a finite, strictly positive PhyloCTMC rate; received " + rate + ".");
        }
    }

    @Override
    public double get() {
        return rateInput.get().get();
    }

    @Override
    public PositiveReal getDomain() {
        return PositiveReal.INSTANCE;
    }
}
