package tiles.rpn;

import beast.base.spec.inference.util.RPNcalculator;
import beastconfig.BEASTState;
import org.phylospec.ast.Stmt;
import tiles.AstNodeTile;
import tiling.TypeToken;

import java.util.Map;

public class RPNAssignmentTile extends AstNodeTile<RPNCalculationResult, Stmt.Assignment> {

    AstNodeTileInput<RPNCalculationResult, Stmt.Assignment> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Class<Stmt.Assignment> getTargetNodeType() {
        return Stmt.Assignment.class;
    }

    @Override
    public RPNCalculationResult applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
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
