package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.Vector;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import tiling.BEASTState;

import java.util.Set;

public class NumTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "num";
    }

    GeneratorTileInput<? extends Vector<?, ?>> vectorInput = new GeneratorTileInput<>("vector");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState) {
        Vector<?, ?> vector = this.vectorInput.apply(beastState);
        return new IntScalarParam<>(vector.shape()[0], NonNegativeInt.INSTANCE);
    }

}
