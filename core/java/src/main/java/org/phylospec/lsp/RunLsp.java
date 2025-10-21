package org.phylospec.lsp;

import java.io.*;
import java.util.concurrent.ExecutionException;

public class RunLsp {
    static final int PORT = 5007;

    public static void main(String[] args) throws IOException {
        Lsp.startServer(System.in, System.out, PORT);
    }
}
