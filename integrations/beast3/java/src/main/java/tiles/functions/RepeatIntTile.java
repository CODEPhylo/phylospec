package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import tiles.GeneratorTile;
import tiling.BEASTState;

import java.util.Arrays;

public class RepeatIntTile extends GeneratorTile<IntVectorParam<Int>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<IntScalarParam<? extends Int>> valueInput = new GeneratorTileInput<>("value");
    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>> numInput = new GeneratorTileInput<>("num");

    @Override
    public IntVectorParam<Int> applyTile(BEASTState beastState) {
        int value = this.valueInput.apply(beastState).get();
        int num = this.numInput.apply(beastState).get();

        int[] values = new int[num];
        Arrays.fill(values, value);

        return new IntVectorParam<>(values, Int.INSTANCE);
    }

}
