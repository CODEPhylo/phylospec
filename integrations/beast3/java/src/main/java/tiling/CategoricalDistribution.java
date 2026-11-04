package tiling;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.Simplex;
import beast.base.util.Randomizer;
import java.util.List;

/** A categorical distribution over labels 1 through K, with live simplex probabilities. */
public final class CategoricalDistribution extends ScalarDistribution<IntScalar<PositiveInt>, Integer> {

    private static final double NORMALIZATION_TOLERANCE = 1e-10;

    public final Input<Simplex> probabilitiesInput =
            new Input<>("probabilities", "Probabilities of categories 1 through K.", Input.Validate.REQUIRED);

    private int categoryCount;

    @Override
    public void initAndValidate() {
        refresh();
        param = paramInput.get();
        // TensorDistribution also checks effective bounds here. An observed positive
        // integer outside 1..K is legal data with zero probability, so only validate
        // its declared domain and leave the support check to calcLogP.
        if (param != null && !param.getDomain().isValid(param.get())) {
            throw new IllegalArgumentException("The categorical parameter must satisfy its integer domain.");
        }
    }

    @Override
    public void refresh() {
        Simplex probabilities = probabilitiesInput.get();
        if (probabilities == null || probabilities.size() == 0) {
            throw new IllegalArgumentException("Categorical probabilities must be a nonempty Simplex.");
        }
        if (categoryCount != 0 && probabilities.size() != categoryCount) {
            throw new IllegalArgumentException("The number of categorical probabilities must remain fixed.");
        }

        double sum = 0.0;
        for (int i = 0; i < probabilities.size(); i++) {
            double probability = probabilities.get(i);
            if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
                throw new IllegalArgumentException("Categorical probabilities must be finite values in [0, 1].");
            }
            sum += probability;
        }
        if (Math.abs(sum - 1.0) > NORMALIZATION_TOLERANCE) {
            throw new IllegalArgumentException("Categorical probabilities must sum to 1.");
        }
        // Cache only the structural dimension, never the probabilities themselves.
        categoryCount = probabilities.size();
    }

    /** Returns the first category with maximal probability for a valid initial state. */
    public int mostLikelyCategory() {
        refresh();
        Simplex probabilities = probabilitiesInput.get();
        int best = 0;
        for (int i = 1; i < categoryCount; i++) {
            if (probabilities.get(i) > probabilities.get(best)) {
                best = i;
            }
        }
        return best + 1;
    }

    @Override
    protected double calcLogP(Integer category) {
        refresh();
        if (category == null || category < 1 || category > categoryCount) {
            return Double.NEGATIVE_INFINITY;
        }
        return Math.log(probabilitiesInput.get().get(category - 1));
    }

    @Override
    public double density(double category) {
        return Math.exp(logDensity(category));
    }

    @Override
    public double logDensity(double category) {
        refresh();
        if (!Double.isFinite(category)
                || category != Math.rint(category)
                || category < 1.0
                || category > categoryCount) {
            return Double.NEGATIVE_INFINITY;
        }
        return Math.log(probabilitiesInput.get().get((int) category - 1));
    }

    @Override
    public double cumulativeProbability(double category) {
        refresh();
        if (Double.isNaN(category)) {
            throw new IllegalArgumentException("A categorical CDF argument must not be NaN.");
        }
        if (category < 1.0) {
            return 0.0;
        }
        if (category >= categoryCount) {
            return 1.0;
        }
        double cumulative = 0.0;
        for (int i = 0; i < (int) category; i++) {
            cumulative += probabilitiesInput.get().get(i);
        }
        return Math.min(1.0, cumulative);
    }

    @Override
    public Integer inverseCumulativeProbability(double probability) {
        refresh();
        if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("A categorical quantile probability must be in [0, 1].");
        }
        // Follow ScalarDistribution's endpoint convention for the declared bounds.
        if (probability == 0.0) {
            return 1;
        }
        if (probability == 1.0) {
            return categoryCount;
        }
        double cumulative = 0.0;
        int lastPositive = 1;
        for (int i = 0; i < categoryCount; i++) {
            double mass = probabilitiesInput.get().get(i);
            if (mass > 0.0) {
                lastPositive = i + 1;
                cumulative += mass;
                if (cumulative >= probability) {
                    return i + 1;
                }
            }
        }
        return lastPositive;
    }

    @Override
    public double getMean() {
        refresh();
        double mean = 0.0;
        for (int i = 0; i < categoryCount; i++) {
            mean += (i + 1.0) * probabilitiesInput.get().get(i);
        }
        return mean;
    }

    @Override
    public List<Integer> sample() {
        refresh();
        double draw = Randomizer.nextDouble();
        double cumulative = 0.0;
        int lastPositive = 1;
        for (int i = 0; i < categoryCount; i++) {
            double mass = probabilitiesInput.get().get(i);
            if (mass > 0.0) {
                lastPositive = i + 1;
                cumulative += mass;
                if (draw < cumulative) {
                    return List.of(i + 1);
                }
            }
        }
        // A valid simplex can sum just below 1 because of floating-point roundoff.
        // Give that residual to a positive category, never a trailing zero mass.
        return List.of(lastPositive);
    }

    @Override
    public List<String> getConditions() {
        if (probabilitiesInput.get() instanceof BEASTInterface probabilities && probabilities.getID() != null) {
            return List.of(probabilities.getID());
        }
        return List.of();
    }

    @Override
    public boolean isIntegerDistribution() {
        return true;
    }

    @Override
    public Integer getLowerBoundOfParameter() {
        return 1;
    }

    @Override
    public Integer getUpperBoundOfParameter() {
        refresh();
        return categoryCount;
    }

    @Override
    public Double getLower() {
        return 1.0;
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
