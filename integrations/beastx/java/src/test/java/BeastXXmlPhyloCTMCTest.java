import dr.inference.mcmc.MCMC;
import tiling.BeastXModel;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlPhyloCTMCTest {

    @Test
    public void writesParsesAndRunsFullPhyloCTMCXmlWithTreeLikelihood() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("phyloCTMCFullRun2");

        Path logPath =
                XmlTestSupport.logPath("phyloCTMCFullRun2");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("phyloCTMCFullRun2");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
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
    
                QMatrix q = jc69()
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q,
                    branchRates=branchRates
                ) observed as data
    
                mcmc {
                    Integer chainLength = 5
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
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        BeastXModel model =
                XmlTestSupport.buildModel("xmlFullPhyloCTMC", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<alignment");
        XmlTestSupport.assertXmlContains(xml, "<patterns");
        XmlTestSupport.assertXmlContains(xml, "<frequencyModel");
        XmlTestSupport.assertXmlContains(xml, "<hkyModel");
        XmlTestSupport.assertXmlContains(xml, "<siteModel");
        XmlTestSupport.assertXmlContains(xml, "id=\"alignment_likelihood_siteRateModel\"");
        XmlTestSupport.assertXmlContains(xml, "<substitutionModel>");
        XmlTestSupport.assertXmlContains(xml, "<hkyModel idref=\"alignment_likelihood_substitutionModel\"");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood");
        XmlTestSupport.assertXmlContains(xml, "<joint id=\"joint\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            org.junit.jupiter.api.Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping full PhyloCTMC XML execution because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "full PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "full PhyloCTMC tree log");

        try (Stream<String> lines = Files.lines(logPath)) {
            assertTrue(
                    lines.count() >= 2,
                    "Expected full PhyloCTMC parameter log to contain a header and at least one sample."
            );
        }

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    private static boolean isMissingBeagleLibrary(Throwable throwable) {
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

    @Test
    public void writesParsesAndRunsFixedGTRPhyloCTMCXmlWithTreeLikelihood() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("gtrPhyloCTMCFullRun-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("gtrPhyloCTMCFullRun-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("gtrPhyloCTMCFullRun-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
        
                Taxa taxa = taxa(data)
        
                PositiveReal birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
        
                PositiveReal clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
        
                PositiveReal rateAC ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
        
                PositiveReal rateAG ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
        
                PositiveReal rateAT ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
        
                PositiveReal rateCG ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
        
                PositiveReal rateCT ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
        
                Simplex baseFrequencies ~ Dirichlet(
                    concentration=[1.0, 1.0, 1.0, 1.0]
                )
        
                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )
        
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
        
                QMatrix q = gtr(
                    rateAC=rateAC,
                    rateAG=rateAG,
                    rateAT=rateAT,
                    rateCG=rateCG,
                    rateCT=rateCT,
                    rateGT=1.0,
                    baseFrequencies=baseFrequencies
                )
        
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
                        parameters=[
                            birthRate,
                            clockRate,
                            rateAC,
                            rateAG,
                            rateAT,
                            rateCG,
                            rateCT,
                            baseFrequencies
                        ]
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
                        .buildModel("xmlFixedGTRPhyloCTMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected fixed-GTR PhyloCTMC XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<gtrModel id=\"alignment_likelihood_substitutionModel\""), xml);

        assertTrue(xml.contains("<rateAC>"), xml);
        assertTrue(xml.contains("<parameter idref=\"rateAC\""), xml);

        assertTrue(xml.contains("<rateAG>"), xml);
        assertTrue(xml.contains("<parameter idref=\"rateAG\""), xml);

        assertTrue(xml.contains("<rateAT>"), xml);
        assertTrue(xml.contains("<parameter idref=\"rateAT\""), xml);

        assertTrue(xml.contains("<rateCG>"), xml);
        assertTrue(xml.contains("<parameter idref=\"rateCG\""), xml);

        assertTrue(xml.contains("<rateCT>"), xml);
        assertTrue(xml.contains("<parameter idref=\"rateCT\""), xml);

        assertTrue(
                !xml.contains("<rateGT>"),
                "rateGT should be omitted because BEAST X GTR XML requires exactly five named rates and one implied reference rate."
        );

        assertTrue(xml.contains("<dirichletParameterPrior id=\"baseFrequencies_prior\""), xml);
        assertTrue(xml.contains("<deltaExchange id=\"baseFrequencies_deltaExchange\""), xml);

        assertTrue(xml.contains("<siteModel id=\"alignment_likelihood_siteRateModel\""), xml);
        assertTrue(xml.contains("<gtrModel idref=\"alignment_likelihood_substitutionModel\""), xml);
        assertTrue(xml.contains("<treeLikelihood id=\"alignment_likelihood\""), xml);
        assertTrue(xml.contains("<strictClockBranchRates idref=\"tree_strictClockBranchRates\""), xml);

        MCMC mcmc =
                new XmlRunner()
                        .parse(xmlPath);

        assertNotNull(
                mcmc,
                "Expected BEAST X parser to parse fixed-GTR PhyloCTMC XML into an MCMC object."
        );

        mcmc.run();

        assertTrue(Files.exists(logPath), "Expected fixed-GTR parameter log file to be written.");
        assertTrue(Files.exists(treeLogPath), "Expected fixed-GTR tree log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected fixed-GTR parameter log file to be non-empty.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected fixed-GTR tree log file to be non-empty.");

        String parameterLog =
                Files.readString(logPath);

        assertTrue(parameterLog.contains("birthRate"), parameterLog);
        assertTrue(parameterLog.contains("clockRate"), parameterLog);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesParsesAndRunsPartitionedPhyloCTMCXmlWithSharedTreeClockAndSiteModels() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("partitionedSiteGtrHkyPhyloCTMCFullRun-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("partitionedSiteGtrHkyPhyloCTMCFullRun-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("partitionedSiteGtrHkyPhyloCTMCFullRun-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Alignment firstPartition = subset(
                    alignment=data,
                    start=1,
                    end=300
                )
    
                Alignment secondPartition = subset(
                    alignment=data,
                    start=301,
                    end=600
                )
    
                Taxa taxa = taxa(data)
    
                PositiveReal birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )
    
                PositiveReal clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )
    
                PositiveReal firstShape ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )
    
                PositiveReal secondShape ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )
    
                Vector<Rate> firstSiteRates ~ DiscreteGammaInv(
                    shape=firstShape,
                    numCategories=4,
                    invariantProportion=0.05,
                    numSites=numSites(firstPartition)
                )
    
                Vector<Rate> secondSiteRates ~ DiscreteGammaInv(
                    shape=secondShape,
                    numCategories=4,
                    invariantProportion=0.10,
                    numSites=numSites(secondPartition)
                )
    
                PositiveReal firstRateAC ~ LogNormal(logMean=0.0, logSd=0.4)
                PositiveReal firstRateAG ~ LogNormal(logMean=0.0, logSd=0.4)
                PositiveReal firstRateAT ~ LogNormal(logMean=0.0, logSd=0.4)
                PositiveReal firstRateCG ~ LogNormal(logMean=0.0, logSd=0.4)
                PositiveReal firstRateCT ~ LogNormal(logMean=0.0, logSd=0.4)
    
                Simplex firstBaseFrequencies ~ Dirichlet(
                    concentration=[1.0, 1.0, 1.0, 1.0]
                )
    
                PositiveReal secondKappa ~ LogNormal(
                    logMean=1.0,
                    logSd=0.5
                )
    
                Simplex secondBaseFrequencies ~ Dirichlet(
                    concentration=[1.0, 1.0, 1.0, 1.0]
                )
    
                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix firstQ = gtr(
                    rateAC=firstRateAC,
                    rateAG=firstRateAG,
                    rateAT=firstRateAT,
                    rateCG=firstRateCG,
                    rateCT=firstRateCT,
                    rateGT=1.0,
                    baseFrequencies=firstBaseFrequencies
                )
    
                QMatrix secondQ = hky(
                    kappa=secondKappa,
                    baseFrequencies=secondBaseFrequencies
                )
    
                Alignment firstAlignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=firstQ,
                    branchRates=branchRates,
                    siteRates=firstSiteRates
                ) observed as firstPartition
    
                Alignment secondAlignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=secondQ,
                    branchRates=branchRates,
                    siteRates=secondSiteRates
                ) observed as secondPartition
    
                mcmc {
                    Integer chainLength = 10000
                    Integer randomSeed = 1234
    
                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[
                            birthRate,
                            clockRate,
                            firstShape,
                            secondShape,
                            firstRateAC,
                            firstRateAG,
                            firstRateAT,
                            firstRateCG,
                            firstRateCT,
                            firstBaseFrequencies,
                            secondKappa,
                            secondBaseFrequencies
                        ]
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
                        .buildModel("xmlPartitionedSiteGtrHkyPhyloCTMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(
                Files.exists(xmlPath),
                "Expected partitioned site-model PhyloCTMC XML file to be written."
        );

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<treeLikelihood id=\"firstAlignment_likelihood\""), xml);
        assertTrue(xml.contains("<treeLikelihood id=\"secondAlignment_likelihood\""), xml);

        assertTrue(xml.contains("<gtrModel id=\"firstAlignment_likelihood_substitutionModel\""), xml);
        assertTrue(xml.contains("<hkyModel id=\"secondAlignment_likelihood_substitutionModel\""), xml);

        assertTrue(xml.contains("<rateAC>"), xml);
        assertTrue(xml.contains("<parameter idref=\"firstRateAC\""), xml);
        assertTrue(xml.contains("<rateCT>"), xml);
        assertTrue(xml.contains("<parameter idref=\"firstRateCT\""), xml);
        assertTrue(!xml.contains("<rateGT>"), xml);

        assertTrue(xml.contains("<dirichletParameterPrior id=\"firstBaseFrequencies_prior\""), xml);
        assertTrue(xml.contains("<dirichletParameterPrior id=\"secondBaseFrequencies_prior\""), xml);

        assertTrue(xml.contains("<siteModel id=\"firstAlignment_likelihood_siteRateModel\""), xml);
        assertTrue(xml.contains("<siteModel id=\"secondAlignment_likelihood_siteRateModel\""), xml);

        assertTrue(xml.contains("<gammaShape gammaCategories=\"5\""), xml);

        assertTrue(xml.contains("<parameter idref=\"firstShape\""), xml);
        assertTrue(xml.contains("<parameter idref=\"secondShape\""), xml);

        assertTrue(xml.contains("<proportionInvariant>"), xml);
        assertTrue(xml.contains("value=\"0.05\""), xml);
        assertTrue(xml.contains("value=\"0.1\""), xml);

        assertTrue(xml.contains("<strictClockBranchRates id=\"tree_strictClockBranchRates\""), xml);
        assertTrue(xml.contains("<strictClockBranchRates idref=\"tree_strictClockBranchRates\""), xml);

        MCMC mcmc =
                new XmlRunner()
                        .parse(xmlPath);

        assertNotNull(
                mcmc,
                "Expected BEAST X parser to parse partitioned site-model PhyloCTMC XML into an MCMC object."
        );

        mcmc.run();

        assertTrue(Files.exists(logPath), "Expected partitioned parameter log file to be written.");
        assertTrue(Files.exists(treeLogPath), "Expected partitioned tree log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected partitioned parameter log file to be non-empty.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected partitioned tree log file to be non-empty.");

        String parameterLog =
                Files.readString(logPath);

        assertTrue(parameterLog.contains("birthRate"), parameterLog);
        assertTrue(parameterLog.contains("clockRate"), parameterLog);
        assertTrue(parameterLog.contains("firstShape"), parameterLog);
        assertTrue(parameterLog.contains("secondShape"), parameterLog);
        assertTrue(parameterLog.contains("firstRateAC"), parameterLog);
        assertTrue(parameterLog.contains("secondKappa"), parameterLog);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsRelaxedClockPhyloCTMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("relaxedClockPhyloCTMC2-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("relaxedClockPhyloCTMC2-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("relaxedClockPhyloCTMC2-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
        
                Taxa taxa = taxa(data)
        
                PositiveReal birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
        
                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )
        
                Vector<Rate> branchRates ~ RelaxedClock(
                    clockRate=0.5,
                    base=LogNormal(
                        mean=1.0,
                        logSd=0.1
                    ),
                    tree=tree
                )
        
                QMatrix q = jc69()
        
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
                        parameters=[birthRate]
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
                        .buildModel("xmlRelaxedClockPhyloCTMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected relaxed-clock PhyloCTMC XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<discretizedBranchRates"), xml);
        assertTrue(xml.contains("<rateCategories>"), xml);
        assertTrue(xml.contains("<treeLikelihood"), xml);
        assertTrue(xml.contains("<discretizedBranchRates idref="), xml);
        assertFalse(xml.contains("branchRateCategories_randomWalk"), xml);
        assertFalse(xml.contains("<narrowExchange"), xml);
        assertFalse(xml.contains("<wideExchange"), xml);
        assertFalse(xml.contains("<subtreeSlide"), xml);
        assertFalse(xml.contains("<wilsonBalding"), xml);

        new XmlRunner()
                .run(xmlPath);

        assertTrue(Files.exists(logPath), "Expected relaxed-clock parameter log to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected relaxed-clock parameter log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected relaxed-clock tree log to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected relaxed-clock tree log to be non-empty.");
    }

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

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsGammaPriorStrictClockPhyloCTMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("gammaPriorStrictClockPhyloCTMC-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("gammaPriorStrictClockPhyloCTMC-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("gammaPriorStrictClockPhyloCTMC-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                PositiveReal birthRate ~ Gamma(
                    shape=2.0,
                    rate=4.0
                )
    
                PositiveReal clockRate ~ Gamma(
                    shape=2.0,
                    rate=4.0
                )
    
                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix q = jc69()
    
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
                        .buildModel("gammaPriorStrictClockPhyloCTMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        String xml =
                Files.readString(xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected XML file to be written.");
        assertTrue(xml.contains("<gammaDistributionModel"), xml);
        assertTrue(xml.contains("birthRate_prior_distribution"), xml);
        assertTrue(xml.contains("clockRate_prior_distribution"), xml);
        assertTrue(xml.contains("<shape>"), xml);
        assertTrue(xml.contains("<rate>"), xml);

        new XmlRunner()
                .run(xmlPath);

        assertTrue(Files.exists(logPath), "Expected parameter log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected parameter log file to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected tree log file to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected tree log file to be non-empty.");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsRootCalibratedPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("rootCalibratedPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("rootCalibratedPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("rootCalibratedPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                Rate birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )
    
                Age calibratedRoot = rootAge(tree=tree) observed between [3.0, 8.0]
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix q = jc69()
    
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
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlRootCalibratedPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<tmrcaStatistic id=\"rootAge\"");
        XmlTestSupport.assertXmlContains(xml, "<distributionLikelihood id=\"rootCalibration\"");
        XmlTestSupport.assertXmlContains(xml, "<uniformDistributionModel id=\"rootCalibration_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<tmrcaStatistic idref=\"rootAge\"/>");
        XmlTestSupport.assertXmlContains(xml, "<yuleModel id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<siteModel idref=\"alignment_likelihood_siteRateModel\"/>");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates idref=\"tree_strictClockBranchRates\"/>");
        XmlTestSupport.assertXmlContains(xml, "<joint id=\"joint\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping full calibrated PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "root-calibrated PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "root-calibrated PhyloCTMC tree log");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsMRCACalibratedPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("mrcaCalibratedPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("mrcaCalibratedPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("mrcaCalibratedPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                Rate birthRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )
    
                Age humanChimp = mrca(
                        clade=["Homo_sapiens", "Pan"],
                        tree=tree
                    ) observed between [0.5, 2.5]
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix q = jc69()
    
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
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlMRCACalibratedPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<tmrcaStatistic id=\"mrcaAge\"");
        XmlTestSupport.assertXmlContains(xml, "<mrca>");
        XmlTestSupport.assertXmlContains(xml, "<taxa id=\"mrcaAge_taxa\"");
        XmlTestSupport.assertXmlContains(xml, "<taxon idref=\"Homo_sapiens\"/>");
        XmlTestSupport.assertXmlContains(xml, "<taxon idref=\"Pan\"/>");
        XmlTestSupport.assertXmlContains(xml, "<distributionLikelihood id=\"mrcaCalibration\"");
        XmlTestSupport.assertXmlContains(xml, "<uniformDistributionModel id=\"mrcaCalibration_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<tmrcaStatistic idref=\"mrcaAge\"/>");
        XmlTestSupport.assertXmlContains(xml, "<yuleModel id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<siteModel idref=\"alignment_likelihood_siteRateModel\"/>");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates idref=\"tree_strictClockBranchRates\"/>");
        XmlTestSupport.assertXmlContains(xml, "<joint id=\"joint\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping full calibrated PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "MRCA-calibrated PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "MRCA-calibrated PhyloCTMC tree log");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsBirthDeathPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("birthDeathPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("birthDeathPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("birthDeathPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                PositiveReal diversificationRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Rate turnover ~ LogNormal(
                    logMean=-1.0,
                    logSd=0.2
                )
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ BirthDeath(
                    diversificationRate=diversificationRate,
                    turnover=turnover,
                    samplingProbability=0.9,
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix q = jc69()
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q,
                    branchRates=branchRates
                ) observed as data
    
                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234
    
                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[diversificationRate, turnover, clockRate]
                    )
    
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlBirthDeathPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"diversificationRate\"");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"turnover\"");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"clockRate\"");

        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"diversificationRate_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"turnover_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"clockRate_prior_distribution\"");

        XmlTestSupport.assertXmlContains(xml, "<birthDeathModel id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<birthMinusDeathRate>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"diversificationRate\"/>");
        XmlTestSupport.assertXmlContains(xml, "<relativeDeathRate>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"turnover\"/>");
        XmlTestSupport.assertXmlContains(xml, "<sampleProbability>");

        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_prior\"");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates id=\"tree_strictClockBranchRates\"");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<siteModel idref=\"alignment_likelihood_siteRateModel\"/>");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates idref=\"tree_strictClockBranchRates\"/>");

        XmlTestSupport.assertXmlContains(xml, "<joint id=\"joint\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping BirthDeath PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "BirthDeath PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "BirthDeath PhyloCTMC tree log");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsCoalescentPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("coalescentPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("coalescentPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("coalescentPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                PositiveReal populationSize ~ LogNormal(
                    logMean=1.0,
                    logSd=1.0
                )
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ Coalescent(
                    populationSize=populationSize,
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix q = jc69()
    
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
                        parameters=[populationSize, clockRate]
                    )
    
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlCoalescentPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"populationSize\"");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"clockRate\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"populationSize_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"clockRate_prior_distribution\"");

        XmlTestSupport.assertXmlContains(xml, "<constantSize id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<populationSize>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"populationSize\"/>");
        XmlTestSupport.assertXmlContains(xml, "<coalescentLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<populationTree>");
        XmlTestSupport.assertXmlContains(xml, "<treeModel idref=\"tree\"/>");

        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates id=\"tree_strictClockBranchRates\"");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<siteModel idref=\"alignment_likelihood_siteRateModel\"/>");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates idref=\"tree_strictClockBranchRates\"/>");

        XmlTestSupport.assertXmlContains(xml, "<joint id=\"joint\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping Coalescent PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "Coalescent PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "Coalescent PhyloCTMC tree log");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsExponentialCoalescentPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("exponentialCoalescentPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("exponentialCoalescentPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("exponentialCoalescentPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                PositiveReal populationSize ~ LogNormal(
                    logMean=1.0,
                    logSd=1.0
                )
    
                Real growthRate ~ Normal(
                    mean=0.0,
                    sd=1.0
                )
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ Coalescent(
                    populationSize=exponentialPopulationFunction(
                        populationSize=populationSize,
                        growthRate=growthRate
                    ),
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix q = jc69()
    
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
                        parameters=[populationSize, growthRate, clockRate]
                    )
    
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlExponentialCoalescentPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"populationSize\"");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"growthRate\"");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"clockRate\"");

        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"populationSize_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<normalDistributionModel id=\"growthRate_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"clockRate_prior_distribution\"");

        XmlTestSupport.assertXmlContains(xml, "<exponentialGrowth id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<populationSize>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"populationSize\"/>");
        XmlTestSupport.assertXmlContains(xml, "<growthRate>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"growthRate\"/>");

        XmlTestSupport.assertXmlContains(xml, "<coalescentLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<exponentialGrowth idref=\"tree_prior_model\"/>");
        XmlTestSupport.assertXmlContains(xml, "<populationTree>");
        XmlTestSupport.assertXmlContains(xml, "<treeModel idref=\"tree\"/>");

        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates id=\"tree_strictClockBranchRates\"");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<siteModel idref=\"alignment_likelihood_siteRateModel\"/>");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates idref=\"tree_strictClockBranchRates\"/>");

        XmlTestSupport.assertXmlContains(xml, "<joint id=\"joint\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping exponential Coalescent PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "exponential Coalescent PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "exponential Coalescent PhyloCTMC tree log");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsLogisticCoalescentPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("logisticCoalescentPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("logisticCoalescentPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("logisticCoalescentPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                NonNegativeReal inflectionAge = 10.0
    
                PositiveReal carryingCapacity = 10000.0
    
                Real growthRate = 0.1
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ Coalescent(
                    populationSize=logisticPopulationFunction(
                        inflectionAge=inflectionAge,
                        carryingCapacity=carryingCapacity,
                        growthRate=growthRate
                    ),
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix q = jc69()
    
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
                        parameters=[clockRate]
                    )
    
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlLogisticCoalescentPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<logisticGrowth id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<populationSize>");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"tree_prior_populationSize\" value=\"10000.0\"");
        XmlTestSupport.assertXmlContains(xml, "<growthRate>");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"tree_prior_growthRate\" value=\"0.1\"");
        XmlTestSupport.assertXmlContains(xml, "<t50>");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"tree_prior_t50\" value=\"10.0\"");
        XmlTestSupport.assertXmlContains(xml, "<coalescentLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<logisticGrowth idref=\"tree_prior_model\"/>");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping logistic Coalescent PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "logistic Coalescent PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "logistic Coalescent PhyloCTMC tree log");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsFossilizedBirthDeathPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("fossilizedBirthDeathPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("fossilizedBirthDeathPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("fossilizedBirthDeathPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
        
                Taxa taxa = taxa(data)
        
                Rate speciationRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )
        
                Rate extinctionRate ~ LogNormal(
                    logMean=-2.0,
                    logSd=0.3
                )
        
                Rate serialSamplingRate ~ LogNormal(
                    logMean=-2.0,
                    logSd=0.3
                )
        
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
        
                Tree tree ~ FossilizedBirthDeath(
                    speciationRate=speciationRate,
                    extinctionRate=extinctionRate,
                    serialSamplingRate=serialSamplingRate,
                    samplingProbability=0.9,
                    rootAge=5.0,
                    taxa=taxa
                )
        
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
        
                QMatrix q = jc69()
        
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
                        parameters=[speciationRate, extinctionRate, serialSamplingRate, clockRate]
                    )
        
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlFossilizedBirthDeathPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<birthDeathSerialSampling id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "hasFinalSample=\"false\"");
        XmlTestSupport.assertXmlContains(xml, "<birthRate>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"speciationRate\"/>");
        XmlTestSupport.assertXmlContains(xml, "<deathRate>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"extinctionRate\"/>");
        XmlTestSupport.assertXmlContains(xml, "<psi>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"serialSamplingRate\"/>");
        XmlTestSupport.assertXmlContains(xml, "<sampleProbability>");
        XmlTestSupport.assertXmlContains(xml, "<origin>");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"tree_prior_origin\"");
        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<birthDeathSerialSampling idref=\"tree_prior_model\"/>");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates id=\"tree_strictClockBranchRates\"");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<siteModel idref=\"alignment_likelihood_siteRateModel\"/>");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates idref=\"tree_strictClockBranchRates\"/>");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping FossilizedBirthDeath PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "FossilizedBirthDeath PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "FossilizedBirthDeath PhyloCTMC tree log");

        String parameterLog =
                Files.readString(logPath);

        XmlTestSupport.assertXmlContains(parameterLog, "speciationRate");
        XmlTestSupport.assertXmlContains(parameterLog, "extinctionRate");
        XmlTestSupport.assertXmlContains(parameterLog, "serialSamplingRate");
        XmlTestSupport.assertXmlContains(parameterLog, "clockRate");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsDiversificationTurnoverFossilizedBirthDeathPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("diversificationTurnoverFBDPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("diversificationTurnoverFBDPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("diversificationTurnoverFBDPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                Rate diversificationRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )
    
                Rate turnover ~ LogNormal(
                    logMean=-1.0,
                    logSd=0.2
                )
    
                Rate serialSamplingRate ~ LogNormal(
                    logMean=-2.0,
                    logSd=0.3
                )
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ FossilizedBirthDeath(
                    diversificationRate=diversificationRate,
                    turnover=turnover,
                    serialSamplingRate=serialSamplingRate,
                    samplingProbability=0.9,
                    rootAge=5.0,
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix q = jc69()
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q,
                    branchRates=branchRates
                ) observed as data
    
                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234
    
                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[diversificationRate, turnover, serialSamplingRate, clockRate]
                    )
    
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlDiversificationTurnoverFBDPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<birthDeathSerialSampling id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "hasFinalSample=\"false\"");
        XmlTestSupport.assertXmlContains(xml, "<birthRate>");
        XmlTestSupport.assertXmlContains(xml, "<deathRate>");
        XmlTestSupport.assertXmlContains(xml, "<psi>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"serialSamplingRate\"/>");
        XmlTestSupport.assertXmlContains(xml, "<sampleProbability>");
        XmlTestSupport.assertXmlContains(xml, "<origin>");
        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<birthDeathSerialSampling idref=\"tree_prior_model\"/>");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates id=\"tree_strictClockBranchRates\"");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates idref=\"tree_strictClockBranchRates\"/>");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        assertFalse(xml.contains("tree_2"), xml);
        assertFalse(xml.contains("tree_prior_2"), xml);

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping diversification/turnover FBD PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "diversification/turnover FBD PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "diversification/turnover FBD PhyloCTMC tree log");

        String parameterLog =
                Files.readString(logPath);

        XmlTestSupport.assertXmlContains(parameterLog, "diversificationRate");
        XmlTestSupport.assertXmlContains(parameterLog, "turnover");
        XmlTestSupport.assertXmlContains(parameterLog, "serialSamplingRate");
        XmlTestSupport.assertXmlContains(parameterLog, "clockRate");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsDatedTipFossilizedBirthDeathPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("datedTipFBDPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("datedTipFBDPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("datedTipFBDPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus(
                    file="src/test/java/resources/dated-simple.nex",
                    age=parse(regex=".*_(\\d+(?:\\.\\d+)?)$")
                )
    
                Taxa taxa = taxa(data)
    
                Rate speciationRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )
    
                Rate extinctionRate ~ LogNormal(
                    logMean=-2.0,
                    logSd=0.3
                )
    
                Rate serialSamplingRate ~ LogNormal(
                    logMean=-2.0,
                    logSd=0.3
                )
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ FossilizedBirthDeath(
                    speciationRate=speciationRate,
                    extinctionRate=extinctionRate,
                    serialSamplingRate=serialSamplingRate,
                    samplingProbability=0.9,
                    rootAge=5.0,
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix q = jc69()
    
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
                        parameters=[speciationRate, extinctionRate, serialSamplingRate, clockRate]
                    )
    
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlDatedTipFBDPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<taxon id=\"taxon1_0.0\">");
        XmlTestSupport.assertXmlContains(xml, "<date value=\"0.0\" direction=\"backwards\" units=\"years\"/>");
        XmlTestSupport.assertXmlContains(xml, "<taxon id=\"taxon4_3.0\">");
        XmlTestSupport.assertXmlContains(xml, "<date value=\"3.0\" direction=\"backwards\" units=\"years\"/>");
        XmlTestSupport.assertXmlContains(xml, "usingDates=\"true\"");
        XmlTestSupport.assertXmlContains(xml, "<birthDeathSerialSampling id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates id=\"tree_strictClockBranchRates\"");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<treeModel idref=\"tree\"/>");

        assertFalse(xml.contains("tree_2"), xml);
        assertFalse(xml.contains("tree_prior_2"), xml);

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping dated-tip FBD PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "dated-tip FBD PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "dated-tip FBD PhyloCTMC tree log");

        String parameterLog =
                Files.readString(logPath);

        XmlTestSupport.assertXmlContains(parameterLog, "speciationRate");
        XmlTestSupport.assertXmlContains(parameterLog, "extinctionRate");
        XmlTestSupport.assertXmlContains(parameterLog, "serialSamplingRate");
        XmlTestSupport.assertXmlContains(parameterLog, "clockRate");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    @Tag("beagle")
    public void writesParsesAndRunsDatedTipFBDRelaxedClockGTRPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("datedTipFBDRelaxedClockGTRPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("datedTipFBDRelaxedClockGTRPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("datedTipFBDRelaxedClockGTRPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus(
                    file="src/test/java/resources/dated-simple.nex",
                    age=parse(regex=".*_(\\d+(?:\\.\\d+)?)$")
                )
    
                Taxa taxa = taxa(data)
    
                Rate diversificationRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )
    
                Rate turnover ~ LogNormal(
                    logMean=-1.0,
                    logSd=0.25
                )
    
                Rate serialSamplingRate ~ LogNormal(
                    logMean=-2.0,
                    logSd=0.5
                )
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )
    
                PositiveReal rateAC ~ LogNormal(logMean=0.0, logSd=0.4)
                PositiveReal rateAG ~ LogNormal(logMean=0.0, logSd=0.4)
                PositiveReal rateAT ~ LogNormal(logMean=0.0, logSd=0.4)
                PositiveReal rateCG ~ LogNormal(logMean=0.0, logSd=0.4)
                PositiveReal rateCT ~ LogNormal(logMean=0.0, logSd=0.4)
    
                Simplex baseFrequencies ~ Dirichlet(
                    concentration=repeat(1.0, num=4)
                )
    
                Tree tree ~ FossilizedBirthDeath(
                    diversificationRate=diversificationRate,
                    turnover=turnover,
                    serialSamplingRate=serialSamplingRate,
                    samplingProbability=0.8,
                    rootAge=5.0,
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ RelaxedClock(
                    clockRate=clockRate,
                    base=LogNormal(mean=1.0, logSd=0.1),
                    tree=tree
                )
    
                QMatrix qMatrix = gtr(
                    rateAC=rateAC,
                    rateAG=rateAG,
                    rateAT=rateAT,
                    rateCG=rateCG,
                    rateCT=rateCT,
                    rateGT=1.0,
                    baseFrequencies=baseFrequencies
                )
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=qMatrix,
                    branchRates=branchRates
                ) observed as data
    
                mcmc {
                    Integer chainLength = 10000
                    Integer randomSeed = 1234
    
                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[
                            diversificationRate,
                            turnover,
                            serialSamplingRate,
                            clockRate,
                            rateAC,
                            rateAG,
                            rateAT,
                            rateCG,
                            rateCT,
                            baseFrequencies
                        ]
                    )
    
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "xmlDatedTipFBDRelaxedClockGTRPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "usingDates=\"true\"");
        XmlTestSupport.assertXmlContains(xml, "<date value=\"0.0\" direction=\"backwards\" units=\"years\"/>");
        XmlTestSupport.assertXmlContains(xml, "<birthDeathSerialSampling id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<gtrModel id=\"alignment_likelihood_substitutionModel\"");
        XmlTestSupport.assertXmlContains(xml, "<frequencyModel id=\"alignment_likelihood_substitutionModel_frequencies\"");
        XmlTestSupport.assertXmlContains(xml, "<discretizedBranchRates");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        XmlTestSupport.assertXmlContains(xml, "<rateAC>");
        XmlTestSupport.assertXmlContains(xml, "<rateAG>");
        XmlTestSupport.assertXmlContains(xml, "<rateAT>");
        XmlTestSupport.assertXmlContains(xml, "<rateCG>");
        XmlTestSupport.assertXmlContains(xml, "<rateCT>");
        assertFalse(xml.contains("<rateGT>"), xml);
        assertFalse(xml.contains("idref=\"rateGT\""), xml);

        assertFalse(xml.contains("<strictClockBranchRates"), xml);
        assertFalse(xml.contains("tree_2"), xml);
        assertFalse(xml.contains("tree_prior_2"), xml);

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping dated-tip FBD relaxed-clock GTR XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "dated-tip FBD relaxed-clock GTR parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "dated-tip FBD relaxed-clock GTR tree log");

        String parameterLog =
                Files.readString(logPath);

        XmlTestSupport.assertXmlContains(parameterLog, "diversificationRate");
        XmlTestSupport.assertXmlContains(parameterLog, "turnover");
        XmlTestSupport.assertXmlContains(parameterLog, "serialSamplingRate");
        XmlTestSupport.assertXmlContains(parameterLog, "clockRate");
        XmlTestSupport.assertXmlContains(parameterLog, "rateAC");
        XmlTestSupport.assertXmlContains(parameterLog, "rateAG");
        XmlTestSupport.assertXmlContains(parameterLog, "rateAT");
        XmlTestSupport.assertXmlContains(parameterLog, "rateCG");
        XmlTestSupport.assertXmlContains(parameterLog, "rateCT");
        XmlTestSupport.assertXmlContains(parameterLog, "baseFrequencies");

        assertFalse(parameterLog.contains("rateGT"), parameterLog);

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    public void writesParsesAndRunsSkylineHKYStrictClockPhyloCTMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("skylineHKYStrictClockPhyloCTMC");

        Path logPath =
                XmlTestSupport.logPath("skylineHKYStrictClockPhyloCTMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("skylineHKYStrictClockPhyloCTMC");

        XmlTestSupport.prepare(xmlPath, logPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                Vector<PositiveReal> populationSizes ~ IID(
                    base=LogNormal(
                        logMean=5.0,
                        logSd=0.5
                    ),
                    num=3
                )
    
                PositiveReal clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                PositiveReal kappa ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Simplex baseFrequencies ~ Dirichlet(
                    concentration=[1.0, 1.0, 1.0, 1.0]
                )
    
                Tree tree ~ SkylineCoalescent(
                    populationSizes=populationSizes,
                    changeTimes=[1.0, 2.0],
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                QMatrix qMatrix = hky(
                    kappa=kappa,
                    baseFrequencies=baseFrequencies
                )
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=qMatrix,
                    branchRates=branchRates
                ) observed as data
    
                mcmc {
                    Integer chainLength = 10000
                    Integer randomSeed = 1234
    
                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[populationSizes, clockRate, kappa, baseFrequencies]
                    )
    
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(logPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        String xml =
                XmlTestSupport.buildAndWriteXml(
                        "skylineHKYStrictClockPhyloCTMC",
                        source,
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<piecewisePopulation");
        XmlTestSupport.assertXmlContains(xml, "populationSizes_prior");
        XmlTestSupport.assertXmlContains(xml, "<hkyModel");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping skyline HKY strict-clock PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "skyline HKY strict-clock parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "skyline HKY strict-clock tree log");
    }
}