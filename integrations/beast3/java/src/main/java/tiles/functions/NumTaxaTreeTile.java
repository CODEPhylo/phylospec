package tiles.functions;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

public class NumTaxaTreeTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numTaxa";
    }

    GeneratorTileInput<Tree> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        return new IntScalarParam<>(tree.getTaxaNames().length, NonNegativeInt.INSTANCE);
    }

}
