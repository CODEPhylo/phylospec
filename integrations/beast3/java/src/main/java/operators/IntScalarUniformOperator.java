package operators;

import beast.base.core.Input;
import beast.base.inference.Operator;
import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.util.Randomizer;

/** Uniform proposals for an integer scalar, using its current effective bounds. */
public final class IntScalarUniformOperator extends Operator {

    public final Input<IntScalarParam<Int>> parameterInput =
            new Input<>("parameter", "The integer scalar to update.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        // Bounds can depend on other sampled parameters; do not cache them here.
    }

    @Override
    public double proposal() {
        IntScalarParam<Int> parameter = parameterInput.get();
        int lower = parameter.getLower();
        int upper = parameter.getUpper();
        int current = parameter.get();
        if (lower >= upper || current < lower || current > upper) {
            return Double.NEGATIVE_INFINITY;
        }

        long width = (long) upper - lower + 1;
        int proposed;
        if (width <= Integer.MAX_VALUE) {
            proposed = lower + Randomizer.nextInt((int) width);
        } else {
            // A wide interval covers at least half of the int range. Rejection sampling
            // avoids both int overflow and modulo bias while using BEAST's seeded RNG.
            do {
                proposed = Randomizer.nextInt();
            } while (proposed < lower || proposed > upper);
        }
        parameter.set(proposed);
        // Only this scalar changes, so the forward and reverse uniform ranges are identical.
        return 0.0;
    }
}
