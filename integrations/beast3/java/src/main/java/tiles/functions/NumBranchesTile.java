package tiles.functions;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

public class NumBranchesTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numBranches";
    }

    GeneratorTileInput<Tree> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        return new IntScalarParam<>(tree.getNodeCount() - 1, NonNegativeInt.INSTANCE);
    }

}
