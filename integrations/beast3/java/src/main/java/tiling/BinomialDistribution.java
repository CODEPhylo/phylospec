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

/** A binomial count with a fixed number of trials and a live success probability. */
public final class BinomialDistribution extends ScalarDistribution<IntScalar<NonNegativeInt>, Integer> {

    public final Input<Integer> numTrialsInput =
            new Input<>("numTrials", "Fixed nonnegative number of trials.", Input.Validate.REQUIRED);
    public final Input<RealScalar<? extends UnitInterval>> pInput =
            new Input<>("p", "Probability of success in each trial.", Input.Validate.REQUIRED);

    private org.apache.commons.statistics.distribution.BinomialDistribution distribution;

    @Override
    public void initAndValidate() {
        refresh();
        param = paramInput.get();
        // As for Categorical, a typed observation outside the distribution's support
        // is legal data with zero probability, not an initialization error.
        if (param != null && !param.getDomain().isValid(param.get())) {
            throw new IllegalArgumentException("The binomial parameter must satisfy its integer domain.");
        }
    }

    @Override
    public void refresh() {
        Integer numTrials = numTrialsInput.get();
        if (numTrials == null || numTrials < 0) {
            throw new IllegalArgumentException("Binomial numTrials must be a fixed nonnegative integer.");
        }
        if (distribution != null && numTrials != distribution.getNumberOfTrials()) {
            throw new IllegalArgumentException("Binomial numTrials must remain fixed.");
        }
        if (pInput.get() == null) {
            throw new IllegalArgumentException("Binomial requires a success probability.");
        }
        double p = pInput.get().get();
        if (!Double.isFinite(p) || p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Binomial p must be finite and in [0, 1].");
        }
        // Check the current input on every use, including after state restoration.
        if (distribution == null || p != distribution.getProbabilityOfSuccess()) {
            distribution = org.apache.commons.statistics.distribution.BinomialDistribution.of(numTrials, p);
        }
    }

    @Override
    protected org.apache.commons.statistics.distribution.BinomialDistribution getApacheDistribution() {
        refresh();
        return distribution;
    }

    /** A mode gives a valid initial count, including degenerate endpoint cases. */
    public int mostLikelyCount() {
        var current = getApacheDistribution();
        return (int) Math.min(
                current.getNumberOfTrials(),
                Math.floor((current.getNumberOfTrials() + 1.0) * current.getProbabilityOfSuccess()));
    }

    @Override
    public double density(double count) {
        return Math.exp(logDensity(count));
    }

    @Override
    public double logDensity(double count) {
        var current = getApacheDistribution();
        if (!Double.isFinite(count)
                || count != Math.rint(count)
                || count < 0.0
                || count > current.getNumberOfTrials()) {
            return Double.NEGATIVE_INFINITY;
        }
        return current.logProbability((int) count);
    }

    @Override
    public double cumulativeProbability(double count) {
        var current = getApacheDistribution();
        if (Double.isNaN(count)) {
            throw new IllegalArgumentException("A binomial CDF argument must not be NaN.");
        }
        if (count < 0.0) return 0.0;
        if (count >= current.getNumberOfTrials()) return 1.0;
        return current.cumulativeProbability((int) count);
    }

    @Override
    public Integer inverseCumulativeProbability(double probability) {
        if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("A binomial quantile probability must be in [0, 1].");
        }
        return getApacheDistribution().inverseCumulativeProbability(probability);
    }

    @Override
    public double getMean() {
        return getApacheDistribution().getMean();
    }

    @Override
    public List<Integer> sample() {
        // Use BEAST's seeded RNG and refresh p before drawing; do not retain a stale sampler.
        return List.of(getApacheDistribution().inverseCumulativeProbability(Randomizer.nextDouble()));
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

    // Keep the parameter's proposal bounds stable as p changes to or from 0 and 1.
    // Impossible counts at those endpoints receive zero probability instead.
    @Override
    public Integer getLowerBoundOfParameter() {
        return 0;
    }

    @Override
    public Integer getUpperBoundOfParameter() {
        return getApacheDistribution().getNumberOfTrials();
    }

    @Override
    public Double getLower() {
        return 0.0;
    }

    @Override
    public Double getUpper() {
        return getUpperBoundOfParameter().doubleValue();
    }

    @Override
    public boolean lowerInclusive() {
        return true;
    }

    @Override
    public boolean upperInclusive() {
        return true;
    }
}
