package tiles.functions;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.lexer.TokenType;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.tiling.tiles.TilePriority;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.ScaledScalar;

/**
 * Tiles {@code c * x} or {@code x * c}, a positive constant times a scalar, into a {@link ScaledScalar}.
 *
 * <p>The arithmetic tiles build an RPN calculator, which BEAST types as a tensor and which no
 * input asking for a {@code RealScalar} accepts, so arithmetic has only worked on the right of
 * an assignment. This tile covers the one product a partition's relative rate needs,
 * {@code rate=(N / n_i) * w[i]}, wherever a positive scalar is wanted. The two inner classes
 * cover the two operand orders.
 */
public abstract class ScaleTile extends AstNodeTile<RealScalar<NonNegativeReal>, Expr.Binary, BEASTState> {

    @Override
    public Class<Expr.Binary> getTargetNodeType() {
        return Expr.Binary.class;
    }

    /** Arithmetic keeps the RPN path wherever it fits, so this tile is only used where a scalar is required. */
    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!(node instanceof Expr.Binary binary) || binary.operator != TokenType.STAR) {
            throw new FailedTilingAttempt.Irrelevant();
        }
        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    static RealScalar<NonNegativeReal> scale(
            BEASTState beastState, double factor, RealScalar<? extends NonNegativeReal> scalar) {
        if (!(factor > 0) || !Double.isFinite(factor)) {
            throw new TileApplicationError(
                    "The factor " + factor + " is not greater than zero.",
                    "Use a positive constant, e.g. 3.0 * w[1] for three equal partitions.");
        }

        ScaledScalar scaled = new ScaledScalar();
        beastState.setInput(scaled, scaled.scalarInput, scalar);
        beastState.setInput(scaled, scaled.factorInput, factor);
        return scaled;
    }

    /** {@code c * x}. */
    public static class FactorFirst extends ScaleTile {

        AstNodeTileInput<Double, Expr.Binary, BEASTState> factorInput =
                new AstNodeTileInput<>("factor", expr -> expr.left);
        AstNodeTileInput<RealScalar<? extends NonNegativeReal>, Expr.Binary, BEASTState> scalarInput =
                new AstNodeTileInput<>("scalar", expr -> expr.right);

        @Override
        public RealScalar<NonNegativeReal> applyTile(
                BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return scale(
                    beastState,
                    this.factorInput.apply(beastState, indexVariables),
                    this.scalarInput.apply(beastState, indexVariables));
        }
    }

    /** {@code x * c}. */
    public static class FactorSecond extends ScaleTile {

        AstNodeTileInput<RealScalar<? extends NonNegativeReal>, Expr.Binary, BEASTState> scalarInput =
                new AstNodeTileInput<>("scalar", expr -> expr.left);
        AstNodeTileInput<Double, Expr.Binary, BEASTState> factorInput =
                new AstNodeTileInput<>("factor", expr -> expr.right);

        @Override
        public RealScalar<NonNegativeReal> applyTile(
                BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return scale(
                    beastState,
                    this.factorInput.apply(beastState, indexVariables),
                    this.scalarInput.apply(beastState, indexVariables));
        }
    }
}
