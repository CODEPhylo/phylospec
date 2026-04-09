package tiles.input;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class FromNewickTile extends GeneratorTile<Tree> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromNewick";
    }

    TileInput<String> newickStringInput = new TileInput<>("newickString");

    @Override
    public Tree applyTile(BEASTState beastState) {
        String newick = this.newickStringInput.apply(beastState);
        return new TreeParser(newick);
    }

}
