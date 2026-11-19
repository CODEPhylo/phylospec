import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.*;

class BeastXExportToolTest {
    @TempDir Path directory;

    @Test
    void loadCheckKeepsExplicitLogPathsInTheXmlWithoutOpeningThem() throws Exception {
        Path script = directory.resolve("example.phylospec");
        Path target = directory.resolve("analysis & reference.xml");
        Path sentinel = Files.writeString(directory.resolve("existing & prior.log"), "earlier run");
        Path missing = directory.resolve("missing.log");
        String relative = Path.of("").toAbsolutePath().relativize(missing).toString().replace('\\', '/');
        Files.writeString(script, """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
                mcmc {
                    Logger first = fileLogger(file="%s", parameters=[x], logEvery=2)
                    Logger second = fileLogger(file="%s", parameters=[x], logEvery=3)
                }
                """.formatted(sentinel.toString().replace('\\', '/'), relative));

        Result result = export(script, target);
        assertEquals(0, result.code(), result.error());
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(target.toFile());
        Element mcmc = (Element) doc.getElementsByTagName("mcmc").item(0);
        assertEquals("analysis & reference_mcmc", mcmc.getAttribute("id"));
        var loggers = doc.getElementsByTagName("log");
        List<String> files = new ArrayList<>();
        for (int i = 0; i < loggers.getLength(); i++) {
            files.add(((Element) loggers.item(i)).getAttribute("fileName"));
        }
        assertTrue(files.contains(sentinel.toString().replace('\\', '/')));
        assertTrue(files.contains(relative));
        assertEquals("earlier run", Files.readString(sentinel));
        assertFalse(Files.exists(missing));
    }

    @Test
    void protectsSourceAndLogAliases() throws Exception {
        Path script = directory.resolve("example.phylospec");
        Path log = Files.writeString(directory.resolve("existing.log"), "earlier run");
        String source = """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
                mcmc {
                    Logger file = fileLogger(file="existing.log", parameters=[x], logEvery=1)
                }
                """;
        Files.writeString(script, source);
        Path alias = Files.createLink(directory.resolve("alias.xml"), log);

        assertTrue(export(script, script).error().contains("XML output is a model input"));
        assertTrue(export(script, alias).error().contains("XML output is also a model log"));
        assertEquals(source, Files.readString(script));
        assertEquals("earlier run", Files.readString(alias));
        assertEquals("earlier run", Files.readString(log));
    }

    @Test
    void acceptsASeedAndRejectsMalformedOptions() throws Exception {
        Path script = Files.writeString(directory.resolve("example.phylospec"), """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
                """);
        Path target = directory.resolve("analysis.xml");

        assertEquals(0, export(script, target, "--seed", "7").code());
        assertTrue(Files.size(target) > 0);

        Result zero = export(script, target, "--seed", "0");
        assertEquals(1, zero.code(), zero.error());
        assertTrue(zero.error().contains("BEAST X requires a nonzero seed"), zero.error());

        assertEquals(2, export(script, target, "--seed").code());
        assertEquals(2, export(script, target, "--seed", "abc").code());
        assertEquals(2, export(script, target, "--unknown").code());
    }

    @Test
    void failedNativeLoadPreservesThePreviousXml() throws Exception {
        Path previous = Files.writeString(directory.resolve("analysis.xml"), "previous export");
        // This is well-formed XML, but cannot be loaded as a native MCMC.
        assertThrows(Exception.class, () -> BeastXExportTool.publishValidated("<beast/>", previous));
        assertEquals("previous export", Files.readString(previous));
        try (var entries = Files.list(directory)) {
            assertEquals(1L, entries.count(), "Staged XML should be removed on error");
        }
    }

    private record Result(int code, String error) {}

    private static Result export(Path source, Path target, String... options) {
        StringWriter err = new StringWriter();
        ToolProvider tool = ToolProvider.findFirst("phylospec-beastx-export").orElseThrow();
        List<String> args = new ArrayList<>(List.of(source.toString(), target.toString()));
        args.addAll(List.of(options));
        int code = tool.run(new PrintWriter(new StringWriter()), new PrintWriter(err), args.toArray(String[]::new));
        return new Result(code, err.toString());
    }
}
