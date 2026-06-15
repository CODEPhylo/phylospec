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

public class BeastXXmlNucleotidePhyloCTMCTest {

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
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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
                    XmlTestSupport.isMissingBeagleLibrary(exception),
                    "Skipping skyline HKY strict-clock PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "skyline HKY strict-clock parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "skyline HKY strict-clock tree log");
    }
}