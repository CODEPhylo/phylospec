import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import tiling.BeastXState;
import tiling.runner.BeastXRunResult;
import tiling.runner.XmlRunResult;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlRunnerEntryTest {

    @Test
    void publicExportKeepsExplicitFileLoggerAndFillsMissingKinds(@TempDir Path directory) throws Exception {
        Path script = directory.resolve("scalar.phylospec");
        Files.writeString(script, """
                Real x ~ Normal(mean=0.0, sd=1.0)
                mcmc {
                    Integer defaultLogEvery = 3
                    String outputPrefix = "userPrefix"
                    Logger fileLogger = fileLogger(logEvery=7, file="selected.log", parameters=[x])
                }
                """);
        BeastXState state = PhyloSpecRunner.buildXmlExportModel(script, "exported", warning -> {}, path -> {}).beastState;
        assertEquals(List.of(new BeastXState.ScreenLoggerSpec(3, List.of("prior", "x"))), state.screenLoggerSpecs);
        assertEquals(List.of(new BeastXState.FileLoggerSpec(7, "selected.log", List.of("x"))), state.fileLoggerSpecs);
        assertTrue(state.treeLoggerSpecs.isEmpty());
    }

    @Test
    void publicExportDerivesLogPrefixFromXmlStemWhenOmitted(@TempDir Path directory) throws Exception {
        Path script = directory.resolve("scalar.phylospec");
        Files.writeString(script, "Real x ~ Normal(mean=0.0, sd=1.0)");
        BeastXState state = PhyloSpecRunner.buildXmlExportModel(script, "renamed", warning -> {}, path -> {}).beastState;
        assertEquals("renamed.log", state.fileLoggerSpecs.getFirst().fileName());
        assertTrue(state.treeLoggerSpecs.isEmpty());
    }

    @Test
    public void phyloSpecRunnerWritesAndRunsXmlMCMCThroughSingleEntryPoint() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("runnerEntryPoint");

        Path logPath =
                XmlTestSupport.logPath("runnerEntryPoint");

        XmlTestSupport.prepare(xmlPath, logPath);

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
                """.formatted(XmlTestSupport.unixPath(logPath));

        MCMC mcmc =
                new PhyloSpecRunner(source)
                        .writeAndRunXmlMCMC("runnerEntryPoint", xmlPath);

        assertNotNull(mcmc);
        assertTrue(Files.exists(xmlPath), "Expected XML file to be written.");
        XmlTestSupport.assertNonEmptyFile(logPath, "XML-run parameter log");
    }

    @Test
    public void phyloSpecRunnerReturnsStructuredXmlExecutionResult() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("structuredXmlRun");

        Path logPath =
                XmlTestSupport.logPath("structuredXmlRun");

        XmlTestSupport.prepare(xmlPath, logPath);

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
                """.formatted(XmlTestSupport.unixPath(logPath));

        XmlRunResult result =
                new PhyloSpecRunner(source)
                        .executeXmlRun("structuredXmlRun", xmlPath);

        assertEquals("structuredXmlRun", result.runName());
        assertEquals(xmlPath, result.xmlPath());
        Assertions.assertEquals(XmlTestSupport.XML_OUTPUT_DIRECTORY, result.outputDirectory());
        assertTrue(result.executed());
        assertNotNull(result.model());
        assertNotNull(result.mcmc());
        assertTrue(Files.exists(xmlPath));
        XmlTestSupport.assertNonEmptyFile(logPath, "structured XML-run parameter log");
    }

    @Test
    public void phyloSpecRunnerExecutesXmlRunFromOptions() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("xmlOptionsRun");

        Path logPath =
                XmlTestSupport.logPath("xmlOptionsRun");

        XmlTestSupport.prepare(xmlPath, logPath);

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
                """.formatted(XmlTestSupport.unixPath(logPath));

        XmlRunnerOptions options =
                XmlRunnerOptions.builder("xmlOptionsRun", xmlPath)
                        .execute(true)
                        .build();

        XmlRunResult result =
                new PhyloSpecRunner(source)
                        .executeXmlRun(options);

        assertEquals("xmlOptionsRun", result.runName());
        assertEquals(xmlPath, result.xmlPath());
        assertTrue(result.executed());
        assertTrue(Files.exists(xmlPath));
        XmlTestSupport.assertNonEmptyFile(logPath, "XML options-run parameter log");
    }

    @Test
    public void phyloSpecRunnerExecutesXmlRunFromPhyloSpecFile() throws Exception {
        Path sourcePath =
                runnerFixturePath();

        Path xmlPath =
                XmlTestSupport.xmlPath("fromFileStrictClock");

        XmlTestSupport.prepare(xmlPath);

        XmlRunResult result =
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
                runnerFixturePath();

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

        XmlRunResult xmlRun =
                runner.buildXmlRun(
                        XmlRunnerOptions.builder(
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

    private static Path runnerFixturePath() {
        return Path.of(
                "src",
                "main",
                "java",
                "tiling",
                "runner",
                "strictClockPhyloCTMCWithMCMC2.phylospec"
        );
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
    public void phyloSpecRunnerExecutesComplexXmlRunFromPhyloSpecFile() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "main",
                        "java",
                        "tiling",
                        "runner",
                        "skylineHKYStrictClockXmlRun.phylospec"
                );

        Path outputDirectory =
                Path.of(
                        "target",
                        "beastx-runs",
                        "skylineHKYStrictClockXmlRun"
                );

        Path xmlPath =
                outputDirectory.resolve("skylineHKYStrictClockXmlRun.xml");

        Path logPath =
                outputDirectory.resolve("skylineHKYStrictClockXmlRun.log");

        Path treeLogPath =
                outputDirectory.resolve("skylineHKYStrictClockXmlRun.trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        XmlRunResult result;

        try {
            result =
                    PhyloSpecRunner.executeDefaultXmlRunFromFile(sourcePath);
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping complex PhyloSpec-file XML execution because BEAGLE native library is not available."
            );

            throw exception;
        }

        assertEquals("skylineHKYStrictClockXmlRun", result.runName());
        assertEquals(xmlPath, result.xmlPath());
        assertTrue(result.executed());
        assertNotNull(result.model());
        assertNotNull(result.mcmc());

        assertTrue(Files.exists(xmlPath));
        assertTrue(Files.size(xmlPath) > 0);

        XmlTestSupport.assertNonEmptyFile(logPath, "complex runner-entry parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "complex runner-entry tree log");
    }
}