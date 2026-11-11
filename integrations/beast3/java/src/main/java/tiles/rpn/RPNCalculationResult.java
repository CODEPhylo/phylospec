package tiles.rpn;

import beast.base.core.BEASTInterface;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Tensor;
import beastconfig.BEASTState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.phylospec.lexer.TokenType;

/**
 * Holds an in-progress RPN expression as a string together with the BEAST tensors it
 * references and the names that identify each tensor within the expression.
 */
public record RPNCalculationResult(String calculation, List<Tensor> inputs, List<String> names) {

    public static RPNCalculationResult combine(
            TokenType operand, RPNCalculationResult left, RPNCalculationResult right) {
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

        String calculation = right.calculation;
        ;
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

    /**
     * Evaluates the expression with the current values of its scalar inputs, e.g. for an argument
     * that must be a constant number when the model is built.
     */
    public double evaluateConstant() {
        Deque<Double> stack = new ArrayDeque<>();
        for (String token : this.calculation.trim().split("\\s+")) {
            int index = this.names.indexOf(token);
            if (index >= 0) {
                Tensor<?, ?> input = this.inputs.get(index);
                if (input instanceof IntScalar<?> intScalar) {
                    stack.push((double) intScalar.get());
                } else if (input instanceof RealScalar<?> realScalar) {
                    stack.push(realScalar.get());
                } else {
                    throw new IllegalArgumentException(
                            "Only scalar inputs can be evaluated, but '" + token + "' is not.");
                }
                continue;
            }
            switch (token) {
                case "exp" -> stack.push(Math.exp(stack.pop()));
                case "log" -> stack.push(Math.log(stack.pop()));
                default -> {
                    double right = stack.pop();
                    double left = stack.pop();
                    stack.push(
                            switch (token) {
                                case "+" -> left + right;
                                case "-" -> left - right;
                                case "*" -> left * right;
                                case "/" -> left / right;
                                default -> throw new IllegalArgumentException("Unsupported operator '" + token + "'.");
                            });
                }
            }
        }
        return stack.pop();
    }
}
