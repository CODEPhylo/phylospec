package org.phylospec.lsp;

import java.io.IOException;
import java.util.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.phylospec.ast.*;
import org.phylospec.components.*;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Range;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenType;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;

/**
 * This class implements the actual LSP responses for a given document.
 * It supports parsing and type error diagnostics, hover information,
 * and basic auto-completion.
 */
class LspDocument implements ErrorEventListener {
    private final String uri;
    private LanguageClient client;

    private final ComponentResolver componentResolver;

    private String content;
    private List<Token> tokens;
    private Parser parser;
    private List<Stmt> statements;
    TypeResolver typeResolver;
    private final List<Diagnostic> foundDiagnostics = new ArrayList<>();

    LspDocument(String uri, String content, LanguageClient client) {
        this.uri = uri;
        this.client = client;

        this.componentResolver = loadComponentResolver();

        updateContent(content);
    }

    private static ComponentResolver loadComponentResolver() {
        List<ComponentLibrary> componentLibraries = null;
        try {
            componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ComponentResolver(componentLibraries);
    }

    /**
     * Updates the document content and re-runs the static analysis.
     */
    void updateContent(String newContent) {
        foundDiagnostics.clear();

        content = newContent;

        // run lexer

        Lexer lexer = new Lexer(newContent);
        lexer.registerEventListener(this);
        tokens = lexer.scanTokens();

        // run parser

        parser = new Parser(tokens);
        parser.registerEventListener(this);
        statements = parser.parse();

        // run type resolver

        typeResolver = new TypeResolver(componentResolver);
        for (Stmt statement : statements) {
            try {
                statement.accept(typeResolver);
            } catch (TypeError error) {
                errorDetected(error, statement);
            }
        }

        // publish diagnostics

        this.client.publishDiagnostics(new PublishDiagnosticsParams(this.uri, foundDiagnostics));
    }

    @Override
    public void errorDetected(Error error) {
        StringBuilder text = new StringBuilder(error.description());

        if (!error.hint().isBlank()) {
            text.append("\n\n").append(error.hint());
        }

        if (!error.examples().isEmpty()) {
            text.append("\n\nFor example:\n");
            for (String example : error.examples()) {
                text.append("\n\t").append(example);
            }
        }

        foundDiagnostics.add(
                new Diagnostic(
                        new org.eclipse.lsp4j.Range(
                                new Position(error.range().startLine - 1, error.range().start),
                                new Position(error.range().endLine - 1, error.range().end)),
                        text.toString()));

        System.out.println(error.toStdOutString(content));
    }

    public void errorDetected(TypeError astNodeError, Stmt stmt) {
        Range range = parser.getRangeForAstNode(astNodeError.getAstNode());
        if (range == null) {
            range = parser.getRangeForAstNode(stmt);
        }
        this.errorDetected(astNodeError.toError(range));
    }

    /**
     * Applied changes to the content. Assumes that the LSP is configured to
     * always receive full changes.
     */
    public void applyContentChanges(List<TextDocumentContentChangeEvent> contentChanges) {
        if (contentChanges.isEmpty()) return;

        // make sure we only get full changes (we configured the server to do so)
        for (TextDocumentContentChangeEvent change : contentChanges) {
            org.eclipse.lsp4j.Range range = change.getRange();
            assert (range == null);
        }

        updateContent(contentChanges.getLast().getText());
    }

    /**
     * Returns the hover information for the given cursor position.
     */
    public MarkupContent getHoverInfo(Position position) {
        Token token = getTokenAtPosition(position);
        AstNode node = parser.getAstNodeForToken(token);
        if (node == null) return null;

        StringBuilder hoverText = new StringBuilder();

        switch (node) {
            case AstType typeNode -> {
                Set<ResolvedType> resolvedTypeSet = typeResolver.resolveTypeSet(typeNode);
                if (resolvedTypeSet == null) return null;

                if (1 < resolvedTypeSet.size()) {
                    hoverText
                            .append("_There are multiple versions of ")
                            .append(getUnqualifiedName(typeNode.name))
                            .append(":_\n\n");
                }

                for (ResolvedType resolvedType : resolvedTypeSet) {
                    if (1 < resolvedTypeSet.size()) {
                        hoverText.append("---\n\n");
                    }
                    hoverText.append(resolvedType.getTypeComponent().getDescription());
                    hoverText.append("\n\n```phylospec\n");
                    hoverText.append(resolvedType);
                    hoverText.append("\n```\n\n");
                }
            }
            case Stmt.Assignment stmt -> {
                Set<ResolvedType> resolvedTypeSet = typeResolver.resolveTypeSet(stmt);
                if (resolvedTypeSet == null) return null;

                if (1 < resolvedTypeSet.size()) {
                    hoverText
                            .append("_There are multiple versions of ")
                            .append(stmt.name)
                            .append(":_\n\n");
                }

                for (ResolvedType resolvedType : resolvedTypeSet) {
                    if (1 < resolvedTypeSet.size()) {
                        hoverText.append("---\n\n");
                    }
                    hoverText.append("```phylospec\n");
                    hoverText.append(resolvedType).append(" ").append(stmt.name);
                    hoverText.append("\n```\n\n");
                }
            }
            case Stmt.Draw stmt -> {
                Set<ResolvedType> resolvedTypeSet = typeResolver.resolveTypeSet(stmt);
                if (resolvedTypeSet == null) return null;

                if (1 < resolvedTypeSet.size()) {
                    hoverText
                            .append("_There are multiple versions of ")
                            .append(stmt.name)
                            .append(":_\n\n");
                }

                for (ResolvedType resolvedType : resolvedTypeSet) {
                    if (1 < resolvedTypeSet.size()) {
                        hoverText.append("---\n\n");
                    }
                    hoverText.append("```phylospec\n");
                    hoverText.append(resolvedType).append(" ").append(stmt.name);
                    hoverText.append("\n```\n\n");
                }
            }
            case Expr.Variable variable -> {
                Set<ResolvedType> resolvedTypeSet =
                        typeResolver.resolveVariable(variable.variableName);

                if (1 < resolvedTypeSet.size()) {
                    hoverText
                            .append("_There are multiple versions of ")
                            .append(variable.variableName)
                            .append(":_\n\n");
                }

                for (ResolvedType resolvedType : resolvedTypeSet) {
                    if (1 < resolvedTypeSet.size()) {
                        hoverText.append("---\n\n");
                    }
                    hoverText.append("```phylospec\n");
                    hoverText.append(resolvedType).append(" ").append(variable.variableName);
                    hoverText.append("\n```\n\n");
                }
            }
            case Expr.Call call -> {
                List<Generator> generators = componentResolver.resolveGenerator(call.functionName);

                if (1 < generators.size()) {
                    hoverText
                            .append("_There are multiple versions of ")
                            .append(call.functionName)
                            .append(":_\n\n");
                }

                for (int i = 0; i < generators.size(); i++) {
                    Generator generator = generators.get(i);

                    if (1 < generators.size()) {
                        hoverText.append("---\n\n");
                    }
                    hoverText.append(generator.getDescription()).append("\n\n");
                    hoverText.append("```phylospec\n");
                    printGeneratorInfo(hoverText, generator);
                    hoverText.append("\n```\n\n");
                    printGeneratorArgumentDescriptions(hoverText, generator);
                }
            }
            case Expr.Argument argument -> {
                Set<ResolvedType> resolvedTypeSet = typeResolver.resolveTypeSet(argument);

                String argumentName = argument.name;
                if (argumentName == null) {
                    if (argument.expression instanceof Expr.Variable variable) {
                        argumentName = variable.variableName;
                    } else {
                        argumentName = "";
                    }
                }

                if (1 < resolvedTypeSet.size()) {
                    hoverText.append("_There are multiple versions of this argument:_\n\n");
                }

                for (ResolvedType resolvedType : resolvedTypeSet) {
                    if (1 < resolvedTypeSet.size()) {
                        hoverText.append("---\n\n");
                    }
                    hoverText.append("```phylospec\n");
                    hoverText.append(resolvedType).append(" ").append(argumentName);
                    hoverText.append("\n```\n\n");
                }
            }
            default -> {
                return null;
            }
        }

        return new MarkupContent("markdown", hoverText.toString());
    }

    /**
     * Returns the completion items for the given cursor position.
     */
    public List<CompletionItem> getCompletionItems(CompletionParams position) {
        CompletionContext context = getCompletionContext(position.getPosition());

        if (context == CompletionContext.TYPE) {
            return getTypeCompletionItems();
        }

        if (context == CompletionContext.VARIABLE_NAME) {
            return getVariableNameCompletionItems(position);
        }

        if (context == CompletionContext.ASSIGNMENT) {
            List<CompletionItem> completionItems = getVariableCompletionItems();
            completionItems.addAll(getGeneratorCompletionItems(false));
            return completionItems;
        }

        if (context == CompletionContext.DRAW) {
            List<CompletionItem> completionItems = getVariableCompletionItems();
            completionItems.addAll(getGeneratorCompletionItems(true));
            return completionItems;
        }

        List<CompletionItem> completionItems = getVariableCompletionItems();
        completionItems.addAll(getGeneratorCompletionItems(false));
        completionItems.addAll(getTypeCompletionItems());
        completionItems.addAll(getKeywordCompletionItems());
        return completionItems;
    }

    private List<CompletionItem> getVariableNameCompletionItems(CompletionParams cursorPosition) {
        Position position = cursorPosition.getPosition();
        int lineStart = 0;

        for (int line = 0; line < position.getLine(); line++) {
            int lineBreak = content.indexOf('\n', lineStart);
            if (lineBreak < 0) return List.of();
            lineStart = lineBreak + 1;
        }

        int lineBreak = content.indexOf('\n', lineStart);
        int lineEnd = lineBreak < 0 ? content.length() : lineBreak;
        int cursorOffset = Math.min(lineStart + position.getCharacter(), lineEnd);
        String lineContentUntilCursor = content.substring(lineStart, cursorOffset);

        List<String> suggestions = new ArrayList<>();

        if (lineContentUntilCursor.startsWith("QMatrix ")) {
            suggestions.add("qMatrix");
            suggestions.add("substitutionMatrix");
            suggestions.add("substitutionModel");
        } else if (lineContentUntilCursor.startsWith("Vector<Rate> ")) {
            suggestions.add("branchRates");
            suggestions.add("siteRates");
        } else if (lineContentUntilCursor.startsWith("Alignment ")) {
            suggestions.add("alignment");
            suggestions.add("data");
        } else if (lineContentUntilCursor.startsWith("Vector<Alignment> ")) {
            suggestions.add("partition");
            suggestions.add("alignments");
            suggestions.add("data");
        } else {
            List<Token> lineTokens = getTokensBeforeCursor(cursorPosition.getPosition());
            if (lineTokens.isEmpty()) return List.of();

            String typeName = lineTokens.getFirst().lexeme;
            String variableName = typeName.toLowerCase(Locale.ROOT);
            suggestions.add(variableName);
        }

        List<CompletionItem> items = new ArrayList<>();
        for (String suggestion : suggestions) {
            CompletionItem item = new CompletionItem(suggestion);
            item.setKind(CompletionItemKind.Variable);
            items.add(item);
        }

        return items;
    }

    private List<CompletionItem> getVariableCompletionItems() {
        List<CompletionItem> completionItems = new ArrayList<>();

        for (String variableName : typeResolver.getVariableNames()) {
            Set<ResolvedType> variableTypeSet = typeResolver.resolveVariable(variableName);
            for (ResolvedType variableType : variableTypeSet) {
                CompletionItem item = new CompletionItem(variableName);
                item.setKind(CompletionItemKind.Variable);

                item.setDetail(variableType.toString());
                item.setDocumentation(variableType.getTypeComponent().getDescription());

                completionItems.add(item);
            }
        }

        return completionItems;
    }

    private List<CompletionItem> getGeneratorCompletionItems(boolean distributionsOnly) {
        List<CompletionItem> completionItems = new ArrayList<>();

        for (String generatorName : componentResolver.getKnownGenerators().keySet()) {
            List<Generator> generators = componentResolver.resolveGenerator(generatorName);

            for (Generator generator : generators) {
                if (distributionsOnly
                        && !getUnqualifiedName(generator.getGeneratedType())
                                .startsWith("Distribution<")) {
                    continue;
                }

                CompletionItem item = new CompletionItem(generator.getName());
                item.setKind(CompletionItemKind.Function);
                item.setDetail(printGeneratorInfo(new StringBuilder(), generator).toString());
                item.setDocumentation(generator.getDescription());

                completionItems.add(item);
            }
        }

        return completionItems;
    }

    private List<CompletionItem> getTypeCompletionItems() {
        List<CompletionItem> completionItems = new ArrayList<>();

        for (String typeName : componentResolver.getKnownTypes().keySet()) {
            Type type = componentResolver.resolveType(typeName);

            CompletionItem item;
            if (type != null) {
                item = new CompletionItem(getUnqualifiedName(type.getName()));
                item.setKind(CompletionItemKind.TypeParameter);
                item.setDocumentation(type.getDescription());
            } else {
                item = new CompletionItem(typeName);
            }

            completionItems.add(item);
        }

        return completionItems;
    }

    private Collection<? extends CompletionItem> getKeywordCompletionItems() {
        List<String> keywords = List.of("observed as", "observed between");

        List<CompletionItem> completionItems = new ArrayList<>();
        for (String keyword : keywords) {
            CompletionItem observedAs = new CompletionItem(keyword);
            observedAs.setKind(CompletionItemKind.Keyword);
            completionItems.add(observedAs);
        }

        return completionItems;
    }

    private CompletionContext getCompletionContext(Position position) {
        List<Token> lineTokens = getTokensBeforeCursor(position);

        if (lineTokens.isEmpty()) return CompletionContext.UNKNOWN;

        // check we are hanging, in that case just return unknown

        boolean isIndented = 0 < lineTokens.getFirst().range.start;

        // remove the token we are currently typing

        if (lineTokens.getLast().range.end >= position.getCharacter()) {
            lineTokens.removeLast();
        }

        // inspect the current context

        if (afterAssignment(lineTokens)) {
            return CompletionContext.ASSIGNMENT;
        }

        if (afterDraw(lineTokens)) {
            return CompletionContext.DRAW;
        }

        if (withinType(lineTokens, isIndented)) {
            return CompletionContext.TYPE;
        }

        if (withinVariableName(lineTokens, isIndented)) {
            return CompletionContext.VARIABLE_NAME;
        }

        return CompletionContext.UNKNOWN;
    }

    private boolean afterAssignment(List<Token> lineTokens) {
        return !lineTokens.isEmpty() && lineTokens.getLast().type == TokenType.EQUAL;
    }

    private boolean afterDraw(List<Token> lineTokens) {
        return !lineTokens.isEmpty() && lineTokens.getLast().type == TokenType.TILDE;
    }

    private List<Token> getTokensBeforeCursor(Position position) {
        List<Token> lineTokens = new ArrayList<>();
        int line = position.getLine() + 1;

        for (Token token : tokens) {
            if (token.range.startLine != line) continue;
            if (position.getCharacter() < token.range.start) continue;
            if (token.type == TokenType.EOL || token.type == TokenType.EOF) continue;
            lineTokens.add(token);
        }

        return lineTokens;
    }

    private boolean withinType(List<Token> lineTokens, boolean isIndented) {
        if (isIndented) return false;
        if (lineTokens.isEmpty()) return true;

        int genericDepth = 0;
        for (Token token : lineTokens) {
            if (token.type == TokenType.LESS) {
                genericDepth++;
            } else if (token.type == TokenType.GREATER) {
                genericDepth--;
            }
        }

        return genericDepth != 0;
    }

    private boolean withinVariableName(List<Token> lineTokens, boolean isIndented) {
        if (isIndented) return false;

        if (lineTokens.size() == 1) return true;

        for (Token token : lineTokens) {
            if (token.type != TokenType.IDENTIFIER
                    && token.type != TokenType.LESS
                    && token.type != TokenType.GREATER) {
                return false;
            }
        }

        return lineTokens.getLast().type == TokenType.GREATER;
    }

    private enum CompletionContext {
        VARIABLE_NAME,
        ASSIGNMENT,
        DRAW,
        TYPE,
        UNKNOWN
    }

    /**
     * Appends argument descriptions for a generator.
     */
    private static void printGeneratorArgumentDescriptions(
            StringBuilder stringBuilder, Generator generator) {
        if (generator.getArguments().isEmpty()) return;

        for (Argument argument : generator.getArguments()) {
            stringBuilder.append("* ").append(argument.getName());
            if (!Boolean.TRUE.equals(argument.getRequired())) {
                stringBuilder.append(" (optional)");
            }
            stringBuilder.append(": ");
            if (argument.getDescription() != null) {
                stringBuilder.append(argument.getDescription());
            }
            stringBuilder.append("\n");
        }
        stringBuilder.append("\n");
    }

    /**
     * Helper method to print the info for a generator.
     */
    private StringBuilder printGeneratorInfo(StringBuilder stringBuilder, Generator generator) {
        stringBuilder.append(getUnqualifiedName(generator.getGeneratedType())).append(" ");
        stringBuilder.append(generator.getName()).append("(");

        for (int i = 0; i < generator.getArguments().size(); i++) {
            Argument argument = generator.getArguments().get(i);

            if (argument.getRequired()) {
                stringBuilder
                        .append(getUnqualifiedName(argument.getType()))
                        .append(" ")
                        .append(argument.getName());
            } else {
                stringBuilder
                        .append("[")
                        .append(getUnqualifiedName(argument.getType()))
                        .append(" ")
                        .append(argument.getName())
                        .append("]");
            }

            if (i != generator.getArguments().size() - 1) {
                stringBuilder.append(", ");
            }
        }

        stringBuilder.append(")");
        return stringBuilder;
    }

    private static String getUnqualifiedName(String name) {
        return ComponentResolver.getUnqualifiedName(name);
    }

    /**
     * Helper method to get the token at the cursor position.
     */
    private Token getTokenAtPosition(Position position) {
        for (Token token : tokens) {
            if (token.range.startLine != position.getLine() + 1) continue;
            if (position.getCharacter() < token.range.start) continue;
            if (token.range.end < position.getCharacter()) continue;
            return token;
        }
        return null;
    }

    public void setRemoteProxy(LanguageClient remoteProxy) {
        this.client = remoteProxy;
    }
}
