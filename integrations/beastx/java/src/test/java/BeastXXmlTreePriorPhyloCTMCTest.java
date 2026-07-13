import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlTreePriorPhyloCTMCTest {

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
        XmlTestSupport.assertXmlContains(xml, "<joint id=\"posterior\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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
        XmlTestSupport.assertXmlContains(xml, "<joint id=\"posterior\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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

        XmlTestSupport.assertXmlContains(xml, "<joint id=\"posterior\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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

        XmlTestSupport.assertXmlContains(xml, "<joint id=\"posterior\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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

        XmlTestSupport.assertXmlContains(xml, "<joint id=\"posterior\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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
                    XmlTestSupport.isMissingBeagleLibrary(exception),
                    "Skipping logistic Coalescent PhyloCTMC XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        XmlTestSupport.assertNonEmptyFile(logPath, "logistic Coalescent PhyloCTMC parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "logistic Coalescent PhyloCTMC tree log");
    }
}
