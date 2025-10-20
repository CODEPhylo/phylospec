package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.ParseEventListener;
import org.phylospec.parser.Parser;

import java.util.ArrayList;
import java.util.List;

class LspDocument implements ParseEventListener {
    final private String uri;
    private LanguageClient client;

    private final List<Diagnostic> foundDiagnostics = new ArrayList<>();

    LspDocument(String uri, String content, LanguageClient client) {
        this.uri = uri;
        this.client = client;
        updateContent(content);
    }

    void updateContent(String newContent) {
        Lexer lexer = new Lexer(newContent);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        parser.registerEventListener(this);

        foundDiagnostics.clear();
        parser.parse();

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
                        new Position(token.line - 1, token.start),
                        new Position(token.line - 1, token.end)
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

    public void setRemoteProxy(LanguageClient remoteProxy) {
        this.client = remoteProxy;
    }
}
