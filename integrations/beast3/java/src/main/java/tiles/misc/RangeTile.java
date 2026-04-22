package tiles.misc;

import beast.base.spec.domain.Int;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.AstNodeTile;
import tiling.TileApplicationError;

import java.util.List;
import java.util.Map;

public class RangeTile extends AstNodeTile<Integer, Expr.Range> {

    AstNodeTileInput<? extends IntScalar<? extends Int>, Expr.Range> fromInput = new AstNodeTileInput<>(
            "from", expr -> expr.from
    );
    AstNodeTileInput<? extends IntScalar<? extends Int>, Expr.Range> toInput = new AstNodeTileInput<>(
            "to", expr -> expr.to
    );

    @Override
    public Integer applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        IntScalar<? extends Int> from = this.fromInput.apply(beastState, indexVariables);
        IntScalar<? extends Int> to = this.toInput.apply(beastState, indexVariables);

        if (from.get() != 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "BEAST 2.8 only supports ranges starting with 1.",
                    "Start the range with 1.",
                    List.of("1:10")
            );
        }

        if (to.get() < from.get()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Invalid range.",
                    "The start of a range has to be smaller than the end.",
                    List.of("1:10")
            );
        }

        return to.get();
    }

}
