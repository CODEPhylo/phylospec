import org.junit.jupiter.api.Test;
import tiling.BeastXModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlTreePriorTest {

    @Test
    public void writesParsesAndRunsPriorOnlyYuleTreeMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("priorOnlyYuleTree");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("priorOnlyYuleTree");

        XmlTestSupport.prepare(xmlPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(treeLogPath));

        BeastXModel model =
                XmlTestSupport.buildModel("xmlYuleTree", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<taxon id=\"Lemur_catta\"/>");
        XmlTestSupport.assertXmlContains(xml, "<newick id=\"tree_startingTree\"");
        XmlTestSupport.assertXmlContains(xml, "<treeModel id=\"tree\">");
        XmlTestSupport.assertXmlContains(xml, "<yuleModel id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(treeLogPath, "BEAST X XML execution tree log");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin taxa;");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    public void writesParsesAndRunsPriorOnlyBirthDeathTreeMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("priorOnlyBirthDeathTree");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("priorOnlyBirthDeathTree");

        XmlTestSupport.prepare(xmlPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ BirthDeath(
                    diversificationRate=0.5,
                    turnover=0.25,
                    samplingProbability=0.9,
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(treeLogPath));

        BeastXModel model =
                XmlTestSupport.buildModel("xmlBirthDeathTree", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<taxon id=\"Lemur_catta\"/>");
        XmlTestSupport.assertXmlContains(xml, "<newick id=\"tree_startingTree\"");
        XmlTestSupport.assertXmlContains(xml, "<treeModel id=\"tree\">");
        XmlTestSupport.assertXmlContains(xml, "<birthDeathModel id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<birthMinusDeathRate>");
        XmlTestSupport.assertXmlContains(xml, "<relativeDeathRate>");
        XmlTestSupport.assertXmlContains(xml, "<sampleProbability>");
        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(treeLogPath, "BEAST X XML execution tree log");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin taxa;");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    public void writesParsesAndRunsParameterizedBirthDeathTreeMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("parameterizedBirthDeathTree");

        Path parameterLogPath =
                XmlTestSupport.logPath("parameterizedBirthDeathTree");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("parameterizedBirthDeathTree");

        XmlTestSupport.prepare(xmlPath, parameterLogPath, treeLogPath);

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

                Tree tree ~ BirthDeath(
                    diversificationRate=diversificationRate,
                    turnover=turnover,
                    samplingProbability=0.9,
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[diversificationRate, turnover]
                    )

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(parameterLogPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        BeastXModel model =
                XmlTestSupport.buildModel("xmlParameterizedBirthDeathTree", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"diversificationRate\"");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"turnover\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"diversificationRate_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"turnover_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<birthDeathModel id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<birthMinusDeathRate>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"diversificationRate\"/>");
        XmlTestSupport.assertXmlContains(xml, "<relativeDeathRate>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"turnover\"/>");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(parameterLogPath, "parameter log file");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "tree log file");

        String parameterLog =
                Files.readString(parameterLogPath);

        XmlTestSupport.assertXmlContains(parameterLog, "diversificationRate");
        XmlTestSupport.assertXmlContains(parameterLog, "turnover");
    }

    @Test
    public void writesParsesAndRunsParameterizedCoalescentTreeMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("parameterizedCoalescentTree");

        Path parameterLogPath =
                XmlTestSupport.logPath("parameterizedCoalescentTree");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("parameterizedCoalescentTree");

        XmlTestSupport.prepare(xmlPath, parameterLogPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                PositiveReal populationSize ~ LogNormal(
                    logMean=1.0,
                    logSd=1.0
                )

                Tree tree ~ Coalescent(
                    populationSize=populationSize,
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[populationSize]
                    )

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(parameterLogPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        BeastXModel model =
                XmlTestSupport.buildModel("xmlParameterizedCoalescentTree", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"populationSize\"");
        XmlTestSupport.assertXmlContains(xml, "<constantSize id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<populationSize>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"populationSize\"/>");
        XmlTestSupport.assertXmlContains(xml, "<coalescentLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<populationTree>");
        XmlTestSupport.assertXmlContains(xml, "<treeModel idref=\"tree\"/>");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(parameterLogPath, "parameter log file");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "tree log file");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    public void writesParsesAndRunsBirthDeathBenchmarkWithLogNormalTurnoverXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("birthDeathXmlBenchmark");

        Path parameterLogPath =
                XmlTestSupport.logPath("birthDeathXmlBenchmark");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("birthDeathXmlBenchmark");

        XmlTestSupport.prepare(xmlPath, parameterLogPath, treeLogPath);

        String source =
                Files.readString(
                                Path.of(
                                        "src",
                                        "test",
                                        "java",
                                        "resources",
                                        "benchmarks",
                                        "birthDeathXmlBenchmark.phylospec"
                                ),
                                StandardCharsets.UTF_8
                        )
                        .replace("{{PARAMETER_LOG}}", XmlTestSupport.unixPath(parameterLogPath))
                        .replace("{{TREE_LOG}}", XmlTestSupport.unixPath(treeLogPath));

        BeastXModel model =
                XmlTestSupport.buildModel("xmlBirthDeathBenchmark", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"diversificationRate\"");
        XmlTestSupport.assertXmlContains(xml, "<parameter id=\"turnover\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"diversificationRate_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<logNormalDistributionModel id=\"turnover_prior_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<birthDeathModel id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"diversificationRate\"/>");
        XmlTestSupport.assertXmlContains(xml, "<parameter idref=\"turnover\"/>");
        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(parameterLogPath, "benchmark parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "benchmark tree log");

        String parameterLog =
                Files.readString(parameterLogPath);

        XmlTestSupport.assertXmlContains(parameterLog, "diversificationRate");
        XmlTestSupport.assertXmlContains(parameterLog, "turnover");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    public void writesParsesAndRunsRootCalibrationYuleTreeMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("rootCalibrationYuleTree");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("rootCalibrationYuleTree");

        XmlTestSupport.prepare(xmlPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                Age root = rootAge(tree=tree) observed between [3.0, 8.0]

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(treeLogPath));

        BeastXModel model =
                XmlTestSupport.buildModel("xmlRootCalibrationYuleTree", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<treeModel id=\"tree\">");
        XmlTestSupport.assertXmlContains(xml, "<yuleModel id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<tmrcaStatistic id=\"rootAge\"");
        XmlTestSupport.assertXmlContains(xml, "<treeModel idref=\"tree\"/>");
        XmlTestSupport.assertXmlContains(xml, "<distributionLikelihood id=\"rootCalibration\"");
        XmlTestSupport.assertXmlContains(xml, "<uniformDistributionModel id=\"rootCalibration_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<lower>");
        XmlTestSupport.assertXmlContains(xml, "<upper>");
        XmlTestSupport.assertXmlContains(xml, "<tmrcaStatistic idref=\"rootAge\"/>");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(treeLogPath, "root-calibrated tree log");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    public void writesParsesAndRunsMRCACalibrationYuleTreeMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("mrcaCalibrationYuleTree");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("mrcaCalibrationYuleTree");

        XmlTestSupport.prepare(xmlPath, treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                Age h = mrca(
                    clade=["Homo_sapiens", "Pan"],
                    tree=tree
                ) observed between [0.5, 3.0]

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(treeLogPath));

        BeastXModel model =
                XmlTestSupport.buildModel("xmlMRCACalibrationYuleTree", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<treeModel id=\"tree\">");
        XmlTestSupport.assertXmlContains(xml, "<yuleModel id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_prior\">");
        XmlTestSupport.assertXmlContains(xml, "<tmrcaStatistic id=\"mrcaAge\"");
        XmlTestSupport.assertXmlContains(xml, "<mrca>");
        XmlTestSupport.assertXmlContains(xml, "<taxa id=\"mrcaAge_taxa\">");
        XmlTestSupport.assertXmlContains(xml, "<taxon idref=\"Homo_sapiens\"/>");
        XmlTestSupport.assertXmlContains(xml, "<taxon idref=\"Pan\"/>");
        XmlTestSupport.assertXmlContains(xml, "<distributionLikelihood id=\"mrcaCalibration\"");
        XmlTestSupport.assertXmlContains(xml, "<uniformDistributionModel id=\"mrcaCalibration_distribution\"");
        XmlTestSupport.assertXmlContains(xml, "<tmrcaStatistic idref=\"mrcaAge\"/>");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(treeLogPath, "MRCA-calibrated tree log");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    public void writesParsesAndRunsPriorOnlyFossilizedBirthDeathTreeMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("priorOnlyFBDTree");

        Path parameterLogPath =
                XmlTestSupport.logPath("priorOnlyFBDTree");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("priorOnlyFBDTree");

        XmlTestSupport.prepare(xmlPath, parameterLogPath, treeLogPath);

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

                Tree tree ~ FossilizedBirthDeath(
                    speciationRate=speciationRate,
                    extinctionRate=extinctionRate,
                    serialSamplingRate=serialSamplingRate,
                    samplingProbability=0.9,
                    rootAge=5.0,
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 100000
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[speciationRate, extinctionRate, serialSamplingRate]
                    )

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        XmlTestSupport.unixPath(parameterLogPath),
                        XmlTestSupport.unixPath(treeLogPath)
                );

        BeastXModel model =
                XmlTestSupport.buildModel("xmlPriorOnlyFBDTree", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

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

        XmlTestSupport.runXml(xmlPath);

        XmlTestSupport.assertNonEmptyFile(parameterLogPath, "FossilizedBirthDeath parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "FossilizedBirthDeath tree log");

        String parameterLog =
                Files.readString(parameterLogPath);

        XmlTestSupport.assertXmlContains(parameterLog, "speciationRate");
        XmlTestSupport.assertXmlContains(parameterLog, "extinctionRate");
        XmlTestSupport.assertXmlContains(parameterLog, "serialSamplingRate");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }

    @Test
    public void writesParsesAndRunsPriorOnlySkylineCoalescentTreeMCMCXml() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("priorOnlySkylineMCMC");

        Path logPath =
                XmlTestSupport.logPath("priorOnlySkylineMCMC");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("priorOnlySkylineMCMC");

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

                Tree tree ~ SkylineCoalescent(
                    populationSizes=populationSizes,
                    changeTimes=[1.0, 2.0],
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 1000
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[populationSizes]
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

        XmlTestSupport.buildWriteAndRunXml(
                "priorOnlySkylineMCMC",
                source,
                xmlPath
        );

        assertTrue(Files.exists(xmlPath));
        assertTrue(Files.exists(logPath));
        assertTrue(Files.exists(treeLogPath));
        assertTrue(Files.size(xmlPath) > 0);
        assertTrue(Files.size(logPath) > 0);
        assertTrue(Files.size(treeLogPath) > 0);
    }
}
