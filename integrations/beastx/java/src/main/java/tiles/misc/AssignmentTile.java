package tiles.misc;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.tiling.tiles.TilePriority;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BeastXState;

import java.util.OptionalLong;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class AssignmentTile extends AstNodeTile<Object, Stmt.Assignment, BeastXState> {

    AstNodeTileInput<Object, Stmt.Assignment, BeastXState> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (node instanceof Stmt.Assignment assignment && isMCMCConfigurationAssignment(assignment.name)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    @Override
    public Object applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.expressionInput.apply(beastState, indexVariables);
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return expressionInput.getTypeToken();
    }

    @Override
    public OptionalLong getFixedOutputSize() {
        return this.expressionInput.getTile() == null
                ? OptionalLong.empty()
                : this.expressionInput.getTile().getFixedOutputSize();
    }

    private static boolean isMCMCConfigurationAssignment(String name) {
        return Set.of(
                "chainLength",
                "randomSeed",
                "defaultLogEvery",
                "outputPrefix",
                "screenLogger",
                "fileLogger",
                "treeLogger"
        ).contains(name) || BeastXState.OperatorConfig.isSupportedSetting(name);
    }
}
