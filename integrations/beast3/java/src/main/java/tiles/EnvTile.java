package tiles;

import patternmatching.FunctionTile;
import patternmatching.TypeToken;

public class EnvTile extends FunctionTile<String> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "env";
    }

    Input<String> variable = new Input<>("variable", new TypeToken<>() {});

    @Override
    protected String apply() {
        String value = System.getenv(this.variable.get());

        if (value == null) {
            throw new TilingError(
                    "Environment variable '" + this.variable.get() + "' is not set.",
                    "Set the environment variable."
            );
        }

        return value;
    }

}
