package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import tiles.GeneratorTile;
import tiling.BEASTState;

import java.util.Arrays;

public class RepeatRealTile extends GeneratorTile<RealVectorParam<Real>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<RealScalarParam<? extends Real>> valueInput = new GeneratorTileInput<>("value");
    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>> numInput = new GeneratorTileInput<>("num");

    @Override
    public RealVectorParam<Real> applyTile(BEASTState beastState) {
        double value = this.valueInput.apply(beastState).get();
        int num = this.numInput.apply(beastState).get();

        double[] values = new double[num];
        Arrays.fill(values, value);

        return new RealVectorParam<>(values, Real.INSTANCE);
    }

}
