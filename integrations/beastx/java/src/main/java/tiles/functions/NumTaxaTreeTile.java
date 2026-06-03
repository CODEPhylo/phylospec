package tiles.functions;

import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveInt;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class NumTaxaTreeTile extends GeneratorTile<IntScalar<PositiveInt>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numTaxa";
    }

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    @Override
    public IntScalar<PositiveInt> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        return new BeastXIntScalarParam<>(
                tree.getTaxonCount(),
                PositiveInt.INSTANCE
        );
    }
}
