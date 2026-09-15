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
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.tiling.tiles.TilePriority;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

/** Configures the file prefix used by automatically created loggers and state output. */
public class OutputPrefixTile extends Tile<Void, BEASTState> implements CandidateTile<BEASTState> {

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!(node instanceof Stmt.Assignment assignment) || !assignment.name.equals("outputPrefix")) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        OutputPrefixTile tile = new OutputPrefixTile();
        tile.setRootNode(node);
        tile.setWeight(getPriority().getWeight());
        return Set.of(tile);
    }

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Stmt.Assignment assignment = (Stmt.Assignment) this.getRootNode();
        String prefix = getStringLiteral(assignment);
        if (prefix.isBlank()) {
            throw new TileApplicationError(
                    assignment,
                    "MCMC output prefix must not be blank.",
                    "Use a non-empty prefix, for example outputPrefix=\"results/myRun\".");
        }
        state.outputPrefix = prefix;
        return null;
    }

    private static String getStringLiteral(Stmt.Assignment assignment) {
        if (assignment.expression instanceof Expr.Literal literal && literal.value instanceof String value) {
            return value;
        }
        throw new TileApplicationError(
                assignment, "MCMC output prefix must be a constant string.", "Use outputPrefix=\"results/myRun\".");
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.CUSTOM;
    }
}
