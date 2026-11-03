package tiling;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.type.BoolScalar;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import java.util.List;

/** A Boolean Bernoulli value with a live, runtime-checked real probability. */
public final class BernoulliDistribution extends ScalarDistribution<BoolScalar, Boolean> {

    public final Input<RealScalar<? extends Real>> pInput =
            new Input<>("p", "Probability of true, finite and in [0, 1].", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        refresh();
        super.initAndValidate();
    }

    @Override
    public void refresh() {
        if (pInput.get() == null || !isProbability(pInput.get().get())) {
            throw new IllegalArgumentException("Bernoulli p must be finite and in [0, 1].");
        }
    }

    /** Choose a value with positive probability, including p = 0 and p = 1. */
    public boolean mostLikelyValue() {
        refresh();
        return pInput.get().get() >= 0.5;
    }

    @Override
    protected double calcLogP(Boolean value) {
        double probability = pInput.get().get();
        // Core declares p as Real. Reject an out-of-range MCMC proposal with
        // zero probability rather than throwing and aborting the chain.
        if (!isProbability(probability)) {
            return Double.NEGATIVE_INFINITY;
        }
        return value ? Math.log(probability) : Math.log1p(-probability);
    }

    @Override
    public List<Boolean> sample() {
        refresh();
        // Read the live input on every draw, also after rejection and restoration.
        return List.of(Randomizer.nextDouble() < pInput.get().get());
    }

    @Override
    public List<String> getConditions() {
        if (pInput.get() instanceof BEASTInterface probability && probability.getID() != null) {
            return List.of(probability.getID());
        }
        return List.of();
    }

    // As in native Bernoulli, a Boolean parameter has no numeric proposal bounds.
    @Override
    public Boolean getLowerBoundOfParameter() {
        throw new IllegalStateException("Bernoulli does not support numeric lower bounds.");
    }

    @Override
    public Boolean getUpperBoundOfParameter() {
        throw new IllegalStateException("Bernoulli does not support numeric upper bounds.");
    }

    private static boolean isProbability(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}
