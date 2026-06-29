package tiling.params;

import dr.inference.model.Statistic;
import org.phylospec.domain.Real;
import org.phylospec.types.RealScalar;

public class BeastXStatisticRealScalar<D extends Real> implements RealScalar<D> {

    private final Statistic statistic;
    private final D domain;

    public BeastXStatisticRealScalar(
            Statistic statistic,
            D domain
    ) {
        this.statistic = statistic;
        this.domain = domain;
    }

    public Statistic getStatistic() {
        return this.statistic;
    }

    @Override
    public double get() {
        return this.statistic.getStatisticValue(0);
    }

    @Override
    public Double get(int... idx) {
        if (idx.length != 0) {
            throw new IllegalArgumentException("RealScalar does not take indices.");
        }

        return get();
    }

    @Override
    public long size() {
        return 1;
    }

    @Override
    public D domainType() {
        return this.domain;
    }
}