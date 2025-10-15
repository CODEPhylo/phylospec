package org.phylospec.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

public class LSPSetup {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        startServer(System.in, System.out);
    }

    public static void startServer(InputStream in, OutputStream out) throws InterruptedException, ExecutionException, IOException {
        int port = 5007;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Listening on port " + port);
        Socket socket = serverSocket.accept();

        LSP server = new LSP();

        Launcher<LanguageClient> l = LSPLauncher.createServerLauncher(
                server, socket.getInputStream(), socket.getOutputStream()
        );
        l.startListening();
    }
}
