package tiles.rpn;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.lexer.TokenType;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.types.RealScalar;
import tiling.rpn.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public abstract class UnaryRPNTile extends AstNodeTile<BeastXRPNCalculationResult, Expr.Unary, BeastXState> {

    @Override
    public Class<Expr.Unary> getTargetNodeType() {
        return Expr.Unary.class;
    }

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Unary unary)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        if (unary.operator != TokenType.MINUS) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BeastXRPNCalculationResult>() {};
    }

    public static class Rpn extends UnaryRPNTile {

        AstNodeTileInput<BeastXRPNCalculationResult, Expr.Unary, BeastXState> rightInput =
                new AstNodeTileInput<>("right", expr -> expr.right);

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combineUnary(
                    "chs",
                    this.rightInput.apply(beastState, indexVariables)
            );
        }
    }

    public static class RealScalarInput extends UnaryRPNTile {

        AstNodeTileInput<RealScalar<? extends Real>, Expr.Unary, BeastXState> rightInput =
                new AstNodeTileInput<>("right", expr -> expr.right);

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combineUnary(
                    "chs",
                    BeastXRPNCalculationResult.from(
                            this.rightInput.apply(beastState, indexVariables),
                            beastState
                    )
            );
        }
    }
}
