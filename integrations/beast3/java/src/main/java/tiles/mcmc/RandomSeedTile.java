package tiles.mcmc;

import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

/** Configures the random seed used for a reproducible BEAST run. */
public class RandomSeedTile extends TemplateTile<Void, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Any randomSeed = $randomSeed
                }""";
    }

    public TemplateTileInput<Integer, BEASTState> randomSeedInput =
            new TemplateTileInput<>("$randomSeed", Set.of(Stochasticity.CONSTANT));

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!(node instanceof Stmt.Assignment assignment) || !assignment.name.equals("randomSeed")) {
            throw new FailedTilingAttempt.Irrelevant();
        }
        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int seed = this.randomSeedInput.apply(state, indexVariables);
        if (seed < 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MCMC random seed must not be negative.",
                    "Use a non-negative integer, for example randomSeed=12345.");
        }
        state.randomSeed = (long) seed;
        return null;
    }
}
