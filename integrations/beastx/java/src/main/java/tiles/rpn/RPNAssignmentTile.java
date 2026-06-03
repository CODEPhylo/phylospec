package tiles.rpn;

import dr.inference.model.RPNcalculatorStatistic;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.TilePriority;
import tiling.rpn.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class RPNAssignmentTile extends AstNodeTile<BeastXRPNCalculationResult, Stmt.Assignment, BeastXState> {

    AstNodeTileInput<BeastXRPNCalculationResult, Stmt.Assignment, BeastXState> expressionInput =
            new AstNodeTileInput<>(
                    "expression",
                    expr -> expr.expression
            );

    @Override
    public BeastXRPNCalculationResult applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BeastXRPNCalculationResult calculationResult =
                this.expressionInput.apply(beastState, indexVariables);

        String id =
                this.getId(this.getRootNode().name, indexVariables, "");

        RPNcalculatorStatistic statistic =
                new RPNcalculatorStatistic(
                        id,
                        new String[]{calculationResult.calculation()},
                        new String[]{id},
                        calculationResult.variables()
                );

        beastState.addCalculationNode(
                statistic,
                new TypeToken<RPNcalculatorStatistic>() {},
                id
        );

        return BeastXRPNCalculationResult.from(statistic, beastState);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BeastXRPNCalculationResult>() {};
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.CUSTOM;
    }
}
