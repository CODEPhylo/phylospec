package tiles.rpn;

import beast.base.spec.type.Tensor;
import beastconfig.BEASTState;
import tiles.GeneratorTile;
import tiling.TypeToken;

import java.util.Map;

public abstract class ExpRPNTile extends GeneratorTile<RPNCalculationResult> {

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

        GeneratorTileInput<RPNCalculationResult> xInput = new GeneratorTileInput<>("x");

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
            RPNCalculationResult xRpn = this.xInput.apply(beastState, indexVariables);
            return RPNCalculationResult.combineUnary("exp", xRpn);
        }
    }

    public static class Real extends ExpRPNTile {

        GeneratorTileInput<? extends Tensor<?, ?>> xInput = new GeneratorTileInput<>("x");

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
            Tensor<?, ?> x = this.xInput.apply(beastState, indexVariables);
            RPNCalculationResult xRpn = RPNCalculationResult.from(x, beastState);

            return RPNCalculationResult.combineUnary("exp", xRpn);
        }
    }

}
