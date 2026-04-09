package tiles.functions;

import tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import tiling.BEASTState;

public class AlignmentTaxaTile extends GeneratorTile<DecoratedAlignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    TileInput<DecoratedAlignment> alignmentInput = new TileInput<>("alignment");

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState) {
        return this.alignmentInput.apply(beastState);
    }

}
