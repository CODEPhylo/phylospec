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

    GeneratorTileInput<String> newickStringInput = new GeneratorTileInput<>("newickString");

    @Override
    public Tree applyTile(BEASTState beastState) {
        String newick = this.newickStringInput.apply(beastState);
        return new TreeParser(newick);
    }

}
