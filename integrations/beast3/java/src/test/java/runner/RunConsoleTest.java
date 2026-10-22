package runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.core.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunConsoleTest {

    @Test
    void capturesExistingBeastStreamsWithoutDuplicatingSharedDestinations(@TempDir Path directory) throws Exception {
        try (SavedStreams saved = SavedStreams.current()) {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            PrintStream originalOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
            PrintStream originalErr = new PrintStream(stderr, true, StandardCharsets.UTF_8);
            install(originalOut, originalErr);
            Path log = directory.resolve("console.log");

            try (RunConsole console = RunConsole.open(log)) {
                assertSame(System.out, console.out());
                assertSame(System.err, console.err());
                assertSame(System.out, Log.info);
                assertSame(System.err, Log.err);
                assertSame(Log.nullStream, Log.debug);
                assertSame(Log.nullStream, Log.trace);
                System.out.println("standard output");
                System.err.println("standard error");
                Log.info.println("native info");
                Log.warning.println("native warning");
                Log.err.println("native error");
                Log.debug.println("disabled debug");
                console.log().println("previous diagnostic");
                console.verify();
            }

            assertSame(originalOut, System.out);
            assertSame(originalErr, System.err);
            assertSame(originalOut, Log.info);
            assertSame(originalErr, Log.err);
            assertSame(originalErr, Log.warning);
            assertSame(Log.nullStream, Log.debug);
            assertSame(Log.nullStream, Log.trace);
            assertEquals("standard output\nnative info\n", stdout.toString(StandardCharsets.UTF_8));
            assertEquals("standard error\nnative warning\nnative error\n", stderr.toString(StandardCharsets.UTF_8));
            assertEquals(
                    "standard output\nstandard error\nnative info\nnative warning\nnative error\nprevious diagnostic\n",
                    Files.readString(log));
            originalOut.println("console still open");
            assertTrue(stdout.toString(StandardCharsets.UTF_8).endsWith("console still open\n"));
            assertFalse(Files.readString(log).contains("console still open"));
        }
    }

    @Test
    void restoresDistinctBeastStreamsExactly(@TempDir Path directory) throws Exception {
        try (SavedStreams saved = SavedStreams.current()) {
            PrintStream nativeInfo = new PrintStream(new ByteArrayOutputStream());
            Log.info = nativeInfo;
            Path log = directory.resolve("console.log");
            try (RunConsole console = RunConsole.open(log)) {
                Log.info.println("separate destination");
            }
            assertSame(nativeInfo, Log.info);
            assertEquals("separate destination\n", Files.readString(log));
        }
    }

    @Test
    void rejectsNestedCaptureAndDoesNotOverwriteAnExistingLog(@TempDir Path directory) throws Exception {
        Path first = directory.resolve("first.log");
        try (RunConsole console = RunConsole.open(first)) {
            IOException failure =
                    assertThrows(IOException.class, () -> RunConsole.open(directory.resolve("nested.log")));
            assertTrue(failure.getMessage().contains("already capturing"));
            assertFalse(Files.exists(directory.resolve("nested.log")));
        }
        Files.writeString(first, "preserve me");
        assertThrows(IOException.class, () -> RunConsole.open(first));
        assertEquals("preserve me", Files.readString(first));
        try (RunConsole console = RunConsole.open(directory.resolve("next.log"))) {
            console.verify();
        }
    }

    @Test
    void verifiesSuppressedStreamErrorsAndRestoresStreamsOnFailure(@TempDir Path directory) throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        RunConsole console = RunConsole.open(directory.resolve("console.log"));
        try {
            console.out().close();
            console.out().println("cannot be written");
            assertThrows(IOException.class, console::verify);
            assertThrows(IOException.class, console::close);
            assertSame(originalOut, System.out);
            assertSame(originalErr, System.err);
        } finally {
            console.close();
        }
        // A failed close must release the global capture guard as well.
        try (RunConsole next = RunConsole.open(directory.resolve("next.log"))) {
            next.verify();
        }
    }

    @Test
    void serializesConcurrentConsoleWrites(@TempDir Path directory) throws Exception {
        try (SavedStreams saved = SavedStreams.current()) {
            install(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
            Path log = directory.resolve("console.log");
            try (RunConsole console = RunConsole.open(log);
                    var workers = Executors.newFixedThreadPool(2)) {
                var stdout = workers.submit(() -> {
                    for (int index = 0; index < 100; index++) System.out.println("out-" + index);
                });
                var stderr = workers.submit(() -> {
                    for (int index = 0; index < 100; index++) System.err.println("err-" + index);
                });
                stdout.get(10, TimeUnit.SECONDS);
                stderr.get(10, TimeUnit.SECONDS);
                console.verify();
            }
            var lines = Files.readAllLines(log);
            assertEquals(200, lines.size());
            for (int index = 0; index < 100; index++) {
                assertTrue(lines.contains("out-" + index));
                assertTrue(lines.contains("err-" + index));
            }
        }
    }

    private static void install(PrintStream out, PrintStream err) {
        System.setOut(out);
        System.setErr(err);
        Log.err = err;
        Log.warning = err;
        Log.info = out;
        Log.debug = Log.nullStream;
        Log.trace = Log.nullStream;
    }

    private record SavedStreams(
            PrintStream out,
            PrintStream err,
            PrintStream logErr,
            PrintStream warning,
            PrintStream info,
            PrintStream debug,
            PrintStream trace)
            implements AutoCloseable {
        static SavedStreams current() {
            return new SavedStreams(System.out, System.err, Log.err, Log.warning, Log.info, Log.debug, Log.trace);
        }

        @Override
        public void close() {
            System.setOut(out);
            System.setErr(err);
            Log.err = logErr;
            Log.warning = warning;
            Log.info = info;
            Log.debug = debug;
            Log.trace = trace;
        }
    }
}
