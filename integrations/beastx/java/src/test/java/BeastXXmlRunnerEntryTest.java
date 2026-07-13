import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assumptions;
import tiling.runner.BeastXRunResult;
import tiling.runner.XmlRunResult;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("beagle")
public class BeastXXmlRunnerEntryTest {

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

    @Test
    public void executesCommonYuleJc69StrictClockExampleThroughXmlRunner() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "commonYuleJc69StrictClock.phylospec"
                );

        Path outputDirectory =
                Path.of("target", "comparison");

        Path xmlPath =
                outputDirectory.resolve("common-yule-jc69-strictclock-beastx.xml");

        Path logPath =
                outputDirectory.resolve("common-yule-jc69-strictclock-beastx.log");

        Path treeLogPath =
                outputDirectory.resolve("common-yule-jc69-strictclock-beastx.trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        try {
            PhyloSpecRunner.executeXmlRunFromFile(sourcePath, xmlPath);

            assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");
            assertTrue(Files.size(xmlPath) > 0, "Expected BEAST X XML file to be non-empty.");

            assertTrue(Files.exists(logPath), "Expected BEAST X parameter log to be written.");
            assertTrue(Files.size(logPath) > 0, "Expected BEAST X parameter log to be non-empty.");

            assertTrue(Files.exists(treeLogPath), "Expected BEAST X tree log to be written.");
            assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST X tree log to be non-empty.");

            String xml =
                    Files.readString(xmlPath);

            assertTrue(xml.contains("<beast"), xml);
            assertTrue(xml.contains("<mcmc"), xml);
            assertTrue(xml.contains("<joint idref=\"posterior\""), xml);
            assertTrue(xml.contains("<prior idref=\"prior\""), xml);
            assertTrue(xml.contains("<likelihood idref=\"likelihood\""), xml);
            assertTrue(xml.contains("<treeLikelihood"), xml);
            assertTrue(xml.contains("<logTree"), xml);

            String log =
                    Files.readString(logPath);

            assertTrue(log.contains("posterior") || log.contains("joint"), log);
            assertTrue(log.contains("prior"), log);
            assertTrue(log.contains("likelihood"), log);
            assertTrue(log.contains("clockRate"), log);

            try (Stream<String> lines = Files.lines(logPath)) {
                assertTrue(
                        lines.count() >= 2,
                        "Expected parameter log to contain a header and at least one sample."
                );
            }

            try (Stream<String> lines = Files.lines(treeLogPath)) {
                assertTrue(
                        lines.anyMatch(line -> line.contains("tree STATE_")),
                        "Expected tree log to contain sampled trees."
                );
            }
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
                    "Skipping full PhyloCTMC XML execution because BEAGLE native library is not available."
            );

            throw exception;
        }
    }

    @Test
    @Tag("beagle")
    public void executesTutorialExponentialCoalescentHKYGammaThroughXmlRunner() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "exponentialCoalescentHKYGamma.phylospec"
                );

        Path outputDirectory =
                Path.of("target", "comparison");

        Path xmlPath =
                outputDirectory.resolve("tutorial-exponential-coalescent-hky-gamma-beastx.xml");

        Path logPath =
                outputDirectory.resolve("tutorial-exponential-coalescent-hky-gamma-beastx.log");

        Path treeLogPath =
                outputDirectory.resolve("tutorial-exponential-coalescent-hky-gamma-beastx.trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        XmlRunResult result;

        try {
            result =
                    PhyloSpecRunner.executeXmlRunFromFile(
                            sourcePath,
                            xmlPath
                    );
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
                    "Skipping tutorial exponential-coalescent HKY+Gamma XML run because BEAGLE native library is not available."
            );

            throw exception;
        }

        assertEquals("exponentialCoalescentHKYGamma", result.runName());
        assertEquals(xmlPath, result.xmlPath());
        assertTrue(result.executed());
        assertNotNull(result.model());
        assertNotNull(result.mcmc());

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");
        assertTrue(Files.size(xmlPath) > 0, "Expected BEAST X XML file to be non-empty.");

        XmlTestSupport.assertNonEmptyFile(logPath, "tutorial exponential-coalescent HKY+Gamma BEAST X log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "tutorial exponential-coalescent HKY+Gamma BEAST X tree log");

        String xml =
                Files.readString(xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<exponentialGrowth id=\"tree_prior_model\"");
        XmlTestSupport.assertXmlContains(xml, "<hkyModel id=\"alignment_likelihood_substitutionModel\"");
        XmlTestSupport.assertXmlContains(xml, "<gammaSiteRateModel id=\"alignment_likelihood_siteRateModel\"");
        XmlTestSupport.assertXmlContains(xml, "<strictClockBranchRates id=\"tree_strictClockBranchRates\"");
        XmlTestSupport.assertXmlContains(xml, "<treeLikelihood id=\"alignment_likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<joint id=\"posterior\"");
        XmlTestSupport.assertXmlContains(xml, "<prior id=\"prior\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\"");
        XmlTestSupport.assertXmlContains(xml, "<log id=\"fileLogger");
        XmlTestSupport.assertXmlContains(xml, "<logTree");

        String parameterLog =
                Files.readString(logPath);

        XmlTestSupport.assertXmlContains(parameterLog, "joint");
        XmlTestSupport.assertXmlContains(parameterLog, "prior");
        XmlTestSupport.assertXmlContains(parameterLog, "likelihood");
        XmlTestSupport.assertXmlContains(parameterLog, "populationSize");
        XmlTestSupport.assertXmlContains(parameterLog, "growthRate");
        XmlTestSupport.assertXmlContains(parameterLog, "clockRate");
        XmlTestSupport.assertXmlContains(parameterLog, "kappa");
        XmlTestSupport.assertXmlContains(parameterLog, "baseFrequencies");
        XmlTestSupport.assertXmlContains(parameterLog, "gammaShape");

        String treeLog =
                Files.readString(treeLogPath);

        XmlTestSupport.assertXmlContains(treeLog, "#NEXUS");
        XmlTestSupport.assertXmlContains(treeLog, "Begin trees;");
        XmlTestSupport.assertXmlContains(treeLog, "STATE_");
    }
}
