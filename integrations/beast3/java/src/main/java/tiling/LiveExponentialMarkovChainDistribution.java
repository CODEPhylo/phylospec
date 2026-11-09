package tiling;

import beast.base.spec.inference.distribution.MarkovChainDistribution;

/** Native exponential-chain density with rejection of invalid live proposals. */
public final class LiveExponentialMarkovChainDistribution extends MarkovChainDistribution {
    @Override
    public double calculateLogP() {
        var values = paramInput.get();
        if (values.size() == 0) return logP = 0.0;

        double mean = initialMeanInput.get().get();
        if (!Double.isFinite(mean) || mean <= 0.0) return logP = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);
            // A final zero has an exponential density. An interior zero would
            // become the next exponential's invalid mean; do not invent a point mass.
            if (!Double.isFinite(value) || value < 0.0 || (value == 0.0 && i < values.size() - 1)) {
                return logP = Double.NEGATIVE_INFINITY;
            }
        }
        return super.calculateLogP();
    }
}
