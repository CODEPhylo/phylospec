package tiling.params;

import dr.inference.model.Parameter;

/**
 * Birth rate {@code diversificationRate / (1 - turnover)} of
 * {@code FossilizedBirthDeath(diversificationRate, turnover)}, whose BEAST X model takes the birth
 * and death rates. The value follows the two parameters when they are sampled, and they are kept so
 * the XML export can write the same expression.
 */
public class BeastXBirthRate extends BeastXDerivedScalarParameter {

    private final Parameter diversificationRate;
    private final Parameter turnover;

    public BeastXBirthRate(Parameter diversificationRate, Parameter turnover) {
        super(
                "birthRate",
                () -> diversificationRate.getParameterValue(0) / (1.0 - validTurnover(turnover)),
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

    static double validTurnover(Parameter turnover) {
        double value = turnover.getParameterValue(0);
        if (value < 0.0 || value >= 1.0) {
            throw new IllegalArgumentException("FossilizedBirthDeath turnover must be in [0, 1).");
        }
        return value;
    }
}
