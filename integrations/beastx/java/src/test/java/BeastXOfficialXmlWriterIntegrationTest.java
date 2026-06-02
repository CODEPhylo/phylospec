import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.xml.BeastXOfficialXmlWriter;
import tiling.xml.BeastXXmlRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXOfficialXmlWriterIntegrationTest {

    @Test
    public void writesParsesAndRunsPriorOnlyLogNormalMCMCWithOfficialXmlWriter() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-official-xml-writer");

        Path xmlPath =
                outputDirectory.resolve("officialWriterLogNormal-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("officialWriterLogNormal-" + suffix + ".log");

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
                        .buildModel("officialWriterLogNormal");

        new BeastXOfficialXmlWriter()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected official XMLWriter XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<beast version=\"10.5.0\">"), xml);
        assertTrue(xml.contains("<parameter id=\"x\""), xml);
        assertTrue(xml.contains("<logNormalDistributionModel"), xml);
        assertTrue(xml.contains("<mcmc id=\"officialWriterLogNormal_mcmc\""), xml);

        MCMC mcmc =
                new BeastXXmlRunner()
                        .parse(xmlPath);

        assertNotNull(mcmc, "Expected official XMLWriter XML to parse into a BEAST X MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(logPath), "Expected official XMLWriter XML execution to write a parameter log.");
        assertTrue(Files.size(logPath) > 0, "Expected official XMLWriter XML execution log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("x"), "Expected parameter log to contain x column.\n" + log);
    }

    @Test
    public void writesParsesAndRunsPriorOnlyBetaMCMCWithOfficialXmlWriter() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-official-xml-writer");

        Path xmlPath =
                outputDirectory.resolve("officialWriterBeta-" + suffix + ".xml");

        Path logPath =
                outputDirectory.resolve("officialWriterBeta-" + suffix + ".log");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);

        String source =
                """
                Probability x ~ Beta(
                    alpha=2.0,
                    beta=5.0
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
                        .buildModel("officialWriterBeta");

        new BeastXOfficialXmlWriter()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected official XMLWriter XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<beast version=\"10.5.0\">"), xml);
        assertTrue(xml.contains("<parameter id=\"x\""), xml);
        assertTrue(xml.contains("<betaDistributionModel"), xml);
        assertTrue(xml.contains("<alpha>"), xml);
        assertTrue(xml.contains("<beta>"), xml);
        assertTrue(xml.contains("<mcmc id=\"officialWriterBeta_mcmc\""), xml);

        MCMC mcmc =
                new BeastXXmlRunner()
                        .parse(xmlPath);

        assertNotNull(mcmc, "Expected official XMLWriter XML to parse into a BEAST X MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(logPath), "Expected official XMLWriter XML execution to write a parameter log.");
        assertTrue(Files.size(logPath) > 0, "Expected official XMLWriter XML execution log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("x"), "Expected parameter log to contain x column.\n" + log);
    }

    @Test
    public void writesParsesAndRunsYuleTreeMCMCWithOfficialXmlWriter() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-official-xml-writer");

        Path xmlPath =
                outputDirectory.resolve("officialWriterYule-" + suffix + ".xml");

        Path treeLogPath =
                outputDirectory.resolve("officialWriterYule-" + suffix + ".trees");

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
                        .buildModel("officialWriterYule");

        new BeastXOfficialXmlWriter()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected official XMLWriter XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<taxon id=\"Lemur_catta\""), xml);
        assertTrue(xml.contains("<newick id=\"tree_startingTree\""), xml);
        assertTrue(xml.contains("<treeModel id=\"tree\""), xml);
        assertTrue(xml.contains("<yuleModel id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<speciationLikelihood id=\"tree_prior\""), xml);
        assertTrue(xml.contains("<logTree"), xml);

        MCMC mcmc =
                new BeastXXmlRunner()
                        .parse(xmlPath);

        assertNotNull(mcmc, "Expected official XMLWriter Yule XML to parse into a BEAST X MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(treeLogPath), "Expected official XMLWriter XML execution to write a tree log.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected tree log to be non-empty.");

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesParsesAndRunsBirthDeathTreeMCMCWithOfficialXmlWriter() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-official-xml-writer");

        Path xmlPath =
                outputDirectory.resolve("officialWriterBirthDeath-" + suffix + ".xml");

        Path parameterLogPath =
                outputDirectory.resolve("officialWriterBirthDeath-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("officialWriterBirthDeath-" + suffix + ".trees");

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

                Rate turnover~LogNormal(
                    mean=1.0,
                    logSd=0.5
                )

                Tree tree ~ BirthDeath(
                    diversificationRate=diversificationRate,
                    turnover=turnover,
                    samplingProbability=0.9,
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 10000
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
                        .buildModel("officialWriterBirthDeath");

        new BeastXOfficialXmlWriter()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected official XMLWriter XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<parameter id=\"diversificationRate\""), xml);
        assertTrue(xml.contains("<parameter id=\"turnover\""), xml);
        assertTrue(xml.contains("<birthDeathModel id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<birthMinusDeathRate>"), xml);
        assertTrue(xml.contains("<relativeDeathRate>"), xml);
        assertTrue(xml.contains("<sampleProbability>"), xml);
        assertTrue(xml.contains("<parameter idref=\"diversificationRate\""), xml);
        assertTrue(xml.contains("<parameter idref=\"turnover\""), xml);
        assertTrue(xml.contains("<speciationLikelihood id=\"tree_prior\""), xml);
        assertTrue(xml.contains("<logTree"), xml);

        MCMC mcmc =
                new BeastXXmlRunner()
                        .parse(xmlPath);

        assertNotNull(mcmc, "Expected official XMLWriter BirthDeath XML to parse into a BEAST X MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(parameterLogPath), "Expected parameter log to exist.");
        assertTrue(Files.size(parameterLogPath) > 0, "Expected parameter log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected tree log to exist.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected tree log to be non-empty.");

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
    public void writesParsesAndRunsCoalescentTreeMCMCWithOfficialXmlWriter() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "beastx-official-xml-writer");

        Path xmlPath =
                outputDirectory.resolve("officialWriterCoalescent-" + suffix + ".xml");

        Path parameterLogPath =
                outputDirectory.resolve("officialWriterCoalescent-" + suffix + ".log");

        Path treeLogPath =
                outputDirectory.resolve("officialWriterCoalescent-" + suffix + ".trees");

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
                        .buildModel("officialWriterCoalescent");

        new BeastXOfficialXmlWriter()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected official XMLWriter XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<parameter id=\"populationSize\""), xml);
        assertTrue(xml.contains("<constantSize id=\"tree_prior_model\""), xml);
        assertTrue(xml.contains("<populationSize>"), xml);
        assertTrue(xml.contains("<coalescentLikelihood id=\"tree_prior\""), xml);
        assertTrue(xml.contains("<populationTree>"), xml);
        assertTrue(xml.contains("<treeModel idref=\"tree\""), xml);
        assertTrue(xml.contains("<logTree"), xml);

        MCMC mcmc =
                new BeastXXmlRunner()
                        .parse(xmlPath);

        assertNotNull(mcmc, "Expected official XMLWriter Coalescent XML to parse into a BEAST X MCMC object.");

        mcmc.run();

        assertTrue(Files.exists(parameterLogPath), "Expected parameter log to exist.");
        assertTrue(Files.size(parameterLogPath) > 0, "Expected parameter log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected tree log to exist.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected tree log to be non-empty.");

        String parameterLog =
                Files.readString(parameterLogPath);

        assertTrue(parameterLog.contains("populationSize"), parameterLog);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }
}