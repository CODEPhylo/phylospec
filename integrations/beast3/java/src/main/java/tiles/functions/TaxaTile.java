package tiles.functions;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class TaxaTile extends GeneratorTile<TaxonSet> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    Input<Alignment> alignmentInput = new Input<>("alignment");

    @Override
    public TaxonSet applyTile(BEASTState beastState) {
        Alignment alignment = this.alignmentInput.apply(beastState);
        return alignment.taxonSetInput.get();
    }

    @Override
    protected Tile<?> createInstance() {
        return new TaxaTile();
    }

}
