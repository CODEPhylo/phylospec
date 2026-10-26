package runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.core.BEASTObject;
import beast.base.core.Loggable;
import beast.base.inference.Logger;
import beast.base.inference.MCMC;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ManagedMCMCTest {

    @Test
    void existingNativePerSampleHookObservesCooperativeCancellation() {
        AtomicBoolean requested = new AtomicBoolean();
        ManagedMCMC mcmc = new ManagedMCMC(requested::get);
        assertDoesNotThrow(() -> mcmc.callUserFunction(0));
        requested.set(true);
        assertThrows(CancellationException.class, () -> mcmc.callUserFunction(1));
    }

    @Test
    void existingNativePerSampleHookObservesAndPreservesInterrupt() {
        boolean previouslyInterrupted = Thread.interrupted();
        try {
            ManagedMCMC mcmc = new ManagedMCMC(() -> false);
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class, () -> mcmc.callUserFunction(1));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            if (previouslyInterrupted) Thread.currentThread().interrupt();
        }
    }

    @Test
    void cleanupSkipsUnopenedLoggersAndDoesNotRepeatNativeClose() {
        ManagedMCMC mcmc = new ManagedMCMC(() -> false);
        Footer footer = new Footer(null);
        TrackingOutputStream bytes = new TrackingOutputStream();
        TestLogger opened = new TestLogger(new PrintStream(bytes), footer);
        TestLogger unopened = new TestLogger(null, new Footer(null));
        mcmc.loggersInput.get().add(unopened);
        mcmc.loggersInput.get().add(opened);

        // Native MCMC.run() dispatches close() through this same virtual method.
        MCMC nativeReference = mcmc;
        nativeReference.close();
        mcmc.close();

        assertEquals(0, unopened.closeCalls);
        assertEquals(1, opened.closeCalls);
        assertEquals(1, footer.closeCalls);
        assertEquals(1, bytes.closeCalls);
        assertEquals("End;\n", bytes.toString(StandardCharsets.UTF_8));
        assertDoesNotThrow(mcmc::verifyLogs);
    }

    @Test
    void failingNativeFooterStillClosesItsStreamAndRemainingLoggers() {
        ManagedMCMC mcmc = new ManagedMCMC(() -> false);
        RuntimeException footerFailure = new IllegalStateException("Cannot write footer");
        TrackingOutputStream firstBytes = new TrackingOutputStream();
        TrackingOutputStream nextBytes = new TrackingOutputStream();
        TestLogger first = new TestLogger(new PrintStream(firstBytes), new Footer(footerFailure));
        TestLogger next = new TestLogger(new PrintStream(nextBytes), new Footer(null));
        mcmc.loggersInput.get().add(first);
        mcmc.loggersInput.get().add(next);

        assertSame(footerFailure, assertThrows(RuntimeException.class, mcmc::close));
        assertEquals(1, firstBytes.closeCalls);
        assertEquals(1, nextBytes.closeCalls);
        assertEquals(1, next.closeCalls);
        assertDoesNotThrow(mcmc::close);
        assertEquals(1, first.closeCalls);
    }

    @Test
    void reportsFileWriteFailureSuppressedByNativePrintStream() {
        ManagedMCMC mcmc = new ManagedMCMC(() -> false);
        PrintStream broken = new PrintStream(new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("Simulated full disk");
            }
        });
        mcmc.loggersInput.get().add(new TestLogger(broken, new Footer(null)));

        assertDoesNotThrow(mcmc::close);
        IOException failure = assertThrows(IOException.class, mcmc::verifyLogs);
        assertTrue(failure.getMessage().contains("writing an analysis log"));
    }

    @Test
    void cleanupLeavesStandardOutputOpen() {
        PrintStream original = System.out;
        TrackingOutputStream bytes = new TrackingOutputStream();
        PrintStream stdout = new PrintStream(bytes);
        try {
            System.setOut(stdout);
            ManagedMCMC mcmc = new ManagedMCMC(() -> false);
            mcmc.loggersInput.get().add(new TestLogger(stdout, new Footer(null)));
            mcmc.close();
            assertEquals(0, bytes.closeCalls);
            stdout.println("still open");
            assertFalse(stdout.checkError());
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("still open"));
        } finally {
            System.setOut(original);
            stdout.close();
        }
    }

    /** Uses real Logger initialization and close behavior; only supplies a test-owned stream. */
    private static final class TestLogger extends Logger {
        private int closeCalls;

        private TestLogger(PrintStream stream, Footer footer) {
            loggersInput.get().add(footer);
            initAndValidate();
            m_out = stream;
        }

        @Override
        public void close() {
            closeCalls++;
            super.close();
        }
    }

    private static final class Footer extends BEASTObject implements Loggable {
        private final RuntimeException failure;
        private int closeCalls;

        private Footer(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void initAndValidate() {}

        @Override
        public void init(PrintStream out) {}

        @Override
        public void log(long sample, PrintStream out) {}

        @Override
        public void close(PrintStream out) {
            closeCalls++;
            if (failure != null) throw failure;
            out.println("End;");
        }
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private int closeCalls;

        @Override
        public void close() {
            closeCalls++;
        }
    }
}
