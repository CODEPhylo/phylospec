package tiles.rpn;

import beast.base.spec.type.Tensor;
import beastconfig.BEASTState;
import tiles.GeneratorTile;
import tiling.TypeToken;

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
        protected RPNCalculationResult applyTile(BEASTState beastState) {
            RPNCalculationResult xRpn = this.xInput.apply(beastState);
            return RPNCalculationResult.combineUnary("log", xRpn);
        }
    }

    public static class Real extends LogRPNTile {

        GeneratorTileInput<? extends Tensor<?, ?>> xInput = new GeneratorTileInput<>("x");

        @Override
        protected RPNCalculationResult applyTile(BEASTState beastState) {
            Tensor<?, ?> x = this.xInput.apply(beastState);
            RPNCalculationResult xRpn = RPNCalculationResult.from(x, beastState);

            return RPNCalculationResult.combineUnary("log", xRpn);
        }
    }

}
