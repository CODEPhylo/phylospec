package tiles;

import patternmatching.FunctionTile;
import patternmatching.TypeToken;

public class EnvTile extends FunctionTile<String> {

    Input<String> variable = new Input<>("variable", new TypeToken<>() {});

    @Override
    protected String apply() {
        String value = System.getenv(this.variable.getValue());

        if (value == null) {
            throw new TilingError(
                    "Environment variable '" + this.variable.getValue() + "' is not set.",
                    "Set the environment variable."
            );
        }

        return value;
    }

    @Override
    public String getGeneratorName() {
        return "env";
    }

}
