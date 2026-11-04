package operators;

import beast.base.core.Input;
import beast.base.inference.Operator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.util.Randomizer;

/** The native integer random-walk kernel with window one, adapted to a scalar. */
public final class IntScalarRandomWalkOperator extends Operator {

    public final Input<IntScalarParam<?>> parameterInput =
            new Input<>("parameter", "The integer scalar to update.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        // Read the current parameter bounds on every proposal.
    }

    @Override
    public double proposal() {
        IntScalarParam<?> parameter = parameterInput.get();
        int step = Randomizer.nextInt(3) - 1;
        long proposed = (long) parameter.get() + step;
        if (step == 0
                || proposed < Integer.MIN_VALUE
                || proposed > Integer.MAX_VALUE
                || !parameter.isValid((int) proposed)) {
            // Keep the native self-proposal rejection and do not redraw at a boundary.
            return Double.NEGATIVE_INFINITY;
        }
        parameter.set((int) proposed);
        return 0.0;
    }
}
