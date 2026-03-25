package org.phylospec.parser;

import org.phylospec.ast.*;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.Range;
import org.phylospec.lexer.TokenType;

import java.util.*;

/**
 * This class takes a list of tokens (usually obtained using the Lexer)
 * and returns an AST tree.
 */
public class Parser {
    /**
     * Parses the following grammar:
     * import            → "use" IDENTIFIER ( "." IDENTIFIER )* | decorated ;
     * decorated         → ( "@" IDENTIFIER "(" arguments? ")" )*  indexedStatement ;
     * observedStatement → indexedStatement ( clamping )? ;
     * indexedStatement  → type IDENTIFIER "[" IDENTIFIER "] ( "=" | "~" ) indexedExpression | statement ;
     * statement         → type IDENTIFIER ( "=" | "~" ) expression ;
     * type              → IDENTIFIER ("<" type ("," type)* ">" ) ;
     * indexedExpression → equality "for" IDENTIFIER "in" expression ;
     * clamping          → "observed as" expression | "observed between" "[" expression "," expression "]" ;
     * expression        → equality ;
     * equality          → comparison ( ( "!=" | "==" ) comparison )* ;
     * comparison        → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
     * term              → factor ( ( "-" | "+" ) factor )* ;
     * factor            → range ( ( "/" | "*" ) range )* ;
     * range             → unary ( ":" unary )? ;
     * unary             → ( "!" | "-" ) unary | postfix ;
     * postfix           → array ( "[" expression (, expression)* "]" | "(" arguments? ")" )* ; // only IDENTIFIER "(" ... ")" is a valid call
     * arguments         → argument ( "," argument )* ;
     * argument          → IDENTIFIER ( "=" | "~" ) expression | expression ;
     * array             → "[" "]" | "[" expression ( "," expression )* "]" | primary;
     * primary           → INT ( unit )? | FLOAT ( unit )? | STRING | "true" | "false" | IDENTIFIER | "(" expression ")" ;
     * unit              → "d" | "yr" | "kyr" | "Myr" | "ka" | "Ma"
     */

    private final List<Token> tokens;
    private int current = 0;
    private boolean skipNewLines = false;
    private Stmt.Block currentBlock = Stmt.Block.NO_BLOCK;

    private final Map<Token, AstNode> tokenAstNodeMap;
    private final Map<AstNode, Range> astNodeRanges;
    private final LinkedList<Integer> astNodeStartPositions;

    private final List<ErrorEventListener> eventListeners;

    /**
     * Creates a new Parser.
     *
     * @param tokens - the PhyloSpec script represented as a list of tokens.
     */
    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.eventListeners = new ArrayList<>();
        this.tokenAstNodeMap = new HashMap<>();
        this.astNodeRanges = new HashMap<>();
        this.astNodeStartPositions = new LinkedList<>();
    }

    public void registerEventListener(ErrorEventListener listener) {
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
                if (isBlockHeader()) {
                    parseBlockHeader();
                } else if (isBlockEnd()) {
                    parseBlockEnd();
                } else {
                    Stmt stmt = importRule();
                    stmt.block = currentBlock;
                    statements.add(stmt);
                }

                if (!isAtEnd()) {
                    consume(
                            TokenType.EOL,
                            "Missing line break.",
                            "There already is a complete statement on this line. Put each statement on its own line."
                    );

                    // skip all EOL until the next statement
                    skipEOLs();
                }
            } catch (Error error) {
                logError(error);
                recover();
            }
        }

        return statements;
    }

    private boolean isBlockHeader() {
        if (isAtEnd()) return false;
        if (!check(TokenType.IDENTIFIER)) return false;
        int nextIdx = current + 1;
        return nextIdx < tokens.size() && tokens.get(nextIdx).type == TokenType.LEFT_BRACE;
    }

    private void parseBlockHeader() throws Error {
        String blockName = advance().lexeme;

        if (currentBlock != Stmt.Block.NO_BLOCK) {
            throw new Error(
                    peek().range,
                    "Block not closed.",
                    "You are starting a '" + blockName + "' without closing the '" + currentBlock.toString() + "' block. End the block with curly braces first.",
                    List.of(currentBlock.toString() + "{\n\t\t...\t\n}\n\t" + blockName + "\n\t\t...\n\t}")
            );
        }

        advance(); // consume LEFT_BRACE
        currentBlock = switch (blockName) {
            case "data" -> Stmt.Block.DATA;
            case "model" -> Stmt.Block.MODEL;
            case "mcmc" -> Stmt.Block.MCMC;
            default -> new Stmt.Block.Custom(blockName);
        };
    }

    private boolean isBlockEnd() {
        return check(TokenType.RIGHT_BRACE);
    }

    private void parseBlockEnd() {
        advance(); // consume RIGHT_BRACE
        currentBlock = Stmt.Block.NO_BLOCK;
    }

    /* parser rules */

    private Stmt importRule() throws Error {
        startAstNode();

        if (match(TokenType.IMPORT)) {
            List<String> namespace = new ArrayList<>();

            do {
                namespace.add(
                        consume(
                                TokenType.IDENTIFIER,
                                "Invalid import path.",
                                "Specify an import path consisting of names delimited with a period.",
                                List.of("use phylospec.io")
                        ).lexeme
                );
            } while (match(TokenType.DOT));

            return remember(new Stmt.Import(namespace));
        }

        return remember(decorated());
    }

    private Stmt decorated() throws Error {
        startAstNode();

        while (match(TokenType.AT)) {
            Token decoratorName = consume(
                    TokenType.IDENTIFIER,
                    "Invalid hint.",
                    "Directly follow the '@' with the name of the engine or extension you want to talk to.",
                    List.of("@revbayes(discretize=true)")
            );
            Expr.Variable decoratorNameVar = new Expr.Variable(decoratorName.lexeme);

            consume(
                    TokenType.LEFT_PAREN,
                    "Missing brackets in hint.",
                    "Follow the engine or extension name with brackets, similar to function calls.",
                    List.of("@revbayes(discretize=true)")
            );

            // we are in a bracket, let's ignore EOL statements
            boolean oldSkipNewLines = skipNewLines;
            skipNewLines = true;

            try {
                Expr decorator = finishCall(decoratorNameVar);
                consume(
                        TokenType.RIGHT_PAREN,
                        "Missing closinng brackets in hint.",
                        "Add the closing brackets ')' at the end of the hint.",
                        List.of("@revbayes(discretize=true)")
                );

                // skip all EOL until the next statement
                skipEOLs();

                Stmt statement = decorated();
                return remember(new Stmt.Decorated((Expr.Call) decorator, statement));
            } finally {
                skipNewLines = oldSkipNewLines;
            }
        }

        return remember(observedStatement());
    }

    private Stmt observedStatement() throws Error {
        startAstNode();

        Stmt stmt = indexedStatement();

        if (match(TokenType.OBSERVED_AS)) {
            startAstNode();
            Expr observedAs = expression();
            return remember(new Stmt.ObservedAs(stmt, observedAs));
        } else if (match(TokenType.OBSERVED_BETWEEN)) {
            startAstNode();

            consume(
                    TokenType.LEFT_SQUARE_BRACKET,
                    "Invalid range.",
                    "Use square brackets to specify a range of an observed variable.",
                    List.of("Real a ~ Exponential(1.0) observed between [20.0, 30.0]")
            );

            Expr observedFrom = expression();

            consume(
                    TokenType.COMMA,
                    "Invalid range.",
                    "Use two values separated by a comma to specify a range of an observed variable.",
                    List.of("Real a ~ Exponential(1.0) observed between [20.0, 30.0]")
            );

            Expr observedTo = expression();

            consume(
                    TokenType.RIGHT_SQUARE_BRACKET,
                    "Invalid range.",
                    "Use square brackets to specify a range of an observed variable.",
                    List.of("Real a ~ Exponential(1.0) observed between [20.0, 30.0]")
            );

            return remember(new Stmt.ObservedBetween(stmt, observedFrom, observedTo));
        }

        return remember(stmt);
    }

    private Stmt indexedStatement() throws Error {
        startAstNode();

        // parse type

        AstType type = type();

        Token nameToken = consume(
                TokenType.IDENTIFIER,
                "Invalid variable name.",
                "Choose a variable name which starts with a letter and only consists of letters and digits.",
                List.of("Real x = 10")
        );

        // check if this is an indexed statement and parse the index if needed

        boolean isIndexed = match(TokenType.LEFT_SQUARE_BRACKET);
        Token index = null;
        if (isIndexed) {
            index = consume(
                    TokenType.IDENTIFIER,
                    "Invalid index.",
                    "Only letters can be used as an index.",
                    List.of("Real x[i] = i for i in 1:3")
            );

            consume(
                    TokenType.RIGHT_SQUARE_BRACKET,
                    "Invalid index.",
                    "Follow the index name with closing square brackets (']').",
                    List.of("Real x[i] = i for i in 1:3")
            );
        }

        // parse the statement

        Stmt stmt;
        if (match(TokenType.EQUAL)) {
            Expr expression = expression();
            stmt = new Stmt.Assignment(type, nameToken.lexeme, expression);
        } else if (match(TokenType.TILDE)) {
            Expr expression = expression();
            stmt = new Stmt.Draw(type, nameToken.lexeme, expression);
        } else {
            throw new Error(
                    peek().range,
                    "No assignment or draw.",
                    "When defining a variable, either directly assign a value with '=', or draw a value from a distribution with '~'.",
                    List.of("Real x = 10")
            );
        }

        if (!isIndexed) {
            return remember(stmt);
        }

        // this is an indexed statement, so we need to parse the range ("for <index> in <range>")

        consume(
                TokenType.FOR,
                "Missing range in index statement.",
                "End index statements by indicating the range of the index variable.",
                List.of("Real x[" + index.lexeme + "] = 1 for " + index.lexeme + " in 1:3")
        );

        Token rangeIndex = consume(
                TokenType.IDENTIFIER,
                "Wrong range in index statement.",
                "End index statements by indicating the range of the index variable.",
                List.of("Real x[" + index.lexeme + "] = 1 for " + index.lexeme + " in 1:3")
        );

        if (!Objects.equals(rangeIndex.lexeme, index.lexeme)) {
            throw new Error(
                    previous().range,
                    "Wrong index variable.",
                    "This statement is indexed by `" + index.lexeme + "', but you specify the range of the variable '" + rangeIndex.lexeme + "'. The two indices must be the same: `... for " + index.lexeme + " in ..."
            );
        }

        consume(
                TokenType.IN,
                "Missing range in index statement.",
                "End index statements by indicating the range of the index variable.",
                List.of("Real x[" + index.lexeme + "] = 1 for " + index.lexeme + " in 1:3")
        );

        Expr range = expression();

        return remember(
                new Stmt.Indexed(stmt, new Expr.Variable(index.lexeme), range)
        );
    }

    private AstType type() throws Error {
        startAstNode();

        Token typeNameToken = consume(
                TokenType.IDENTIFIER,
                "Invalid variable type.",
                "Type names can only consist of letters."
        );

        if (match(TokenType.LESS)) {
            List<AstType> innerTypes = new ArrayList<>();
            innerTypes.add(type());

            while (match(TokenType.COMMA)) {
                innerTypes.add(type());
            }

            // parse closing brackets
            consume(
                    TokenType.GREATER, "Generic type must be closed with a '>'.",
                    "Close the opening square brackets of the generic type with a '>'."
            );

            return remember(
                    new AstType.Generic(typeNameToken.lexeme, innerTypes.toArray(AstType[]::new))
            );
        }

        return remember(new AstType.Atomic(typeNameToken.lexeme));
    }

    private Expr expression() throws Error {
        return equality();
    }

    private Expr equality() throws Error {
        startAstNode();

        Expr expr = comparison();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operatorToken = previous();
            Expr rightExpr = comparison();
            expr = new Expr.Binary(expr, operatorToken.type, rightExpr);
        }

        return remember(expr);
    }

    private Expr comparison() throws Error {
        startAstNode();

        Expr expr = term();

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operatorToken = previous();
            Expr rightExpr = term();
            expr = new Expr.Binary(expr, operatorToken.type, rightExpr);
        }

        return remember(expr);
    }

    private Expr term() throws Error {
        startAstNode();

        Expr expr = factor();

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operatorToken = previous();
            Expr rightExpr = factor();
            expr = new Expr.Binary(expr, operatorToken.type, rightExpr);
        }

        return remember(expr);
    }

    private Expr factor() throws Error {
        startAstNode();

        Expr expr = range();

        while (match(TokenType.STAR, TokenType.SLASH)) {
            Token operatorToken = previous();
            Expr rightExpr = range();
            expr = new Expr.Binary(expr, operatorToken.type, rightExpr);
        }

        return remember(expr);
    }

    private Expr range() throws Error {
        startAstNode();

        Expr expr = unary();

        if (match(TokenType.COLON)) {
            Expr upperBound = unary();
            return remember(new Expr.Range(expr, upperBound));
        }

        return remember(expr);
    }

    private Expr unary() throws Error {
        startAstNode();

        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token operatorToken = previous();
            Expr rightExpr = unary();
            return remember(new Expr.Unary(operatorToken.type, rightExpr));
        } else {
            return remember(postfix());
        }
    }

    private Expr postfix() throws Error {
        startAstNode();

        Expr expr = array();

        while (true) {
            if (expr instanceof Expr.Variable && match(TokenType.LEFT_PAREN)) {
                // this is a function call

                // remove the mapping of the variable token, because we will remember it as a call
                forgetLast();

                Expr.Variable functionName = (Expr.Variable) expr;

                // we are inside brackets, let's ignore EOL tokens
                boolean oldSkipNewLines = skipNewLines;
                skipNewLines = true;

                try {
                    expr = finishCall(functionName);
                    consume(
                            TokenType.RIGHT_PAREN,
                            "Function arguments not closed.",
                            "Add closing brackets ')' after the arguments."
                    );
                } finally {
                    skipNewLines = oldSkipNewLines;
                }
            } else if (match(TokenType.LEFT_SQUARE_BRACKET)) {
                // this is an index access (e.g. x[1] or data[1]["header"])

                // we are inside brackets, let's ignore EOL tokens
                boolean oldSkipNewLines = skipNewLines;
                skipNewLines = true;

                try {
                    List<Expr> indices = new ArrayList<>();
                    indices.add(expression());

                    while (match(TokenType.COMMA)) {
                        indices.add(expression());
                    }

                    consume(
                            TokenType.RIGHT_SQUARE_BRACKET,
                            "Index not closed.",
                            "Add closing square brackets ']' after the index expression.",
                            List.of("x[1]", "data[1][\"header\"]")
                    );

                    expr = new Expr.Index(expr, indices);
                } finally {
                    skipNewLines = oldSkipNewLines;
                }
            } else {
                break;
            }
        }

        return remember(expr);
    }

    private Expr finishCall(Expr.Variable callee) throws Error {
        List<Expr.Argument> arguments = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (check(TokenType.RIGHT_PAREN)) {
                    // the last comma has been a trailing one
                    break;
                }

                arguments.add(argument());
            } while (match(TokenType.COMMA));
        }

        return new Expr.Call(callee.variableName, arguments.toArray(Expr.Argument[]::new));
    }

    private Expr.Argument argument() throws Error {
        startAstNode();

        Expr expression = expression();

        if (!(expression instanceof Expr.Variable)) {
            return remember(new Expr.AssignedArgument(expression));
        }

        // remove the assignment of the variable token, because we will remember it as an argument
        forgetLast();

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

    private Expr array() throws Error {
        startAstNode();

        if (match(TokenType.LEFT_SQUARE_BRACKET)) {
            // we are in a bracket, let's ignore EOL statements
            boolean oldSkipNewLines = skipNewLines;
            skipNewLines = true;
            try {
                List<Expr> elements = new ArrayList<>();

                if (match(TokenType.RIGHT_SQUARE_BRACKET)) {
                    // we have an empty list
                    return remember(new Expr.Array(elements));
                }

                Expr element = expression();
                elements.add(element);

                while (match(TokenType.COMMA)) {
                    if (match(TokenType.RIGHT_SQUARE_BRACKET)) {
                        // the last comma has been a trailing one
                        return remember(new Expr.Array(elements));
                    }

                    element = expression();
                    elements.add(element);
                }

                consume(
                        TokenType.RIGHT_SQUARE_BRACKET,
                        "Vector not terminated.", "Add square brackets ']' after the last item of the vector.",
                        List.of("[1, 5]")
                );

                return remember(new Expr.Array(elements));
            } finally {
                skipNewLines = oldSkipNewLines;
            }

        }

        return remember(primary());
    }

    private Expr primary() throws Error {
        startAstNode();

        if (match(TokenType.FALSE)) return remember(new Expr.Literal(false));
        if (match(TokenType.TRUE)) return remember(new Expr.Literal(true));

        if (match(TokenType.INT, TokenType.FLOAT)) {
            Expr.Literal literal = new Expr.Literal(previous().literal);

            Token next = peek();
            if (next.type == TokenType.IDENTIFIER && Unit.isValidUnit(next.lexeme)) {
                match(TokenType.IDENTIFIER);
                literal.attachUnit(Unit.toUnit(next.lexeme));
            }

            return remember(literal);
        }

        if (match(TokenType.STRING_END)) {
            return remember(new Expr.Literal(previous().literal));
        }

        if (match(TokenType.STRING_PART)) {
            return remember(stringTemplate());
        }

        if (match(TokenType.LEFT_PAREN)) {
            // we are in a bracket, let's ignore EOL statements
            boolean oldIgnoreNewLines = skipNewLines;
            skipNewLines = true;
            try {
                Expr expr = expression();
                consume(
                        TokenType.RIGHT_PAREN,
                        "Missing ')' after expression.",
                        "Add brackets ')' to close the grouped expression."
                );
                return remember(new Expr.Grouping(expr));
            } finally {
                skipNewLines = oldIgnoreNewLines;
            }
        }

        if (match(TokenType.IDENTIFIER)) {
            return remember(new Expr.Variable(previous().lexeme));
        }

        throw new Error(peek().range, "Invalid expression.", "Something is missing.");
    }

    private Expr stringTemplate() throws Error {
        startAstNode();

        List<Expr.StringTemplate.Part> parts = new ArrayList<>();
        parts.add(new Expr.StringTemplate.StringPart(previous().literal.toString()));

        while (true) {
            if (check(TokenType.IDENTIFIER)) {
                Token interpolated = advance();
                parts.add(new Expr.StringTemplate.ExpressionPart(new Expr.Variable(interpolated.lexeme)));

                if (!check(TokenType.STRING_END, TokenType.STRING_PART)) {
                    throw new Error(
                            peek().range,
                            "Invalid string template.",
                            "Only use simple variable names in string templates.",
                            List.of("String file = \"name ${seed}.nex\"")
                    );
                }
            } else if (check(TokenType.STRING_PART)) {
                Token stringPart = advance();
                parts.add(new Expr.StringTemplate.StringPart(stringPart.literal.toString()));
            } else if (check(TokenType.STRING_END)) {
                Token stringPart = advance();
                parts.add(new Expr.StringTemplate.StringPart(stringPart.literal.toString()));
                break;
            } else {
                throw new Error(
                        peek().range,
                        "Invalid string template.",
                        "Only use variable names in string templates and end the template with curly braces.",
                        List.of("String file = \"name ${seed}.nex\"")
                );
            }
        }

        return remember(new Expr.StringTemplate(parts));
    }

    /* helper methods to inspect the tokens */

    /**
     * Returns the current token and advances the cursor afterward.
     */
    private Token advance() {
        if (isAtEnd()) return previous();

        if (skipNewLines) {
            while (tokens.get(current).type == TokenType.EOL && current + 1 < this.tokens.size()) {
                current++;
            }
        }

        current++;

        return previous();
    }

    /**
     * Checks if the current token matches any of the expected ones and
     * advances the cursor if that is the case.
     */
    private boolean match(TokenType... tokenTypes) {
        for (TokenType type : tokenTypes) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the current token matches any of the expected ones without
     * advancing the cursor.
     */
    private boolean check(TokenType... tokenTypes) {
        if (isAtEnd()) return false;

        for (TokenType type : tokenTypes) {
            if (peek().type == type) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the current character without advancing the cursor.
     */
    private Token peek() {
        if (skipNewLines) {
            int currentToPeek = current;
            while (tokens.get(currentToPeek).type == TokenType.EOL && currentToPeek + 1 < tokens.size()) {
                currentToPeek++;
            }
            return tokens.get(currentToPeek);
        }

        return tokens.get(current);
    }

    /**
     * Returns the last character without changing the cursor.
     */
    private Token previous() {
        if (skipNewLines) {
            int currentToPeek = current - 1;
            while (tokens.get(currentToPeek).type == TokenType.EOL && 0 < currentToPeek) {
                currentToPeek--;
            }
            return tokens.get(currentToPeek);
        }

        return tokens.get(current - 1);
    }

    /**
     * Advances the cursor if the next token matches the expected token type. If this
     * is not the case, an error is raised.
     */
    private Token consume(TokenType tokenType, String message, String hint) throws Error {
        return consume(tokenType, message, hint, List.of());
    }

    /**
     * Advances the cursor if the next token matches the expected token type. If this
     * is not the case, an error with the given correct code examples is raised.
     */
    private Token consume(TokenType tokenType, String message, String hint, List<String> examples) throws Error {
        if (check(tokenType)) return advance();

        // we couldn't consume the requested token
        // let's throw an error

        Range range;
        if (astNodeStartPositions.isEmpty()) {
            range = peek().range;
        } else {
            Token startToken = tokens.get(astNodeStartPositions.peek());
            Token currentToken = tokens.get(current);
            range = new Range(startToken.range.startLine, currentToken.range.endLine, startToken.range.start, currentToken.range.end);
        }

        throw new Error(range, message, hint, examples);
    }

    /**
     * Checks if the current cursor points to the end of the file.
     */
    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    /**
     * Marks the beginning of an AstNode.
     */
    private void startAstNode() {
        astNodeStartPositions.push(current);
    }

    /**
     * Associated the tokens since the last {@code startAstNode()} call with the
     * given parsed {@link AstNode}.
     */
    private <T extends AstNode> T remember(T newAstNode) {
        int lastPosition = astNodeStartPositions.pop();

        Range startRange = tokens.get(lastPosition).range;

        for (int i = lastPosition; i < current; i++) {
            Token token = tokens.get(i);
            tokenAstNodeMap.putIfAbsent(token, newAstNode);
        }

        Range endRange = tokens.get(current - 1).range;
        Range astNodeRange = Range.combine(startRange, endRange);
        astNodeRanges.put(newAstNode, astNodeRange);

        return newAstNode;
    }

    /**
     * Remove the previously made entries in the token-to-ast-node map
     * since the last {@code startAstNode()} call. This is useful if
     * the last parsed AstNode is dropped and replaced by a more general
     * one.
     */
    private void forgetLast() {
        int lastPosition = astNodeStartPositions.peek();

        for (int i = lastPosition; i < current; i++) {
            Token token = tokens.get(i);
            tokenAstNodeMap.remove(token);
        }
    }

    /**
     * Returns the {@link AstNode} associated with the given {@link Token}.
     * Returns null if no node was associated.
     */
    public AstNode getAstNodeForToken(Token token) {
        return this.tokenAstNodeMap.get(token);
    }

    /**
     * Returns the range associated with the given {@link AstNode}. Returns null
     * if no range was associated.
     */
    public Range getRangeForAstNode(AstNode node) {
        return this.astNodeRanges.get(node);
    }

    /* error handling */

    private void logError(Error error) {
        for (ErrorEventListener listener : eventListeners) {
            listener.errorDetected(error);
        }
    }

    /**
     * Finds the next location in the source string with a valid statement and advances
     * to that point.
     */
    private void recover() {
        while (!isAtEnd()) {
            // the next statement has to be preceded by an EOL. let's find it
            while (peek().type != TokenType.EOL && !isAtEnd()) {
                advance();
            }

            skipEOLs();

            // we could now be at the beginning of a new statement, let's check that

            int oldCurrent = current;
            int oldStackSize = astNodeStartPositions.size();
            try {
                decorated();
                // we successfully parsed a statement
                // let's reset cursor and return to the normal parsing loop
                current = oldCurrent;
                return;
            } catch (Error ignored) {
                // restore the astNodeStartPositions stack to its pre-attempt state,
                // since startAstNode() calls inside the failed decorated() were never
                // matched by a remember() call
                while (astNodeStartPositions.size() > oldStackSize) {
                    astNodeStartPositions.pop();
                }
                // we couldn't parse a proper statement, let's search for longer
                skipEOLs();
            }
        }
    }

    private void skipEOLs() {
        while (match(TokenType.EOL)) {
        }
    }

}
