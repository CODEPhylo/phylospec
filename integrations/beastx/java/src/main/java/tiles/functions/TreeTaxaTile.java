package tiles.functions;

import dr.evolution.util.Taxa;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class TreeTaxaTile extends GeneratorTile<Taxa, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    @Override
    public Taxa applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        return new Taxa(tree);
    }
}
