package tiles.misc;

import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.typeresolver.Stochasticity;

/** Field access on fixed string-valued records, such as rows returned by fromCSV. */
public class StringMapIndexTile extends AstNodeTile<String, Expr.Index, BEASTState> {
    AstNodeTileInput<Map<String, String>, Expr.Index, BEASTState> mapInput =
            new AstNodeTileInput<>("map", expression -> expression.object);
    AstNodeTileInput<String, Expr.Index, BEASTState> keyInput =
            new AstNodeTileInput<>("key", expression -> expression.indices.getFirst());

    @Override
    public Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC);
    }

    @Override
    public String applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Map<String, String> record = mapInput.apply(state, indexVariables);
        String key = keyInput.apply(state, indexVariables);
        if (!record.containsKey(key)) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Record has no field '" + key + "'.",
                    "Available fields: " + String.join(", ", record.keySet()) + ".");
        }
        return record.get(key);
    }
}
