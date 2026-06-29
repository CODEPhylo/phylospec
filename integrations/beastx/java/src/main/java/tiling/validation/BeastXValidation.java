package tiling.validation;

import org.phylospec.ast.AstNode;
import org.phylospec.tiling.errors.TileApplicationError;

import java.util.List;

public final class BeastXValidation {

    private BeastXValidation() {
    }

    public static void requireStrictlyIncreasing(
            double[] values,
            AstNode rootNode,
            String description,
            String hint,
            List<String> examples
    ) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] <= values[i - 1]) {
                throw new TileApplicationError(
                        rootNode,
                        description,
                        hint,
                        examples
                );
            }
        }
    }
}