package tiles.functions;

import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class EnvTile extends GeneratorTile<String, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "env";
    }

    GeneratorTileInput<String, BEASTState> variableInput =
            new GeneratorTileInput<>("variable", true, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public String applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String variable = this.variableInput.apply(beastState, indexVariables);
        String value = System.getenv(variable);

        if (value == null) {
            throw new TileApplicationError(
                    "Environment variable '" + variable + "' is not set.", "Set the environment variable.");
        }

        return value;
    }
}
