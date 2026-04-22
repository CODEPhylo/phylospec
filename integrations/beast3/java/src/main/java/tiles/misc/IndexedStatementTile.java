package tiles.misc;

import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import tiles.AstNodeTile;
import tiling.TileApplicationError;
import tiling.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IndexedStatementTile extends AstNodeTile<List<?>, Stmt.Indexed> {

    AstNodeTileInput<Object, Stmt.Indexed> statementInput = new AstNodeTileInput<>(
            "statement", expr -> expr.statement
    );
    AstNodeTileInput<Integer, Stmt.Indexed> rangeInput = new AstNodeTileInput<>(
            "range", expr -> expr.ranges.getFirst()
    );

    @Override
    public Class<Stmt.Indexed> getTargetNodeType() {
        return Stmt.Indexed.class;
    }

    @Override
    public List<Object> applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        Integer range = this.rangeInput.apply(beastState, indexVariables);

        List<Expr.Variable> indices = this.getRootNode().indices;
        if (indices.size() != 1) {
            throw new TileApplicationError(
                    "BEAST 2.8 does not support statement with two different indices.", "Only use one index variable."
            );
        }

        String indexName = indices.getFirst().variableName;
        Integer oldIndexValue = indexVariables.get(indexName);

        List<Object> list = new ArrayList<>();
        for (int i = 0; i < range; i++) {
            indexVariables.put(indexName, i + 1);
            Object element = this.statementInput.apply(beastState, indexVariables);
            list.add(element);
        }

        indexVariables.put(indexName, oldIndexValue);

        return list;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> valueType = this.statementInput.getTypeToken();
        if (valueType != null) {
            return TypeToken.parameterized(List.class, valueType.getType());
        }

        // we return the basic vector type
        return super.getTypeToken();
    }

}
