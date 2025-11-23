package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.phylospec.ast.*;
import org.phylospec.components.*;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.Range;
import org.phylospec.parser.ParseEventListener;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This class implements the actual LSP responses for a given document.
 * It supports parsing and type error diagnostics, hover information,
 * and basic auto-completion.
 */
class LspDocument implements ParseEventListener {
    final private String uri;
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

    /** Updates the document content and re-runs the static analysis. */
    void updateContent(String newContent) {
        // run lexer

        Lexer lexer = new Lexer(newContent);
        tokens = lexer.scanTokens();

        // run parser

        parser = new Parser(tokens);
        parser.registerEventListener(this);

        foundDiagnostics.clear();
        statements = parser.parse();

        // run type resolver and publish diagnostics

        typeResolver = new TypeResolver(componentResolver);
        for (Stmt statement : statements) {
            try {
                statement.accept(typeResolver);
            } catch (TypeError error) {
                Range range = parser.getRangeForAstNode(error.getAstNode());
                if (range == null) {
                    range = parser.getRangeForAstNode(statement);
                }

                String message = error.getMessage();

                foundDiagnostics.add(new Diagnostic(
                        new org.eclipse.lsp4j.Range(
                                new Position(range.startLine - 1, range.start), // we use 1-based line indexing
                                new Position(range.endLine - 1, range.end)
                        ), message
                ));
            }
        }

        this.client.publishDiagnostics(
                new PublishDiagnosticsParams(
                        this.uri, foundDiagnostics
                )
        );
    }

    @Override
    public void parseErrorDetected(Token token, String message) {
        foundDiagnostics.add(new Diagnostic(
                new org.eclipse.lsp4j.Range(
                        new Position(token.range.startLine - 1, token.range.start),
                        new Position(token.range.endLine - 1, token.range.end)
                ), message
        ));
    }

    /** Applied changes to the content. Assumes that the LSP is configured to
     * always receive full changes. */
    public void applyContentChanges(List<TextDocumentContentChangeEvent> contentChanges) {
        if (contentChanges.isEmpty()) return;

        // make sure we only get full changes (we configured the server to do so)
        for (TextDocumentContentChangeEvent change : contentChanges) {
            org.eclipse.lsp4j.Range range = change.getRange();
            assert (range == null);
        }

        updateContent(contentChanges.getLast().getText());
    }

    /** Returns the hover information for the given cursor position. */
    public MarkupContent getHoverInfo(Position position) {
        Token token = getTokenAtPosition(position);
        AstNode node = parser.getAstNodeForToken(token);
        if (node == null) return null;

        StringBuilder hoverText = new StringBuilder();

        switch (node) {
            case AstType typeNode -> {
                ResolvedType resolvedType = typeResolver.resolveType(typeNode);
                if (resolvedType == null) return null;

                hoverText.append("```phylospec\n");
                hoverText.append(resolvedType);
                hoverText.append("\n```\n\n");
                hoverText.append(resolvedType.getTypeComponent().getDescription());
            }
            case Stmt.Assignment stmt -> {
                ResolvedType resolvedType = typeResolver.resolveType(stmt);
                if (resolvedType == null) return null;

                hoverText.append("```phylospec\n");
                hoverText.append(resolvedType).append(" ").append(stmt.name);
                hoverText.append("\n```");
            }
            case Stmt.Draw stmt -> {
                ResolvedType resolvedType = typeResolver.resolveType(stmt);
                if (resolvedType == null) return null;

                hoverText.append("```phylospec\n");
                hoverText.append(resolvedType).append(" ").append(stmt.name);
                hoverText.append("\n```");
            }
            case Expr.Variable variable -> {
                Set<ResolvedType> resolvedTypeSet = typeResolver.resolveVariable(variable.variableName);

                for (ResolvedType resolvedType : resolvedTypeSet) {
                    hoverText.append("```phylospec\n");
                    hoverText.append(resolvedType).append(" ").append(variable.variableName);
                    hoverText.append("\n```\n\n");
                }
            }
            case Expr.Call call -> {
                List<Generator> generators = componentResolver.resolveGenerator(call.functionName);

                for (Generator generator : generators) {
                    hoverText.append("```phylospec\n");
                    printGeneratorInfo(hoverText, generator);
                    hoverText.append("\n```\n\n");
                    hoverText.append(generator.getDescription()).append("\n\n");
                }
            }
            case Expr.Argument argument -> {
                Set<ResolvedType> resolvedTypeSet = typeResolver.resolveType(argument);

                for (ResolvedType resolvedType : resolvedTypeSet) {
                    hoverText.append("```phylospec\n");
                    hoverText.append(resolvedType).append(" ").append(argument.name);
                    hoverText.append("\n```\n\n");
                }
            }
            case Expr.Get get -> {
                Set<ResolvedType> resolvedTypeSet = typeResolver.resolveType(get);

                for (ResolvedType resolvedType : resolvedTypeSet) {
                    hoverText.append("```phylospec\n");
                    hoverText.append(resolvedType).append(" ").append(get.properyName);
                    hoverText.append("\n```\n\n");
                }
            }
            default -> {
                return null;
            }
        }

        return new MarkupContent(
                "markdown",
                hoverText.toString()
        );
    }

    /** Returns the completion items for the given cursor position. */
    public List<CompletionItem> getCompletionItems(CompletionParams position) {
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

        for (String generatorName : componentResolver.getImportedGenerators().keySet()) {
            List<Generator> generators = componentResolver.resolveGenerator(generatorName);

            for (Generator generator : generators) {
                CompletionItem item = new CompletionItem(generator.getName());
                item.setKind(CompletionItemKind.Function);
                item.setDetail(printGeneratorInfo(new StringBuilder(), generator).toString());
                item.setDocumentation(generator.getDescription());

                completionItems.add(item);
            }
        }

        for (String typeName : componentResolver.getImportedTypes().keySet()) {
            Type type = componentResolver.resolveType(typeName);

            CompletionItem item;
            if (type != null) {
                item = new CompletionItem(type.getName());
                item.setKind(CompletionItemKind.TypeParameter);
                item.setDocumentation(type.getDescription());
            } else {
                item = new CompletionItem(typeName);
            }

            completionItems.add(item);
        }

        return completionItems;
    }

    /** Helper method to print the info for a generator. */
    private StringBuilder printGeneratorInfo(StringBuilder stringBuilder, Generator generator) {
        stringBuilder.append(generator.getGeneratedType()).append(" ");
        stringBuilder.append(generator.getName()).append("(");

        for (int i = 0; i < generator.getArguments().size(); i++) {
            Argument argument = generator.getArguments().get(i);

            if (argument.getRequired()) {
                stringBuilder.append(argument.getType()).append(" ").append(argument.getName());
            } else {
                stringBuilder.append("[").append(argument.getType()).append(" ").append(argument.getName()).append("]");
            }

            if (i != generator.getArguments().size() - 1) {
                stringBuilder.append(", ");
            }
        }

        stringBuilder.append(")");
        return stringBuilder;
    }

    /** Helper method to get the token at the cursor position. */
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
