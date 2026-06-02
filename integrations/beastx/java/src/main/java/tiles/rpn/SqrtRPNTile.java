package tiles.rpn;

import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.lexer.TokenType;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class SqrtRPNTile extends GeneratorTile<BeastXRPNCalculationResult, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sqrt";
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BeastXRPNCalculationResult>() {};
    }

    GeneratorTileInput<BeastXRPNCalculationResult, BeastXState> xInput =
            new GeneratorTileInput<>("x");

    @Override
    public BeastXRPNCalculationResult applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BeastXRPNCalculationResult logValue =
                BeastXRPNCalculationResult.combineUnary(
                        "log",
                        this.xInput.apply(beastState, indexVariables)
                );

        BeastXRPNCalculationResult half =
                BeastXRPNCalculationResult.from(
                        new Parameter.Default(0.5),
                        beastState
                );

        BeastXRPNCalculationResult halfLogValue =
                BeastXRPNCalculationResult.combine(
                        TokenType.STAR,
                        half,
                        logValue
                );

        return BeastXRPNCalculationResult.combineUnary(
                "exp",
                halfLogValue
        );
    }
}