package tiles.rpn;

import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveInt;
import org.phylospec.domain.PositiveReal;
import org.phylospec.lexer.TokenType;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import org.phylospec.types.RealScalar;
import tiling.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

public abstract class LogRPNTile extends GeneratorTile<BeastXRPNCalculationResult, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "log";
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BeastXRPNCalculationResult>() {};
    }

    protected BeastXRPNCalculationResult applyLogBase(
            BeastXRPNCalculationResult value,
            IntScalar<? extends PositiveInt> base,
            BeastXState beastState
    ) {
        BeastXRPNCalculationResult naturalLog =
                BeastXRPNCalculationResult.combineUnary(
                        "log",
                        value
                );

        if (base == null) {
            return naturalLog;
        }

        if (base.get() == 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Log base must not be 1.",
                    "Use base greater than 1, or omit base for the natural logarithm.",
                    List.of("log(x=x * x + 4.0, base=2)")
            );
        }

        BeastXRPNCalculationResult logBase =
                BeastXRPNCalculationResult.from(
                        new Parameter.Default(Math.log(base.get())),
                        beastState
                );

        return BeastXRPNCalculationResult.combine(
                TokenType.SLASH,
                naturalLog,
                logBase
        );
    }

    public static class Rpn extends LogRPNTile {

        GeneratorTileInput<BeastXRPNCalculationResult, BeastXState> xInput =
                new GeneratorTileInput<>("x");

        GeneratorTileInput<IntScalar<? extends PositiveInt>, BeastXState> baseInput =
                new GeneratorTileInput<>("base", false);

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return this.applyLogBase(
                    this.xInput.apply(beastState, indexVariables),
                    this.baseInput.apply(beastState, indexVariables),
                    beastState
            );
        }
    }

    public static class Real extends LogRPNTile {

        GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> xInput =
                new GeneratorTileInput<>("x");

        GeneratorTileInput<IntScalar<? extends PositiveInt>, BeastXState> baseInput =
                new GeneratorTileInput<>("base", false);

        @Override
        public BeastXRPNCalculationResult applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            return this.applyLogBase(
                    BeastXRPNCalculationResult.from(
                            this.xInput.apply(beastState, indexVariables),
                            beastState
                    ),
                    this.baseInput.apply(beastState, indexVariables),
                    beastState
            );
        }
    }
}