package tiles.rpn;

import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public abstract class SqrtRPNTile extends GeneratorTile<BeastXRPNCalculationResult, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sqrt";
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BeastXRPNCalculationResult>() {};
    }

    public static class Rpn extends SqrtRPNTile {

        GeneratorTileInput<BeastXRPNCalculationResult, BeastXState> xInput =
                new GeneratorTileInput<>("x");

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combineUnary(
                    "sqrt",
                    this.xInput.apply(beastState, indexVariables)
            );
        }
    }

    public static class Real extends SqrtRPNTile {

        GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BeastXState> xInput =
                new GeneratorTileInput<>("x");

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combineUnary(
                    "sqrt",
                    BeastXRPNCalculationResult.from(
                            this.xInput.apply(beastState, indexVariables),
                            beastState
                    )
            );
        }
    }
}