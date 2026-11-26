package tiling.params;

import dr.inference.model.Statistic;
import org.phylospec.domain.Real;
import org.phylospec.types.RealScalar;

/**
 * Wraps a BEAST X statistic as a PhyloSpec real scalar.
 * This is useful to validate the parameter domain during tiling.
 */
public class BeastXStatisticRealScalar<D extends Real> implements RealScalar<D> {

    private final Statistic statistic;
    private final D domain;
    private final boolean sampled;

    /**
     * @param sampled whether the statistic is computed from a sampled tree, so that its value
     *                changes during MCMC (false for a fixed or observed tree)
     */
    public BeastXStatisticRealScalar(
            Statistic statistic,
            D domain,
            boolean sampled
    ) {
        this.statistic = statistic;
        this.domain = domain;
        this.sampled = sampled;
    }

    public Statistic getStatistic() {
        return this.statistic;
    }

    public boolean isSampled() {
        return this.sampled;
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
