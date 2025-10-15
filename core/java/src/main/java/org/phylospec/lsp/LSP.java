package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.concurrent.CompletableFuture;

public class LSP implements org.eclipse.lsp4j.services.LanguageServer {

    private TextDocumentService textService;
    private WorkspaceService workspaceService;
    LanguageClient client;

    public LSP() {
        this.textService = new PhyloSpecTextDocumentService();
        this.workspaceService = new PhyloSpecWorkspaceService();
        System.out.println("PhyloSpec LSP Server initialized with file change detection");
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
        res.getCapabilities().getWorkspace().setFileOperations(new WorkspaceFileOperationsServerCapabilities());
        res.getCapabilities().getWorkspace().setDidChangeWatchedFiles(new DidChangeWatchedFilesRegistrationOptions());
        res.getCapabilities().getWorkspace().getDidChangeWatchedFiles().setDynamicRegistration(Boolean.TRUE);

        System.out.println("LSP Server capabilities initialized with file watching enabled");
        return CompletableFuture.supplyAsync(() -> res);
    }

    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.supplyAsync(() -> Boolean.TRUE);
    }

    public void exit() {
    }

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
