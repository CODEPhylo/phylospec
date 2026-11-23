package tiles.misc;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Field access on fixed string-valued records, such as rows returned by fromCSV (as in BEAST 3). */
public class StringMapIndexTile extends AstNodeTile<String, Expr.Index, BeastXState> {

    AstNodeTileInput<Map<String, String>, Expr.Index, BeastXState> mapInput =
            new AstNodeTileInput<>(
                    "map",
                    expr -> expr.object
            );

    AstNodeTileInput<String, Expr.Index, BeastXState> keyInput =
            new AstNodeTileInput<>(
                    "key",
                    expr -> expr.indices.getFirst()
            );

    @Override
    public Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC);
    }

    @Override
    public String applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Map<String, String> record =
                this.mapInput.apply(beastState, indexVariables);

        String key =
                this.keyInput.apply(beastState, indexVariables);

        if (!record.containsKey(key)) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Record has no field '" + key + "'.",
                    "Available fields: " + String.join(", ", record.keySet()) + "."
            );
        }

        return record.get(key);
    }
}
