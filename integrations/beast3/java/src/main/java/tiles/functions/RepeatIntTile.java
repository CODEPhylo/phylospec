package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntVectorParam;
import tiles.GeneratorTile;
import tiling.BEASTState;

import java.util.Arrays;

public class RepeatIntTile extends GeneratorTile<IntVectorParam<Int>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<Integer> valueInput = new GeneratorTileInput<>("value");
    GeneratorTileInput<Integer> numInput = new GeneratorTileInput<>("num");

    @Override
    public IntVectorParam<Int> applyTile(BEASTState beastState) {
        Integer value = this.valueInput.apply(beastState);
        Integer num = this.numInput.apply(beastState);

        int[] values = new int[num];
        Arrays.fill(values, value);

        return new IntVectorParam<>(values, Int.INSTANCE);
    }

}
