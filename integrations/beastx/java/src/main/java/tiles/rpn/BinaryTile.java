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
import tiling.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public abstract class BinaryTile extends AstNodeTile<BeastXRPNCalculationResult, Expr.Binary, BeastXState> {

    @Override
    public Class<Expr.Binary> getTargetNodeType() {
        return Expr.Binary.class;
    }

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Binary binary)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        Set<TokenType> supported =
                Set.of(TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH);

        if (!supported.contains(binary.operator)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BeastXRPNCalculationResult>() {};
    }

    public static class RpnRpn extends BinaryTile {

        AstNodeTileInput<BeastXRPNCalculationResult, Expr.Binary, BeastXState> leftInput =
                new AstNodeTileInput<>("left", expr -> expr.left);

        AstNodeTileInput<BeastXRPNCalculationResult, Expr.Binary, BeastXState> rightInput =
                new AstNodeTileInput<>("right", expr -> expr.right);

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combine(
                    this.getRootNode().operator,
                    this.leftInput.apply(beastState, indexVariables),
                    this.rightInput.apply(beastState, indexVariables)
            );
        }
    }

    public static class RpnReal extends BinaryTile {

        AstNodeTileInput<BeastXRPNCalculationResult, Expr.Binary, BeastXState> leftInput =
                new AstNodeTileInput<>("left", expr -> expr.left);

        AstNodeTileInput<RealScalar<? extends Real>, Expr.Binary, BeastXState> rightInput =
                new AstNodeTileInput<>("right", expr -> expr.right);

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combine(
                    this.getRootNode().operator,
                    this.leftInput.apply(beastState, indexVariables),
                    BeastXRPNCalculationResult.from(
                            this.rightInput.apply(beastState, indexVariables),
                            beastState
                    )
            );
        }
    }

    public static class RealRpn extends BinaryTile {

        AstNodeTileInput<RealScalar<? extends Real>, Expr.Binary, BeastXState> leftInput =
                new AstNodeTileInput<>("left", expr -> expr.left);

        AstNodeTileInput<BeastXRPNCalculationResult, Expr.Binary, BeastXState> rightInput =
                new AstNodeTileInput<>("right", expr -> expr.right);

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combine(
                    this.getRootNode().operator,
                    BeastXRPNCalculationResult.from(
                            this.leftInput.apply(beastState, indexVariables),
                            beastState
                    ),
                    this.rightInput.apply(beastState, indexVariables)
            );
        }
    }

    public static class RealReal extends BinaryTile {

        AstNodeTileInput<RealScalar<? extends Real>, Expr.Binary, BeastXState> leftInput =
                new AstNodeTileInput<>("left", expr -> expr.left);

        AstNodeTileInput<RealScalar<? extends Real>, Expr.Binary, BeastXState> rightInput =
                new AstNodeTileInput<>("right", expr -> expr.right);

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combine(
                    this.getRootNode().operator,
                    BeastXRPNCalculationResult.from(
                            this.leftInput.apply(beastState, indexVariables),
                            beastState
                    ),
                    BeastXRPNCalculationResult.from(
                            this.rightInput.apply(beastState, indexVariables),
                            beastState
                    )
            );
        }
    }
}