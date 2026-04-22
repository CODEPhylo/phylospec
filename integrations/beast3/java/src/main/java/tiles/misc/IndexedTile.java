package tiles.misc;

import beast.base.spec.domain.Int;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.AstNodeTile;
import tiling.TileApplicationError;
import tiling.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndexedTile extends AstNodeTile<Object, Expr.Index> {

    AstNodeTileInput<List<?>, Expr.Index> vectorInput = new AstNodeTileInput<>(
            "vector", expr -> expr.object, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    AstNodeTileInput<? extends IntScalar<? extends Int>, Expr.Index> firstIndexInput = new AstNodeTileInput<>(
            "index", expr -> expr.indices.getFirst()
    );

    @Override
    public Class<Expr.Index> getTargetNodeType() {
        return Expr.Index.class;
    }

    @Override
    public Object applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
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
        // we first try to get the state node type from the vector input
        Type expressionType = this.vectorInput.getTypeToken().getType();
        if (expressionType instanceof ParameterizedType pt) {
            Type typeArg = pt.getActualTypeArguments()[0];
            if (!(typeArg instanceof TypeVariable) && !(typeArg instanceof WildcardType)) {
                return TypeToken.of(typeArg);
            }
        }

        // we return the basic vector type
        return super.getTypeToken();
    }

}
