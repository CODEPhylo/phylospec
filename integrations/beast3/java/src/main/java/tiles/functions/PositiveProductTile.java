package tiles.functions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.lexer.TokenType;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.tiling.tiles.TilePriority;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.PositiveScalarProduct;

/** Binds two live positive factors where an input such as StrictClock.clockRate requires a scalar. */
public final class PositiveProductTile extends AstNodeTile<PositiveScalarProduct, Expr.Binary, BEASTState> {
    AstNodeTileInput<RealScalar<? extends PositiveReal>, Expr.Binary, BEASTState> leftInput =
            new AstNodeTileInput<>("left", expr -> expr.left, Set.of(Stochasticity.STOCHASTIC));
    AstNodeTileInput<RealScalar<? extends PositiveReal>, Expr.Binary, BEASTState> rightInput =
            new AstNodeTileInput<>("right", expr -> expr.right, Set.of(Stochasticity.STOCHASTIC));

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> inputs,
            VariableResolver variables,
            StochasticityResolver stochasticity)
            throws FailedTilingAttempt {
        if (!(node instanceof Expr.Binary binary) || binary.operator != TokenType.STAR) {
            throw new FailedTilingAttempt.Irrelevant();
        }
        return super.tryToTile(node, inputs, variables, stochasticity);
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

    @Override
    public PositiveScalarProduct applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var product = new PositiveScalarProduct();
        state.setInput(product, product.leftInput, leftInput.apply(state, indices));
        state.setInput(product, product.rightInput, rightInput.apply(state, indices));
        return product;
    }
}
