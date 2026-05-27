package tiles.misc;

import org.phylospec.ast.Expr;
import org.phylospec.domain.Int;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.types.IntScalar;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

public class RangeTile extends AstNodeTile<Integer, Expr.Range, BeastXState> {

    AstNodeTileInput<? extends IntScalar<NonNegativeInt>, Expr.Range, BeastXState> fromInput =
            new AstNodeTileInput<>(
                    "from",
                    expr -> expr.from
            );

    AstNodeTileInput<? extends IntScalar<NonNegativeInt>, Expr.Range, BeastXState> toInput =
            new AstNodeTileInput<>(
                    "to",
                    expr -> expr.to
            );

    @Override
    public Integer applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        IntScalar<? extends Int> from =
                this.fromInput.apply(beastState, indexVariables);

        IntScalar<? extends Int> to =
                this.toInput.apply(beastState, indexVariables);

        if (from.get() != 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "BEAST X only supports indexed ranges starting with 1.",
                    "Start the indexed range with 1.",
                    List.of("1:10")
            );
        }

        if (to.get() < from.get()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Invalid indexed range.",
                    "The start of an indexed range has to be smaller than or equal to the end.",
                    List.of("1:10")
            );
        }

        return to.get();
    }
}