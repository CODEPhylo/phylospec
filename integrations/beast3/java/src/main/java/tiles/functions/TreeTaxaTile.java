package tiles.functions;

import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;

/** Extracts taxa from a fixed tree without modifying or sampling that tree. */
public class TreeTaxaTile extends GeneratorTile<List<TaxonValue>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<Tree, BEASTState> treeInput =
            new GeneratorTileInput<>("tree", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public TaxaData applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return fromFixedTree(state, treeInput.apply(state, indexVariables));
    }

    static TaxaData fromFixedTree(BEASTState state, Tree tree) {
        if (tree.isEstimatedInput.get() || state.stateNodes.containsKey(tree)) {
            throw new TileApplicationError(
                    "taxa(tree) currently requires a fixed tree.",
                    "Use a tree imported with fromTree or fromNewick; sampled tip ages are not supported here.");
        }

        // Read existing leaf heights directly. getDateTrait() can process traits and
        // change node heights; registering the reference tree can reinitialize it.
        List<TaxonValue> values = new ArrayList<>();
        try {
            for (Node leaf : tree.getExternalNodes()) {
                values.add(new TaxonValue(leaf.getID(), null, leaf.getHeight()));
            }
        } catch (IllegalArgumentException e) {
            throw new TileApplicationError("Invalid tree taxa.", e.getMessage());
        }
        return TaxaData.fromList(state, values);
    }
}
