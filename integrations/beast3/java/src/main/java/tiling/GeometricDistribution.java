package tiling;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import java.util.List;

/** Number of failures before the first success, with a live success probability. */
public final class GeometricDistribution extends ScalarDistribution<IntScalar<NonNegativeInt>, Integer> {

    public final Input<RealScalar<? extends UnitInterval>> pInput =
            new Input<>("p", "Probability of success, finite and in (0, 1].", Input.Validate.REQUIRED);

    private org.apache.commons.statistics.distribution.GeometricDistribution distribution;

    @Override
    public void initAndValidate() {
        refresh();
        param = paramInput.get();
        // At p=1 a positive observation has zero mass but is still valid integer data.
        if (param != null && !param.getDomain().isValid(param.get())) {
            throw new IllegalArgumentException("The geometric parameter must satisfy its integer domain.");
        }
    }

    @Override
    public void refresh() {
        if (pInput.get() == null || !isProbability(pInput.get().get())) {
            throw new IllegalArgumentException("Geometric p must be finite and in (0, 1].");
        }
        double p = pInput.get().get();
        // Compare exactly: even a tiny probability update must be visible after restoration.
        if (distribution == null || p != distribution.getProbabilityOfSuccess()) {
            distribution = org.apache.commons.statistics.distribution.GeometricDistribution.of(p);
        }
    }

    @Override
    protected org.apache.commons.statistics.distribution.GeometricDistribution getApacheDistribution() {
        refresh();
        return distribution;
    }

    /** Zero is a mode with positive probability for every valid p. */
    public int mostLikelyCount() {
        refresh();
        return 0;
    }

    @Override
    protected double calcLogP(Integer count) {
        return count == null ? Double.NEGATIVE_INFINITY : logDensity(count);
    }

    @Override
    public double density(double count) {
        return Math.exp(logDensity(count));
    }

    @Override
    public double logDensity(double count) {
        // A proposal to p=0 has no finite-count probability; reject it without aborting MCMC.
        if (pInput.get() == null
                || !isProbability(pInput.get().get())
                || !Double.isFinite(count)
                || count != Math.rint(count)
                || count < 0.0) {
            return Double.NEGATIVE_INFINITY;
        }
        var current = getApacheDistribution();
        if (count <= Integer.MAX_VALUE) return current.logProbability((int) count);
        // Scalar states use Java int, but the mathematical mass function has no finite cutoff.
        double p = current.getProbabilityOfSuccess();
        return Math.log(p) + count * Math.log1p(-p);
    }

    @Override
    public double cumulativeProbability(double count) {
        var current = getApacheDistribution();
        if (Double.isNaN(count)) {
            throw new IllegalArgumentException("A geometric CDF argument must not be NaN.");
        }
        if (count < 0.0) return 0.0;
        if (count == Double.POSITIVE_INFINITY) return 1.0;
        if (count <= Integer.MAX_VALUE) return current.cumulativeProbability((int) count);
        // The mathematical distribution has no finite upper cutoff; avoid an overflowing cast.
        return -Math.expm1(Math.log1p(-current.getProbabilityOfSuccess()) * (Math.floor(count) + 1.0));
    }

    @Override
    public Integer inverseCumulativeProbability(double probability) {
        if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("A geometric quantile probability must be in [0, 1].");
        }
        var current = getApacheDistribution();
        if (current.getProbabilityOfSuccess() == 1.0) return 0;
        if (probability == 1.0 || probability > current.cumulativeProbability(Integer.MAX_VALUE)) {
            // Apache saturates to Integer.MAX_VALUE here. Do not turn the unrepresented tail into a point mass.
            throw new IllegalArgumentException("The geometric quantile exceeds the supported integer range.");
        }
        return current.inverseCumulativeProbability(probability);
    }

    @Override
    public double getMean() {
        return getApacheDistribution().getMean();
    }

    @Override
    public List<Integer> sample() {
        refresh();
        return List.of(inverseCumulativeProbability(Randomizer.nextDouble()));
    }

    @Override
    public List<String> getConditions() {
        if (pInput.get() instanceof BEASTInterface probability && probability.getID() != null) {
            return List.of(probability.getID());
        }
        return List.of();
    }

    @Override
    public boolean isIntegerDistribution() {
        return true;
    }

    // Keep proposal bounds stable when p reaches or leaves the degenerate endpoint p=1.
    @Override
    public Integer getLowerBoundOfParameter() {
        return 0;
    }

    @Override
    public Integer getUpperBoundOfParameter() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Double getLower() {
        return 0.0;
    }

    @Override
    public Double getUpper() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean lowerInclusive() {
        return true;
    }

    @Override
    public boolean upperInclusive() {
        return false;
    }

    private static boolean isProbability(double value) {
        return Double.isFinite(value) && value > 0.0 && value <= 1.0;
    }
}
