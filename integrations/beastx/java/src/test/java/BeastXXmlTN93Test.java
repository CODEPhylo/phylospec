import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.xml.StateXmlGenerator;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("beagle")
public class BeastXXmlTN93Test {

    @Test
    public void exportsMultipleFixedGTRRatesUsingOneImpliedReference() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("tn93");

        Path logPath =
                XmlTestSupport.logPath("tn93");

        XmlTestSupport.prepare(xmlPath, logPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")

                Taxa taxa = taxa(data)

                PositiveReal birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                PositiveReal rateAG ~ LogNormal(
                    logMean=0.0,
                    logSd=1.25
                )

                PositiveReal rateCT ~ LogNormal(
                    logMean=0.0,
                    logSd=1.25
                )

                Simplex baseFrequencies ~ Dirichlet(
                    concentration=[1.0, 1.0, 1.0, 1.0]
                )

                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )

                QMatrix q = gtr(
                    rateAC=1.0,
                    rateAG=rateAG,
                    rateAT=1.0,
                    rateCG=1.0,
                    rateCT=rateCT,
                    rateGT=1.0,
                    baseFrequencies=baseFrequencies
                )

                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q
                ) observed as data

                mcmc {
                    Integer chainLength = 10
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[
                            rateAG,
                            rateCT,
                            baseFrequencies,
                            birthRate
                        ]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath)
                );

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("tn93");

        new StateXmlGenerator()
                .write(model, xmlPath);

        String xml =
                Files.readString(xmlPath);

        String gtrModel =
                xml.substring(
                        xml.indexOf("<gtrModel id=\"alignment_likelihood_substitutionModel\""),
                        xml.indexOf("</gtrModel>") + "</gtrModel>".length()
                );

        assertFalse(gtrModel.contains("<rateAC>"), gtrModel);
        assertTrue(gtrModel.contains("<rateAG>"), gtrModel);
        assertTrue(gtrModel.contains("<rateAT>"), gtrModel);
        assertTrue(gtrModel.contains("<rateCG>"), gtrModel);
        assertTrue(gtrModel.contains("<rateCT>"), gtrModel);
        assertTrue(gtrModel.contains("<rateGT>"), gtrModel);

        assertEquals(
                1,
                countOccurrences(gtrModel, "<parameter idref=\"rateAG\""),
                gtrModel
        );
        assertEquals(
                1,
                countOccurrences(gtrModel, "<parameter idref=\"rateCT\""),
                gtrModel
        );
        assertEquals(
                3,
                countOccurrences(gtrModel, "value=\"1.0\""),
                gtrModel
        );

        assertEquals(
                1,
                countOccurrences(xml, "<parameter id=\"rateAG\""),
                xml
        );
        assertEquals(
                1,
                countOccurrences(xml, "<parameter id=\"rateCT\""),
                xml
        );
        assertEquals(
                1,
                countOccurrences(xml, "<scaleOperator id=\"rateAG_scale\""),
                xml
        );
        assertEquals(
                1,
                countOccurrences(xml, "<scaleOperator id=\"rateCT_scale\""),
                xml
        );

    }

    private static int countOccurrences(
            String text,
            String fragment
    ) {
        int count =
                0;

        int index =
                0;

        while ((index = text.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }

        return count;
    }
}
