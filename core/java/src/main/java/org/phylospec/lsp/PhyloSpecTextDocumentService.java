package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implements the LSP Text Document Service. This is mainly a wrapper for {@link LspDocument},
 * an instance of which exists for every open document.
 */
public class PhyloSpecTextDocumentService implements TextDocumentService {

    private final Map<String, LspDocument> documents = new HashMap<>();
    private LanguageClient client;

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem document = params.getTextDocument();

        LspDocument lspDocument = new LspDocument(document.getUri(), document.getText(), client);
        documents.put(document.getUri(), lspDocument);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        TextDocumentIdentifier document = params.getTextDocument();
        String uri = document.getUri();

        documents.get(uri).applyContentChanges(params.getContentChanges());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        documents.remove(params.getTextDocument().getUri());
    }

    @Override
    public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        LspDocument lspDocument = this.documents.get(params.getTextDocument().getUri());
        MarkupContent markupContent = lspDocument.getHoverInfo(params.getPosition());

        if (markupContent != null)
            return CompletableFuture.completedFuture(new Hover(markupContent));
        else
            return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        LspDocument lspDocument = this.documents.get(position.getTextDocument().getUri());
        List<CompletionItem> completionItems = lspDocument.getCompletionItems(position);

        return CompletableFuture.completedFuture(
                Either.forRight(new CompletionList(completionItems))
        );
    }

    public void setRemoteProxy(LanguageClient remoteProxy) {
        this.client = remoteProxy;

        for (LspDocument document : documents.values()) {
            document.setRemoteProxy(remoteProxy);
        }
    }
}
