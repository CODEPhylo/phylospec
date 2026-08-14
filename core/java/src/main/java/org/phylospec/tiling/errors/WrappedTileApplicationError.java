package org.phylospec.tiling.errors;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.phylospec.ast.AstNode;

/**
 * This is an extension of {@code TileApplicationError} which wraps an engine error.
 */
public class WrappedTileApplicationError extends TileApplicationError {

    private final Exception engineException;

    public WrappedTileApplicationError(
            AstNode node, String description, Exception engineException) {
        super(
                node,
                description,
                "Check out the underlying engine error:\n\n" + getError(engineException));
        this.engineException = engineException;
    }

    public Exception getEngineException() {
        return engineException;
    }

    private static String getError(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
