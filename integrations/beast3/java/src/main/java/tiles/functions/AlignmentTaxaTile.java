package tiles.functions;

import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;

public class AlignmentTaxaTile extends GeneratorTile<List<TaxonValue>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<DecoratedAlignment, BEASTState> alignmentInput = new GeneratorTileInput<>("alignment");

    @Override
    public TaxaData applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        DecoratedAlignment alignment = this.alignmentInput.apply(beastState, indexVariables);
        return new TaxaData(alignment.taxonSet(), alignment.ages(), alignment.speciesNames());
    }
}
