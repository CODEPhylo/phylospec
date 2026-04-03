package tiles;

import patternmatching.BEASTState;
import patternmatching.GeneratorTile;
import patternmatching.Tile;

public class EnvTile extends GeneratorTile<String> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "env";
    }

    Input<String> variableInput = new Input<>("variable");

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
    protected Tile<String> createInstance() {
        return new EnvTile();
    }

}
