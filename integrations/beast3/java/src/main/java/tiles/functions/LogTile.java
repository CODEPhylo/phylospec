package tiles.functions;

import tiling.BEASTState;
import tiling.Tile;
import tiling.TilingError;
import tiles.GeneratorTile;

public class LogTile extends GeneratorTile<String> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "log";
    }

    Input<String> variableInput = new Input<>("x");

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

    @Override
    protected Tile<?> createInstance() {
        return new LogTile();
    }

}
