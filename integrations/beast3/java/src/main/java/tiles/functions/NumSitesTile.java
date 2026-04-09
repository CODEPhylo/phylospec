package tiles.functions;

import tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import tiling.BEASTState;

public class NumSitesTile extends GeneratorTile<Integer> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numSites";
    }

    TileInput<DecoratedAlignment> alignmentInput = new TileInput<>("alignment");

    @Override
    public Integer applyTile(BEASTState beastState) {
        DecoratedAlignment tree = this.alignmentInput.apply(beastState);
        return tree.alignment().getSiteCount();
    }

}
