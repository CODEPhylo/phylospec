package tiles.mcmc;

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
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class DefaultLogEveryTile extends TemplateTile<Void, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Any defaultLogEvery = $defaultLogEvery
                }""";
    }

    public TemplateTileInput<Integer, BeastXState> defaultLogEveryInput =
            new TemplateTileInput<>("$defaultLogEvery", Set.of(Stochasticity.CONSTANT));

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

        if (!assignment.name.equals("defaultLogEvery")) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int defaultLogEvery =
                this.defaultLogEveryInput.apply(beastState, indexVariables);

        if (defaultLogEvery <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MCMC default logger frequency must be positive.",
                    "Use a positive integer, for example defaultLogEvery=1000."
            );
        }

        beastState.defaultLogEvery = defaultLogEvery;
        return null;
    }
}