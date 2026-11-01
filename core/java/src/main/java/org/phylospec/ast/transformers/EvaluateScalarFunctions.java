package org.phylospec.ast.transformers;

import java.util.List;
import java.util.Objects;
import org.phylospec.ast.ArgumentResolutionError;
import org.phylospec.ast.AstTransformer;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Unit;

/**
 * This transformation evaluates all scalar functions if their arguments are literals.
 */
public class EvaluateScalarFunctions extends AstTransformer {

    @Override
    public Expr visitCall(Expr.Call expr) {
        if ("log".equals(expr.functionName)) {
            // Transform children once, but do not erase a call that still needs validation.
            super.visitCall(expr);
            try {
                return foldLog(expr);
            } catch (ArgumentResolutionError | UnpackError e) {
                return expr;
            }
        }
        try {
            return switch (expr.functionName) {
                case "exp" -> new Expr.Literal(Math.exp(this.get("x", expr.arguments, true)));
                case "sqrt" -> new Expr.Literal(Math.sqrt(this.get("x", expr.arguments, true)));
                default -> super.visitCall(expr);
            };
        } catch (UnpackError e) {
            return super.visitCall(expr);
        }
    }

    private double get(String name, Expr.Argument[] arguments, boolean isFirst) throws UnpackError {
        if (arguments.length == 0) {
            throw new UnpackError();
        }

        for (Expr.Argument argument : arguments) {
            if (Objects.equals(argument.name, name)) {
                return this.unpackDouble(argument.expression);
            }
        }

        if (isFirst && arguments[0].name == null) {
            return this.unpackDouble(arguments[0].expression);
        }

        throw new UnpackError();
    }

    private Expr.Literal foldLog(Expr.Call expr) throws UnpackError {
        var arguments = expr.resolveArgumentNames(
                List.of(new Expr.Call.Parameter("x", true), new Expr.Call.Parameter("base", false)));
        double x = logLiteral(arguments.get("x")).doubleValue();
        if (!(x > 0.0)) throw new UnpackError();

        double value = Math.log(x);
        if (arguments.containsKey("base")) {
            Number base = logLiteral(arguments.get("base"));
            // Preserve the core Integer requirement, including for integral-looking doubles.
            if (!(base instanceof Integer || base instanceof Long) || base.longValue() <= 1) {
                throw new UnpackError();
            }
            value /= Math.log(base.doubleValue());
        }
        if (!Double.isFinite(value)) throw new UnpackError();

        Expr.Literal folded = new Expr.Literal(value);
        folded.attachRange(expr.getRange());
        return folded;
    }

    private Number logLiteral(Expr.Argument argument) throws UnpackError {
        if (!(argument instanceof Expr.AssignedArgument)
                || !(argument.expression instanceof Expr.Literal literal)
                || literal.unit != Unit.IMPLICIT) {
            throw new UnpackError();
        }
        if (literal.value instanceof Integer
                || literal.value instanceof Long
                || literal.value instanceof Float
                || literal.value instanceof Double) {
            return (Number) literal.value;
        }
        throw new UnpackError();
    }

    private double unpackDouble(Expr expression) throws UnpackError {
        if (!(expression instanceof Expr.Literal literal)) throw new UnpackError();

        if (!(literal.value instanceof Number number)) throw new UnpackError();

        return number.doubleValue();
    }

    private class UnpackError extends Throwable {}
}
