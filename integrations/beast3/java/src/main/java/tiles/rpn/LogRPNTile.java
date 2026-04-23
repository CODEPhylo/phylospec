package tiles.rpn;

import beast.base.spec.type.Tensor;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import tiling.TypeToken;

import java.util.IdentityHashMap;

public abstract class LogRPNTile extends GeneratorTile<RPNCalculationResult> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "log";
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<RPNCalculationResult>() {
        };
    }

    public static class Rpn extends LogRPNTile {

        GeneratorTileInput<RPNCalculationResult> xInput = new GeneratorTileInput<>("x");

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            RPNCalculationResult xRpn = this.xInput.apply(beastState, indexVariables);
            return RPNCalculationResult.combineUnary("log", xRpn);
        }
    }

    public static class Real extends LogRPNTile {

        GeneratorTileInput<? extends Tensor<?, ?>> xInput = new GeneratorTileInput<>("x");

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            Tensor<?, ?> x = this.xInput.apply(beastState, indexVariables);
            RPNCalculationResult xRpn = RPNCalculationResult.from(x, beastState);

            return RPNCalculationResult.combineUnary("log", xRpn);
        }
    }

}
