package tiling;

import beast.base.spec.inference.distribution.Dirichlet;

/** Native Dirichlet density with rejection of invalid live concentration proposals. */
public final class LiveDirichletDistribution extends Dirichlet {
    @Override
    public double calculateLogP() {
        var concentration = alphaInput.get();
        if (concentration.size() == 0
                || concentration.size() != paramInput.get().size()) {
            return logP = Double.NEGATIVE_INFINITY;
        }
        for (int i = 0; i < concentration.size(); i++) {
            double value = concentration.get(i);
            if (!Double.isFinite(value) || value <= 0.0) return logP = Double.NEGATIVE_INFINITY;
        }
        return super.calculateLogP();
    }
}
