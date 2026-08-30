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

/**
 * Applies user-provided overrides to the automatically generated
 * BEAUti-style default operator schedule.
 *
 * <p>This tile configures operator weights and tuning parameters.
 * It does not allow users to define arbitrary operators.</p>
 */
public class DefaultOperatorConfigTile extends Tile<Void, BeastXState> implements CandidateTile<BeastXState> {

    private final String settingName;

    public DefaultOperatorConfigTile() {
        this.settingName =
                null;
    }

    private DefaultOperatorConfigTile(String settingName) {
        this.settingName =
                settingName;
    }

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

        if (!(assignment.block instanceof Stmt.Block.Mcmc)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        if (!BeastXState.OperatorConfig.isSupportedSetting(assignment.name)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        DefaultOperatorConfigTile tile =
                new DefaultOperatorConfigTile(assignment.name);

        tile.setRootNode(node);
        tile.setWeight(getPriority().getWeight());

        return Set.of(tile);
    }

    @Override
    protected Void applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Stmt.Assignment assignment =
                (Stmt.Assignment) this.getRootNode();

        double value =
                getRealLiteral(assignment.expression, assignment);

        validateValue(this.settingName, value, assignment);

        beastState.operatorConfig.set(this.settingName, value);

        return null;
    }

    private static double getRealLiteral(
            Expr expression,
            Stmt.Assignment assignment
    ) {
        if (!(expression instanceof Expr.Literal literal)) {
            throw new TileApplicationError(
                    assignment,
                    "MCMC operator setting must be a constant numeric value.",
                    "Use a literal value, for example parameterScaleFactor=0.75."
            );
        }

        if (!(literal.value instanceof Number number)) {
            throw new TileApplicationError(
                    assignment,
                    "MCMC operator setting must be a constant numeric value.",
                    "Use a literal value, for example parameterScaleFactor=0.75."
            );
        }

        return number.doubleValue();
    }

    private static void validateValue(
            String settingName,
            double value,
            Stmt.Assignment assignment
    ) {
        if (BeastXState.OperatorConfig.isScaleFactor(settingName)) {
            if (value <= 0.0 || value >= 1.0) {
                throw new TileApplicationError(
                        assignment,
                        "MCMC operator scale factor must be between 0 and 1.",
                        "Use a value such as parameterScaleFactor=0.75 or treeClockUpDownScaleFactor=0.75."
                );
            }

            return;
        }

        if (BeastXState.OperatorConfig.isPositiveSetting(settingName)) {
            if (value <= 0.0) {
                throw new TileApplicationError(
                        assignment,
                        "MCMC operator setting must be positive.",
                        "Use a positive value, for example randomWalkWindowSize=1.0."
                );
            }

            return;
        }

        if (BeastXState.OperatorConfig.isWeight(settingName)) {
            if (value < 0.0) {
                throw new TileApplicationError(
                        assignment,
                        "MCMC operator weight must not be negative.",
                        "Use a non-negative value, for example demographicOperatorWeight=3.0."
                );
            }

            return;
        }

        throw new TileApplicationError(
                assignment,
                "Unsupported MCMC operator setting.",
                "Use a supported setting such as parameterScaleFactor, clockRateOperatorWeight, or treeClockUpDownWeight."
        );
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.CUSTOM;
    }
}
