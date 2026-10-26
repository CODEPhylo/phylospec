package org.phylospec.typeresolver.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.phylospec.workspace.Workspace;

class LightweightFileParsersTest {

    @TempDir
    Path sourceDirectory;

    @Test
    void sourceDirectoryWinsOverConflictingWorkingDirectoryFile() throws IOException {
        Path workingDirectoryFile = createWorkingDirectoryInput();
        try {
            Path sourceFile = sourceDirectory.resolve(workingDirectoryFile);
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, nexus(3));

            Path resolved = LightweightFileParsers.resolveSmallFile(
                            workingDirectoryFile.toString(), new Workspace(sourceDirectory))
                    .orElseThrow();

            assertEquals(sourceFile, resolved);
            assertEquals(
                    3, LightweightFileParsers.parseNexus(resolved).orElseThrow().numTaxa());
        } finally {
            deleteWorkingDirectoryInput(workingDirectoryFile);
        }
    }

    @Test
    void missingSourceDirectoryFileDoesNotFallBackToWorkingDirectoryOrWorkspaceFolders() throws IOException {
        Path workingDirectoryFile = createWorkingDirectoryInput();
        try {
            Workspace workspace = new Workspace(sourceDirectory);
            workspace.FOLDERS.add(Path.of("").toAbsolutePath());

            assertTrue(LightweightFileParsers.resolveSmallFile(workingDirectoryFile.toString(), workspace)
                    .isEmpty());
        } finally {
            deleteWorkingDirectoryInput(workingDirectoryFile);
        }
    }

    @Test
    void absoluteInputPathIsPreserved() throws IOException {
        Path input = Files.writeString(sourceDirectory.resolve("absolute input.nex"), nexus(4));
        Workspace workspace = new Workspace(sourceDirectory.resolve("another directory"));

        Path resolved = LightweightFileParsers.resolveSmallFile(input.toString(), workspace)
                .orElseThrow();

        assertEquals(input, resolved);
        assertEquals(
                4, LightweightFileParsers.parseNexus(resolved).orElseThrow().numTaxa());
    }

    @Test
    void defaultWorkspaceRetainsWorkingDirectoryPrecedence() throws IOException {
        Path workingDirectoryFile = createWorkingDirectoryInput();
        try {
            Path sourceFile = sourceDirectory.resolve(workingDirectoryFile);
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, nexus(3));
            Workspace workspace = new Workspace();
            workspace.FOLDERS.add(sourceDirectory);

            Path resolved = LightweightFileParsers.resolveSmallFile(workingDirectoryFile.toString(), workspace)
                    .orElseThrow();

            assertEquals(workingDirectoryFile, resolved);
            assertEquals(
                    2, LightweightFileParsers.parseNexus(resolved).orElseThrow().numTaxa());
        } finally {
            deleteWorkingDirectoryInput(workingDirectoryFile);
        }
    }

    @Test
    void defaultWorkspaceStillFindsFilesInWorkspaceFolders() throws IOException {
        Path input = Files.writeString(sourceDirectory.resolve("workspace input.nex"), nexus(5));
        Workspace workspace = new Workspace();
        workspace.FOLDERS.add(sourceDirectory);

        assertEquals(
                input,
                LightweightFileParsers.resolveSmallFile(input.getFileName().toString(), workspace)
                        .orElseThrow());
    }

    private static Path createWorkingDirectoryInput() throws IOException {
        Path directory = Files.createTempDirectory(Files.createDirectories(Path.of("target")), "input-path-test-");
        return Files.writeString(directory.resolve("input.nex"), nexus(2));
    }

    private static void deleteWorkingDirectoryInput(Path input) throws IOException {
        Files.delete(input);
        Files.delete(input.getParent());
    }

    private static String nexus(int taxa) {
        return "#NEXUS\nBEGIN DATA;\nDIMENSIONS NTAX=" + taxa + " NCHAR=10;\nEND;\n";
    }
}
