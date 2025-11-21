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
                if (right.value instanceof Float) yield new Expr.Literal(-(float) right.value);
                if (right.value instanceof Double) yield new Expr.Literal(-(double) right.value);
                if (right.value instanceof Integer) yield new Expr.Literal(-(int) right.value);
                if (right.value instanceof Long) yield new Expr.Literal(-(long) right.value);
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
        if (!(expr.left instanceof Expr.Literal left) || !(expr.right instanceof Expr.Literal right)) return expr;

        return switch (expr.operator) {
            case TokenType.PLUS -> {
                if (left.value instanceof Float l && right.value instanceof Float r) yield new Expr.Literal(l + r);
                if (left.value instanceof Double l && right.value instanceof Double r) yield new Expr.Literal(l + r);
                if (left.value instanceof Integer l && right.value instanceof Integer r) yield new Expr.Literal(l + r);
                if (left.value instanceof Long l && right.value instanceof Long r) yield new Expr.Literal(l + r);
                if (left.value instanceof String l && right.value instanceof String r) yield new Expr.Literal(l + r);
                yield expr;
            }
            case TokenType.MINUS -> {
                if (left.value instanceof Float l && right.value instanceof Double r) yield new Expr.Literal(l - r);
                if (left.value instanceof Double l && right.value instanceof Double r) yield new Expr.Literal(l - r);
                if (left.value instanceof Integer l && right.value instanceof Integer r) yield new Expr.Literal(l - r);
                if (left.value instanceof Long l && right.value instanceof Long r) yield new Expr.Literal(l - r);
                yield expr;
            }
            case TokenType.STAR -> {
                if (left.value instanceof Float l && right.value instanceof Double r) yield new Expr.Literal(l * r);
                if (left.value instanceof Double l && right.value instanceof Double r) yield new Expr.Literal(l * r);
                if (left.value instanceof Integer l && right.value instanceof Integer r) yield new Expr.Literal(l * r);
                if (left.value instanceof Long l && right.value instanceof Long r) yield new Expr.Literal(l * r);
                yield expr;
            }
            case TokenType.SLASH -> {
                if (left.value instanceof Float l && right.value instanceof Double r) yield new Expr.Literal(l / r);
                if (left.value instanceof Double l && right.value instanceof Double r) yield new Expr.Literal(l / r);
                if (left.value instanceof Integer l && right.value instanceof Integer r) yield new Expr.Literal(l / r);
                if (left.value instanceof Long l && right.value instanceof Long r) yield new Expr.Literal(l / r);
                yield expr;
            }
            default -> expr;
        };
    }
}


