package adapters.popfunc;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import org.phylospec.tiling.TypeAdapter;

/** Enables a PopFunc ancestral-size input when that optional value is present. */
public final class AncestralIndicatorAdapter
        implements TypeAdapter<
                RealScalar<? extends PositiveReal>,
                IntScalar<? extends NonNegativeInt>,
                BEASTState> {

    @Override
    public IntScalar<? extends NonNegativeInt> adapt(
            RealScalar<? extends PositiveReal> value,
            BEASTState state) {

        return new IntScalarParam<>(1, NonNegativeInt.INSTANCE);
    }
}
