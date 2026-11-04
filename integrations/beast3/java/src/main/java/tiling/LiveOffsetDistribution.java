package tiling;

import beast.base.core.BEASTInterface;
import beast.base.spec.inference.distribution.OffsetReal;
import beast.base.util.Randomizer;
import java.util.ArrayList;
import java.util.List;

/** Native OffsetReal with shifted CDFs and sampling from the current base law. */
public final class LiveOffsetDistribution extends OffsetReal {

    @Override
    public void initAndValidate() {
        refresh();
        distributionInput.get().initAndValidate();
        param = paramInput.get();
        // An observation outside the shifted support is legal data with zero density.
        if (param != null && !param.getDomain().isValid(param.get())) {
            throw new IllegalArgumentException("The offset parameter must satisfy its real domain.");
        }
    }

    @Override
    public double cumulativeProbability(double value) {
        return distributionInput
                .get()
                .cumulativeProbability(value - offsetInput.get().get());
    }

    @Override
    public List<Double> sample() {
        // Native bases can return immutable lists and retain a sampler from old inputs.
        // Their quantile methods read the current law. Exclude zero for unbounded bases.
        double probability;
        do {
            probability = Randomizer.nextDouble();
        } while (probability == 0.0);
        return List.of(inverseCumulativeProbability(probability));
    }

    @Override
    public List<String> getConditions() {
        List<String> conditions = new ArrayList<>();
        if (distributionInput.get().getID() != null) {
            conditions.add(distributionInput.get().getID());
        }
        if (offsetInput.get() instanceof BEASTInterface offset && offset.getID() != null) {
            conditions.add(offset.getID());
        }
        return conditions;
    }

    @Override
    public Double getLower() {
        return getLowerBoundOfParameter();
    }

    @Override
    public Double getUpper() {
        return getUpperBoundOfParameter();
    }

    @Override
    public boolean lowerInclusive() {
        return distributionInput.get().lowerInclusive();
    }

    @Override
    public boolean upperInclusive() {
        return distributionInput.get().upperInclusive();
    }
}
