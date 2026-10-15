package adapters.popfunc;

import popfunc.beast.evolution.populationmodel.Cons_Exp_ConsGrowth;

/** Checks bound inputs before entering the native initializer; all population calculations stay native. */
public final class ValidatedConsExpCons extends Cons_Exp_ConsGrowth {
    @Override
    public void initAndValidate() {
        double start = xInput.get().get();
        double end = tauInput.get().get();
        // The native initializer retries the same invalid x indefinitely. Component constraints
        // currently emit warnings, so check this precondition at the engine boundary as well.
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end < start) {
            throw new IllegalArgumentException("constantExponentialConstantPopulationFunction requires "
                    + "finite ages with 0 <= growthStartAge <= growthEndAge.");
        }
        // Overflowed literals can have a positive domain, but are not usable demographic inputs.
        double size = NCInput.get().get();
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("constantExponentialConstantPopulationFunction requires "
                    + "a finite, positive recentPopulationSize.");
        }
        double rate = rInput.get().get();
        if (!Double.isFinite(rate) || rate <= 0) {
            throw new IllegalArgumentException("constantExponentialConstantPopulationFunction requires "
                    + "a finite, positive growthRate.");
        }
        super.initAndValidate();
    }
}
