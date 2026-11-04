package tiling;

import beast.base.core.Input;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;

/** Read-only conversion from birth/death rates to the native Gernhard08 parameterization. */
public final class BirthDeathRateValue extends CalculationNode implements RealScalar<PositiveReal> {

    public final Input<RealScalar<? extends PositiveReal>> birthRateInput =
            new Input<>("birthRate", "Birth rate per lineage.", Input.Validate.REQUIRED);
    public final Input<RealScalar<? extends PositiveReal>> deathRateInput =
            new Input<>("deathRate", "Death rate per lineage.", Input.Validate.REQUIRED);
    public final Input<Boolean> turnoverInput =
            new Input<>("turnover", "Return death/birth instead of birth-death.", false);

    @Override
    public void initAndValidate() {
        get();
    }

    @Override
    public double get() {
        double birth = birthRateInput.get().get();
        double death = deathRateInput.get().get();
        if (!Double.isFinite(birth) || !Double.isFinite(death) || birth <= 0.0 || death <= 0.0) {
            throw new IllegalArgumentException("BirthDeath requires finite positive birthRate and deathRate.");
        }
        if (birth <= death) {
            // This region is allowed by core, but is outside the native model's supported range.
            // Returning -Infinity would silently impose a new constraint on the user's posterior.
            throw new IllegalArgumentException(
                    "The current BEAST 3 BirthDeath(birthRate, deathRate) implementation requires birthRate > deathRate; "
                            + "birthRate=" + birth + ", deathRate=" + death + ". "
                            + "The unsupported region is not treated as zero probability.");
        }
        double value = turnoverInput.get() ? death / birth : birth - death;
        if (!(value > 0.0) || !Double.isFinite(value) || (turnoverInput.get() && value >= 1.0)) {
            throw new IllegalArgumentException(
                    "The converted BirthDeath rate cannot be represented accurately in the native model's range.");
        }
        // Reading the inputs on every access also sees values restored after a rejected proposal.
        return value;
    }

    @Override
    public PositiveReal getDomain() {
        return PositiveReal.INSTANCE;
    }
}
