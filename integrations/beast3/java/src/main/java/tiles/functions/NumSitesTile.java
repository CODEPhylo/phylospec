package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;

public class NumSitesTile extends GeneratorTile<IntScalarParam<NonNegativeInt>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numSites";
    }

    GeneratorTileInput<DecoratedAlignment, BEASTState> alignmentInput = new GeneratorTileInput<>("alignment");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        DecoratedAlignment alignment = this.alignmentInput.apply(beastState, indexVariables);
        return new IntScalarParam<>(alignment.alignment().getSiteCount(), NonNegativeInt.INSTANCE);
    }
}
