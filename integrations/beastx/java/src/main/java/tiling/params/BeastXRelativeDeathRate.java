package tiling.params;

import dr.inference.model.Parameter;

/**
 * Turnover {@code deathRate / birthRate} of BirthDeath(birthRate, deathRate), whose BEAST X model takes the
 * diversification rate and the turnover. The value follows the two rates when they are sampled, and they
 * are kept so the XML export can write the same expression.
 */
public class BeastXRelativeDeathRate extends BeastXDerivedScalarParameter {

    private final Parameter birthRate;
    private final Parameter deathRate;

    public BeastXRelativeDeathRate(Parameter birthRate, Parameter deathRate) {
        super(
                "relativeDeathRate",
                () -> deathRate.getParameterValue(0) / birthRate.getParameterValue(0),
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
