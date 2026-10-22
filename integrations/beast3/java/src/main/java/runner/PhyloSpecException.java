package runner;

import java.util.List;

/** A failed check or tile application, without terminating the calling JVM. */
public final class PhyloSpecException extends RuntimeException {
    private final PhyloSpecDiagnostic diagnostic;
    private final List<PhyloSpecDiagnostic> warnings;

    PhyloSpecException(PhyloSpecDiagnostic diagnostic, Throwable cause, List<PhyloSpecDiagnostic> warnings) {
        super(diagnostic.stage() + ": " + diagnostic.error(), cause);
        this.diagnostic = diagnostic;
        this.warnings = List.copyOf(warnings);
    }

    public PhyloSpecDiagnostic diagnostic() {
        return diagnostic;
    }

    /** Warnings reported before this failure, in encounter order. */
    public List<PhyloSpecDiagnostic> warnings() {
        return warnings;
    }
}
