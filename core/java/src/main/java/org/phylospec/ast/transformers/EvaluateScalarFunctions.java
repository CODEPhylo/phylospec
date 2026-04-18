package org.phylospec.ast.transformers;


import org.phylospec.ast.AstTransformer;
import org.phylospec.ast.Expr;

import java.util.Objects;

/**
 * This transformation evaluates all scalar functions if their arguments are literals.
 */
public class EvaluateScalarFunctions extends AstTransformer {

    @Override
    public Expr visitCall(Expr.Call expr) {
        try {
            return switch (expr.functionName) {
                case "exp" -> new Expr.Literal(
                        Math.exp(this.get("x", expr.arguments, true))
                );
                case "sqrt" -> new Expr.Literal(
                        Math.sqrt(this.get("x", expr.arguments, true))
                );
                case "log" -> new Expr.Literal(
                        Math.log(this.get("x", expr.arguments, true)) / Math.log(this.get("base", expr.arguments, false, Math.E))
                );
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

    private double get(String name, Expr.Argument[] arguments, boolean isFirst, double defaultValue) throws UnpackError {
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

        return defaultValue;
    }

    private double unpackDouble(Expr expression) throws UnpackError {
        if (!(expression instanceof Expr.Literal literal)) throw new UnpackError();

        if (!(literal.value instanceof Number number)) throw new UnpackError();

        return number.doubleValue();
    }

    private class UnpackError extends Throwable {
    }
}

