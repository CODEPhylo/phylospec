package tiles.rpn;

import beast.base.spec.type.Tensor;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.tiling.TypeToken;

import java.util.IdentityHashMap;

/**
 * Tiles the {@code exp} generator into an RPN fragment.
 */
public abstract class ExpRPNTile extends GeneratorTile<RPNCalculationResult, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exp";
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<RPNCalculationResult>() {
        };
    }

    public static class Rpn extends ExpRPNTile {

        GeneratorTileInput<RPNCalculationResult, BEASTState> xInput = new GeneratorTileInput<>("x");

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            RPNCalculationResult xRpn = this.xInput.apply(beastState, indexVariables);
            return RPNCalculationResult.combineUnary("exp", xRpn);
        }
    }

    public static class Real extends ExpRPNTile {

        GeneratorTileInput<? extends Tensor<?, ?>, BEASTState> xInput = new GeneratorTileInput<>("x");

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            Tensor<?, ?> x = this.xInput.apply(beastState, indexVariables);
            RPNCalculationResult xRpn = RPNCalculationResult.from(x, beastState);

            return RPNCalculationResult.combineUnary("exp", xRpn);
        }
    }

}
