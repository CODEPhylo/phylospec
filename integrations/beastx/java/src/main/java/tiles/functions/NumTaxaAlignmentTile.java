package tiles.functions;

import dr.evolution.alignment.Alignment;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveInt;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import tiling.BeastXIntScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class NumTaxaAlignmentTile extends GeneratorTile<IntScalar<PositiveInt>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numTaxa";
    }

    GeneratorTileInput<Alignment, BeastXState> alignmentInput =
            new GeneratorTileInput<>("alignment");

    @Override
    public IntScalar<PositiveInt> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Alignment alignment =
                this.alignmentInput.apply(beastState, indexVariables);

        return new BeastXIntScalarParam<>(
                alignment.getTaxonCount(),
                PositiveInt.INSTANCE
        );
    }
}
