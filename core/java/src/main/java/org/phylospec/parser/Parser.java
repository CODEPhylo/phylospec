package org.phylospec.parser;
import org.phylospec.PhyloSpec;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.Type;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * This class takes a list of tokens (usually obtained using the Lexer)
 * and returns an AST tree.
 */
public class Parser {
    /**
     * Parses the following grammar:
     * decorated      → ( "@" call )*  statement ;
     * statement      → "import" IDENTIFIER ( "." IDENTIFIER )* | type IDENTIFIER ( "=" | "~" ) expression ;
     * type           → IDENTIFIER ("<" type ("," type)* ">" ) ;
     * expression     → equality ;
     * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
     * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
     * term           → factor ( ( "-" | "+" ) factor )* ;
     * factor         → unary ( ( "/" | "*" ) unary )* ;
     * unary          → ( "!" | "-" ) unary | call ;
     * call           → array ( "(" arguments? ")" | "." IDENTIFIER )* ;
     * arguments      → argument ( "," argument )* | expression ;
     * argument       → IDENTIFIER "=" expression | IDENTIFIER ;
     * array          → "[" "]" | "[" expression ( "," expression )* ","? "]"  | primary;
     * primary        → INT | FLOAT | STRING | "true" | "false" | IDENTIFIER | "(" expression ")" ;
     */

    private final List<Token> tokens;
    private int current = 0;
    private boolean skipNewLines = false;

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
    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();

        // skip all EOL until the first statement
        while (match(TokenType.EOL)) {}

        while (!isAtEnd()) {
            statements.add(decorated());

            if (!isAtEnd()) {
                consume(TokenType.EOL, "Assignment has to be terminated by a line break.");

                // skip all EOL until the next statement
                while (match(TokenType.EOL)) {}
            }
        }

        return statements;
    }

    /* parser methods */

    private Stmt decorated() {
        if (match(TokenType.AT)) {
            Expr decorator = call();

            if (!(decorator instanceof Expr.Call)) {
                throw error(previous(), "Decorators can only be function calls.");
            }

            // skip all EOL until the next statement
            while (match(TokenType.EOL)) {}

            Stmt statement = decorated();
            return new Stmt.Decorated((Expr.Call) decorator, statement);
        }

        return statement();
    }

    private Stmt statement() {
        if (match(TokenType.IMPORT)) {
            List<String> importPath = new ArrayList<>();
            importPath.add(
                    consume(TokenType.IDENTIFIER, "Import path must be provided.").lexeme
            );

            while (match(TokenType.DOT)) {
                importPath.add(
                        consume(TokenType.IDENTIFIER, "Invalid import path.").lexeme
                );
            }

            return new Stmt.Import(importPath);
        }

        Type type = type();

        Token name = consume(TokenType.IDENTIFIER, "Invalid variable name.");

        if (match(TokenType.EQUAL)) {
            Expr expression = expression();
            return new Stmt.Assignment(type, name.lexeme, expression);
        }

        if (match(TokenType.TILDE)) {
            Expr expression = expression();
            return new Stmt.Draw(type, name.lexeme, expression);
        }

        throw error(peek(), "Except assignment or draw.");
    }

    private Type type() {
        Token typeName = consume(TokenType.IDENTIFIER, "Invalid variable type.");

        if (match(TokenType.LESS)) {
            List<Type> innerTypes = new ArrayList<>();
            innerTypes.add(type());

            while (match(TokenType.COMMA)) {
                innerTypes.add(type());
            }

            // parse closing brackets
            consume(TokenType.GREATER, "Generic type must be closed with a '>'.");

            return new Type.Generic(typeName.lexeme, innerTypes.toArray(Type[]::new));
        }

        return new Type.Atomic(typeName.lexeme);
    }

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
            return call();
        }
    }

    private Expr call() {
        Expr expr = array();

        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                // we are in a bracket, let's ignore EOL statements
                boolean oldSkipNewLines = skipNewLines;
                skipNewLines = true;

                expr = finishCall(expr);

                consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");

                skipNewLines = oldSkipNewLines;
            } else if (match(TokenType.DOT)) {
                Token name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name.lexeme);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr.Argument> arguments = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (check(TokenType.RIGHT_PAREN)) {
                    // the last comma has been a trailing one
                    break;
                }

                arguments.add(argument());

                if (arguments.get(0).name == null && check(TokenType.COMMA)) {
                    error(peek(), "Arguments can only be omitted when there is only one argument.");
                }
            } while (match(TokenType.COMMA));
        }

        return new Expr.Call(callee, arguments.toArray(Expr.Argument[]::new));
    }

    private Expr.Argument argument() {
        Expr expression = expression();

        if (!(expression instanceof Expr.Variable)) {
            return new Expr.AssignedArgument(expression);
        }

        String argumentName = ((Expr.Variable) expression).variable;

        if (match(TokenType.TILDE)) {
            expression = expression();
            return new Expr.DrawnArgument(argumentName, expression);
        }

        if (match(TokenType.EQUAL)) {
            expression = expression();
        } else {
            expression = new Expr.Variable(argumentName);
        }

        return new Expr.AssignedArgument(argumentName, expression);
    }

    private Expr array() {
        if (match(TokenType.LEFT_SQUARE_BRACKET)) {
            // we are in a bracket, let's ignore EOL statements
            boolean oldSkipNewLines = skipNewLines;
            skipNewLines = true;

            List<Expr> elements = new ArrayList<>();

            if (match(TokenType.RIGHT_SQUARE_BRACKET)) {
                // we have an empty list
                skipNewLines = oldSkipNewLines;
                return new Expr.Array(elements);
            }

            Expr element = expression();
            elements.add(element);

            while (match(TokenType.COMMA)) {
                if (match(TokenType.RIGHT_SQUARE_BRACKET)) {
                    // the last comma has been a trailing one
                    skipNewLines = oldSkipNewLines;
                    return new Expr.Array(elements);
                }

                element = expression();
                elements.add(element);
            }

            match(TokenType.RIGHT_SQUARE_BRACKET);

            skipNewLines = oldSkipNewLines;

            return new Expr.Array(elements);
        }

        return primary();
    }

    private Expr primary() {
        if (match(TokenType.FALSE)) return new Expr.Literal(false);
        if (match(TokenType.TRUE)) return new Expr.Literal(true);

        if (match(TokenType.INT, TokenType.FLOAT, TokenType.STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(TokenType.LEFT_PAREN)) {
            // we are in a bracket, let's ignore EOL statements
            boolean oldIgnoreNewLines = skipNewLines;
            skipNewLines = true;

            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");

            skipNewLines = oldIgnoreNewLines;

            return new Expr.Grouping(expr);
        }

        if (match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous().lexeme);
        }

        throw error(peek(), "Expect expression.");
    }

    /* helper methods to inspect the tokens */

    /** Returns the current token and advances the cursor afterward. */
    private Token advance() {
        if (isAtEnd()) return previous();

        if (skipNewLines) {
            while (tokens.get(current).type == TokenType.EOL) {
                current++;
            }
        }

        current++;

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
        if (skipNewLines) {
            int currentToPeek = current;
            while (tokens.get(currentToPeek).type == TokenType.EOL) {
                currentToPeek++;
            }
            return tokens.get(currentToPeek);
        }

        return tokens.get(current);
    }

    /** Returns the last character without changing the cursor. */
    private Token previous() {
        if (skipNewLines) {
            int currentToPeek = current - 1;
            while (tokens.get(currentToPeek).type == TokenType.EOL) {
                currentToPeek--;
            }
            return tokens.get(currentToPeek);
        }

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
