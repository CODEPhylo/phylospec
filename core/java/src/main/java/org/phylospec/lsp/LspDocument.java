package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.phylospec.ast.ResolvedType;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.TypeError;
import org.phylospec.ast.TypeResolver;
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

class LspDocument implements ParseEventListener {
    final private String uri;
    private LanguageClient client;

    private final ComponentResolver componentResolver;

    private String content;
    private List<Token> tokens;
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

        Parser parser = new Parser(tokens);
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
        if (token == null) return null;

        String lexeme = token.lexeme;

        StringBuilder hoverText = new StringBuilder();

        ResolvedType type = typeResolver.resolveVariable(lexeme);
        Type typeComponent = componentResolver.resolveType(lexeme);
        List<Generator> generatorComponents = componentResolver.resolveGenerator(lexeme);

        if (type != null) {
            hoverText.append("```phylospec\n")
                    .append(type)
                    .append(" ")
                    .append(lexeme)
                    .append("\n```");

        } else if (generatorComponents != null && !generatorComponents.isEmpty()) {
            for (Generator generator : generatorComponents) {
                hoverText.append("```phylospec\n");
                hoverText.append(generator.getGeneratedType()).append(" ");
                hoverText.append(generator.getName()).append("(");

                for (int i = 0; i < generator.getArguments().size(); i++) {
                    Argument argument = generator.getArguments().get(i);

                    if (argument.getRequired()) {
                        hoverText.append(argument.getType()).append(" ").append(argument.getName());
                    } else {
                        hoverText.append("[").append(argument.getType()).append(" ").append(argument.getName()).append("]");
                    }

                    if (i != generator.getArguments().size() - 1) {
                        hoverText.append(", ");
                    }
                }

                hoverText.append(")").append("\n```\n\n");
                hoverText.append(generator.getDescription()).append("\n\n");
            }
        } else if (typeComponent != null) {
            hoverText.append("```phylospec\n").append(typeComponent.getName());

            if (!typeComponent.getTypeParameters().isEmpty()) {
                hoverText.append("<").append(String.join(",", typeComponent.getTypeParameters())).append(">");
            }

            hoverText.append("\n```\n\n");
            hoverText.append(typeComponent.getDescription());
        }

        if (hoverText.isEmpty()) return null;

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
            item.setDetail(typeResolver.variableTypes.get(variableName).toString());
            completionItems.add(item);
        }

        for (String generatorName : componentResolver.importedGenerators.keySet()) {
            CompletionItem item = new CompletionItem(generatorName);
            item.setKind(CompletionItemKind.Function);
            completionItems.add(item);
        }

        for (String typeName : componentResolver.importedTypes.keySet()) {
            CompletionItem item = new CompletionItem(typeName);
            item.setKind(CompletionItemKind.TypeParameter);
            completionItems.add(item);
        }

        return completionItems;
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
