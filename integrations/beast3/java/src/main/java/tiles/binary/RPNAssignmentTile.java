package tiles.binary;

import beast.base.spec.inference.util.RPNcalculator;
import beastconfig.BEASTState;
import org.phylospec.ast.Stmt;
import tiles.AstNodeTile;
import tiling.TypeToken;

public class RPNAssignmentTile extends AstNodeTile<RPNCalculationResult, Stmt.Assignment> {

    AstNodeTileInput<RPNCalculationResult, Stmt.Assignment> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Class<Stmt.Assignment> getTargetNodeType() {
        return Stmt.Assignment.class;
    }

    @Override
    public RPNCalculationResult applyTile(BEASTState beastState) {
        RPNCalculationResult calculationResult = this.expressionInput.apply(beastState);

        RPNcalculator rpnCalculator = new RPNcalculator();
        beastState.setInput(rpnCalculator, rpnCalculator.strExpressionInput, calculationResult.calculation());
        beastState.setInput(rpnCalculator, rpnCalculator.parametersInput, calculationResult.inputs());
        beastState.setInput(rpnCalculator, rpnCalculator.argNamesInput, String.join(",", calculationResult.names()));

        beastState.addCalculationNode(rpnCalculator, new TypeToken<RPNcalculator>() {
        }, this.getRootNode().name);

        return calculationResult;
    }

}
