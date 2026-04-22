package tiles.functions;

import tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import beastconfig.BEASTState;

import java.util.Map;

public class AlignmentTaxaTile extends GeneratorTile<DecoratedAlignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<DecoratedAlignment> alignmentInput = new GeneratorTileInput<>("alignment");

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        return this.alignmentInput.apply(beastState, indexVariables);
    }

}
