package beastconfig;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputPathsTest {

    @TempDir
    Path directory;

    @Test
    void userPathsResolveInsideTheOutputDirectory() {
        OutputPaths paths = new OutputPaths(directory);

        assertEquals(directory.resolve("logs/trace.log").toString(), paths.resolve("logs/./trace.log"));
        assertEquals(directory.resolve("trace.log").toString(), paths.resolve("logs/../trace.log"));
    }

    @Test
    void pathsRejectBlankAndNativeTemplateNames() {
        OutputPaths paths = new OutputPaths(directory);

        for (String invalid : List.of("", " ", "$(tree).trees")) {
            assertThrows(IllegalArgumentException.class, () -> paths.resolve(invalid), invalid);
        }
    }

    @Test
    void pathsKeepAbsoluteNamesAndResolveRelativeNamesAgainstTheDirectory() {
        OutputPaths paths = new OutputPaths(directory);
        String resolved = directory.resolve("nested/trace.log").toString();
        String external = directory.resolveSibling("external.log").toString();

        assertEquals(resolved, paths.resolve(resolved));
        assertEquals(resolved, paths.resolve("nested/trace.log"));
        assertEquals(external, paths.resolve(external));
        assertEquals(external, paths.resolve("../external.log"));
    }

    @Test
    void prepareCreatesNestedParentsWithoutLeavingProbeFiles() throws IOException {
        OutputPaths paths = new OutputPaths(directory);
        String trace = paths.resolve("logs/trace.log");
        String tree = paths.resolve("trees/posterior.trees");
        String state = paths.resolve("state/analysis.state.xml");

        paths.prepare(List.of(trace, tree), state);

        for (String name : List.of(trace, tree, state, state + ".new")) {
            assertTrue(Files.isDirectory(Path.of(name).getParent()));
            assertFalse(Files.exists(Path.of(name)), "Write probes must be removed before BEAST opens " + name);
        }
    }

    @Test
    void prepareDoesNotOverwriteExistingFilesOrLeaveEarlierProbes() throws IOException {
        OutputPaths paths = new OutputPaths(directory);
        Path existing = directory.resolve("existing.log");
        Files.writeString(existing, "previous analysis");
        String first = paths.resolve("new.log");
        String state = paths.resolve("analysis.state.xml");

        assertThrows(IOException.class, () -> paths.prepare(List.of(first, existing.toString()), state));

        assertEquals("previous analysis", Files.readString(existing));
        assertFalse(Files.exists(Path.of(first)));
        assertFalse(Files.exists(Path.of(state)));
        assertFalse(Files.exists(Path.of(state + ".new")));
    }

    @Test
    void prepareRejectsNormalizedDuplicateLoggerNames() {
        OutputPaths paths = new OutputPaths(directory);
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
        OutputPaths paths = new OutputPaths(work);
        String external = directory.resolve("elsewhere/external.log").toString();
        String state = work.resolve("analysis.state.xml").toString();

        paths.prepare(List.of(external), state);

        assertTrue(Files.isDirectory(Path.of(external).getParent()));
        assertFalse(Files.exists(Path.of(external)));
    }

    @Test
    void prepareRejectsLoggerAndStateUsingTheSameFile() {
        OutputPaths paths = new OutputPaths(directory);
        String state = directory.resolve("analysis.state.xml").toString();

        assertThrows(IllegalArgumentException.class, () -> paths.prepare(List.of(state), state));
        assertFalse(Files.exists(Path.of(state)));
    }

    @Test
    void prepareReservesTheNativeTemporaryStateName() {
        OutputPaths paths = new OutputPaths(directory);
        String state = directory.resolve("analysis.state.xml").toString();

        assertThrows(IllegalArgumentException.class, () -> paths.prepare(List.of(state + ".new"), state));
        assertFalse(Files.exists(Path.of(state)));
        assertFalse(Files.exists(Path.of(state + ".new")));
    }

    @Test
    void preparePreservesAnExistingNativeTemporaryStateFile() throws IOException {
        OutputPaths paths = new OutputPaths(directory);
        String state = directory.resolve("analysis.state.xml").toString();
        Path temporary = Path.of(state + ".new");
        Files.writeString(temporary, "recoverable previous state");

        assertThrows(
                IOException.class,
                () -> paths.prepare(List.of(directory.resolve("trace.log").toString()), state));
        assertEquals("recoverable previous state", Files.readString(temporary));
    }

    @Test
    void prepareRejectsAFileThatWouldAlsoBeAnOutputParent() {
        OutputPaths paths = new OutputPaths(directory);
        String parentFile = directory.resolve("logs").toString();
        String childFile = directory.resolve("logs/trace.log").toString();
        String state = directory.resolve("analysis.state.xml").toString();

        assertThrows(IllegalArgumentException.class, () -> paths.prepare(List.of(parentFile, childFile), state));
        assertThrows(IllegalArgumentException.class, () -> paths.prepare(List.of(childFile, parentFile), state));
        assertFalse(Files.exists(Path.of(parentFile)));
    }

    @Test
    void prepareRejectsAnExistingDirectoryAtAnOutputFile() throws IOException {
        OutputPaths paths = new OutputPaths(directory);
        Path occupied = Files.createDirectory(directory.resolve("trace.log"));

        assertThrows(
                IOException.class,
                () -> paths.prepare(
                        List.of(occupied.toString()),
                        directory.resolve("analysis.state.xml").toString()));
        assertTrue(Files.isDirectory(occupied));
    }

    @Test
    void prepareRejectsSymbolicLinksToOutsideDirectories() throws IOException {
        Path output = Files.createDirectory(directory.resolve("output"));
        Path external = Files.createDirectory(directory.resolve("external"));
        Files.createSymbolicLink(output.resolve("linked"), external);
        OutputPaths paths = new OutputPaths(output);

        assertThrows(
                IOException.class,
                () -> paths.prepare(
                        List.of(output.resolve("linked/trace.log").toString()),
                        output.resolve("analysis.state.xml").toString()));
        assertFalse(Files.exists(external.resolve("trace.log")));
        assertTrue(Files.isSymbolicLink(output.resolve("linked")));
    }

    @Test
    void prepareRejectsDanglingSymbolicLinksAtOutputFiles() throws IOException {
        Path output = Files.createDirectory(directory.resolve("output"));
        Path external = directory.resolve("external.log");
        Path link = Files.createSymbolicLink(output.resolve("trace.log"), external);
        OutputPaths paths = new OutputPaths(output);

        assertThrows(
                IOException.class,
                () -> paths.prepare(
                        List.of(link.toString()),
                        output.resolve("analysis.state.xml").toString()));
        assertFalse(Files.exists(external));
        assertTrue(Files.isSymbolicLink(link));
    }

    @Test
    void prepareHonorsTheFilesystemsCaseSensitivityAndCleansProbes() throws IOException {
        Path probe = Files.createFile(directory.resolve("case-probe"));
        boolean caseInsensitive = Files.exists(directory.resolve("CASE-PROBE"));
        Files.delete(probe);
        OutputPaths paths = new OutputPaths(directory);
        List<String> files = List.of(
                directory.resolve("trace.log").toString(),
                directory.resolve("TRACE.LOG").toString());
        String state = directory.resolve("analysis.state.xml").toString();

        if (caseInsensitive) {
            assertThrows(IOException.class, () -> paths.prepare(files, state));
        } else {
            paths.prepare(files, state);
        }
        assertFalse(Files.exists(directory.resolve("trace.log")));
        assertFalse(Files.exists(directory.resolve("TRACE.LOG")));
        assertFalse(Files.exists(Path.of(state)));
    }

    @Test
    void createRunDirectoryCreatesMissingParents() throws IOException {
        Path run = directory.resolve("runs/new analysis");

        assertEquals(run.toAbsolutePath().normalize(), OutputPaths.createRunDirectory(run));
        assertTrue(Files.isDirectory(run));
    }

    @Test
    void createRunDirectoryRejectsExistingDirectories() throws IOException {
        Path run = OutputPaths.createRunDirectory(directory.resolve("run"));

        assertThrows(IOException.class, () -> OutputPaths.createRunDirectory(run));
    }

    @Test
    void createRunDirectoryRejectsFilesAndSymbolicLinks() throws IOException {
        Path file = directory.resolve("file");
        Files.writeString(file, "keep this file");
        assertThrows(IOException.class, () -> OutputPaths.createRunDirectory(file));
        assertEquals("keep this file", Files.readString(file));

        Path target = Files.createDirectory(directory.resolve("target"));
        Path link = Files.createSymbolicLink(directory.resolve("linked run"), target);
        assertThrows(IOException.class, () -> OutputPaths.createRunDirectory(link));
        assertTrue(Files.isSymbolicLink(link));

        Path missingTarget = directory.resolve("missing target");
        Path dangling = Files.createSymbolicLink(directory.resolve("dangling run"), missingTarget);
        assertThrows(IOException.class, () -> OutputPaths.createRunDirectory(dangling));
        assertTrue(Files.isSymbolicLink(dangling));
        assertFalse(Files.exists(missingTarget));
    }
}
