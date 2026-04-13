package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import tiling.BEASTState;

import java.util.Set;

public class RangeTile extends GeneratorTile<IntVectorParam<Int>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "range";
    }

    GeneratorTileInput<IntScalarParam<? extends Int>> startInput = new GeneratorTileInput<>(
            "start", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<IntScalarParam<? extends Int>> endInput = new GeneratorTileInput<>(
            "end", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public IntVectorParam<Int> applyTile(BEASTState beastState) {
        int start = this.startInput.apply(beastState).get();
        int end = this.endInput.apply(beastState).get();
        int num = Math.abs(end - start);

        int[] values = new int[num];
        int gap = start < end ? 1 : -1;

        for (int i = 0; i < num; i++) {
            values[i] = start + i*gap;
        }

        return new IntVectorParam<>(values, Int.INSTANCE);
    }

}
