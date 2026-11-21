package tiling.params;

import dr.inference.model.Parameter;

/**
 * Mean {@code 1 / rate} of PhyloSpec's {@code Exponential(rate)}.
 *
 * <p>BEAST X's exponential distribution takes the mean, so the value follows {@code rate} when it
 * is sampled. The rate is kept so the XML export can write the same expression.</p>
 */
public class BeastXExponentialMean extends BeastXDerivedScalarParameter {

    private final Parameter rate;

    public BeastXExponentialMean(Parameter rate) {
        super("mean", () -> 1.0 / rate.getParameterValue(0), rate);
        this.rate = rate;
    }

    public Parameter getRate() {
        return this.rate;
    }
}
