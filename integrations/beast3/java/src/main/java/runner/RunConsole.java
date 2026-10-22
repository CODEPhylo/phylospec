package runner;

import beast.base.core.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Captures one managed run's console while preserving the existing console destinations. */
final class RunConsole implements AutoCloseable {

    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private final Streams original;
    private final ConsoleFile file;
    private final Map<PrintStream, PrintStream> captured = new IdentityHashMap<>();
    private final PrintStream out;
    private final PrintStream err;
    private final PrintStream log;
    private boolean closed;

    private RunConsole(Path consoleFile) throws IOException {
        // Initialize BEAST Log before replacing System streams, including its disabled levels.
        original = Streams.current();
        file = new ConsoleFile(
                Files.newOutputStream(consoleFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
        out = capture(original.out());
        err = capture(original.err());
        log = tee(null);
    }

    static RunConsole open(Path consoleFile) throws IOException {
        if (!ACTIVE.compareAndSet(false, true)) {
            throw new IOException("Another managed run is already capturing this JVM's console.");
        }
        RunConsole console = null;
        try {
            console = new RunConsole(consoleFile);
            console.install();
            return console;
        } catch (IOException | RuntimeException | Error failure) {
            if (console == null) {
                ACTIVE.set(false);
            } else {
                try {
                    console.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
    }

    private void install() {
        System.setOut(out);
        System.setErr(err);
        Log.err = capture(original.logErr());
        Log.warning = capture(original.warning());
        Log.info = capture(original.info());
        Log.debug = capture(original.debug());
        Log.trace = capture(original.trace());
    }

    private PrintStream capture(PrintStream destination) {
        // BEAST uses identity comparison with nullStream to maintain its log-level filtering.
        if (destination == Log.nullStream) return destination;
        return captured.computeIfAbsent(destination, this::tee);
    }

    private PrintStream tee(PrintStream destination) {
        return new PrintStream(
                new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        if (destination != null) destination.write(value);
                        file.write(value);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        if (destination != null) destination.write(bytes, offset, length);
                        file.write(bytes, offset, length);
                    }

                    @Override
                    public void flush() throws IOException {
                        // Never hold the file lock while entering another PrintStream's lock.
                        if (destination != null) destination.flush();
                        file.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        flush();
                    }
                },
                true,
                StandardCharsets.UTF_8);
    }

    PrintStream out() {
        return out;
    }

    PrintStream err() {
        return err;
    }

    /** Records earlier diagnostics or full error details without printing them a second time. */
    PrintStream log() {
        return log;
    }

    /** PrintStream suppresses IOExceptions, so a successful run must explicitly check capture. */
    void verify() throws IOException {
        boolean failed = log.checkError();
        for (PrintStream stream : captured.values()) {
            failed |= stream.checkError();
        }
        file.verify();
        if (failed) throw new IOException("Could not write the complete console log.");
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            original.restore();
            verify();
        } finally {
            try {
                file.close();
            } finally {
                ACTIVE.set(false);
            }
        }
    }

    private record Streams(
            PrintStream out,
            PrintStream err,
            PrintStream logErr,
            PrintStream warning,
            PrintStream info,
            PrintStream debug,
            PrintStream trace) {
        static Streams current() {
            return new Streams(System.out, System.err, Log.err, Log.warning, Log.info, Log.debug, Log.trace);
        }

        void restore() {
            System.setOut(out);
            System.setErr(err);
            Log.err = logErr;
            Log.warning = warning;
            Log.info = info;
            Log.debug = debug;
            Log.trace = trace;
        }
    }

    /** Serializes file writes only; it never calls a console PrintStream while holding its lock. */
    private static final class ConsoleFile {
        private final OutputStream output;
        private IOException failure;

        private ConsoleFile(OutputStream output) {
            this.output = output;
        }

        synchronized void write(int value) throws IOException {
            try {
                output.write(value);
            } catch (IOException error) {
                remember(error);
                throw error;
            }
        }

        synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            try {
                output.write(bytes, offset, length);
            } catch (IOException error) {
                remember(error);
                throw error;
            }
        }

        synchronized void flush() throws IOException {
            try {
                output.flush();
            } catch (IOException error) {
                remember(error);
                throw error;
            }
        }

        synchronized void verify() throws IOException {
            flush();
            if (failure != null) throw new IOException("Could not write the complete console log.", failure);
        }

        synchronized void close() throws IOException {
            output.close();
        }

        private void remember(IOException error) {
            if (failure == null) failure = error;
        }
    }
}
