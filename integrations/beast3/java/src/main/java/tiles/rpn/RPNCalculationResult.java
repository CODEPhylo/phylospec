package tiles.rpn;

import beast.base.core.BEASTInterface;
import beast.base.spec.type.Tensor;
import beastconfig.BEASTState;
import org.phylospec.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

public record RPNCalculationResult(String calculation, List<Tensor> inputs, List<String> names) {

    public static RPNCalculationResult combine(TokenType operand, RPNCalculationResult left, RPNCalculationResult right) {
        String calculation = "";
        List<Tensor> inputs = new ArrayList<>();
        List<String> names = new ArrayList<>();

        // add left operand

        calculation += left.calculation;
        inputs.addAll(left.inputs);
        names.addAll(left.names);

        // add right operands
        // we assume that there are no name conflicts

        calculation += " " + right.calculation;
        inputs.addAll(right.inputs);
        names.addAll(right.names);

        // add operation

        calculation += " " + TokenType.getLexeme(operand);

        return new RPNCalculationResult(calculation, inputs, names);
    }

    public static RPNCalculationResult combineUnary(String operand, RPNCalculationResult right) {
        // add right operand

        String calculation = right.calculation;;
        List<Tensor> inputs = new ArrayList<>(right.inputs);
        List<String> names = new ArrayList<>(right.names);

        // add operation

        calculation += " " + operand;

        return new RPNCalculationResult(calculation, inputs, names);
    }

    public static RPNCalculationResult from(Tensor right, BEASTState beastState) {
        BEASTInterface rightBeastObject = (BEASTInterface) right;

        if (rightBeastObject.getID() == null) {
            rightBeastObject.setID(beastState.getAvailableID("var"));
        }

        String name = rightBeastObject.getID();
        return new RPNCalculationResult(name, List.of(right), List.of(name));
    }

}
