package tiles.mcmc;

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
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class OutputPrefixTile extends Tile<Void, BeastXState> implements CandidateTile<BeastXState> {

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (!(node instanceof Stmt.Assignment assignment)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        if (!assignment.name.equals("outputPrefix")) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        OutputPrefixTile tile =
                new OutputPrefixTile();

        tile.setRootNode(node);
        tile.setWeight(getPriority().getWeight());

        return Set.of(tile);
    }

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Stmt.Assignment assignment =
                (Stmt.Assignment) this.getRootNode();

        String outputPrefix =
                getStringLiteral(assignment.expression, assignment);

        if (outputPrefix.isBlank()) {
            throw new TileApplicationError(
                    assignment,
                    "MCMC output prefix must not be blank.",
                    "Use a non-empty prefix, for example outputPrefix=\"target/results/myRun\"."
            );
        }

        beastState.outputPrefix = outputPrefix;
        return null;
    }

    private static String getStringLiteral(Expr expression, Stmt.Assignment assignment) {
        if (!(expression instanceof Expr.Literal literal)) {
            throw new TileApplicationError(
                    assignment,
                    "MCMC output prefix must be a constant string.",
                    "Use outputPrefix=\"target/results/myRun\"."
            );
        }

        if (!(literal.value instanceof String value)) {
            throw new TileApplicationError(
                    assignment,
                    "MCMC output prefix must be a constant string.",
                    "Use outputPrefix=\"target/results/myRun\"."
            );
        }

        return value;
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.CUSTOM;
    }
}