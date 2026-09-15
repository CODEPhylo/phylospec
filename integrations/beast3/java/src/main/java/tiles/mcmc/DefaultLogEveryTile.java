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

/** Configures the sampling interval used by automatically created loggers. */
public class DefaultLogEveryTile extends TemplateTile<Void, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Any defaultLogEvery = $defaultLogEvery
                }""";
    }

    public TemplateTileInput<Integer, BEASTState> defaultLogEveryInput =
            new TemplateTileInput<>("$defaultLogEvery", Set.of(Stochasticity.CONSTANT));

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!(node instanceof Stmt.Assignment assignment) || !assignment.name.equals("defaultLogEvery")) {
            throw new FailedTilingAttempt.Irrelevant();
        }
        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int logEvery = this.defaultLogEveryInput.apply(state, indexVariables);
        if (logEvery <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MCMC default logger frequency must be positive.",
                    "Use a positive integer, for example defaultLogEvery=1000.");
        }
        state.defaultLogEvery = logEvery;
        return null;
    }
}
