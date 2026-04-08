package tiles.functions;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class TaxaTile extends GeneratorTile<Alignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    TileInput<Alignment> alignmentInput = new TileInput<>("alignment");

    @Override
    public Alignment applyTile(BEASTState beastState) {
        return this.alignmentInput.apply(beastState);
    }

    @Override
    protected Tile<?> createInstance() {
        return new TaxaTile();
    }

}
