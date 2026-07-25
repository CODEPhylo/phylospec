import org.junit.jupiter.api.Test;

import java.nio.file.Path;

public class BeastXXmlDefaultLoggerTest {

    @Test
    public void defaultPhyloCTMCLoggerIncludesModelDensityAndTreeStatistics()
            throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("defaultPhyloCTMCLogger");

        Path logPath =
                XmlTestSupport.logPath("defaultPhyloCTMCLogger");

        XmlTestSupport.prepare(xmlPath, logPath);

        String source =
                """
                Alignment data = fromNexus(
                    "src/test/java/resources/primate-mtDNA.nex"
                )

                Taxa taxa = taxa(data)

                PositiveReal birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )

                QMatrix q = jc69()

                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q
                ) observed as data

                mcmc {
                    Integer chainLength = 5

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s"
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "defaultPhyloCTMCLogger",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(
                xml,
                "<joint idref=\"posterior\""
        );
        XmlTestSupport.assertXmlContains(
                xml,
                "<prior idref=\"prior\""
        );
        XmlTestSupport.assertXmlContains(
                xml,
                "<likelihood idref=\"likelihood\""
        );
        XmlTestSupport.assertXmlContains(
                xml,
                "<treeHeightStatistic id=\"tree.height\""
        );
        XmlTestSupport.assertXmlContains(
                xml,
                "<treeHeightStatistic idref=\"tree.height\""
        );
        XmlTestSupport.assertXmlContains(
                xml,
                "<treeLengthStatistic id=\"tree.treeLength\""
        );
        XmlTestSupport.assertXmlContains(
                xml,
                "<treeLengthStatistic idref=\"tree.treeLength\""
        );
        XmlTestSupport.assertXmlContains(
                xml,
                "<parameter idref=\"birthRate\""
        );
    }

    @Test
    public void defaultPriorOnlyLoggerDoesNotReferenceMissingJointOrLikelihood()
            throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("defaultPriorOnlyLogger");

        Path logPath =
                XmlTestSupport.logPath("defaultPriorOnlyLogger");

        XmlTestSupport.prepare(xmlPath, logPath);

        String source =
                """
                PositiveReal x ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                mcmc {
                    Integer chainLength = 5

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s"
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "defaultPriorOnlyLogger",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(
                xml,
                "<prior idref=\"prior\""
        );
        XmlTestSupport.assertXmlContains(
                xml,
                "<parameter idref=\"x\""
        );
        XmlTestSupport.assertXmlDoesNotContain(
                xml,
                "<joint idref=\"posterior\""
        );
        XmlTestSupport.assertXmlDoesNotContain(
                xml,
                "<likelihood idref=\"likelihood\""
        );
    }
}
