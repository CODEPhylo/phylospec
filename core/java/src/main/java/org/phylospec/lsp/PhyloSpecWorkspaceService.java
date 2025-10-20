package org.phylospec.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PhyloSpecWorkspaceService implements WorkspaceService {

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        System.out.println("Workspace configuration changed");
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        List<FileEvent> changes = params.getChanges();
        System.out.println("Watched files changed: " + changes.size() + " files");
        
        for (FileEvent change : changes) {
            System.out.println("  File: " + change.getUri() + " - Type: " + change.getType());
        }
    }

    @Override
    public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        System.out.println("Workspace folders changed");
    }

}
