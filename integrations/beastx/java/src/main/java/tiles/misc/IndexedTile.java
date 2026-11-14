package tiles.misc;

import org.phylospec.ast.Expr;
import org.phylospec.domain.Int;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.types.IntScalar;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class IndexedTile extends AstNodeTile<Object, Expr.Index, BeastXState> {

    AstNodeTileInput<List<?>, Expr.Index, BeastXState> vectorInput =
            new AstNodeTileInput<>(
                    "vector",
                    expr -> expr.object
            );

    // The element is chosen once while building the model, so a sampled index would be frozen.
    AstNodeTileInput<? extends IntScalar<? extends Int>, Expr.Index, BeastXState> firstIndexInput =
            new AstNodeTileInput<>(
                    "index",
                    expr -> expr.indices.getFirst(),
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public Object applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<?> vector =
                this.vectorInput.apply(beastState, indexVariables);

        int index =
                this.firstIndexInput.apply(beastState, indexVariables).get();

        if (index == 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Index " + index + " is smaller than 1.",
                    "PhyloSpec uses 1-based indexing. Use an index between 1 and " + vector.size() + ".",
                    List.of("values[1]")
            );
        }

        if (index < 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Index " + index + " is smaller than 1.",
                    "Use an index between 1 and " + vector.size() + ".",
                    List.of("values[1]")
            );
        }

        if (vector.size() < index) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Index " + index + " is greater than the number of elements.",
                    "Use an index between 1 and " + vector.size() + ".",
                    List.of("values[" + vector.size() + "]")
            );
        }

        return vector.get(index - 1);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> resolved =
                TypeToken.firstConcreteTypeArg(this.vectorInput.getTypeToken());

        if (resolved != null) {
            return resolved;
        }

        return super.getTypeToken();
    }
}
