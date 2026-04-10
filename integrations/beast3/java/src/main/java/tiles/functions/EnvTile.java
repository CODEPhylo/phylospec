package tiles.functions;

import tiling.BEASTState;
import tiles.GeneratorTile;
import tiling.TilingError;

public class EnvTile extends GeneratorTile<String> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "env";
    }

    GeneratorTileInput<String> variableInput = new GeneratorTileInput<>("variable");

    @Override
    public String applyTile(BEASTState beastState) {
        String variable = this.variableInput.apply(beastState);
        String value = System.getenv(variable);

        if (value == null) {
            throw new TilingError(
                    "Environment variable '" + variable + "' is not set.",
                    "Set the environment variable."
            );
        }

        return value;
    }

}
