package tiles.rpn;

import beast.base.spec.type.Tensor;
import beastconfig.BEASTState;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.lexer.TokenType;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.AstNodeTile;
import tiling.FailedTilingAttempt;
import tiling.Tile;
import tiling.TypeToken;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public abstract class BinaryTile extends AstNodeTile<RPNCalculationResult, Expr.Binary> {

    @Override
    public Class<Expr.Binary> getTargetNodeType() {
        return Expr.Binary.class;
    }

    @Override
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Binary binary)) throw new FailedTilingAttempt.Irrelevant();

        // check if we support the operator

        Set<TokenType> relevantTokens = Set.of(TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH);
        if (!relevantTokens.contains(binary.operator)) throw new FailedTilingAttempt.Irrelevant();

        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<RPNCalculationResult>() {
        };
    }

    public static class RpnRpn extends BinaryTile {

        AstNodeTileInput<RPNCalculationResult, Expr.Binary> leftInput = new AstNodeTileInput<>(
                "leftExpression", expr -> expr.left
        );
        AstNodeTileInput<RPNCalculationResult, Expr.Binary> rightInput = new AstNodeTileInput<>(
                "rightExpression", expr -> expr.right
        );

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            RPNCalculationResult leftRpn = this.leftInput.apply(beastState, indexVariables);
            RPNCalculationResult rightRpn = this.rightInput.apply(beastState, indexVariables);

            TokenType operation = this.getRootNode().operator;

            return RPNCalculationResult.combine(operation, leftRpn, rightRpn);
        }
    }

    public static class RpnReal extends BinaryTile {

        AstNodeTileInput<RPNCalculationResult, Expr.Binary> leftInput = new AstNodeTileInput<>(
                "leftExpression", expr -> expr.left
        );
        AstNodeTileInput<? extends Tensor<?, ?>, Expr.Binary> rightInput = new AstNodeTileInput<>(
                "rightExpression", expr -> expr.right
        );

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            RPNCalculationResult leftRpn = this.leftInput.apply(beastState, indexVariables);

            Tensor<?, ?> right = this.rightInput.apply(beastState, indexVariables);
            RPNCalculationResult rightRpn = RPNCalculationResult.from(right, beastState);

            TokenType operation = this.getRootNode().operator;

            return RPNCalculationResult.combine(operation, leftRpn, rightRpn);
        }
    }

    public static class RealRpn extends BinaryTile {

        AstNodeTileInput<? extends Tensor<?, ?>, Expr.Binary> leftInput = new AstNodeTileInput<>(
                "leftExpression", expr -> expr.left
        );
        AstNodeTileInput<RPNCalculationResult, Expr.Binary> rightInput = new AstNodeTileInput<>(
                "rightExpression", expr -> expr.right
        );

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            Tensor<?, ?> left = this.leftInput.apply(beastState, indexVariables);
            RPNCalculationResult leftRpn = RPNCalculationResult.from(left, beastState);

            RPNCalculationResult rightRpn = this.rightInput.apply(beastState, indexVariables);

            TokenType operation = this.getRootNode().operator;

            return RPNCalculationResult.combine(operation, leftRpn, rightRpn);
        }
    }

    public static class RealReal extends BinaryTile {

        AstNodeTileInput<? extends Tensor<?, ?>, Expr.Binary> leftInput = new AstNodeTileInput<>(
                "leftExpression", expr -> expr.left
        );
        AstNodeTileInput<? extends Tensor<?, ?>, Expr.Binary> rightInput = new AstNodeTileInput<>(
                "rightExpression", expr -> expr.right
        );

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            Tensor<?, ?> left = this.leftInput.apply(beastState, indexVariables);
            RPNCalculationResult leftRpn = RPNCalculationResult.from(left, beastState);

            Tensor<?, ?> right = this.rightInput.apply(beastState, indexVariables);
            RPNCalculationResult rightRpn = RPNCalculationResult.from(right, beastState);

            TokenType operation = this.getRootNode().operator;

            return RPNCalculationResult.combine(operation, leftRpn, rightRpn);
        }
    }

}
