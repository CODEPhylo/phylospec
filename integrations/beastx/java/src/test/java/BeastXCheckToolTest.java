import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.spi.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;

class BeastXCheckToolTest {
    @TempDir Path directory;

    @Test
    void readsSourceRelativePropertiesButDoesNotApplyTheSelectedTiles() throws Exception {
        Path source = directory.resolve("model.phylospec");
        // Enough metadata to infer the site's bound, yet no matrix for a native alignment reader.
        Files.writeString(directory.resolve("alignment.nex"), "#NEXUS\nBEGIN DATA;\nDIMENSIONS NTAX=2 NCHAR=4;\nEND;");
        Files.writeString(source, """
                Alignment data = fromNexus("alignment.nex")
                Alignment subsetData = subset(alignment=data, start=1, end=5)
                """);
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        ToolProvider tool = ToolProvider.findFirst("phylospec-beastx-check").orElseThrow();

        assertEquals(0, tool.run(new PrintWriter(out), new PrintWriter(err), source.toString()), err::toString);
        assertTrue(out.toString().contains("No MCMC was run."), out::toString);
        assertTrue(err.toString().contains("The inputs for 'subset' might be invalid."), err::toString);
        assertTrue(err.toString().contains("end=5"), err::toString);
    }

    @Test
    void invalidScriptReportsItsSourceLocationAndDoesNotPass() throws Exception {
        Path source = directory.resolve("invalid.phylospec");
        Files.writeString(source, "Integer count = \"no\"");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        assertEquals(1, new BeastXCheckTool().run(new PrintWriter(out), new PrintWriter(err), source.toString()));
        assertTrue(out.toString().isEmpty(), out::toString);
        assertTrue(err.toString().contains(source.toString()), err::toString);
        assertTrue(err.toString().contains("Integer count = \"no\""), err::toString);
    }
}
