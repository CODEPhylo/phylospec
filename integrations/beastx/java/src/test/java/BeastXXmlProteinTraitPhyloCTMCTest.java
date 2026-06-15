import tiling.BeastXModel;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                    concentration=repeat(1.0, num=61)
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

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsBinaryTraitMkPhyloCTMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("binaryTraitMkPhyloCTMC-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("binaryTraitMkPhyloCTMC-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("binaryTraitMkPhyloCTMC-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                """
                Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")

                Taxa taxa = taxa(molecularData)

                Alignment traitData = discreteTraitsFromTaxa(
                    taxa=taxa,
                    trait=parse(regex=".*_([01])$")
                )

                PositiveReal birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )

                PositiveReal clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )

                Rate traitRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )

                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )

                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )

                QMatrix traitQ = mk(
                    rate=traitRate
                )

                Alignment traitAlignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=traitQ,
                    branchRates=branchRates
                ) observed as traitData

                mcmc {
                    Integer chainLength = 10000
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[birthRate, clockRate, traitRate]
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
                        .buildModel("xmlBinaryTraitMkPhyloCTMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected binary trait Mk PhyloCTMC XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<alignment"), xml);
        assertTrue(xml.contains("dataType=\"binary\""), xml);
        assertTrue(xml.contains("<generalSubstitutionModel"), xml);
        assertTrue(xml.contains("<rates>"), xml);
        assertTrue(xml.contains("traitRate"), xml);
        assertTrue(xml.contains("<treeLikelihood"), xml);
        assertTrue(xml.contains("<strictClockBranchRates"), xml);

        new XmlRunner()
                .run(xmlPath);

        assertTrue(Files.exists(logPath), "Expected binary trait Mk parameter log to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected binary trait Mk parameter log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected binary trait Mk tree log to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected binary trait Mk tree log to be non-empty.");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsJointMolecularTraitMkPhyloCTMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("jointMolecularTraitMkPhyloCTMC-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("jointMolecularTraitMkPhyloCTMC-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("jointMolecularTraitMkPhyloCTMC-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                """
                Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")

                Taxa taxa = taxa(molecularData)

                Alignment traitData = discreteTraitsFromTaxa(
                    taxa=taxa,
                    trait=parse(regex=".*_([01])$")
                )

                Rate birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )

                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )

                PositiveReal kappa ~ LogNormal(
                    logMean=1.0,
                    logSd=0.4
                )

                Simplex baseFrequencies ~ Dirichlet(
                    concentration=repeat(1.0, num=4)
                )

                Rate traitRate ~ LogNormal(
                    logMean=-1.0,
                    logSd=0.5
                )

                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )

                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )

                QMatrix molecularQ = hky(
                    kappa=kappa,
                    baseFrequencies=baseFrequencies
                )

                QMatrix traitQ = mk(
                    rate=traitRate
                )

                Alignment molecularAlignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=molecularQ,
                    branchRates=branchRates
                ) observed as molecularData

                Alignment traitAlignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=traitQ,
                    branchRates=branchRates
                ) observed as traitData

                mcmc {
                    Integer chainLength = 1000
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[birthRate, clockRate, kappa, traitRate]
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
                        .buildModel("xmlJointMolecularTraitMkPhyloCTMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(
                Files.exists(xmlPath),
                "Expected joint molecular + trait Mk PhyloCTMC XML file to be written."
        );

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("molecularAlignment_likelihood"), xml);
        assertTrue(xml.contains("traitAlignment_likelihood"), xml);
        assertTrue(xml.contains("<hkyModel"), xml);
        assertTrue(xml.contains("<generalSubstitutionModel"), xml);
        assertTrue(xml.contains("<strictClockBranchRates"), xml);
        assertTrue(xml.contains("<treeLikelihood id=\"molecularAlignment_likelihood\""), xml);
        assertTrue(xml.contains("<treeLikelihood id=\"traitAlignment_likelihood\""), xml);

        new XmlRunner()
                .run(xmlPath);

        assertTrue(
                Files.exists(logPath),
                "Expected joint molecular + trait Mk parameter log to be written."
        );
        assertTrue(
                Files.size(logPath) > 0,
                "Expected joint molecular + trait Mk parameter log to be non-empty."
        );

        assertTrue(
                Files.exists(treeLogPath),
                "Expected joint molecular + trait Mk tree log to be written."
        );
        assertTrue(
                Files.size(treeLogPath) > 0,
                "Expected joint molecular + trait Mk tree log to be non-empty."
        );
    }

}