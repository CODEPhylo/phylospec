package tiles.rpn;

import beast.base.spec.inference.util.RPNcalculator;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.TypeToken;

import java.util.IdentityHashMap;

/**
 * Finalizes an RPN expression by wiring the fully assembled {@link RPNCalculationResult}
 * into a BEAST {@code RPNcalculator} and registering it with the {@code BEASTState}.
 */
public class RPNAssignmentTile extends AstNodeTile<RPNCalculationResult, Stmt.Assignment, BEASTState> {

    AstNodeTileInput<RPNCalculationResult, Stmt.Assignment, BEASTState> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public RPNCalculationResult applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RPNCalculationResult calculationResult = this.expressionInput.apply(beastState, indexVariables);

        RPNcalculator rpnCalculator = new RPNcalculator();
        beastState.setInput(rpnCalculator, rpnCalculator.strExpressionInput, calculationResult.calculation());
        beastState.setInput(rpnCalculator, rpnCalculator.parametersInput, calculationResult.inputs());
        beastState.setInput(rpnCalculator, rpnCalculator.argNamesInput, String.join(",", calculationResult.names()));

        beastState.addCalculationNode(rpnCalculator, new TypeToken<RPNcalculator>() {
        }, this.getRootNode().name);

        return calculationResult;
    }

}
