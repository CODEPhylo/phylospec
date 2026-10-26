package runner;

import beast.base.inference.Logger;
import beast.base.inference.MCMC;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Adds cooperative cancellation and resource cleanup through BEAST's existing extension hooks. */
final class ManagedMCMC extends MCMC {
    private final BooleanSupplier cancellationRequested;
    private final Set<Logger> closed = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean logWriteFailed;

    ManagedMCMC(BooleanSupplier cancellationRequested) {
        this.cancellationRequested = cancellationRequested;
    }

    @Override
    protected void callUserFunction(long sample) {
        checkCancellation();
    }

    void checkCancellation() {
        if (cancellationRequested.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("The managed run was interrupted before completion.");
        }
    }

    /** Also safe before all loggers have initialized, and after native run() already called close(). */
    @Override
    public void close() {
        RuntimeException failure = null;
        for (Logger logger : loggersInput.get()) {
            PrintStream stream = logger.getM_out();
            if (stream == null || !closed.add(logger)) continue;
            try {
                logWriteFailed |= stream.checkError();
                logger.close();
                logWriteFailed |= stream.checkError();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            } finally {
                // A failing Loggable footer must not leave its output stream open.
                if (stream != System.out) stream.close();
            }
        }
        if (failure != null) throw failure;
    }

    void verifyLogs() throws IOException {
        if (logWriteFailed) throw new IOException("BEAST reported an error while writing an analysis log.");
    }
}
