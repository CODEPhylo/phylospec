package org.phylospec.parser;
import org.phylospec.PhyloSpec;
import org.phylospec.ast.Expr;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenType;

import java.util.List;

/**
 * This class takes a list of tokens (usually obtained using the Lexer)
 * and returns an AST tree.
 */
public class Parser {
    /**
     * Parses the following grammar:
     * expression     → equality
     * equality       → comparison ( ( "!=" | "==" ) comparison )*
     * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
     * term           → factor ( ( "-" | "+" ) factor )* ;
     * factor         → unary ( ( "/" | "*" ) unary )* ;
     * unary          → ( "!" | "-" ) unary | primary
     * primary        → INT | FLOAT | STRING | "true" | "false" | "(" expression ")"
     */

    private final List<Token> tokens;
    private int current = 0;

    /**
     * Creates a new Parser.
     *
     * @param tokens - the PhyloSpec script represented as a list of tokens.
     */
    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Reads the source code provided in the constructor and returns a list
     * of tokens.
     *
     * @return list of scanned tokens.
     */
    public Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    /* parser methods */

    private Expr expression() {
        return equality();
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operator = previous();
            Expr rightExpr = comparison();
            expr = new Expr.Binary(expr, operator, rightExpr);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operator = previous();
            Expr rightExpr = term();
            expr = new Expr.Binary(expr, operator, rightExpr);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expr rightExpr = factor();
            expr = new Expr.Binary(expr, operator, rightExpr);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(TokenType.STAR, TokenType.SLASH)) {
            Token operator = previous();
            Expr rightExpr = unary();
            expr = new Expr.Binary(expr, operator, rightExpr);
        }

        return expr;
    }

    private Expr unary() {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token operator = previous();
            Expr rightExpr = unary();
            return new Expr.Unary(operator, rightExpr);
        } else {
            return primary();
        }
    }

    private Expr primary() {
        if (match(TokenType.FALSE)) return new Expr.Literal(false);
        if (match(TokenType.TRUE)) return new Expr.Literal(true);

        if (match(TokenType.INT, TokenType.FLOAT, TokenType.STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    /* helper methods to inspect the tokens */

    /** Returns the current token and advances the cursor afterward. */
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    /** Checks if the current token matches any of the expected ones and
     * advances the cursor if that is the case. */
    private boolean match(TokenType... tokenTypes) {
        for (TokenType type : tokenTypes) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    /** Checks if the current token matches any of the expected ones without
     * advancing the cursor. */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    /** Returns the current character without advancing the cursor. */
    private Token peek() {
        return tokens.get(current);
    }

    /** Returns the last character without changing the cursor. */
    private Token previous() {
        return tokens.get(current - 1);
    }

    /** Advances the cursor if the next token matches the expected token type. If this
     * is not the case, an error is raised. */
    private Token consume(TokenType tokenType, String message) {
        if (check(tokenType)) return advance();

        throw error(peek(), message);
    }

    /** Checks if the current cursor points to the end of the file. */
    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    /* error handling */

    private ParseError error(Token token, String message) {
        PhyloSpec.error(token, message);
        return new ParseError();
    }

    private static class ParseError extends RuntimeException {}
}
