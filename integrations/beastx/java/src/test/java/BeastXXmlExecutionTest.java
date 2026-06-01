import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.xml.BeastXXmlRunner;
import tiling.xml.BeastXXmlWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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

        String source = """
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

        new BeastXXmlWriter()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");

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

        String source = """
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

        new BeastXXmlWriter()
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

        String source = """
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

        new BeastXXmlWriter()
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

        String source = """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                PositiveReal diversificationRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                PositiveReal turnover ~ LogNormal(
                    logMean=-2.0,
                    logSd=1.0
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

        new BeastXXmlWriter()
                .write(model, xmlPath);

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("<parameter id=\"diversificationRate\""), xml);
        assertTrue(xml.contains("<parameter id=\"turnover\""), xml);
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
}