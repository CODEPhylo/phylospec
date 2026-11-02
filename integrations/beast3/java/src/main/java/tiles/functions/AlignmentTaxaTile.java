package tiles.functions;

import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import tiles.input.TaxaData;

public class AlignmentTaxaTile extends GeneratorTile<TaxaData, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<DecoratedAlignment, BEASTState> alignmentInput = new GeneratorTileInput<>("alignment");

    @Override
    public TaxaData applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        DecoratedAlignment alignment = this.alignmentInput.apply(beastState, indexVariables);
        return new TaxaData(alignment.taxonSet(), alignment.ages());
    }
}
