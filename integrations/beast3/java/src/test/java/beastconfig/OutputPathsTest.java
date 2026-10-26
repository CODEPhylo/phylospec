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

        assertEquals(directory.resolve("logs/trace.log").toString(), paths.resolveUserPath("logs/./trace.log"));
        assertEquals(directory.resolve("trace.log").toString(), paths.resolveUserPath("logs/../trace.log"));
    }

    @Test
    void userPathsRejectBlankAbsoluteEscapingAndNativeTemplateNames() {
        OutputPaths paths = new OutputPaths(directory);

        for (String invalid : List.of("", " ", "../escape.log", "logs/../../escape.log", "$(tree).trees")) {
            assertThrows(IllegalArgumentException.class, () -> paths.resolveUserPath(invalid), invalid);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> paths.resolveUserPath(directory.resolve("trace.log").toString()));
    }

    @Test
    void internalPathsAcceptResolvedNamesButRejectExternalNames() {
        OutputPaths paths = new OutputPaths(directory);
        String resolved = directory.resolve("nested/trace.log").toString();

        assertEquals(resolved, paths.resolve(resolved));
        assertEquals(resolved, paths.resolve("nested/trace.log"));
        assertThrows(
                IllegalArgumentException.class,
                () -> paths.resolve(directory.resolveSibling("escape.log").toString()));
    }

    @Test
    void prepareCreatesNestedParentsWithoutLeavingProbeFiles() throws IOException {
        OutputPaths paths = new OutputPaths(directory);
        String trace = paths.resolveUserPath("logs/trace.log");
        String tree = paths.resolveUserPath("trees/posterior.trees");
        String state = paths.resolveUserPath("state/analysis.state.xml");

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
        String first = paths.resolveUserPath("new.log");
        String state = paths.resolveUserPath("analysis.state.xml");

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
    void prepareRejectsExternalLoggerAndStatePaths() throws IOException {
        Path output = Files.createDirectory(directory.resolve("output"));
        OutputPaths paths = new OutputPaths(output);
        String external = directory.resolve("external.log").toString();
        String trace = output.resolve("trace.log").toString();
        String state = output.resolve("analysis.state.xml").toString();

        assertThrows(IllegalArgumentException.class, () -> paths.prepare(List.of(external), state));
        assertThrows(IllegalArgumentException.class, () -> paths.prepare(List.of(trace), external));
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
    void createRunDirectoryAcceptsMissingAndEmptyRoots() throws IOException {
        Path missing = directory.resolve("new analysis");
        Path empty = Files.createDirectory(directory.resolve("existing empty analysis"));

        for (Path root : List.of(missing, empty)) {
            Path output = OutputPaths.createRunDirectory(root);
            assertEquals(root.resolve("output").toAbsolutePath().normalize(), output);
            assertTrue(Files.isDirectory(output));
            try (var children = Files.list(root)) {
                assertEquals(List.of(output), children.toList());
            }
        }
    }

    @Test
    void createRunDirectoryRejectsNonemptyAndAlreadyPreparedRoots() throws IOException {
        Path nonempty = Files.createDirectory(directory.resolve("nonempty"));
        Path marker = nonempty.resolve("keep.txt");
        Files.writeString(marker, "keep this analysis");

        assertThrows(IOException.class, () -> OutputPaths.createRunDirectory(nonempty));
        assertEquals("keep this analysis", Files.readString(marker));
        assertFalse(Files.exists(nonempty.resolve("output")));

        Path root = directory.resolve("already prepared");
        Path output = OutputPaths.createRunDirectory(root);
        assertThrows(IOException.class, () -> OutputPaths.createRunDirectory(root));
        assertTrue(Files.isDirectory(output));
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
        assertFalse(Files.exists(target.resolve("output")));

        Path missingTarget = directory.resolve("missing target");
        Path dangling = Files.createSymbolicLink(directory.resolve("dangling run"), missingTarget);
        assertThrows(IOException.class, () -> OutputPaths.createRunDirectory(dangling));
        assertTrue(Files.isSymbolicLink(dangling));
        assertFalse(Files.exists(missingTarget));
    }
}
