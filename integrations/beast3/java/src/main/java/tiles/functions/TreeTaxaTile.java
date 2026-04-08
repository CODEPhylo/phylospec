package tiles.functions;

import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.Tree;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class TreeTaxaTile extends GeneratorTile<TaxonSet> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    TileInput<Tree> treeInput = new TileInput<>("tree");

    @Override
    public TaxonSet applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        return tree.getTaxonset();
    }

}
