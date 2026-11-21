package tiles.trees;

import org.phylospec.ast.AstNode;
import org.phylospec.tiling.errors.TileApplicationError;

import java.util.List;

/**
 * The {@code rootAge} argument of the speciation tree priors, which BEAST X does not support yet.
 *
 * <p>BEAST X would only use it for the starting tree: the root would then move freely during
 * MCMC, and the density would not be conditioned on it. A given rootAge is therefore rejected.</p>
 */
final class UnsupportedRootAge {

    private UnsupportedRootAge() {
    }

    static void reject(AstNode node, Object rootAge, String generatorName) {
        if (rootAge != null) {
            throw new TileApplicationError(
                    node,
                    "BEAST X does not support rootAge in " + generatorName + " yet.",
                    "Remove rootAge, or calibrate the root with Age root = rootAge(tree) observed between [lower, upper].",
                    List.of("Age root = rootAge(tree=tree) observed between [5.0, 10.0]")
            );
        }
    }
}
