package adapters.popfunc;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import org.phylospec.tiling.InputFallback;

/** Supplies an ignored positive placeholder for a required PopFunc input. */
public final class UnitPositiveFallback
        implements InputFallback<RealScalar<? extends PositiveReal>, BEASTState> {

    @Override
    public RealScalar<? extends PositiveReal> get(BEASTState state) {
        return new RealScalarParam<>(1.0, PositiveReal.INSTANCE);
    }
}
