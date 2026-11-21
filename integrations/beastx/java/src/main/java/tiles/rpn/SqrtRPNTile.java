package tiles.rpn;

import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.types.RealScalar;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.lexer.TokenType;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.rpn.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;

/**
 * {@code sqrt(x)} as an RPN calculation, written as {@code exp(0.5 * log(x))}.
 *
 * <p>Like {@code exp} and {@code log}, there is one tile for an RPN expression and one for a
 * plain real value, such as a sampled parameter.</p>
 */
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
            return sqrt(this.xInput.apply(beastState, indexVariables), beastState);
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
            return sqrt(
                    BeastXRPNCalculationResult.from(this.xInput.apply(beastState, indexVariables), beastState),
                    beastState
            );
        }
    }

    private static BeastXRPNCalculationResult sqrt(BeastXRPNCalculationResult x, BeastXState beastState) {
        BeastXRPNCalculationResult logValue =
                BeastXRPNCalculationResult.combineUnary("log", x);

        BeastXRPNCalculationResult half =
                BeastXRPNCalculationResult.from(new Parameter.Default(0.5), beastState);

        BeastXRPNCalculationResult halfLogValue =
                BeastXRPNCalculationResult.combine(TokenType.STAR, half, logValue);

        return BeastXRPNCalculationResult.combineUnary("exp", halfLogValue);
    }
}
