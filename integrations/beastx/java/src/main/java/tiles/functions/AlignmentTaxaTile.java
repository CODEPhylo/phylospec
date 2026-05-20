package tiles.functions;

import dr.evolution.alignment.Alignment;
import dr.evolution.util.Taxa;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class AlignmentTaxaTile extends GeneratorTile<Taxa, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<Alignment, BeastXState> alignmentInput =
            new GeneratorTileInput<>("alignment");

    @Override
    public Taxa applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Alignment alignment =
                this.alignmentInput.apply(beastState, indexVariables);

        return new Taxa(alignment);
    }
}