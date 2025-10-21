package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.concurrent.CompletableFuture;

public class LSP implements org.eclipse.lsp4j.services.LanguageServer {

    private final PhyloSpecTextDocumentService textService;
    private LanguageClient client;

    public LSP() {
        this.textService = new PhyloSpecTextDocumentService();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        final InitializeResult res = new InitializeResult(new ServerCapabilities());
        res.getCapabilities().setCompletionProvider(new CompletionOptions());
        res.getCapabilities().setHoverProvider(Boolean.TRUE);
        res.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        res.getCapabilities().setDiagnosticProvider(
                new DiagnosticRegistrationOptions(false, false)
        );

        return CompletableFuture.supplyAsync(() -> res);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.supplyAsync(() -> Boolean.TRUE);
    }

    @Override
    public void exit() {}

    @Override
    public TextDocumentService getTextDocumentService() {
        return this.textService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return null;
    }

    public void setRemoteProxy(LanguageClient remoteProxy) {
        this.client = remoteProxy;
        this.textService.setRemoteProxy(remoteProxy);
    }
}
