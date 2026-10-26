package runner;

import java.nio.file.Path;
import org.phylospec.errors.Error;

/** A source diagnostic from checking or applying a PhyloSpec model. */
public record PhyloSpecDiagnostic(Stage stage, Error error, boolean warning, String source, Path sourceFile) {

    public enum Stage {
        LEXING,
        PARSING,
        SIMPLIFICATION,
        TYPE_CHECKING,
        MAPPING,
        MODEL_BUILDING
    }

    /** Reuses the core diagnostic renderer, including its source range, hint and examples. */
    public String format() {
        String location = sourceFile == null ? "" : " (" + sourceFile + ")";
        return stage + location + "\n" + error.toStdOutString(source, warning);
    }
}
