import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("beagle")
public class BeastXXmlRestrictedGTRTest {

    @Test
    public void preservesSharedGTRRateParameterReferences() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("restrictedGTR");

        Path logPath =
                XmlTestSupport.logPath("restrictedGTR");

        XmlTestSupport.prepare(xmlPath, logPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")

                Taxa taxa = taxa(data)

                PositiveReal birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                PositiveReal rateTransversion ~ LogNormal(
                    logMean=0.0,
                    logSd=1.25
                )

                PositiveReal rateAG ~ LogNormal(
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
                    rateAC=rateTransversion,
                    rateAG=rateAG,
                    rateAT=rateTransversion,
                    rateCG=rateTransversion,
                    rateCT=1.0,
                    rateGT=rateTransversion,
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
                            rateTransversion,
                            rateAG,
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
                        .buildModel("restrictedGTR");

        new StateXmlGenerator()
                .write(model, xmlPath);

        String xml =
                Files.readString(xmlPath);

        String gtrModel =
                xml.substring(
                        xml.indexOf("<gtrModel id=\"alignment_likelihood_substitutionModel\""),
                        xml.indexOf("</gtrModel>") + "</gtrModel>".length()
                );

        assertTrue(gtrModel.contains("<rateAC>"), gtrModel);
        assertTrue(gtrModel.contains("<rateAG>"), gtrModel);
        assertTrue(gtrModel.contains("<rateAT>"), gtrModel);
        assertTrue(gtrModel.contains("<rateCG>"), gtrModel);
        assertTrue(gtrModel.contains("<rateGT>"), gtrModel);
        assertTrue(!gtrModel.contains("<rateCT>"), gtrModel);

        assertEquals(
                4,
                countOccurrences(
                        gtrModel,
                        "<parameter idref=\"rateTransversion\""
                ),
                gtrModel
        );

        assertEquals(
                1,
                countOccurrences(
                        xml,
                        "<parameter id=\"rateTransversion\""
                ),
                xml
        );

        assertEquals(
                1,
                countOccurrences(
                        xml,
                        "<scaleOperator id=\"rateTransversion_scale\""
                ),
                xml
        );

        MCMC mcmc =
                new XmlRunner()
                        .parse(xmlPath);

        assertNotNull(
                mcmc,
                "Expected BEAST X to parse XML containing shared GTR rate references."
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
