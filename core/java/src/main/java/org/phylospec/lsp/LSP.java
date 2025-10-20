package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.concurrent.CompletableFuture;

public class LSP implements org.eclipse.lsp4j.services.LanguageServer {

    private final TextDocumentService textService;
    private final WorkspaceService workspaceService;
    LanguageClient client;

    public LSP() {
        this.textService = new PhyloSpecTextDocumentService();
        this.workspaceService = new PhyloSpecWorkspaceService();
    }

    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        final InitializeResult res = new InitializeResult(new ServerCapabilities());
        res.getCapabilities().setCodeActionProvider(Boolean.TRUE);
        res.getCapabilities().setCompletionProvider(new CompletionOptions());
        res.getCapabilities().setDefinitionProvider(Boolean.TRUE);
        res.getCapabilities().setHoverProvider(Boolean.TRUE);
        res.getCapabilities().setReferencesProvider(Boolean.TRUE);
        res.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        res.getCapabilities().setDocumentSymbolProvider(Boolean.TRUE);
        
        // Enable file watching to detect file changes
        res.getCapabilities().setWorkspace(new WorkspaceServerCapabilities());

        return CompletableFuture.supplyAsync(() -> res);
    }

    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.supplyAsync(() -> Boolean.TRUE);
    }

    public void exit() {}

    public TextDocumentService getTextDocumentService() {
        return this.textService;
    }

    public WorkspaceService getWorkspaceService() {
        return this.workspaceService;
    }

    public void setRemoteProxy(LanguageClient remoteProxy) {
        this.client = remoteProxy;
    }
}
