import org.junit.jupiter.api.Test;
import tiling.BeastXModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlScalarPriorTest {

    @Test
    public void writesAndRunsPriorOnlyLogNormalMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("priorOnlyLogNormal");

        Path logPath =
                XmlTestSupport.logPath("priorOnlyLogNormal");

        XmlTestSupport.prepare(xmlPath, logPath);

        String source =
                """
                PositiveReal x ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[x]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(logPath));

        BeastXModel model =
                XmlTestSupport.buildModel("xmlPriorOnly", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"x\"");
        XmlTestSupport.assertXmlContains(xml, "<distributionLikelihood id=\"x_prior\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"x_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<scaleOperator id=\"x_scale\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(logPath, "BEAST X XML execution log file");

        try (Stream<String> lines = Files.lines(logPath)) {
            assertTrue(
                    lines.count() >= 2,
                    "Expected BEAST X XML execution log to contain a header and at least one sample."
            );
        }
    }

    @Test
    public void writesParsesAndRunsExponentialPriorMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("exponentialPriorMCMC");

        Path logPath =
                XmlTestSupport.logPath("exponentialPriorMCMC");

        XmlTestSupport.prepare(xmlPath, logPath);

        String source =
                """
                PositiveReal x ~ Exponential(
                    rate=2.0
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[x]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(logPath));

        BeastXModel model =
                XmlTestSupport.buildModel("exponentialPriorMCMC", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<exponentialDistributionModel");
        XmlTestSupport.assertXmlContains(xml, "x_prior_distribution");
        XmlTestSupport.assertXmlContains(xml, "<mean>");
        XmlTestSupport.assertXmlContains(xml, "x_prior_mean");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"x\"/>");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(logPath, "parameter log file");
    }

    @Test
    public void writesParsesAndRunsUniformPriorMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("uniformPriorMCMC");

        Path logPath =
                XmlTestSupport.logPath("uniformPriorMCMC");

        XmlTestSupport.prepare(xmlPath, logPath);

        String source =
                """
                Real x ~ Uniform(
                    lower=-2.0,
                    upper=2.0
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[x]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(logPath));

        BeastXModel model =
                XmlTestSupport.buildModel("uniformPriorMCMC", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<uniformDistributionModel");
        XmlTestSupport.assertXmlContains(xml, "x_prior_distribution");
        XmlTestSupport.assertXmlContains(xml, "<lower>");
        XmlTestSupport.assertXmlContains(xml, "<upper>");
        XmlTestSupport.assertXmlContains(xml, "x_prior_lower");
        XmlTestSupport.assertXmlContains(xml, "x_prior_upper");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"x\"/>");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(logPath, "parameter log file");
    }

    @Test
    public void writesParsesAndRunsNormalPriorMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("normalPriorMCMC");

        Path logPath =
                XmlTestSupport.logPath("normalPriorMCMC");

        XmlTestSupport.prepare(xmlPath, logPath);

        String source =
                """
                Real x ~ Normal(
                    mean=0.0,
                    sd=1.0
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[x]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(logPath));

        BeastXModel model =
                XmlTestSupport.buildModel("normalPriorMCMC", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<normalDistributionModel");
        XmlTestSupport.assertXmlContains(xml, "<randomWalkOperator");
        XmlTestSupport.assertXmlContains(xml, "x_randomWalk");
        XmlTestSupport.assertXmlDoesNotContain(xml, "x_scale");
        XmlTestSupport.assertXmlContains(xml, "x_prior_distribution");
        XmlTestSupport.assertXmlContains(xml, "<mean>");
        XmlTestSupport.assertXmlContains(xml, "<stdev>");
        XmlTestSupport.assertXmlContains(xml, "x_prior_mean");
        XmlTestSupport.assertXmlContains(xml, "x_prior_stdev");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"x\"/>");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(logPath, "parameter log file");
    }
}
