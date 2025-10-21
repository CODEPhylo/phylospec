package org.phylospec.lsp;

import java.io.*;
import java.util.concurrent.ExecutionException;

public class RunLsp {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        Lsp.startServer(System.in, System.out);
    }
}
