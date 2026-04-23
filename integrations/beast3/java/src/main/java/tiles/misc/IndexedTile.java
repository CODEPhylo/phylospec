package tiles.misc;

import beast.base.spec.domain.Int;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.AstNodeTile;
import tiling.TileApplicationError;
import tiling.TypeToken;

import java.util.IdentityHashMap;
import java.util.List;

public class IndexedTile extends AstNodeTile<Object, Expr.Index> {

    AstNodeTileInput<List<?>, Expr.Index> vectorInput = new AstNodeTileInput<>(
            "vector", expr -> expr.object
    );
    AstNodeTileInput<? extends IntScalar<? extends Int>, Expr.Index> firstIndexInput = new AstNodeTileInput<>(
            "index", expr -> expr.indices.getFirst()
    );

    @Override
    public Object applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        List<?> vector = this.vectorInput.apply(beastState, indexVariables);
        int index = this.firstIndexInput.apply(beastState, indexVariables).get();

        if (index < 1) {
            throw new TileApplicationError(
                    "Index " + index + " is smaller than 1",
                    "Use an index which is between 1 and " + vector.size() + "."
            );
        }

        if (vector.size() < index) {
            throw new TileApplicationError(
                    "Index " + index + " is greater than the number of elements.",
                    "Use an index which is between 1 and " + vector.size() + "."
            );
        }

        return vector.get(index - 1);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        // we first try to get the element type from the vector input
        TypeToken<?> resolved = TypeToken.firstConcreteTypeArg(this.vectorInput.getTypeToken());
        if (resolved != null) return resolved;

        // we return the basic vector type
        return super.getTypeToken();
    }


}
