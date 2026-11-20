package tiling.model;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.inference.model.TruncatedDistributionLikelihood;

/**
 * A BEAST X {@link TruncatedDistributionLikelihood} that also keeps its bounds.
 *
 * <p>BEAST X does not expose the bounds of a truncated likelihood, but the XML export needs
 * them to write the truncation. The bounds are fixed values.</p>
 */
public class BeastXTruncatedLikelihood extends TruncatedDistributionLikelihood {

    private final double lower;
    private final double upper;

    public BeastXTruncatedLikelihood(
            DistributionLikelihood base,
            double lower,
            double upper
    ) {
        super(base, new Parameter.Default(lower), new Parameter.Default(upper));
        this.lower = lower;
        this.upper = upper;
    }

    public double getLower() {
        return lower;
    }

    public double getUpper() {
        return upper;
    }
}
