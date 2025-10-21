package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

/**
 * This is an LSP for PhyloSpec. It can be started on port 5007 by calling {@code LSP.startServer(...)}.
 * The LSP supports diagnosing parsing and type errors, hover information, and basic auto-completion.
 */
public class Lsp implements org.eclipse.lsp4j.services.LanguageServer {

    private final PhyloSpecTextDocumentService textService;

    public static void startServer(InputStream in, OutputStream out) throws IOException {
        int port = 5007;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Listening on port " + port);
            Socket socket = serverSocket.accept();

            Lsp server = new Lsp();

            Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
                    server, socket.getInputStream(), socket.getOutputStream()
            );
            server.setRemoteProxy(launcher.getRemoteProxy());
            launcher.startListening();
        }
    }

    public Lsp() {
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
        this.textService.setRemoteProxy(remoteProxy);
    }
}
