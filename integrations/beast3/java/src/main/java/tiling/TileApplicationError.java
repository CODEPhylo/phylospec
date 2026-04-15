package tiling;

import org.phylospec.ast.AstNode;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TileApplicationError extends TilingError {

    private final Exception beastException;

    public TileApplicationError(AstNode node, String description, Exception beastException) {
        super(node, description, "Check out the underlying BEAST 2.8 error:\n\n" + getError(beastException));
        this.beastException = beastException;
    }

    public Exception getBeastException() {
        return beastException;
    }

    private static String getError(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

}
