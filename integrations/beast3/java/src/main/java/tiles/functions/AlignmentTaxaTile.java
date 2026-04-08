package tiles.functions;

import beast.base.evolution.alignment.Alignment;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class AlignmentTaxaTile extends GeneratorTile<Alignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    TileInput<Alignment> alignmentInput = new TileInput<>("alignment");

    @Override
    public Alignment applyTile(BEASTState beastState) {
        return this.alignmentInput.apply(beastState);
    }

}
