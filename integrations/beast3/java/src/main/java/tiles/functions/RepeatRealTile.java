package tiles.functions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.RealVectorParam;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

import java.util.Arrays;

public class RepeatRealTile extends GeneratorTile<RealVectorParam<Real>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    TileInput<Double> valueInput = new TileInput<>("value");
    TileInput<Integer> numInput = new TileInput<>("num");

    @Override
    public RealVectorParam<Real> applyTile(BEASTState beastState) {
        Double value = this.valueInput.apply(beastState);
        Integer num = this.numInput.apply(beastState);

        double[] values = new double[num];
        Arrays.fill(values, value);

        return new RealVectorParam<>(values, Real.INSTANCE);
    }

}
