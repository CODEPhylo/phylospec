package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.phylospec.ast.*;
import org.phylospec.components.Argument;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.components.Type;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenRange;
import org.phylospec.parser.ParseEventListener;
import org.phylospec.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        ComponentResolver componentResolver = new ComponentResolver();

        try {
            componentResolver.registerLibraryFromFile("schema/phylospec-core-component-library.json");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        componentResolver.importEntireNamespace(List.of("phylospec"));
        return componentResolver;
    }

    void updateContent(String newContent) {
        // run lexer

        Lexer lexer = new Lexer(newContent);
        tokens = lexer.scanTokens();

        // run parser

        parser = new Parser(tokens);
        parser.registerEventListener(this);

        foundDiagnostics.clear();
        statements = parser.parse();

        // run type resolver

        typeResolver = new TypeResolver(componentResolver);

        // publish diagnostics

        for (Stmt statement : statements) {
            try {
                statement.accept(typeResolver);
            } catch (TypeError error) {
                TokenRange range = error.getTokenRange();
                String message = error.getMessage();

                if (range == null) {
                    range = statement.tokenRange;
                }

                foundDiagnostics.add(new Diagnostic(
                        new Range(
                                new Position(range.line - 1, range.start),
                                new Position(range.line - 1, range.end)
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
                new Range(
                        new Position(token.range.line - 1, token.range.start),
                        new Position(token.range.line - 1, token.range.end)
                ), message
        ));
    }

    public void registerChanges(List<TextDocumentContentChangeEvent> contentChanges) {
        if (contentChanges.isEmpty()) return;

        // make sure we only get full changes (we configured the server to do so)
        for (TextDocumentContentChangeEvent change : contentChanges) {
            Range range = change.getRange();
            assert (range == null);
        }

        updateContent(contentChanges.getLast().getText());
    }

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
                hoverText.append("\n```");
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
                ResolvedType resolvedType = typeResolver.resolveVariable(variable.variableName);
                if (resolvedType == null) return null;

                hoverText.append("```phylospec\n");
                hoverText.append(resolvedType).append(" ").append(variable.variableName);
                hoverText.append("\n```");
            }
            case Expr.Call call -> {
                printCall(hoverText, call);
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

    public List<CompletionItem> getCompletionItems() {
        List<CompletionItem> completionItems = new ArrayList<>();

        for (String variableName : typeResolver.variableTypes.keySet()) {
            CompletionItem item = new CompletionItem(variableName);
            item.setKind(CompletionItemKind.Variable);

            ResolvedType variableType = typeResolver.resolveVariable(variableName);
            if (variableType != null) {
                item.setDetail(variableType.toString());
                item.setDocumentation(variableType.getTypeComponent().getDescription());
            }

            completionItems.add(item);
        }

        for (String generatorName : componentResolver.importedGenerators.keySet()) {
            List<Generator> generators = componentResolver.resolveGenerator(generatorName);

            for (Generator generator : generators) {
                CompletionItem item = new CompletionItem(generator.getName());
                item.setKind(CompletionItemKind.Function);
                item.setDetail(printGenerator(new StringBuilder(), generator).toString());
                item.setDocumentation(generator.getDescription());

                completionItems.add(item);
            }
        }

        for (String typeName : componentResolver.importedTypes.keySet()) {
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

    private void printCall(StringBuilder hoverText, Expr.Call call) {
        List<Generator> generators = componentResolver.resolveGenerator(call.functionName);

        for (Generator generator : generators) {
            hoverText.append("```phylospec\n");
            printGenerator(hoverText, generator);
            hoverText.append("\n```\n\n");
            hoverText.append(generator.getDescription()).append("\n\n");
        }
    }

    private StringBuilder printGenerator(StringBuilder stringBuilder, Generator generator) {
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

    private Token getTokenAtPosition(Position position) {
        for (Token token : tokens) {
            if (token.range.line != position.getLine() + 1) continue;
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
