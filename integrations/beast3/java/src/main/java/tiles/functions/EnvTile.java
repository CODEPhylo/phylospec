package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import beastconfig.BEASTState;
import tiles.GeneratorTile;
import tiling.TileApplicationError;

import java.util.IdentityHashMap;
import java.util.Set;

public class EnvTile extends GeneratorTile<String> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "env";
    }

    GeneratorTileInput<String> variableInput = new GeneratorTileInput<>(
            "variable", true, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public String applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String variable = this.variableInput.apply(beastState, indexVariables);
        String value = System.getenv(variable);

        if (value == null) {
            throw new TileApplicationError(
                    "Environment variable '" + variable + "' is not set.",
                    "Set the environment variable."
            );
        }

        return value;
    }

}
