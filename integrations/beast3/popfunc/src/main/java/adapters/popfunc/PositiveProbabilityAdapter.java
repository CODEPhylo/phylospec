package adapters.popfunc;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import org.phylospec.tiling.TypeAdapter;

/** Preserves a strictly positive probability parameter for a positive-real BEAST input. */
public final class PositiveProbabilityAdapter
        implements TypeAdapter<
                RealScalar<? extends UnitInterval>,
                RealScalar<? extends PositiveReal>,
                BEASTState> {

    @Override
    @SuppressWarnings("unchecked")
    public RealScalar<? extends PositiveReal> adapt(
            RealScalar<? extends UnitInterval> value,
            BEASTState state) {
        if (value.get() <= 0.0) {
            throw new IllegalArgumentException("A positive probability must be greater than zero.");
        }

        // Preserve the original StateNode so posterior updates remain visible to the model.
        return (RealScalar<? extends PositiveReal>) (RealScalar<?>) value;
    }
}
