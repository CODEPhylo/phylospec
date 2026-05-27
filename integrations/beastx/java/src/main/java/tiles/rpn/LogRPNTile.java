package tiles.rpn;

import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public abstract class LogRPNTile extends GeneratorTile<BeastXRPNCalculationResult, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "log";
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BeastXRPNCalculationResult>() {};
    }

    public static class Rpn extends LogRPNTile {

        GeneratorTileInput<BeastXRPNCalculationResult, BeastXState> xInput =
                new GeneratorTileInput<>("x");

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combineUnary(
                    "log",
                    this.xInput.apply(beastState, indexVariables)
            );
        }
    }

    public static class Real extends LogRPNTile {

        GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> xInput =
                new GeneratorTileInput<>("x");

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return BeastXRPNCalculationResult.combineUnary(
                    "log",
                    BeastXRPNCalculationResult.from(
                            this.xInput.apply(beastState, indexVariables),
                            beastState
                    )
            );
        }
    }
}