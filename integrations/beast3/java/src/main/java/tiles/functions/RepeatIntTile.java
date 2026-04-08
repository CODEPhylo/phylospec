package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntVectorParam;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

import java.util.Arrays;

public class RepeatIntTile extends GeneratorTile<IntVectorParam<Int>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    TileInput<Integer> valueInput = new TileInput<>("value");
    TileInput<Integer> numInput = new TileInput<>("num");

    @Override
    public IntVectorParam<Int> applyTile(BEASTState beastState) {
        Integer value = this.valueInput.apply(beastState);
        Integer num = this.numInput.apply(beastState);

        int[] values = new int[num];
        Arrays.fill(values, value);

        return new IntVectorParam<>(values, Int.INSTANCE);
    }

    @Override
    protected Tile<?> createInstance() {
        return new RepeatIntTile();
    }

}
