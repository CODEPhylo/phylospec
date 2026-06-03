import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.xml.BeastXStateXmlGenerator;
import tiling.xml.BeastXXmlRunner;
import tiling.xml.BeastXXmlPlan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlExecutionTest {

    @Test
    public void writesAndRunsPriorOnlyLogNormalMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("priorOnlyLogNormal-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("priorOnlyLogNormal-" + suffix + ".log");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);

        String source =
                """
                PositiveReal x ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[x]
                    )
                }
                """.formatted(logPath.toString().replace("\\", "/"));

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlPriorOnly");

        new BeastXStateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<parameter id=\"x\""), xml);
        assertTrue(xml.contains("<distributionLikelihood id=\"x_prior\""), xml);
        assertTrue(xml.contains("<logNormalDistributionModel id=\"x_prior_distribution\""), xml);
        assertTrue(xml.contains("<scaleOperator id=\"x_scale\""), xml);
        assertTrue(xml.contains("<log id=\"fileLogger"), xml);

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(mcmc, "Expected BEAST X XML to parse into an MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(logPath), "Expected BEAST X XML execution to write a log file.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST X XML execution log to be non-empty.");

        try (Stream<String> lines = Files.lines(logPath)) {
            assertTrue(
                    lines.count() >= 2,
                    "Expected BEAST X XML execution log to contain a header and at least one sample."
            );
        }
    }

    @Test
    public void writesParsesAndRunsPriorOnlyYuleTreeMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("priorOnlyYuleTree-" + suffix + ".xml");

        Path treeLogPath =
                outputDirectory.resolve("priorOnlyYuleTree-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(treeLogPath);

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
                """.formatted(treeLogPath.toString().replace("\\", "/"));

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlYuleTree");

        new BeastXStateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<taxon id=\"Lemur_catta\"/>"), xml);
        assertTrue(xml.contains("<newick id=\"tree_startingTree\""), xml);
        assertTrue(xml.contains("<treeModel id=\"tree\">"), xml);
        assertTrue(xml.contains("<yuleModel id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<speciationLikelihood id=\"tree_prior\">"), xml);
        assertTrue(xml.contains("<logTree"), xml);

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(mcmc, "Expected BEAST X tree XML to parse into an MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(treeLogPath), "Expected BEAST X XML execution to write a tree log.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST X XML execution tree log to be non-empty.");

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin taxa;"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesParsesAndRunsPriorOnlyBirthDeathTreeMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("priorOnlyBirthDeathTree-" + suffix + ".xml");

        Path treeLogPath =
                outputDirectory.resolve("priorOnlyBirthDeathTree-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(treeLogPath);

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
                """.formatted(treeLogPath.toString().replace("\\", "/"));

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlBirthDeathTree");

        new BeastXStateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<taxon id=\"Lemur_catta\"/>"), xml);
        assertTrue(xml.contains("<newick id=\"tree_startingTree\""), xml);
        assertTrue(xml.contains("<treeModel id=\"tree\">"), xml);
        assertTrue(xml.contains("<birthDeathModel id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<birthMinusDeathRate>"), xml);
        assertTrue(xml.contains("<relativeDeathRate>"), xml);
        assertTrue(xml.contains("<sampleProbability>"), xml);
        assertTrue(xml.contains("<speciationLikelihood id=\"tree_prior\">"), xml);
        assertTrue(xml.contains("<logTree"), xml);

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(mcmc, "Expected BEAST X BirthDeath tree XML to parse into an MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(treeLogPath), "Expected BEAST X XML execution to write a tree log.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST X XML execution tree log to be non-empty.");

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin taxa;"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesParsesAndRunsParameterizedBirthDeathTreeMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("parameterizedBirthDeathTree-" + suffix + ".xml");

        Path parameterLogPath =
                outputDirectory.resolve("parameterizedBirthDeathTree-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("parameterizedBirthDeathTree-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(parameterLogPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                PositiveReal diversificationRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Probability turnover ~ Beta(
                    alpha=2.0,
                    beta=5.0
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
                        parameterLogPath.toString().replace("\\", "/"),
                        treeLogPath.toString().replace("\\", "/")
                );

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlParameterizedBirthDeathTree");

        new BeastXStateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<parameter id=\"diversificationRate\""), xml);
        assertTrue(xml.contains("<parameter id=\"turnover\""), xml);
        assertTrue(xml.contains("<logNormalDistributionModel id=\"diversificationRate_prior_distribution\""), xml);
        assertTrue(xml.contains("<betaDistributionModel id=\"turnover_prior_distribution\""), xml);
        assertTrue(xml.contains("<birthDeathModel id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<birthMinusDeathRate>"), xml);
        assertTrue(xml.contains("<parameter idref=\"diversificationRate\"/>"), xml);
        assertTrue(xml.contains("<relativeDeathRate>"), xml);
        assertTrue(xml.contains("<parameter idref=\"turnover\"/>"), xml);
        assertTrue(xml.contains("<sampleProbability>"), xml);
        assertTrue(xml.contains("<speciationLikelihood id=\"tree_prior\">"), xml);
        assertTrue(xml.contains("<logTree"), xml);

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(mcmc, "Expected parameterized BEAST X BirthDeath XML to parse into an MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(parameterLogPath), "Expected BEAST X XML execution to write a parameter log.");
        assertTrue(Files.size(parameterLogPath) > 0, "Expected parameter log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST X XML execution to write a tree log.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected tree log to be non-empty.");

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesParsesAndRunsParameterizedCoalescentTreeMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("parameterizedCoalescentTree-" + suffix + ".xml");

        Path parameterLogPath =
                outputDirectory.resolve("parameterizedCoalescentTree-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("parameterizedCoalescentTree-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(parameterLogPath);
        Files.deleteIfExists(treeLogPath);

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
                        parameterLogPath.toString().replace("\\", "/"),
                        treeLogPath.toString().replace("\\", "/")
                );

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlParameterizedCoalescentTree");

        new BeastXStateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<parameter id=\"populationSize\""), xml);
        assertTrue(xml.contains("<constantSize id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<populationSize>"), xml);
        assertTrue(xml.contains("<parameter idref=\"populationSize\"/>"), xml);
        assertTrue(xml.contains("<coalescentLikelihood id=\"tree_prior\">"), xml);
        assertTrue(xml.contains("<populationTree>"), xml);
        assertTrue(xml.contains("<treeModel idref=\"tree\"/>"), xml);
        assertTrue(xml.contains("<logTree"), xml);

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(mcmc, "Expected parameterized BEAST X Coalescent XML to parse into an MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(parameterLogPath), "Expected BEAST X XML execution to write a parameter log.");
        assertTrue(Files.size(parameterLogPath) > 0, "Expected parameter log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST X XML execution to write a tree log.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected tree log to be non-empty.");

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesParsesAndRunsBirthDeathBenchmarkWithLogNormalTurnoverXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("birthDeathXmlBenchmark-" + suffix + ".xml");

        Path parameterLogPath =
                outputDirectory.resolve("birthDeathXmlBenchmark-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("birthDeathXmlBenchmark-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(parameterLogPath);
        Files.deleteIfExists(treeLogPath);

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
                        .replace("{{PARAMETER_LOG}}", parameterLogPath.toString().replace("\\", "/"))
                        .replace("{{TREE_LOG}}", treeLogPath.toString().replace("\\", "/"));

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlBirthDeathBenchmark");

        new BeastXStateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<parameter id=\"diversificationRate\""), xml);
        assertTrue(xml.contains("<parameter id=\"turnover\""), xml);
        assertTrue(xml.contains("<logNormalDistributionModel id=\"diversificationRate_prior_distribution\""), xml);
        assertTrue(xml.contains("<logNormalDistributionModel id=\"turnover_prior_distribution\""), xml);
        assertTrue(xml.contains("<birthDeathModel id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<parameter idref=\"diversificationRate\"/>"), xml);
        assertTrue(xml.contains("<parameter idref=\"turnover\"/>"), xml);
        assertTrue(xml.contains("<speciationLikelihood id=\"tree_prior\">"), xml);
        assertTrue(xml.contains("<logTree"), xml);

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(mcmc, "Expected benchmark BEAST X XML to parse into an MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(parameterLogPath), "Expected benchmark XML execution to write a parameter log.");
        assertTrue(Files.size(parameterLogPath) > 0, "Expected benchmark parameter log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected benchmark XML execution to write a tree log.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected benchmark tree log to be non-empty.");

        String parameterLog =
                Files.readString(parameterLogPath);

        assertTrue(parameterLog.contains("diversificationRate"), parameterLog);
        assertTrue(parameterLog.contains("turnover"), parameterLog);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesParsesAndRunsRootCalibrationYuleTreeMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("rootCalibrationYuleTree-" + suffix + ".xml");

        Path treeLogPath =
                outputDirectory.resolve("rootCalibrationYuleTree-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(treeLogPath);

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
                """.formatted(treeLogPath.toString().replace("\\", "/"));

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlRootCalibrationYuleTree");

        new BeastXStateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected root-calibrated BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<treeModel id=\"tree\">"), xml);
        assertTrue(xml.contains("<yuleModel id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<speciationLikelihood id=\"tree_prior\">"), xml);
        assertTrue(xml.contains("<tmrcaStatistic id=\"rootAge\""), xml);
        assertTrue(xml.contains("<treeModel idref=\"tree\"/>"), xml);
        assertTrue(xml.contains("<distributionLikelihood id=\"rootCalibration\""), xml);
        assertTrue(xml.contains("<uniformDistributionModel id=\"rootCalibration_distribution\""), xml);
        assertTrue(xml.contains("<lower>"), xml);
        assertTrue(xml.contains("<upper>"), xml);
        assertTrue(xml.contains("<tmrcaStatistic idref=\"rootAge\"/>"), xml);
        assertTrue(xml.contains("<logTree"), xml);

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(mcmc, "Expected root-calibrated BEAST X XML to parse into an MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(treeLogPath), "Expected root-calibrated XML execution to write a tree log.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected root-calibrated tree log to be non-empty.");

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesParsesAndRunsMRCACalibrationYuleTreeMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("mrcaCalibrationYuleTree-" + suffix + ".xml");

        Path treeLogPath =
                outputDirectory.resolve("mrcaCalibrationYuleTree-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(treeLogPath);

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
                """.formatted(treeLogPath.toString().replace("\\", "/"));

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlMRCACalibrationYuleTree");

        new BeastXStateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected MRCA-calibrated BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<treeModel id=\"tree\">"), xml);
        assertTrue(xml.contains("<yuleModel id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<speciationLikelihood id=\"tree_prior\">"), xml);
        assertTrue(xml.contains("<tmrcaStatistic id=\"mrcaAge\""), xml);
        assertTrue(xml.contains("<mrca>"), xml);
        assertTrue(xml.contains("<taxa id=\"mrcaAge_taxa\">"), xml);
        assertTrue(xml.contains("<taxon idref=\"Homo_sapiens\"/>"), xml);
        assertTrue(xml.contains("<taxon idref=\"Pan\"/>"), xml);
        assertTrue(xml.contains("<distributionLikelihood id=\"mrcaCalibration\""), xml);
        assertTrue(xml.contains("<uniformDistributionModel id=\"mrcaCalibration_distribution\""), xml);
        assertTrue(xml.contains("<tmrcaStatistic idref=\"mrcaAge\"/>"), xml);
        assertTrue(xml.contains("<logTree"), xml);

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(mcmc, "Expected MRCA-calibrated BEAST X XML to parse into an MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(treeLogPath), "Expected MRCA-calibrated XML execution to write a tree log.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected MRCA-calibrated tree log to be non-empty.");

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesParsesAndRunsPriorOnlyStrictClockYuleTreeMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("strictClockYuleTree-" + suffix + ".xml");

        Path parameterLogPath =
                outputDirectory.resolve("strictClockYuleTree-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("strictClockYuleTree-" + suffix + ".trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(parameterLogPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )
    
                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=clockRate,
                    tree=tree
                )
    
                mcmc {
                    Integer chainLength = 5
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
                        parameterLogPath.toString().replace("\\", "/"),
                        treeLogPath.toString().replace("\\", "/")
                );

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlStrictClockYuleTree");

        new BeastXStateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected strict-clock BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<parameter id=\"clockRate\""), xml);
        assertTrue(xml.contains("<strictClockBranchRates id=\"tree_strictClockBranchRates\""), xml);
        assertTrue(xml.contains("<rate>"), xml);
        assertTrue(xml.contains("<parameter idref=\"clockRate\"/>"), xml);
        assertTrue(xml.contains("<yuleModel id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<speciationLikelihood id=\"tree_prior\">"), xml);
        assertTrue(xml.contains("<logTree"), xml);

        BeastXXmlRunner runner =
                new BeastXXmlRunner();

        MCMC mcmc =
                runner.parse(xmlPath);

        assertNotNull(mcmc, "Expected strict-clock BEAST X XML to parse into an MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(parameterLogPath), "Expected strict-clock XML execution to write a parameter log.");
        assertTrue(Files.size(parameterLogPath) > 0, "Expected strict-clock parameter log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected strict-clock XML execution to write a tree log.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected strict-clock tree log to be non-empty.");

        String parameterLog =
                Files.readString(parameterLogPath);

        assertTrue(parameterLog.contains("clockRate"), parameterLog);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void rejectsFullPhyloCTMCStrictClockXmlExportWithClearBoundaryMessage() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
    
                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
    
                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )
    
                QMatrix q = jc69()
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q,
                    branchRates~StrictClock(
                        clockRate=clockRate,
                        tree=tree
                    )
                ) observed as data
    
                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234
    
                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="target/beastx-xml-execution/rejected-full-phyloctmc.log",
                        parameters=[clockRate]
                    )
    
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="target/beastx-xml-execution/rejected-full-phyloctmc.trees",
                        trees=[tree]
                    )
                }
                """;

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlRejectedFullPhyloCTMC");

        UnsupportedOperationException error =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> new BeastXStateXmlGenerator().toXml(model)
                );

        assertTrue(
                error.getMessage().contains("PhyloCTMC likelihood XML export is not supported yet"),
                error.getMessage()
        );

        assertTrue(
                error.getMessage().contains("StrictClock branch-rate XML can currently be exported"),
                error.getMessage()
        );

        assertTrue(
                error.getMessage().contains("tree-likelihood XML serialization"),
                error.getMessage()
        );
    }

    @Test
    public void buildsPhyloCTMCAlignmentAndPatternXmlDataLayer() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
    
                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )
    
                QMatrix q = jc69()
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q
                ) observed as data
                """;

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlPhyloCTMCDataLayer");

        BeastXXmlPlan plan =
                new tiling.xml.BeastXXmlPlanBuilder()
                        .buildPhyloCTMCComponentLayer(model);

        assertTrue(
                plan.has(BeastXXmlPlan.Section.ALIGNMENTS),
                "Expected PhyloCTMC XML data layer to contain alignment XML."
        );

        assertTrue(
                plan.has(BeastXXmlPlan.Section.PATTERN_LISTS),
                "Expected PhyloCTMC XML data layer to contain patterns XML."
        );

        assertTrue(
                plan.get(BeastXXmlPlan.Section.ALIGNMENTS).size() == 1,
                "Expected exactly one alignment XML element."
        );

        assertTrue(
                plan.get(BeastXXmlPlan.Section.PATTERN_LISTS).size() == 1,
                "Expected exactly one patterns XML element."
        );
    }

    @Test
    public void buildsPhyloCTMCJC69SubstitutionModelXmlComponentLayer() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
    
                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )
    
                QMatrix q = jc69()
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q
                ) observed as data
                """;

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlPhyloCTMCJC69ComponentLayer");

        BeastXXmlPlan plan =
                new tiling.xml.BeastXXmlPlanBuilder()
                        .buildPhyloCTMCComponentLayer(model);

        assertTrue(
                plan.has(BeastXXmlPlan.Section.ALIGNMENTS),
                "Expected PhyloCTMC XML component layer to contain alignment XML."
        );

        assertTrue(
                plan.has(BeastXXmlPlan.Section.PATTERN_LISTS),
                "Expected PhyloCTMC XML component layer to contain patterns XML."
        );

        assertTrue(
                plan.has(BeastXXmlPlan.Section.SUBSTITUTION_SITE_MODELS),
                "Expected PhyloCTMC XML component layer to contain substitution-model XML."
        );

        String substitutionXml =
                plan.get(BeastXXmlPlan.Section.SUBSTITUTION_SITE_MODELS)
                        .toString();

        assertTrue(
                substitutionXml.contains("frequencyModel"),
                substitutionXml
        );

        assertTrue(
                substitutionXml.contains("hkyModel"),
                substitutionXml
        );
    }

    @Test
    public void buildsPhyloCTMCDefaultGammaSiteRateModelXmlComponentLayer() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
    
                Taxa taxa = taxa(data)
    
                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )
    
                QMatrix q = jc69()
    
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=q
                ) observed as data
                """;

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlPhyloCTMCGammaSiteRateComponentLayer");

        BeastXXmlPlan plan =
                new tiling.xml.BeastXXmlPlanBuilder()
                        .buildPhyloCTMCComponentLayer(model);

        assertTrue(
                plan.has(BeastXXmlPlan.Section.SUBSTITUTION_SITE_MODELS),
                "Expected PhyloCTMC XML component layer to contain substitution/site model XML."
        );

        String substitutionAndSiteModelXml =
                plan.get(BeastXXmlPlan.Section.SUBSTITUTION_SITE_MODELS)
                        .toString();

        assertTrue(
                substitutionAndSiteModelXml.contains("frequencyModel"),
                substitutionAndSiteModelXml
        );

        assertTrue(
                substitutionAndSiteModelXml.contains("hkyModel"),
                substitutionAndSiteModelXml
        );

        assertTrue(
                substitutionAndSiteModelXml.contains("gammaSiteRateModel"),
                substitutionAndSiteModelXml
        );

        assertTrue(
                substitutionAndSiteModelXml.contains("substitutionModel"),
                substitutionAndSiteModelXml
        );

        assertTrue(
                substitutionAndSiteModelXml.contains("alignment_likelihood_substitutionModel"),
                substitutionAndSiteModelXml
        );

        assertTrue(
                substitutionAndSiteModelXml.contains("alignment_likelihood_siteRateModel"),
                substitutionAndSiteModelXml
        );
    }
}