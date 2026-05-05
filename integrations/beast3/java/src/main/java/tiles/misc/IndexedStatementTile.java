package tiles.misc;

import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.TypeToken;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class IndexedStatementTile extends AstNodeTile<List<?>, Stmt.Indexed, BEASTState> {

    AstNodeTileInput<Object, Stmt.Indexed, BEASTState> statementInput = new AstNodeTileInput<>(
            "statement", expr -> expr.statement
    );
    AstNodeTileInput<Integer, Stmt.Indexed, BEASTState> rangeInput = new AstNodeTileInput<>(
            "range", expr -> expr.ranges.getFirst()
    );

    @Override
    public List<Object> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer range = this.rangeInput.apply(beastState, indexVariables);

        List<Expr.Variable> indices = this.getRootNode().indices;
        if (indices.size() != 1) {
            throw new TileApplicationError(
                    "BEAST 2.8 does not support statement with multiple indices.", "Only use one index variable."
            );
        }

        Expr.Variable index = indices.getFirst();
        Integer oldIndexValue = indexVariables.get(index);

        // we go through every element in 1:range and evaluate the statement

        List<Object> list = new ArrayList<>();
        for (int i = 0; i < range; i++) {
            indexVariables.put(indices.getFirst(), i + 1);
            Object element = this.statementInput.apply(beastState, indexVariables);
            list.add(element);
        }

        // set the index variable back to the old value

        if (oldIndexValue == null) {
            indexVariables.remove(index);
        } else {
            indexVariables.put(index, oldIndexValue);
        }

        return list;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> valueType = this.statementInput.getTypeToken();
        if (valueType != null) return TypeToken.listOf(valueType);

        // we return the basic vector type
        return super.getTypeToken();
    }

}
