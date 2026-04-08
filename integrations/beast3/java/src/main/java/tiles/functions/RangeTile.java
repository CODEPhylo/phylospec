package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntVectorParam;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class RangeTile extends GeneratorTile<IntVectorParam<Int>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "range";
    }

    TileInput<Integer> startInput = new TileInput<>("start");
    TileInput<Integer> endInput = new TileInput<>("end");

    @Override
    public IntVectorParam<Int> applyTile(BEASTState beastState) {
        Integer start = this.startInput.apply(beastState);
        Integer end = this.endInput.apply(beastState);
        int num = Math.abs(end - start);

        int[] values = new int[num];
        int gap = start < end ? 1 : -1;

        for (int i = 0; i < num; i++) {
            values[i] = start + i*gap;
        }

        return new IntVectorParam<>(values, Int.INSTANCE);
    }

}
