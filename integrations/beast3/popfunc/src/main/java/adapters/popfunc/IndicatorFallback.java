package adapters.popfunc;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import org.phylospec.tiling.InputFallback;

/** Disables an optional PopFunc feature when its PhyloSpec argument is absent. */
public final class IndicatorFallback
        implements InputFallback<IntScalar<? extends NonNegativeInt>, BEASTState> {

    @Override
    public IntScalar<? extends NonNegativeInt> get(BEASTState state) {
        return new IntScalarParam<>(0, NonNegativeInt.INSTANCE);
    }
}
