package tiles.functions;

import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveInt;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import tiling.BeastXIntScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class NumBranchesTile extends GeneratorTile<IntScalar<PositiveInt>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numBranches";
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

        int numBranches =
                tree.getNodeCount() - 1;

        if (numBranches <= 0) {
            throw new IllegalArgumentException(
                    "A tree must have at least one branch."
            );
        }

        return new BeastXIntScalarParam<>(
                numBranches,
                PositiveInt.INSTANCE
        );
    }
}