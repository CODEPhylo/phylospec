package tiling;

import beast.base.evolution.tree.coalescent.Coalescent;

/** Reuses the native coalescent density, rejecting invalid population or boundary proposals. */
public final class SkylineCoalescentModel extends Coalescent {

    @Override
    public double calculateLogP() {
        PiecewiseConstantPopulation population = (PiecewiseConstantPopulation) popSizeInput.get();
        if (!population.hasValidPopulationSizes() || !population.hasValidChangeTimes()) {
            return logP = Double.NEGATIVE_INFINITY;
        }
        return super.calculateLogP();
    }
}
