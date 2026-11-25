package org.phylospec.tiling.mcmc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunOutputsTest {

    @TempDir
    Path directory;

    @Test
    void userPathsResolveInsideTheOutputDirectory() {
        RunOutputs paths = new RunOutputs(directory);

        assertEquals(directory.resolve("logs/trace.log").toString(), paths.resolve("logs/./trace.log"));
        assertEquals(directory.resolve("trace.log").toString(), paths.resolve("logs/../trace.log"));
    }

    @Test
    void pathsRejectBlankNames() {
        RunOutputs paths = new RunOutputs(directory);

        for (String invalid : List.of("", " ")) {
            assertThrows(IllegalArgumentException.class, () -> paths.resolve(invalid), invalid);
        }
    }

    @Test
    void pathsKeepAbsoluteNamesAndResolveRelativeNamesAgainstTheDirectory() {
        RunOutputs paths = new RunOutputs(directory);
        String resolved = directory.resolve("nested/trace.log").toString();
        String external = directory.resolveSibling("external.log").toString();

        assertEquals(resolved, paths.resolve(resolved));
        assertEquals(resolved, paths.resolve("nested/trace.log"));
        assertEquals(external, paths.resolve(external));
        assertEquals(external, paths.resolve("../external.log"));
    }

    @Test
    void prepareCreatesNestedParentsWithoutCreatingFiles() throws IOException {
        RunOutputs paths = new RunOutputs(directory);
        String trace = paths.resolve("logs/trace.log");
        String tree = paths.resolve("trees/posterior.trees");
        String state = paths.resolve("state/analysis.state.xml");

        paths.prepare(List.of(trace, tree), state);

        for (String name : List.of(trace, tree, state)) {
            assertTrue(Files.isDirectory(Path.of(name).getParent()));
            assertFalse(Files.exists(Path.of(name)));
        }
    }

    @Test
    void prepareDoesNotOverwriteExistingFiles() throws IOException {
        RunOutputs paths = new RunOutputs(directory);
        Path existing = directory.resolve("existing.log");
        Files.writeString(existing, "previous analysis");
        String first = paths.resolve("new.log");
        String state = paths.resolve("analysis.state.xml");

        assertThrows(IOException.class, () -> paths.prepare(List.of(first, existing.toString()), state));

        assertEquals("previous analysis", Files.readString(existing));
        assertFalse(Files.exists(Path.of(first)));
        assertFalse(Files.exists(Path.of(state)));
    }

    @Test
    void prepareRejectsNormalizedDuplicateLoggerNames() {
        RunOutputs paths = new RunOutputs(directory);
        String first = directory.resolve("trace.log").toString();
        String duplicate = directory.resolve("nested/../trace.log").toString();

        assertThrows(
                IllegalArgumentException.class,
                () -> paths.prepare(
                        List.of(first, duplicate),
                        directory.resolve("state.xml").toString()));
        assertFalse(Files.exists(Path.of(first)));
    }

    @Test
    void prepareAcceptsLoggerAndStatePathsOutsideTheWorkingDirectory() throws IOException {
        Path work = Files.createDirectory(directory.resolve("work"));
        RunOutputs paths = new RunOutputs(work);
        String external = directory.resolve("elsewhere/external.log").toString();
        String state = work.resolve("analysis.state.xml").toString();

        paths.prepare(List.of(external), state);

        assertTrue(Files.isDirectory(Path.of(external).getParent()));
        assertFalse(Files.exists(Path.of(external)));
    }

    @Test
    void prepareRejectsLoggerAndStateUsingTheSameFile() {
        RunOutputs paths = new RunOutputs(directory);
        String state = directory.resolve("analysis.state.xml").toString();

        assertThrows(IllegalArgumentException.class, () -> paths.prepare(List.of(state), state));
        assertFalse(Files.exists(Path.of(state)));
    }

    @Test
    void prepareRejectsAnExistingDirectoryAtAnOutputFile() throws IOException {
        RunOutputs paths = new RunOutputs(directory);
        Path occupied = Files.createDirectory(directory.resolve("trace.log"));

        assertThrows(
                IOException.class,
                () -> paths.prepare(
                        List.of(occupied.toString()),
                        directory.resolve("analysis.state.xml").toString()));
        assertTrue(Files.isDirectory(occupied));
    }

    @Test
    void prepareRejectsDanglingSymbolicLinksAtOutputFiles() throws IOException {
        Path output = Files.createDirectory(directory.resolve("output"));
        Path external = directory.resolve("external.log");
        Path link = Files.createSymbolicLink(output.resolve("trace.log"), external);
        RunOutputs paths = new RunOutputs(output);

        assertThrows(
                IOException.class,
                () -> paths.prepare(
                        List.of(link.toString()),
                        output.resolve("analysis.state.xml").toString()));
        assertFalse(Files.exists(external));
        assertTrue(Files.isSymbolicLink(link));
    }

    @Test
    void createRunDirectoryCreatesMissingParents() throws IOException {
        Path run = directory.resolve("runs/new analysis");

        assertEquals(run.toAbsolutePath().normalize(), RunOutputs.claimRecordDirectory(run));
        assertTrue(Files.isDirectory(run));
    }

    @Test
    void createRunDirectoryRejectsExistingDirectories() throws IOException {
        Path run = RunOutputs.claimRecordDirectory(directory.resolve("run"));

        assertThrows(IOException.class, () -> RunOutputs.claimRecordDirectory(run));
    }

    @Test
    void createRunDirectoryReplacesTheRecordOfAFailedRun() throws IOException {
        Path run = this.createFailedRecord();

        assertEquals(run.toAbsolutePath().normalize(), RunOutputs.claimRecordDirectory(run));
        assertFalse(Files.exists(run.resolve("run.json")));
        assertFalse(Files.exists(run.resolve("console.log")));
    }

    @Test
    void createRunDirectoryKeepsTheRecordOfAFailedRunIfTheCleanupFails() throws IOException {
        Path run = this.createFailedRecord();
        Files.writeString(run.resolve("notes.txt"), "keep this file");

        assertThrows(IOException.class, () -> RunOutputs.claimRecordDirectory(run));
        assertTrue(Files.exists(run.resolve("run.json")));
        assertTrue(Files.exists(run.resolve("console.log")));
        assertTrue(Files.exists(run.resolve("model.phylospec")));
    }

    @Test
    void createRunDirectoryReplacesAnyEarlierRecordWithOverwrite() throws IOException {
        Path run = RunOutputs.claimRecordDirectory(directory.resolve("run"));
        Files.writeString(run.resolve("run.json"), "{\"status\": \"succeeded\"}");

        assertThrows(IOException.class, () -> RunOutputs.claimRecordDirectory(run));
        assertEquals(run.toAbsolutePath().normalize(), RunOutputs.claimRecordDirectory(run, true));
        assertFalse(Files.exists(run.resolve("run.json")));
    }

    @Test
    void prepareReplacesExistingOutputsWithOverwrite() throws IOException {
        Path trace = Files.writeString(directory.resolve("trace.log"), "outdated");

        assertThrows(IOException.class, () -> new RunOutputs(directory).prepare(List.of("trace.log"), null));
        new RunOutputs(directory, true).prepare(List.of("trace.log"), null);
        assertFalse(Files.exists(trace));
    }

    private Path createFailedRecord() throws IOException {
        Path run = Files.createDirectory(directory.resolve("run"));
        Files.writeString(run.resolve("run.json"), "{\"status\": \"failed\", \"generatedOutputs\": []}");
        Files.writeString(run.resolve("console.log"), "failure");
        Files.writeString(run.resolve("model.phylospec"), "mcmc {}");
        return run;
    }

    @Test
    void createRunDirectoryRejectsFilesAndSymbolicLinks() throws IOException {
        Path file = directory.resolve("file");
        Files.writeString(file, "keep this file");
        assertThrows(IOException.class, () -> RunOutputs.claimRecordDirectory(file));
        assertEquals("keep this file", Files.readString(file));

        Path target = Files.createDirectory(directory.resolve("target"));
        Path link = Files.createSymbolicLink(directory.resolve("linked run"), target);
        assertThrows(IOException.class, () -> RunOutputs.claimRecordDirectory(link));
        assertTrue(Files.isSymbolicLink(link));

        Path missingTarget = directory.resolve("missing target");
        Path dangling = Files.createSymbolicLink(directory.resolve("dangling run"), missingTarget);
        assertThrows(IOException.class, () -> RunOutputs.claimRecordDirectory(dangling));
        assertTrue(Files.isSymbolicLink(dangling));
        assertFalse(Files.exists(missingTarget));
    }
}
