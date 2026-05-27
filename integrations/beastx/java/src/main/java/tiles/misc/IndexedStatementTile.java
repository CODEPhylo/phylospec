package tiles.misc;

import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class IndexedStatementTile extends AstNodeTile<List<?>, Stmt.Indexed, BeastXState> {

    AstNodeTileInput<Object, Stmt.Indexed, BeastXState> statementInput =
            new AstNodeTileInput<>(
                    "statement",
                    expr -> expr.statement
            );

    AstNodeTileInput<Integer, Stmt.Indexed, BeastXState> rangeInput =
            new AstNodeTileInput<>(
                    "range",
                    expr -> expr.ranges.getFirst()
            );

    @Override
    public List<Object> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Integer range =
                this.rangeInput.apply(beastState, indexVariables);

        List<Expr.Variable> indices =
                this.getRootNode().indices;

        if (indices.size() != 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "BEAST X does not support statements with multiple indices.",
                    "Use only one index variable.",
                    List.of("Real x[i] ~ Normal(mean=0.0, sd=1.0) for i in 1:3")
            );
        }

        Expr.Variable index =
                indices.getFirst();

        Integer oldIndexValue =
                indexVariables.get(index);

        List<Object> list =
                new ArrayList<>();

        for (int i = 0; i < range; i++) {
            indexVariables.put(index, i + 1);

            Object element =
                    this.statementInput.apply(beastState, indexVariables);

            list.add(element);
        }

        if (oldIndexValue == null) {
            indexVariables.remove(index);
        } else {
            indexVariables.put(index, oldIndexValue);
        }

        return list;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> valueType =
                this.statementInput.getTypeToken();

        if (valueType != null) {
            return TypeToken.listOf(valueType);
        }

        return super.getTypeToken();
    }
}
