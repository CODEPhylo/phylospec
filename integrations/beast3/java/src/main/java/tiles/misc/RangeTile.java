package tiles.misc;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.typeresolver.Stochasticity;

/**
 * This tile matches range statements like 1:5. Only ranges starting from 1 are supported. The result of this tile
 * is the upper bound (e.g. 5 for 1:5).
 */
public class RangeTile extends AstNodeTile<Integer, Expr.Range, BEASTState> {

    // indexed statements are unrolled while building the model, so the number of elements
    // cannot follow a sampled range bound
    AstNodeTileInput<? extends IntScalar<NonNegativeInt>, Expr.Range, BEASTState> fromInput = new AstNodeTileInput<>(
            "from", expr -> expr.from, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    AstNodeTileInput<? extends IntScalar<NonNegativeInt>, Expr.Range, BEASTState> toInput =
            new AstNodeTileInput<>("to", expr -> expr.to, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public Integer applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        IntScalar<? extends Int> from = this.fromInput.apply(beastState, indexVariables);
        IntScalar<? extends Int> to = this.toInput.apply(beastState, indexVariables);

        if (from.get() != 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "BEAST 2.8 only supports ranges starting with 1.",
                    "Start the range with 1.",
                    List.of("1:10"));
        }

        if (to.get() < from.get()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Invalid range.",
                    "The start of a range has to be smaller than the end.",
                    List.of("1:10"));
        }

        return to.get();
    }
}
