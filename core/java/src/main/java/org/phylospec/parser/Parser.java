package org.phylospec.parser;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.AstType;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenType;

import java.util.*;

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
     * call           → array ( "." IDENTIFIER )* | IDENTIFIER ( "(" arguments? ")" ) ;
     * arguments      → argument ( "," argument )* | expression ;
     * argument       → IDENTIFIER "=" expression | IDENTIFIER ;
     * array          → "[" "]" | "[" expression ( "," expression )* ","? "]"  | primary;
     * primary        → INT | FLOAT | STRING | "true" | "false" | IDENTIFIER | "(" expression ")" ;
     */

    private final List<Token> tokens;
    private int current = 0;
    private boolean skipNewLines = false;

    private final Map<Token, AstNode> tokenAstNodeMap;
    private final LinkedList<Integer> astNodeStartPositions;

    private final List<ParseEventListener> eventListeners;

    /**
     * Creates a new Parser.
     *
     * @param tokens - the PhyloSpec script represented as a list of tokens.
     */
    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.eventListeners = new ArrayList<>();
        this.tokenAstNodeMap = new HashMap<>();
        this.astNodeStartPositions = new LinkedList<>();
    }

    public void registerEventListener (ParseEventListener listener) {
        this.eventListeners.add(listener);
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
        skipEOLs();

        while (!isAtEnd()) {
            try {
                statements.add(decorated());

                if (!isAtEnd()) {
                    consume(TokenType.EOL, "Assignment has to be terminated by a line break.");

                    // skip all EOL until the next statement
                    skipEOLs();
                }
            } catch (ParseError error) {
                logError(error);
                recover();
            }
        }

        return statements;
    }

    /* parser methods */

    private Stmt decorated() {
        startAstNode();

        if (match(TokenType.AT)) {
            Expr decorator = call();

            if (!(decorator instanceof Expr.Call)) {
                throw new ParseError(previous(), "Decorators can only be function calls.");
            }

            // skip all EOL until the next statement
            skipEOLs();

            Stmt statement = decorated();
            return remember(new Stmt.Decorated((Expr.Call) decorator, statement, statement.tokenRange));
        }

        return remember(statement());
    }

    private Stmt statement() {
        startAstNode();

        if (match(TokenType.IMPORT)) {
            List<String> namespace = new ArrayList<>();
            namespace.add(
                    consume(TokenType.IDENTIFIER, "Import path must be provided.").lexeme
            );

            while (match(TokenType.DOT)) {
                namespace.add(
                        consume(TokenType.IDENTIFIER, "Invalid import path.").lexeme
                );
            }

            return remember(new Stmt.Import(namespace));
        }

        AstType type = type();

        Token nameToken = consume(TokenType.IDENTIFIER, "Invalid variable name.");

        if (match(TokenType.EQUAL)) {
            Expr expression = expression();
            return remember(new Stmt.Assignment(type, nameToken.lexeme, expression, nameToken.range));
        }

        if (match(TokenType.TILDE)) {
            Expr expression = expression();
            return remember(new Stmt.Draw(type, nameToken.lexeme, expression, nameToken.range));
        }

        throw new ParseError(peek(), "Except assignment or draw.");
    }

    private AstType type() {
        startAstNode();

        Token typeNameToken = consume(TokenType.IDENTIFIER, "Invalid variable type.");

        if (match(TokenType.LESS)) {
            List<AstType> innerTypes = new ArrayList<>();
            innerTypes.add(type());

            while (match(TokenType.COMMA)) {
                innerTypes.add(type());
            }

            // parse closing brackets
            consume(TokenType.GREATER, "Generic type must be closed with a '>'.");

            return remember(
                    new AstType.Generic(typeNameToken.lexeme, typeNameToken.range, innerTypes.toArray(AstType[]::new))
            );
        }

        return remember(new AstType.Atomic(typeNameToken.lexeme, typeNameToken.range));
    }

    private Expr expression() {
        return equality();
    }

    private Expr equality() {
        startAstNode();

        Expr expr = comparison();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operatorToken = previous();
            Expr rightExpr = comparison();
            expr = new Expr.Binary(expr, operatorToken.type, rightExpr, operatorToken.range);
        }

        return remember(expr);
    }

    private Expr comparison() {
        startAstNode();

        Expr expr = term();

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operatorToken = previous();
            Expr rightExpr = term();
            expr = new Expr.Binary(expr, operatorToken.type, rightExpr, operatorToken.range);
        }

        return remember(expr);
    }

    private Expr term() {
        startAstNode();

        Expr expr = factor();

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operatorToken = previous();
            Expr rightExpr = factor();
            expr = new Expr.Binary(expr, operatorToken.type, rightExpr, operatorToken.range);
        }

        return remember(expr);
    }

    private Expr factor() {
        startAstNode();

        Expr expr = unary();

        while (match(TokenType.STAR, TokenType.SLASH)) {
            Token operatorToken = previous();
            Expr rightExpr = unary();
            expr = new Expr.Binary(expr, operatorToken.type, rightExpr, operatorToken.range);
        }

        return remember(expr);
    }

    private Expr unary() {
        startAstNode();

        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token operatorToken = previous();
            Expr rightExpr = unary();
            return remember(new Expr.Unary(operatorToken.type, rightExpr, operatorToken.range));
        } else {
            return remember(call());
        }
    }

    private Expr call() {
        startAstNode();

        Expr expr = array();

        if (expr instanceof Expr.Variable && match(TokenType.LEFT_PAREN)) {
            // this is a function call
            Expr.Variable functionName = (Expr.Variable) expr;

            // we are in a bracket, let's ignore EOL statements
            boolean oldSkipNewLines = skipNewLines;
            skipNewLines = true;

            expr = finishCall(functionName);

            consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");

            skipNewLines = oldSkipNewLines;
        }

        while (match(TokenType.DOT)) {
            Token name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.");
            expr = new Expr.Get(expr, name.lexeme);
        }

        return remember(expr);
    }

    private Expr finishCall(Expr.Variable callee) {
        List<Expr.Argument> arguments = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (check(TokenType.RIGHT_PAREN)) {
                    // the last comma has been a trailing one
                    break;
                }

                arguments.add(argument());

                if (arguments.getFirst().name == null && check(TokenType.COMMA)) {
                    throw new ParseError(peek(), "Arguments can only be omitted when there is only one argument.");
                }
            } while (match(TokenType.COMMA));
        }

        return new Expr.Call(callee.variableName, callee.tokenRange, arguments.toArray(Expr.Argument[]::new));
    }

    private Expr.Argument argument() {
        startAstNode();

        Expr expression = expression();

        if (!(expression instanceof Expr.Variable)) {
            return remember(new Expr.AssignedArgument(expression));
        }

        String argumentName = ((Expr.Variable) expression).variableName;

        if (match(TokenType.TILDE)) {
            expression = expression();
            return remember(new Expr.DrawnArgument(argumentName, expression));
        }

        if (match(TokenType.EQUAL)) {
            expression = expression();
        } else {
            expression = new Expr.Variable(argumentName);
        }

        return remember(new Expr.AssignedArgument(argumentName, expression));
    }

    private Expr array() {
        startAstNode();

        if (match(TokenType.LEFT_SQUARE_BRACKET)) {
            // we are in a bracket, let's ignore EOL statements
            boolean oldSkipNewLines = skipNewLines;
            skipNewLines = true;

            List<Expr> elements = new ArrayList<>();

            if (match(TokenType.RIGHT_SQUARE_BRACKET)) {
                // we have an empty list
                skipNewLines = oldSkipNewLines;
                return remember(new Expr.Array(elements));
            }

            Expr element = expression();
            elements.add(element);

            while (match(TokenType.COMMA)) {
                if (match(TokenType.RIGHT_SQUARE_BRACKET)) {
                    // the last comma has been a trailing one
                    skipNewLines = oldSkipNewLines;
                    return remember(new Expr.Array(elements));
                }

                element = expression();
                elements.add(element);
            }

            match(TokenType.RIGHT_SQUARE_BRACKET);

            skipNewLines = oldSkipNewLines;

            return remember(new Expr.Array(elements));
        }

        return remember(primary());
    }

    private Expr primary() {
        startAstNode();

        if (match(TokenType.FALSE)) return remember(new Expr.Literal(false, previous().range));
        if (match(TokenType.TRUE)) return remember(new Expr.Literal(true, previous().range));

        if (match(TokenType.INT, TokenType.FLOAT, TokenType.STRING)) {
            return remember(new Expr.Literal(previous().literal, previous().range));
        }

        if (match(TokenType.LEFT_PAREN)) {
            // we are in a bracket, let's ignore EOL statements
            boolean oldIgnoreNewLines = skipNewLines;
            skipNewLines = true;

            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");

            skipNewLines = oldIgnoreNewLines;

            return remember(new Expr.Grouping(expr));
        }

        if (match(TokenType.IDENTIFIER)) {
            return remember(new Expr.Variable(previous().lexeme, previous().range));
        }

        throw new ParseError(peek(), "Expect expression.");
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

        throw new ParseError(peek(), message);
    }

    /** Checks if the current cursor points to the end of the file. */
    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    /** Marks the beginning of a new parsed AstNode. */
    private void startAstNode() {
        astNodeStartPositions.push(current);
    }

    /** Remembers the tokens of the new AstNode. */
    private <T extends AstNode> T remember(T newAstNode) {
        int lastPosition = astNodeStartPositions.pop();

        for (int i = lastPosition; i < current; i++) {
            Token token = tokens.get(i);
            tokenAstNodeMap.putIfAbsent(token, newAstNode);
        }

        return newAstNode;
    }

    public AstNode getAstNodeForToken(Token token) {
        return this.tokenAstNodeMap.get(token);
    }

    /* error handling */

    private void logError(ParseError error) {
        for (ParseEventListener listener : eventListeners) {
            listener.parseErrorDetected(error.token, error.message);
        }
    }

    /** Finds the next location in the source string with a valid statement */
    private void recover() {
        while (!isAtEnd()) {
            // the next statement has to be preceded by an EOL. let's find it
            while (peek().type != TokenType.EOL && !isAtEnd()) {
                advance();
            }

            skipEOLs();

            // we could now be at the beginning of a new statement, let's check that

            int oldCurrent = current;
            try {
                decorated();
                // we successfully parsed a statement
                // let's reset cursor and return to the normal parsing loop
                current = oldCurrent;
                return;
            } catch (ParseError ignored) {
                // we couldn't parse a proper statement, let's search for longer
                skipEOLs();
            }
        }
    }

    private void skipEOLs() {
        while (match(TokenType.EOL)) {}
    }

    private static class ParseError extends RuntimeException {
        private final Token token;
        private final String message;

        public ParseError(Token token, String message) {
            this.token = token;
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
