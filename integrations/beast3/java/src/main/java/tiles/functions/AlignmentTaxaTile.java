package tiles.functions;

import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class AlignmentTaxaTile extends GeneratorTile<DecoratedAlignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<DecoratedAlignment> alignmentInput = new GeneratorTileInput<>("alignment");

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.alignmentInput.apply(beastState, indexVariables);
    }

}
