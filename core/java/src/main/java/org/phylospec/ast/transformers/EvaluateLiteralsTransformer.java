package org.phylospec.ast.transformers;


import org.phylospec.ast.AstTransformer;
import org.phylospec.ast.Expr;
import org.phylospec.lexer.TokenType;

/**
 * This transformation evaluates all unary and binary operation on literals, as well as deterministic
 * tra
 */
public class EvaluateLiteralsTransformer extends AstTransformer {
    @Override
    public Expr visitUnary(Expr.Unary expr) {
        expr.right = expr.right.accept(this);
        if (!(expr.right instanceof Expr.Literal right)) return expr;

        return switch (expr.operator) {
            case TokenType.MINUS -> {
                if (right.value instanceof Float r) yield new Expr.Literal(-r);
                if (right.value instanceof Double r) yield new Expr.Literal(-r);
                if (right.value instanceof Integer r) yield new Expr.Literal(-r);
                if (right.value instanceof Long r) yield new Expr.Literal(-r);
                yield expr;
            }
            case TokenType.BANG -> {
                if (right.value instanceof Boolean) yield new Expr.Literal(!(boolean) right.value);
                yield expr;
            }
            default -> expr;
        };
    }

    @Override
    public Expr visitBinary(Expr.Binary expr) {
        expr.left = expr.left.accept(this);
        expr.right = expr.right.accept(this);
        if (!(expr.left instanceof Expr.Literal left) || !(expr.right instanceof Expr.Literal right))
            return expr;

        // handle == and !=

        switch (expr.operator) {
            case TokenType.EQUAL_EQUAL: return new Expr.Literal(expr.left.equals(expr.right));
            case TokenType.BANG_EQUAL: return new Expr.Literal(expr.left.equals(expr.right));
        }

        // handle string concatenation

        if (expr.operator == TokenType.PLUS && left.value instanceof String l && right.value instanceof String r)
            return new Expr.Literal(l + r);

        // we should only have numbers now

        Number leftNum = (Number) left.value;
        Number rightNum = (Number) right.value;
        
        if (leftNum instanceof Double || rightNum instanceof Double) {
            return new Expr.Literal(applyDouble(expr.operator, leftNum.doubleValue(), rightNum.doubleValue()));
        } else if (leftNum instanceof Float || rightNum instanceof Float) {
            return new Expr.Literal(applyFloat(expr.operator, leftNum.floatValue(), rightNum.floatValue()));
        } else if (leftNum instanceof Long || rightNum instanceof Long) {
            return new Expr.Literal(applyLong(expr.operator, leftNum.longValue(), rightNum.longValue()));
        } else if (leftNum instanceof Integer || rightNum instanceof Integer) {
            return new Expr.Literal(applyInt(expr.operator, leftNum.intValue(), rightNum.intValue()));
        }

        return expr;
    }

    private Object applyDouble(TokenType operator, double l, double r) {
        return switch (operator) {
            case PLUS -> l + r;
            case MINUS -> l - r;
            case STAR -> l * r;
            case SLASH -> l / r;
            case LESS -> l < r;
            case LESS_EQUAL -> l <= r;
            case GREATER -> l > r;
            case GREATER_EQUAL -> l >= r;
            default -> throw new RuntimeException("Unknown binary expression " + operator + ".This should not happen.");
        };
    }

    private Object applyFloat(TokenType operator, float l, float r) {
        return switch (operator) {
            case PLUS -> l + r;
            case MINUS -> l - r;
            case STAR -> l * r;
            case SLASH -> l / r;
            case LESS -> l < r;
            case LESS_EQUAL -> l <= r;
            case GREATER -> l > r;
            case GREATER_EQUAL -> l >= r;
            default -> throw new RuntimeException("Unknown binary expression " + operator + ".This should not happen.");
        };
    }

    private Object applyLong(TokenType operator, long l, long r) {
        return switch (operator) {
            case PLUS -> l + r;
            case MINUS -> l - r;
            case STAR -> l * r;
            case SLASH -> l / r;
            case LESS -> l < r;
            case LESS_EQUAL -> l <= r;
            case GREATER -> l > r;
            case GREATER_EQUAL -> l >= r;
            default -> throw new RuntimeException("Unknown binary expression " + operator + ".This should not happen.");
        };
    }

    private Object applyInt(TokenType operator, int l, int r) {
        return switch (operator) {
            case PLUS -> l + r;
            case MINUS -> l - r;
            case STAR -> l * r;
            case SLASH -> l / r;
            case LESS -> l < r;
            case LESS_EQUAL -> l <= r;
            case GREATER -> l > r;
            case GREATER_EQUAL -> l >= r;
            default -> throw new RuntimeException("Unknown binary expression " + operator + ".This should not happen.");
        };
    }

}

