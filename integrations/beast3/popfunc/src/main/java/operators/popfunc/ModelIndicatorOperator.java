package operators.popfunc;

import beast.base.core.Input;
import beast.base.inference.Operator;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.util.Randomizer;

/** Proposes a different valid model index with equal probability. */
public final class ModelIndicatorOperator extends Operator {

    public final Input<IntScalarParam<NonNegativeInt>> indicatorInput =
            new Input<>("indicator", "The selected model index.", Input.Validate.REQUIRED);

    public final Input<Integer> modelCountInput =
            new Input<>("modelCount", "The number of selectable models.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (modelCountInput.get() < 1) {
            throw new IllegalArgumentException("Model count must be positive.");
        }
    }

    @Override
    public double proposal() {
        int modelCount = modelCountInput.get();
        if (modelCount < 2) {
            return Double.NEGATIVE_INFINITY;
        }

        IntScalarParam<NonNegativeInt> indicator = indicatorInput.get();
        int current = indicator.get();
        if (current < 0 || current >= modelCount) {
            return Double.NEGATIVE_INFINITY;
        }

        int proposed = Randomizer.nextInt(modelCount - 1);
        if (proposed >= current) {
            proposed++;
        }
        indicator.set(proposed);
        return 0.0;
    }
}
