import dr.app.beauti.util.XMLWriter;
import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Test;
import tiling.xml.BeastXXmlRunner;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXOfficialXmlWriterTest {

    @Test
    public void officialXmlWriterCanWriteRunnablePriorOnlyMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-official-xml-writer");

        Path xmlPath =
                outputDirectory.resolve("officialXmlWriterPriorOnly-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("officialXmlWriterPriorOnly-" + suffix + ".log");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);

        String xml =
                writePriorOnlyLogNormalXml(
                        "officialXmlWriterPriorOnly",
                        logPath.toString().replace("\\", "/")
                );

        Files.writeString(
                xmlPath,
                xml,
                StandardCharsets.UTF_8
        );

        assertTrue(
                Files.exists(xmlPath),
                "Expected official XMLWriter XML file to be written."
        );

        assertTrue(
                xml.contains("<beast version=\"10.5.0\">"),
                xml
        );

        assertTrue(
                xml.contains("<mcmc id=\"officialXmlWriterPriorOnly_mcmc\""),
                xml
        );

        assertTrue(
                xml.contains("<logNormalDistributionModel"),
                xml
        );

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(
                mcmc,
                "Expected official XMLWriter XML to parse into a BEAST X MCMC object."
        );

        mcmc.run();

        assertTrue(
                Files.exists(logPath),
                "Expected official XMLWriter XML execution to write a parameter log."
        );

        assertTrue(
                Files.size(logPath) > 0,
                "Expected official XMLWriter XML execution log to be non-empty."
        );

        String log =
                Files.readString(logPath);

        assertTrue(
                log.contains("x"),
                "Expected parameter log to contain x column.\n" + log
        );
    }

    private static String writePriorOnlyLogNormalXml(
            String runName,
            String logFileName
    ) {
        StringWriter output =
                new StringWriter();

        XMLWriter writer =
                new XMLWriter(output);

        writer.writeText("<?xml version=\"1.0\" standalone=\"yes\"?>");
        writer.writeBlankLine();

        writer.writeOpenTag("beast version=\"10.5.0\"");

        writer.writeOpenTag("parameter id=\"x\" value=\"0.5\" lower=\"0.0\"");
        writer.writeCloseTag("parameter");

        writer.writeOpenTag("mcmc id=\"" + runName + "_mcmc\" chainLength=\"5\"");

        writer.writeOpenTag("posterior id=\"posterior\"");
        writer.writeOpenTag("prior id=\"prior\"");

        writer.writeOpenTag("distributionLikelihood id=\"x_prior\"");
        writer.writeOpenTag("distribution");

        writer.writeOpenTag("logNormalDistributionModel id=\"x_prior_distribution\"");

        writer.writeOpenTag("mu");
        writer.writeOpenTag("parameter id=\"x_prior_mu\" value=\"0.0\"");
        writer.writeCloseTag("parameter");
        writer.writeCloseTag("mu");

        writer.writeOpenTag("precision");
        writer.writeOpenTag("parameter id=\"x_prior_precision\" value=\"1.0\"");
        writer.writeCloseTag("parameter");
        writer.writeCloseTag("precision");

        writer.writeCloseTag("logNormalDistributionModel");

        writer.writeCloseTag("distribution");

        writer.writeOpenTag("data");
        writer.writeOpenTag("parameter idref=\"x\"");
        writer.writeCloseTag("parameter");
        writer.writeCloseTag("data");

        writer.writeCloseTag("distributionLikelihood");

        writer.writeCloseTag("prior");
        writer.writeCloseTag("posterior");

        writer.writeOpenTag("operators");

        writer.writeOpenTag("scaleOperator id=\"x_scale\" scaleFactor=\"0.75\" weight=\"1.0\"");
        writer.writeOpenTag("parameter idref=\"x\"");
        writer.writeCloseTag("parameter");
        writer.writeCloseTag("scaleOperator");

        writer.writeCloseTag("operators");

        writer.writeOpenTag(
                "log id=\"fileLogger1\" logEvery=\"1\" fileName=\""
                        + escapeXmlAttribute(logFileName)
                        + "\" overwrite=\"true\""
        );

        writer.writeOpenTag("parameter idref=\"x\"");
        writer.writeCloseTag("parameter");

        writer.writeCloseTag("log");

        writer.writeCloseTag("mcmc");

        writer.writeCloseTag("beast");

        writer.flush();

        return output.toString();
    }

    private static String escapeXmlAttribute(String text) {
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}