package tiling.params;

import dr.inference.model.Parameter;

/**
 * Diversification rate {@code birthRate - deathRate} of BirthDeath(birthRate, deathRate), whose BEAST X
 * model takes the diversification rate and the turnover. The value follows the two rates when they are
 * sampled, and they are kept so the XML export can write the same expression.
 */
public class BeastXBirthMinusDeathRate extends BeastXDerivedScalarParameter {

    private final Parameter birthRate;
    private final Parameter deathRate;

    public BeastXBirthMinusDeathRate(Parameter birthRate, Parameter deathRate) {
        super(
                "birthMinusDeathRate",
                () -> birthRate.getParameterValue(0) - deathRate.getParameterValue(0),
                birthRate,
                deathRate
        );
        this.birthRate = birthRate;
        this.deathRate = deathRate;
    }

    public Parameter getBirthRate() {
        return this.birthRate;
    }

    public Parameter getDeathRate() {
        return this.deathRate;
    }
}
