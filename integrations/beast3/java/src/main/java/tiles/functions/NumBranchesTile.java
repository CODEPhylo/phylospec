package tiles.functions;

import beast.base.evolution.tree.Tree;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class NumBranchesTile extends GeneratorTile<Integer> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numBranches";
    }

    GeneratorTileInput<Tree> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public Integer applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        return tree.getNodeCount() - 1;
    }

}
