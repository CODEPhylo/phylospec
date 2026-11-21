package tiling.params;

import dr.inference.model.Parameter;

/**
 * Death rate {@code turnover * diversificationRate / (1 - turnover)}, the birth rate times the
 * turnover, of {@code FossilizedBirthDeath(diversificationRate, turnover)}. The value follows the
 * two parameters when they are sampled, and they are kept so the XML export can write the same
 * expression.
 */
public class BeastXDeathRate extends BeastXDerivedScalarParameter {

    private final Parameter diversificationRate;
    private final Parameter turnover;

    public BeastXDeathRate(Parameter diversificationRate, Parameter turnover) {
        super(
                "deathRate",
                () -> {
                    double turnoverValue = BeastXBirthRate.validTurnover(turnover);
                    return turnoverValue * diversificationRate.getParameterValue(0) / (1.0 - turnoverValue);
                },
                diversificationRate,
                turnover
        );
        this.diversificationRate = diversificationRate;
        this.turnover = turnover;
    }

    public Parameter getDiversificationRate() {
        return this.diversificationRate;
    }

    public Parameter getTurnover() {
        return this.turnover;
    }
}
