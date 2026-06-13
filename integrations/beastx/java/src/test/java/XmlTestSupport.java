import org.junit.jupiter.api.Assertions;
import tiling.BeastXModel;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

import java.nio.file.Files;
import java.nio.file.Path;

public final class XmlTestSupport {

    public static final Path XML_OUTPUT_DIRECTORY =
            Path.of("target", "beastx-xml-execution");

    private XmlTestSupport() {
    }

    public static long suffix() {
        return System.nanoTime();
    }

    public static Path xmlPath(String prefix) {
        return XML_OUTPUT_DIRECTORY.resolve(prefix + "-" + suffix() + ".xml");
    }

    public static Path logPath(String prefix) {
        return XML_OUTPUT_DIRECTORY.resolve(prefix + "-" + suffix() + ".log");
    }

    public static Path treeLogPath(String prefix) {
        return XML_OUTPUT_DIRECTORY.resolve(prefix + "-" + suffix() + ".trees");
    }

    public static void prepare(Path... paths) throws Exception {
        Files.createDirectories(XML_OUTPUT_DIRECTORY);

        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    public static String unixPath(Path path) {
        return path.toString().replace("\\", "/");
    }

    public static BeastXModel buildModel(String runName, String source) throws Exception {
        return new PhyloSpecRunner(source)
                .buildModel(runName);
    }

    public static String writeXml(BeastXModel model, Path xmlPath) throws Exception {
        new StateXmlGenerator()
                .write(model, xmlPath);

        Assertions.assertTrue(
                Files.exists(xmlPath),
                "Expected BEAST X XML file to be written: " + xmlPath
        );

        Assertions.assertTrue(
                Files.size(xmlPath) > 0,
                "Expected BEAST X XML file to be non-empty: " + xmlPath
        );

        return Files.readString(xmlPath);
    }

    public static String buildAndWriteXml(
            String runName,
            String source,
            Path xmlPath
    ) throws Exception {
        BeastXModel model =
                buildModel(runName, source);

        return writeXml(model, xmlPath);
    }

    public static void runXml(Path xmlPath) throws Exception {
        new XmlRunner()
                .run(xmlPath);
    }

    public static void buildWriteAndRunXml(
            String runName,
            String source,
            Path xmlPath
    ) throws Exception {
        buildAndWriteXml(runName, source, xmlPath);
        runXml(xmlPath);
    }

    public static void assertNonEmptyFile(Path path, String description) throws Exception {
        Assertions.assertTrue(
                Files.exists(path),
                "Expected " + description + " to be written: " + path
        );

        Assertions.assertTrue(
                Files.size(path) > 0,
                "Expected " + description + " to be non-empty: " + path
        );
    }

    public static void assertXmlContains(String xml, String expected) {
        Assertions.assertTrue(
                xml.contains(expected),
                "Expected XML to contain: " + expected + "\n\nActual XML:\n" + xml
        );
    }

    public static void assertXmlDoesNotContain(String xml, String unexpected) {
        Assertions.assertFalse(
                xml.contains(unexpected),
                "Expected XML not to contain: " + unexpected + "\n\nActual XML:\n" + xml
        );
    }

    public static boolean isMissingBeagleLibrary(Throwable throwable) {
        Throwable current =
                throwable;

        while (current != null) {
            String message =
                    current.getMessage();

            if (
                    message != null
                            && message.contains("No acceptable BEAGLE library plugins found")
            ) {
                return true;
            }

            current =
                    current.getCause();
        }

        return false;
    }
}
