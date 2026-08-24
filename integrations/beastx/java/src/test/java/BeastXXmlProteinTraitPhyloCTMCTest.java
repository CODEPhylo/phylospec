import tiling.BeastXModel;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlProteinTraitPhyloCTMCTest {

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsProteinJTTPhyloCTMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("proteinJTTPhyloCTMC2-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("proteinJTTPhyloCTMC2-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("proteinJTTPhyloCTMC2-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/protein-simple.nex")

                Taxa taxa = taxa(data)

                PositiveReal birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                PositiveReal clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )

                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )

                QMatrix q = jtt()

                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q,
                    branchRates=branchRates
                ) observed as data

                mcmc {
                    Integer chainLength = 10000
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[birthRate, clockRate]
                    )

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        logPath.toString().replace("\\", "/"),
                        treeLogPath.toString().replace("\\", "/")
                );

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlProteinJTTPhyloCTMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected protein PhyloCTMC XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<alignment"), xml);
        assertTrue(xml.contains("dataType=\"amino acid\""), xml);
        assertTrue(xml.contains("<aminoAcidModel"), xml);
        assertTrue(xml.contains("type=\"JTT\""), xml);
        assertTrue(xml.contains("<treeLikelihood"), xml);
        assertTrue(xml.contains("<strictClockBranchRates"), xml);

        new XmlRunner()
                .run(xmlPath);

        assertTrue(Files.exists(logPath), "Expected protein parameter log to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected protein parameter log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected protein tree log to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected protein tree log to be non-empty.");
    }

    @Test
    public void rejectsFullGY94CodonPhyloCTMCXmlExportWithClearBoundaryMessage() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("gy94CodonPhyloCTMC-" + suffix + ".xml");

        String source =
                """
                Alignment fullData = fromNexus("src/test/java/resources/primate-mtDNA.nex")

                Alignment codonData = subset(
                    alignment=fullData,
                    start=1,
                    end=600
                )

                Taxa taxa = taxa(codonData)

                PositiveReal birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )

                PositiveReal clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )

                PositiveReal kappa ~ LogNormal(
                    logMean=1.0,
                    logSd=0.4
                )

                PositiveReal omega ~ LogNormal(
                    logMean=-0.5,
                    logSd=0.5
                )

                Simplex codonFrequencies ~ Dirichlet(
                    concentration=repeat(value=1.0, num=61)
                )

                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )

                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )

                QMatrix q = gy94(
                    kappa=kappa,
                    omega=omega,
                    baseFrequencies=codonFrequencies
                )

                Alignment codonAlignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q,
                    branchRates=branchRates
                ) observed as codonData

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="target/beastx-xml-execution/gy94CodonPhyloCTMC.log",
                        parameters=[birthRate, clockRate, kappa, omega]
                    )

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="target/beastx-xml-execution/gy94CodonPhyloCTMC.trees",
                        trees=[tree]
                    )
                }
                """;

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlGY94CodonPhyloCTMC");

        UnsupportedOperationException exception =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> new StateXmlGenerator()
                                .write(model, xmlPath)
                );

        assertTrue(
                exception.getMessage().contains(
                        "Full GY94 codon PhyloCTMC XML export is not supported yet"
                ),
                exception.getMessage()
        );
    }

}
