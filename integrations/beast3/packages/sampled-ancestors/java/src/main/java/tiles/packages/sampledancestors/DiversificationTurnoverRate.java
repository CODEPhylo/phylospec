package tiles.packages.sampledancestors;

import beast.base.core.Input;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.type.RealScalar;

/** A birth or death rate derived from net diversification and turnover. */
final class DiversificationTurnoverRate<D extends Real> extends CalculationNode
        implements RealScalar<D> {

    enum Kind {
        BIRTH,
        DEATH
    }

    final Input<RealScalar<? extends PositiveReal>> diversificationRateInput =
            new Input<>(
                    "diversificationRate",
                    "net diversification rate",
                    Input.Validate.REQUIRED,
                    RealScalar.class
            );
    final Input<RealScalar<UnitInterval>> turnoverInput =
            new Input<>(
                    "turnover",
                    "death-to-birth rate ratio",
                    Input.Validate.REQUIRED,
                    RealScalar.class
            );

    private final Kind kind;
    private final D domain;

    DiversificationTurnoverRate(Kind kind, D domain) {
        this.kind = kind;
        this.domain = domain;
    }

    @Override
    public void initAndValidate() {
        validateTurnover(turnoverInput.get().get());
    }

    @Override
    public D getDomain() {
        return domain;
    }

    @Override
    public double get() {
        double turnover = turnoverInput.get().get();
        validateTurnover(turnover);

        double birthRate = diversificationRateInput.get().get() / (1.0 - turnover);
        return kind == Kind.BIRTH ? birthRate : birthRate * turnover;
    }

    private static void validateTurnover(double turnover) {
        if (turnover < 0.0 || turnover >= 1.0) {
            throw new IllegalArgumentException("FossilizedBirthDeath turnover must be in [0, 1).");
        }
    }

    static DiversificationTurnoverRate<PositiveReal> birthRate() {
        return new DiversificationTurnoverRate<>(Kind.BIRTH, PositiveReal.INSTANCE);
    }

    static DiversificationTurnoverRate<NonNegativeReal> deathRate() {
        return new DiversificationTurnoverRate<>(Kind.DEATH, NonNegativeReal.INSTANCE);
    }
}
