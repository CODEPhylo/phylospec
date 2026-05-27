package tiles.rpn;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public abstract class ExpRPNTile extends GeneratorTile<BeastXRPNCalculationResult, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exp";
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BeastXRPNCalculationResult>() {};
    }

    public static class Rpn extends ExpRPNTile {

        GeneratorTileInput<BeastXRPNCalculationResult, BeastXState> xInput =
                new GeneratorTileInput<>("x");

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combineUnary(
                    "exp",
                    this.xInput.apply(beastState, indexVariables)
            );
        }
    }

    public static class Real extends ExpRPNTile {

        GeneratorTileInput<RealScalar<? extends org.phylospec.domain.Real>, BeastXState> xInput =
                new GeneratorTileInput<>("x");

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combineUnary(
                    "exp",
                    BeastXRPNCalculationResult.from(
                            this.xInput.apply(beastState, indexVariables),
                            beastState
                    )
            );
        }
    }
}