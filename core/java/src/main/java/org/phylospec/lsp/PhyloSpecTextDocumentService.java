package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.HashMap;
import java.util.Map;

public class PhyloSpecTextDocumentService implements TextDocumentService {

    private final Map<String, LspDocument> documents = new HashMap<>();
    private LanguageClient client;

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem document = params.getTextDocument();

        System.out.println("Opened " + document.getUri());

        LspDocument lspDocument = new LspDocument(document.getUri(), document.getText(), client);
        documents.put(document.getUri(), lspDocument);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        TextDocumentIdentifier document = params.getTextDocument();
        String uri = document.getUri();

        System.out.println("Changed " + uri);

        documents.get(uri).registerChanges(params.getContentChanges());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String content = params.getText();
        documents.computeIfAbsent(uri,x -> new LspDocument(uri, content, client))
                .updateContent(content);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        documents.remove(params.getTextDocument().getUri());
    }

    public void setRemoteProxy(LanguageClient remoteProxy) {
        this.client = remoteProxy;

        for (LspDocument document : documents.values()) {
            document.setRemoteProxy(remoteProxy);
        }
    }
}
