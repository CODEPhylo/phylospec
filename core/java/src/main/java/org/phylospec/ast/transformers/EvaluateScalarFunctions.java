package org.phylospec.ast.transformers;

import java.util.List;
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
        // Transform children once, but do not erase a call that still needs validation.
        super.visitCall(expr);
        try {
            return switch (expr.functionName) {
                case "log" -> foldLog(expr);
                case "sqrt" -> foldSqrt(expr);
                case "exp" -> foldExp(expr);
                default -> expr;
            };
        } catch (ArgumentResolutionError | UnpackError e) {
            return expr;
        }
    }

    private Expr.Literal foldLog(Expr.Call expr) throws UnpackError {
        var arguments = expr.resolveArgumentNames(
                List.of(new Expr.Call.Parameter("x", true), new Expr.Call.Parameter("base", false)));
        double x = numericLiteral(arguments.get("x")).doubleValue();
        if (!(x > 0.0)) throw new UnpackError();

        double value = Math.log(x);
        if (arguments.containsKey("base")) {
            Number base = numericLiteral(arguments.get("base"));
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

    private Expr.Literal foldSqrt(Expr.Call expr) throws UnpackError {
        var arguments = expr.resolveArgumentNames(List.of(new Expr.Call.Parameter("x", true)));
        double x = numericLiteral(arguments.get("x")).doubleValue();
        if (!(x >= 0.0)) throw new UnpackError();

        double value = Math.sqrt(x);
        if (!Double.isFinite(value)) throw new UnpackError();

        Expr.Literal folded = new Expr.Literal(value);
        folded.attachRange(expr.getRange());
        return folded;
    }

    private Expr.Literal foldExp(Expr.Call expr) throws UnpackError {
        var arguments = expr.resolveArgumentNames(List.of(new Expr.Call.Parameter("x", true)));
        double x = numericLiteral(arguments.get("x")).doubleValue();
        // Preserve the existing Math.exp results, including floating-point underflow and overflow.
        Expr.Literal folded = new Expr.Literal(Math.exp(x));
        folded.attachRange(expr.getRange());
        return folded;
    }

    private Number numericLiteral(Expr.Argument argument) throws UnpackError {
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

    private class UnpackError extends Throwable {}
}
