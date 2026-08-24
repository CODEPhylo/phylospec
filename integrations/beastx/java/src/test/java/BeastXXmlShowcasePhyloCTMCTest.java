import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlShowcasePhyloCTMCTest {

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
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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
                    concentration=repeat(value=1.0, num=4)
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
        XmlTestSupport.assertXmlContains(xml, "<multiplicativeBranchRates");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates");
        XmlTestSupport.assertXmlContains(xml, "<discretizedBranchRates");
        XmlTestSupport.assertXmlContains(xml, "<multiplicativeBranchRates idref=");
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

        assertFalse(xml.contains("tree_2"), xml);
        assertFalse(xml.contains("tree_prior_2"), xml);

        try {
            XmlTestSupport.runXml(xmlPath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
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

}
