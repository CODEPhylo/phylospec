package tiles.functions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.RealVectorParam;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class LinSpaceTile extends GeneratorTile<RealVectorParam<Real>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "linspace";
    }

    TileInput<Double> startInput = new TileInput<>("start");
    TileInput<Double> endInput = new TileInput<>("end");
    TileInput<Integer> numInput = new TileInput<>("num");

    @Override
    public RealVectorParam<Real> applyTile(BEASTState beastState) {
        Double start = this.startInput.apply(beastState);
        Double end = this.endInput.apply(beastState);
        Integer num = this.numInput.apply(beastState);

        double[] values = new double[num];
        double gap = (end - start) / (num - 1);

        for (int i = 0; i < num; i++) {
            values[i] = start + i * gap;
        }

        return new RealVectorParam<>(values, Real.INSTANCE);
    }

    @Override
    protected Tile<?> createInstance() {
        return new LinSpaceTile();
    }

}
