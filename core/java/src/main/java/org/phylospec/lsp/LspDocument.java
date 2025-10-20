package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.phylospec.ast.Stmt;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.ParseEventListener;
import org.phylospec.parser.Parser;

import java.util.ArrayList;
import java.util.List;

class LspDocument implements ParseEventListener {
    final private String uri;
    private String content;

    private final List<Diagnostic> foundDiagnostics = new ArrayList<>();

    private Lexer lexer;
    private LanguageClient client;

    LspDocument(String uri, String content, LanguageClient client) {
        this.uri = uri;
        this.client = client;
        setContent(content);
    }

    void setContent(String newContent) {
        this.content = newContent;

        Lexer lexer = new Lexer(content);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        parser.registerEventListener(this);

        foundDiagnostics.clear();
        List<Stmt> statements = parser.parse();

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

    public void setRemoteProxy(LanguageClient remoteProxy) {
        this.client = remoteProxy;
    }

    public void registerChanges(List<TextDocumentContentChangeEvent> contentChanges) {
        String newContent = content;

        for (TextDocumentContentChangeEvent change : contentChanges) {
            Range range = change.getRange();
            String text = change.getText();

            if (range == null) {
                // Full document change
                newContent = text;
                continue;
            }

            int startOffset = positionToOffset(newContent, range.getStart());
            int endOffset = positionToOffset(newContent, range.getEnd());

            if (startOffset < 0) startOffset = 0;
            if (endOffset < startOffset) endOffset = startOffset;
            if (endOffset > newContent.length()) endOffset = newContent.length();

            StringBuilder builder = new StringBuilder(newContent.length() - (endOffset - startOffset) + text.length());
            builder.append(newContent, 0, startOffset);
            builder.append(text);
            builder.append(newContent, endOffset, newContent.length());
            newContent = builder.toString();
        }

        setContent(newContent);
    }

    private int positionToOffset(String text, Position position) {
        int targetLine = Math.max(0, position.getLine());
        int targetCharacter = Math.max(0, position.getCharacter());

        int currentLine = 0;
        int offset = 0;
        int length = text.length();

        while (offset < length && currentLine < targetLine) {
            char c = text.charAt(offset++);
            if (c == '\n') {
                currentLine++;
            }
        }

        // We are at the start of the target line or EOF
        int lineStart = offset;
        int remainingInDoc = length - lineStart;

        // Count characters within the line, not crossing newline
        int charsAdvanced = 0;
        while (offset < length && charsAdvanced < targetCharacter) {
            char c = text.charAt(offset);
            if (c == '\n') break;
            offset++;
            charsAdvanced++;
        }

        // If requested character is beyond EOL, clamp to end of line
        return Math.min(offset, length);
    }
}
