import dr.inference.mcmc.MCMC;
import dr.evolution.datatype.AminoAcids;
import dr.evolution.datatype.Codons;
import dr.evolution.alignment.SimpleAlignment;
import dr.evolution.sequence.Sequence;
import dr.evolution.util.Taxon;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.aminoacid.AminoAcidModelType;
import dr.evomodel.substmodel.aminoacid.EmpiricalAminoAcidModel;
import dr.evomodel.substmodel.codon.GY94CodonModel;
import dr.inference.model.Parameter;
import tiling.BeastXModel;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;
import tiling.runner.BeastXXmlRunResult;
import tiling.runner.BeastXXmlRunnerOptions;
import tiling.runner.BeastXRunResult;
import tiling.xml.XmlElement;
import tiling.xml.builders.AlignmentXmlBuilder;
import tiling.xml.builders.SubstitutionModelXmlBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlTest {

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

        new StateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<parameter id=\"x\""), xml);
        assertTrue(xml.contains("<distributionLikelihood id=\"x_prior\""), xml);
        assertTrue(xml.contains("<logNormalDistributionModel id=\"x_prior_distribution\""), xml);
        assertTrue(xml.contains("<scaleOperator id=\"x_scale\""), xml);
        assertTrue(xml.contains("<log id=\"fileLogger"), xml);

        XmlRunner runner =
                new XmlRunner();

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

        new StateXmlGenerator()
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

        XmlRunner runner =
                new XmlRunner();

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

        new StateXmlGenerator()
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

        XmlRunner runner =
                new XmlRunner();

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

        new StateXmlGenerator()
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

        XmlRunner runner =
                new XmlRunner();

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

        new StateXmlGenerator()
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

        XmlRunner runner =
                new XmlRunner();

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

        new StateXmlGenerator()
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

        XmlRunner runner =
                new XmlRunner();

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

        new StateXmlGenerator()
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

        XmlRunner runner =
                new XmlRunner();

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

        new StateXmlGenerator()
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

        XmlRunner runner =
                new XmlRunner();

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
    public void writesParsesAndRunsFullPhyloCTMCXmlWithTreeLikelihood() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("phyloCTMCFullRun2-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("phyloCTMCFullRun2-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("phyloCTMCFullRun2-" + suffix + ".trees");

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
                        logPath.toString().replace("\\", "/"),
                        treeLogPath.toString().replace("\\", "/")
                );

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("xmlFullPhyloCTMCParseOnly");

        new StateXmlGenerator()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected full PhyloCTMC XML file to be written.");

        MCMC mcmc =
                new XmlRunner()
                        .parse(xmlPath);

        assertNotNull(
                mcmc,
                "Expected BEAST X parser to parse full PhyloCTMC XML into an MCMC object."
        );

        mcmc.run();

        assertTrue(Files.exists(logPath), "Expected parameter log file to be written.");
        assertTrue(Files.exists(treeLogPath), "Expected tree log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected parameter log file to be non-empty.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected tree log file to be non-empty.");
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
    public void phyloSpecRunnerWritesAndRunsXmlMCMCThroughSingleEntryPoint() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("runnerEntryPoint-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("runnerEntryPoint-" + suffix + ".log");

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

        MCMC mcmc =
                new PhyloSpecRunner(source)
                        .writeAndRunXmlMCMC("runnerEntryPoint", xmlPath);

        assertNotNull(mcmc);
        assertTrue(Files.exists(xmlPath), "Expected XML file to be written.");
        assertTrue(Files.exists(logPath), "Expected XML-run parameter log to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected XML-run parameter log to be non-empty.");
    }

    @Test
    public void phyloSpecRunnerReturnsStructuredXmlExecutionResult() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("structuredXmlRun-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("structuredXmlRun-" + suffix + ".log");

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

        BeastXXmlRunResult result =
                new PhyloSpecRunner(source)
                        .executeXmlRun("structuredXmlRun", xmlPath);

        assertEquals("structuredXmlRun", result.runName());
        assertEquals(xmlPath, result.xmlPath());
        assertEquals(outputDirectory, result.outputDirectory());
        assertTrue(result.executed());
        assertNotNull(result.model());
        assertNotNull(result.mcmc());
        assertTrue(Files.exists(xmlPath));
        assertTrue(Files.exists(logPath));
        assertTrue(Files.size(logPath) > 0);
    }

    @Test
    public void phyloSpecRunnerExecutesXmlRunFromOptions() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("xmlOptionsRun-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("xmlOptionsRun-" + suffix + ".log");

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

        BeastXXmlRunnerOptions options =
                BeastXXmlRunnerOptions.builder("xmlOptionsRun", xmlPath)
                        .execute(true)
                        .build();

        BeastXXmlRunResult result =
                new PhyloSpecRunner(source)
                        .executeXmlRun(options);

        assertEquals("xmlOptionsRun", result.runName());
        assertEquals(xmlPath, result.xmlPath());
        assertTrue(result.executed());
        assertTrue(Files.exists(xmlPath));
        assertTrue(Files.exists(logPath));
        assertTrue(Files.size(logPath) > 0);
    }

    @Test
    public void phyloSpecRunnerExecutesXmlRunFromPhyloSpecFile() throws Exception {
        long suffix =
                System.nanoTime();

        Path sourcePath =
                Path.of(
                        "src",
                        "main",
                        "java",
                        "tiling",
                        "runner",
                        "strictClockPhyloCTMCWithMCMC2.phylospec"
                );

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("fromFileStrictClock-" + suffix + ".xml");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);

        BeastXXmlRunResult result =
                PhyloSpecRunner.buildXmlRunFromFile(sourcePath, xmlPath);

        assertEquals("strictClockPhyloCTMCWithMCMC2", result.runName());
        assertEquals(xmlPath, result.xmlPath());
        assertFalse(result.executed());
        assertNotNull(result.model());
        assertNotNull(result.mcmc());
        assertTrue(Files.exists(xmlPath));
        assertTrue(Files.size(xmlPath) > 0);
    }

    @Test
    public void samePhyloSpecFileBuildsBothInMemoryAndXmlMCMC() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "main",
                        "java",
                        "tiling",
                        "runner",
                        "strictClockPhyloCTMCWithMCMC2.phylospec"
                );

        Path outputDirectory =
                Path.of("target", "beastx-backend-comparison");

        Path xmlPath =
                outputDirectory.resolve("strictClockPhyloCTMCWithMCMC2.xml");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);

        PhyloSpecRunner runner =
                PhyloSpecRunner.fromFile(sourcePath);

        BeastXRunResult inMemoryRun =
                runner.buildMaterializedRun("strictClockPhyloCTMCWithMCMC2");

        BeastXXmlRunResult xmlRun =
                runner.buildXmlRun(
                        BeastXXmlRunnerOptions.builder(
                                        "strictClockPhyloCTMCWithMCMC2",
                                        xmlPath
                                )
                                .build()
                );

        assertNotNull(inMemoryRun);
        assertNotNull(xmlRun);

        assertTrue(inMemoryRun.hasModel());
        assertTrue(inMemoryRun.hasMCMC());

        assertNotNull(xmlRun.model());
        assertNotNull(xmlRun.mcmc());

        assertTrue(Files.exists(xmlPath));
        assertTrue(Files.size(xmlPath) > 0);
    }

    @Test
    public void buildsWAGEmpiricalAminoAcidSubstitutionModelXmlComponentLayer() {
        assertEmpiricalAminoAcidSubstitutionModelXml(
                AminoAcidModelType.WAG,
                "WAG"
        );
    }

    @Test
    public void buildsLGEmpiricalAminoAcidSubstitutionModelXmlComponentLayer() {
        assertEmpiricalAminoAcidSubstitutionModelXml(
                AminoAcidModelType.LG,
                "LG"
        );
    }

    private void assertEmpiricalAminoAcidSubstitutionModelXml(
            AminoAcidModelType aminoAcidModelType,
            String expectedXmlType
    ) {
        EmpiricalAminoAcidModel model =
                new EmpiricalAminoAcidModel(
                        aminoAcidModelType.getRateMatrixInstance(),
                        new FrequencyModel(
                                AminoAcids.INSTANCE,
                                aminoAcidModelType.getRateMatrixInstance().getEmpiricalFrequencies()
                        )
                );

        List<XmlElement> elements =
                new SubstitutionModelXmlBuilder()
                        .buildSubstitutionModel(
                                model,
                                "protein_likelihood_substitutionModel"
                        );

        String xml =
                elements.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"));

        assertTrue(xml.contains("<frequencyModel"), xml);
        assertTrue(xml.contains("dataType=\"amino acid\""), xml);
        assertTrue(xml.contains("<aminoAcidModel"), xml);
        assertTrue(xml.contains("type=\"" + expectedXmlType + "\""), xml);
        assertTrue(xml.contains("<frequencyModel idref=\"protein_likelihood_substitutionModel_frequencies\"/>"), xml);
    }

    @Test
    public void buildsEmpiricalAminoAcidSubstitutionModelXmlComponentLayer() {
        EmpiricalAminoAcidModel model =
                new EmpiricalAminoAcidModel(
                        AminoAcidModelType.JTT.getRateMatrixInstance(),
                        new FrequencyModel(
                                AminoAcids.INSTANCE,
                                AminoAcidModelType.JTT.getRateMatrixInstance().getEmpiricalFrequencies()
                        )
                );

        List<XmlElement> elements =
                new SubstitutionModelXmlBuilder()
                        .buildSubstitutionModel(
                                model,
                                "protein_likelihood_substitutionModel"
                        );

        String xml =
                elements.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"));

        assertTrue(xml.contains("<frequencyModel"), xml);
        assertTrue(xml.contains("dataType=\"amino acid\""), xml);
        assertTrue(xml.contains("<aminoAcidModel"), xml);
        assertTrue(xml.contains("type=\"JTT\""), xml);
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
    public void buildsGY94CodonSubstitutionModelXmlComponentLayer() {
        Parameter kappa =
                new Parameter.Default(2.0);

        kappa.setId("kappa");

        Parameter omega =
                new Parameter.Default(0.5);

        omega.setId("omega");

        double[] frequencies =
                new double[Codons.UNIVERSAL.getStateCount()];

        Arrays.fill(
                frequencies,
                1.0 / frequencies.length
        );

        FrequencyModel frequencyModel =
                new FrequencyModel(
                        Codons.UNIVERSAL,
                        frequencies
                );

        GY94CodonModel model =
                new GY94CodonModel(
                        Codons.UNIVERSAL,
                        kappa,
                        omega,
                        frequencyModel
                );

        List<XmlElement> elements =
                new SubstitutionModelXmlBuilder()
                        .buildSubstitutionModel(
                                model,
                                "codon_likelihood_substitutionModel"
                        );

        String xml =
                elements.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"));

        assertTrue(xml.contains("<frequencyModel"), xml);
        assertTrue(xml.contains("dataType=\"codon-universal\""), xml);
        assertTrue(xml.contains("<yangCodonModel"), xml);
        assertTrue(xml.contains("<omega>"), xml);
        assertTrue(xml.contains("<parameter idref=\"omega\""), xml);
        assertTrue(xml.contains("<kappa>"), xml);
        assertTrue(xml.contains("<parameter idref=\"kappa\""), xml);
        assertTrue(xml.contains("<frequencyModel idref=\"codon_likelihood_substitutionModel_frequencies\""), xml);
    }

    @Test
    public void buildsCodonAlignmentAndPatternsXmlComponentLayer() {
        SimpleAlignment alignment =
                new SimpleAlignment();

        alignment.setDataType(Codons.UNIVERSAL);

        Sequence firstSequence =
                new Sequence(
                        new Taxon("taxon1"),
                        "ATGAAACCCGGG"
                );

        firstSequence.setDataType(Codons.UNIVERSAL);

        Sequence secondSequence =
                new Sequence(
                        new Taxon("taxon2"),
                        "ATGAAACCCGGA"
                );

        secondSequence.setDataType(Codons.UNIVERSAL);

        alignment.addSequence(firstSequence);
        alignment.addSequence(secondSequence);
        alignment.updateSiteCount();

        List<XmlElement> elements =
                new AlignmentXmlBuilder()
                        .buildAlignmentAndPatterns(
                                alignment,
                                "codon_likelihood_alignment",
                                "codon_likelihood_patterns"
                        );

        String xml =
                elements.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"));

        assertTrue(xml.contains("<alignment"), xml);
        assertTrue(xml.contains("id=\"codon_likelihood_alignment\""), xml);
        assertTrue(xml.contains("dataType=\"codon-universal\""), xml);
        assertTrue(xml.contains("<sequence>"), xml);
        assertTrue(xml.contains("<taxon idref=\"taxon1\""), xml);
        assertTrue(xml.contains("ATGAAACCCGGG"), xml);
        assertTrue(xml.contains("<patterns"), xml);
        assertTrue(xml.contains("id=\"codon_likelihood_patterns\""), xml);
        assertTrue(xml.contains("<alignment idref=\"codon_likelihood_alignment\""), xml);
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
    public void writesParsesAndRunsExponentialPriorMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("exponentialPriorMCMC-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("exponentialPriorMCMC-" + suffix + ".log");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);

        String source =
                """
                PositiveReal x ~ Exponential(
                    rate=2.0
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
                """.formatted(
                        logPath.toString().replace("\\", "/")
                );

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("exponentialPriorMCMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        String xml =
                Files.readString(xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected XML file to be written.");
        assertTrue(xml.contains("<exponentialDistributionModel"), xml);
        assertTrue(xml.contains("x_prior_distribution"), xml);
        assertTrue(xml.contains("<mean>"), xml);
        assertTrue(xml.contains("x_prior_mean"), xml);
        assertTrue(xml.contains("<parameter idref=\"x\"/>"), xml);

        new XmlRunner()
                .run(xmlPath);

        assertTrue(Files.exists(logPath), "Expected parameter log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected parameter log file to be non-empty.");
    }

    @Test
    public void writesParsesAndRunsUniformPriorMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("uniformPriorMCMC-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("uniformPriorMCMC-" + suffix + ".log");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);

        String source =
                """
                Real x ~ Uniform(
                    lower=-2.0,
                    upper=2.0
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
                """.formatted(
                        logPath.toString().replace("\\", "/")
                );

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("uniformPriorMCMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        String xml =
                Files.readString(xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected XML file to be written.");
        assertTrue(xml.contains("<uniformDistributionModel"), xml);
        assertTrue(xml.contains("x_prior_distribution"), xml);
        assertTrue(xml.contains("<lower>"), xml);
        assertTrue(xml.contains("<upper>"), xml);
        assertTrue(xml.contains("x_prior_lower"), xml);
        assertTrue(xml.contains("x_prior_upper"), xml);
        assertTrue(xml.contains("<parameter idref=\"x\"/>"), xml);

        new XmlRunner()
                .run(xmlPath);

        assertTrue(Files.exists(logPath), "Expected parameter log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected parameter log file to be non-empty.");
    }

    @Test
    public void writesParsesAndRunsNormalPriorMCMCXml() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-xml-execution");

        Path xmlPath =
                outputDirectory.resolve("normalPriorMCMC-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("normalPriorMCMC-" + suffix + ".log");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);

        String source =
                """
                Real x ~ Normal(
                    mean=0.0,
                    sd=1.0
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
                """.formatted(
                        logPath.toString().replace("\\", "/")
                );

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("normalPriorMCMC");

        new StateXmlGenerator()
                .write(model, xmlPath);

        String xml =
                Files.readString(xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected XML file to be written.");
        assertTrue(xml.contains("<normalDistributionModel"), xml);
        assertTrue(xml.contains("<randomWalkOperator"), xml);
        assertTrue(xml.contains("x_randomWalk"), xml);
        assertFalse(xml.contains("x_scale"), xml);
        assertTrue(xml.contains("x_prior_distribution"), xml);
        assertTrue(xml.contains("<mean>"), xml);
        assertTrue(xml.contains("<stdev>"), xml);
        assertTrue(xml.contains("x_prior_mean"), xml);
        assertTrue(xml.contains("x_prior_stdev"), xml);
        assertTrue(xml.contains("<parameter idref=\"x\"/>"), xml);

        new XmlRunner()
                .run(xmlPath);

        assertTrue(Files.exists(logPath), "Expected parameter log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected parameter log file to be non-empty.");
    }
}