package tiles.misc;

import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Int;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.types.IntScalar;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

/**
 * One-based access to taxa, as in taxa(data)[1]. BEAST X's Taxa is not a list, so the
 * list index tile does not apply to it.
 */
public class TaxaIndexTile extends AstNodeTile<Taxon, Expr.Index, BeastXState> {

    AstNodeTileInput<Taxa, Expr.Index, BeastXState> taxaInput =
            new AstNodeTileInput<>(
                    "taxa",
                    expr -> expr.object
            );

    // The taxon is chosen once while building the model, so a sampled index would be frozen.
    AstNodeTileInput<? extends IntScalar<? extends Int>, Expr.Index, BeastXState> indexInput =
            new AstNodeTileInput<>(
                    "index",
                    expr -> expr.indices.getFirst(),
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public Taxon applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        int index =
                this.indexInput.apply(beastState, indexVariables).get();

        IndexedTile.checkIndex(this.getRootNode(), index, taxa.getTaxonCount());

        return taxa.getTaxon(index - 1);
    }
}
