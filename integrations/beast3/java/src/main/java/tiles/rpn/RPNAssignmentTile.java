package tiles.rpn;

import beast.base.spec.inference.util.RPNcalculator;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import tiles.AstNodeTile;
import tiling.TypeToken;

import java.util.IdentityHashMap;

public class RPNAssignmentTile extends AstNodeTile<RPNCalculationResult, Stmt.Assignment> {

    AstNodeTileInput<RPNCalculationResult, Stmt.Assignment> expressionInput = new AstNodeTileInput<>(
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
