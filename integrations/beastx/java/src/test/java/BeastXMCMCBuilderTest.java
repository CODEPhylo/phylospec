import dr.evomodel.operators.ExchangeOperator;
import dr.evomodel.operators.RandomWalkNodeHeightOperator;
import dr.evomodel.operators.SubtreeSlideOperator;
import dr.evomodel.operators.UniformNodeHeightOperator;
import dr.evomodel.operators.WilsonBalding;
import dr.inference.loggers.Logger;
import dr.inference.loggers.MCLogger;
import dr.inference.operators.*;
import dr.inference.operators.DeltaExchangeOperator;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.RandomWalkOperator;
import dr.inference.operators.ScaleOperator;
import dr.inference.operators.UpDownOperator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.phylospec.tiling.mcmc.LoggerSpec;
import org.phylospec.tiling.mcmc.McmcSamplerSpec;
import tiling.BeastXModel;
import tiling.mcmc.MCMCBuilder;
import tiling.operators.OperatorBuilder;
import tiling.runner.BeastXRunResult;
import tiling.runner.RunMode;
import tiling.runner.RunnerOptions;
import tiling.summary.BeastXModelSummary;
import tiling.xml.StateXmlGenerator;
import tiling.BeastXState;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXMCMCBuilderTest {

    @Test
    public void readsAutoOperatorSettingsFromBeastXBlock() throws Exception {
        String source = """
                Real clockRate ~ LogNormal(logMean=0.0, logSd=1.0)

                beastX {
                    Real parameterOperatorWeight = 2.0
                    Real parameterScaleFactor = 0.5
                    Real randomWalkWindowSize = 0.25

                    Real treeScaleWeight = 9.0
                    Real treeRootScaleWeight = 6.0
                    Real treeRootScaleFactor = 0.4
                    Real treeSubtreeSlideSize = 7.0
                    Real treeSubtreeSlideWeight = 11.0
                    Real treeNarrowExchangeWeight = 13.0
                    Real treeWideExchangeWeight = 3.0
                    Real treeWilsonBaldingWeight = 4.0

                    Real treeClockUpDownWeight = 8.0
                    Real treeClockUpDownScaleFactor = 0.6
                }
                """;

        BeastXState state =
                new PhyloSpecRunner(source).buildState("test");

        assertEquals(2.0, state.operatorConfig.parameterOperatorWeight);
        assertEquals(0.5, state.operatorConfig.parameterScaleFactor);
        assertEquals(0.25, state.operatorConfig.randomWalkWindowSize);

        assertEquals(9.0, state.operatorConfig.treeScaleWeight);
        assertEquals(6.0, state.operatorConfig.treeRootScaleWeight);
        assertEquals(0.4, state.operatorConfig.treeRootScaleFactor);
        assertEquals(7.0, state.operatorConfig.treeSubtreeSlideSize);
        assertEquals(11.0, state.operatorConfig.treeSubtreeSlideWeight);
        assertEquals(13.0, state.operatorConfig.treeNarrowExchangeWeight);
        assertEquals(3.0, state.operatorConfig.treeWideExchangeWeight);
        assertEquals(4.0, state.operatorConfig.treeWilsonBaldingWeight);

        assertEquals(8.0, state.operatorConfig.treeClockUpDownWeight);
        assertEquals(0.6, state.operatorConfig.treeClockUpDownScaleFactor);
    }

    @Test
    public void operatorSummaryUsesConfiguredParameterScaleFactorAndWeight() throws Exception {
        String source = """
                Real clockRate ~ LogNormal(logMean=0.0, logSd=1.0)

                beastX {
                    Real parameterOperatorWeight = 2.0
                    Real parameterScaleFactor = 0.5
                }
                """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("test");

        BeastXModelSummary summary =
                BeastXModelSummary.from(model);

        assertTrue(
                summary.operatorDetails.stream()
                        .anyMatch(detail -> detail.equals(
                                "ScaleOperator(parameter=clockRate, weight=2.0, scaleFactor=0.5)"
                        )),
                "Expected configured ScaleOperator details.\nActual: " + summary.operatorDetails
        );
    }

    @Test
    public void operatorSummaryUsesConfiguredRandomWalkWindowSize() throws Exception {
        String source = """
                Real growthRate ~ Normal(mean=0.0, sd=1.0)

                beastX {
                    Real parameterOperatorWeight = 3.0
                    Real randomWalkWindowSize = 0.2
                }
                """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("test");

        BeastXModelSummary summary =
                BeastXModelSummary.from(model);

        assertTrue(
                summary.operatorDetails.stream()
                        .anyMatch(detail -> detail.equals(
                                "RandomWalkOperator(parameter=growthRate, weight=3.0, windowSize=0.2, boundary=reflecting)"
                        )),
                "Expected configured RandomWalkOperator details.\nActual: " + summary.operatorDetails
        );
    }

    @Test
    public void rejectsInvalidScaleFactor() throws Exception {
        String source = """
                Real clockRate ~ LogNormal(logMean=0.0, logSd=1.0)

                beastX {
                    Real parameterScaleFactor = 1.5
                }
                """;

        try {
            new PhyloSpecRunner(source).buildState("test");
        } catch (PhyloSpecRunnerException exception) {
            assertTrue(
                    exception.getMessage().contains("MCMC operator scale factor must be between 0 and 1"),
                    exception.getMessage()
            );
            return;
        }

        throw new AssertionError("Expected invalid scale factor to fail.");
    }

    @Test
    public void rejectsNegativeOperatorWeight() throws Exception {
        String source = """
                Real clockRate ~ LogNormal(logMean=0.0, logSd=1.0)

                beastX {
                    Real treeScaleWeight = -1.0
                }
                """;

        try {
            new PhyloSpecRunner(source).buildState("test");
        } catch (PhyloSpecRunnerException exception) {
            assertTrue(
                    exception.getMessage().contains("MCMC operator weight must not be negative"),
                    exception.getMessage()
            );
            return;
        }

        throw new AssertionError("Expected negative operator weight to fail.");
    }

    @Test
    public void rejectsNonPositiveRandomWalkWindowSize() throws Exception {
        String source = """
                Real growthRate ~ Normal(mean=0.0, sd=1.0)

                beastX {
                    Real randomWalkWindowSize = 0.0
                }
                """;

        try {
            new PhyloSpecRunner(source).buildState("test");
        } catch (PhyloSpecRunnerException exception) {
            assertTrue(
                    exception.getMessage().contains("MCMC operator setting must be positive"),
                    exception.getMessage()
            );
            return;
        }

        throw new AssertionError("Expected non-positive random walk window size to fail.");
    }

    @Test
    public void readsChainLengthFromMCMCBlock() throws Exception {
        String source = """
                mcmc {
                    Sampler sampler = mcmc(chainLength=1000)
                }
                """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        assertEquals(1000, state.getSamplerSpec().chainLength());
    }

    @Test
    public void readsScreenLoggerConfigFromMCMCBlock() throws Exception {
        String source = """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)

                mcmc {
                    Logger screenLogger = screenLogger(logEvery=500)
                }
                """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        assertEquals(1, state.screenLoggerSpecs.size());
        assertEquals(500, state.screenLoggerSpecs.getFirst().logEvery());
    }

    @Test
    public void readsScreenLoggerParameterListFromMCMCBlock() throws Exception {
        String source = """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
                PositiveReal y ~ LogNormal(logMean=0.0, logSd=1.0)

                mcmc {
                    Logger screenLogger = screenLogger(
                        logEvery=500,
                        parameters=[x]
                    )
                }
                """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        assertEquals(1, state.screenLoggerSpecs.size());
        assertEquals(500, state.screenLoggerSpecs.getFirst().logEvery());
        assertEquals(List.of("x"), state.screenLoggerSpecs.getFirst().parameters());
    }

    @Test
    public void buildsScreenLoggerForSelectedParametersOnly() throws Exception {
        String source = """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
                PositiveReal y ~ LogNormal(logMean=0.0, logSd=1.0)

                mcmc {
                    Logger screenLogger = screenLogger(
                        logEvery=500,
                        parameters=[x]
                    )
                }
                """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        List<Logger> loggers =
                new MCMCBuilder().buildLoggers(state);

        assertEquals(1, loggers.size());

        MCLogger logger =
                assertInstanceOf(MCLogger.class, loggers.getFirst());

        assertEquals(500, logger.getLogEvery());
        assertEquals(1, logger.getColumnCount());
        assertEquals("x", logger.getColumnLabel(0));
    }

    @Test
    public void buildsScreenLoggerForAllStateNodesWhenParametersAreOmitted() throws Exception {
        String source = """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
                PositiveReal y ~ LogNormal(logMean=0.0, logSd=1.0)

                mcmc {
                    Logger screenLogger = screenLogger(logEvery=500)
                }
                """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        List<Logger> loggers =
                new MCMCBuilder().buildLoggers(state);

        assertEquals(1, loggers.size());

        MCLogger logger =
                assertInstanceOf(MCLogger.class, loggers.getFirst());

        assertEquals(500, logger.getLogEvery());
        assertEquals(2, logger.getColumnCount());
    }

    @Test
    public void readsFileLoggerConfigFromMCMCBlock() throws Exception {
        String source = """
            PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)

            mcmc {
                Logger fileLogger = fileLogger(
                    logEvery=1000,
                    file="output.log",
                    parameters=[x]
                )
            }
            """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        assertEquals(1, state.fileLoggerSpecs.size());
        assertEquals(1000, state.fileLoggerSpecs.getFirst().logEvery());
        assertEquals("output.log", state.fileLoggerSpecs.getFirst().fileName());
        assertEquals(List.of("x"), state.fileLoggerSpecs.getFirst().parameters());
    }

    @Test
    public void buildsFileLoggerForSelectedParametersOnly() throws Exception {
        String source = """
            PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
            PositiveReal y ~ LogNormal(logMean=0.0, logSd=1.0)

            mcmc {
                Logger fileLogger = fileLogger(
                    logEvery=1000,
                    file="target/test-fileLogger-selected.log",
                    parameters=[x]
                )
            }
            """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        List<Logger> loggers =
                new MCMCBuilder().buildLoggers(state);

        assertEquals(1, loggers.size());

        MCLogger logger =
                assertInstanceOf(MCLogger.class, loggers.getFirst());

        assertEquals(1000, logger.getLogEvery());
        assertEquals(1, logger.getColumnCount());
        assertEquals("x", logger.getColumnLabel(0));
    }

    @Test
    public void readsTreeLoggerConfigFromMCMCBlock() throws Exception {
        String source = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Taxa taxa = taxa(data)

            Tree tree ~ Yule(
                birthRate=1.0,
                taxa=taxa
            )

            mcmc {
                Logger treeLogger = treeLogger(
                    logEvery=1000,
                    file="target/test-trees.log",
                    tree=tree
                )
            }
            """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        assertEquals(1, state.treeLoggerSpecs.size());
        assertEquals(1000, state.treeLoggerSpecs.getFirst().logEvery());
        assertEquals("target/test-trees.log", state.treeLoggerSpecs.getFirst().fileName());
        assertEquals("tree", state.treeLoggerSpecs.getFirst().tree());
    }

    @Test
    public void buildsTreeLoggerForSelectedTree() throws Exception {
        String source = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Taxa taxa = taxa(data)

            Tree tree ~ Yule(
                birthRate=1.0,
                taxa=taxa
            )

            mcmc {
                Logger treeLogger = treeLogger(
                    logEvery=1000,
                    file="target/test-trees.log",
                    tree=tree
                )
            }
            """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        List<Logger> loggers =
                new MCMCBuilder().buildLoggers(state);

        assertEquals(1, loggers.size());
        assertInstanceOf(dr.evomodel.tree.TreeLogger.class, loggers.getFirst());
    }

    @Test
    public void buildsFileLoggerForSelectedCalculationNode() throws Exception {
        String source = """
        PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
        PositiveReal y ~ LogNormal(logMean=0.0, logSd=1.0)
        Real z = x + y

        mcmc {
            Logger fileLogger = fileLogger(
                logEvery=1000,
                file="target/test-fileLogger-rpn.log",
                parameters=[z]
            )
        }
        """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        List<Logger> loggers =
                new MCMCBuilder().buildLoggers(state);

        assertEquals(1, loggers.size());

        MCLogger logger =
                assertInstanceOf(MCLogger.class, loggers.getFirst());

        assertEquals(1000, logger.getLogEvery());
        assertEquals(1, logger.getColumnCount());
        assertEquals("z", logger.getColumnLabel(0));
    }

    @Test
    public void storesTheRunnerSeedOnTheState() throws Exception {
        PhyloSpecRunner runner = new PhyloSpecRunner("Real x ~ Normal(mean=0.0, sd=1.0)");
        runner.setSeed(12345L);

        BeastXState state = runner.buildState("test");

        assertEquals(12345L, state.randomSeed);
    }

    @Test
    public void loggedValuesOfDifferentTypesCanBeCombined() throws Exception {
        new PhyloSpecRunner("""
                Real x ~ Normal(mean=0.0, sd=1.0)
                Simplex y ~ Dirichlet(repeat(value=2.0, num=3))

                mcmc {
                    Logger logger = screenLogger(parameters=[x, y])
                }
                """).buildMCMC("test", 10);
    }

    @Test
    public void loggedValuesWhichCannotBeLoggedAreRejected() {
        for (String parameter : List.of("label", "\"x\"")) {
            String source = """
                    Real x ~ Normal(mean=0.0, sd=1.0)
                    String label = "label"

                    mcmc {
                        Logger logger = screenLogger(parameters=[%s])
                    }
                    """.formatted(parameter);
            assertThrows(
                    Exception.class,
                    () -> new PhyloSpecRunner(source).buildMCMC("test", 10),
                    parameter
            );
        }
    }

    @Test
    public void usesTheDefaultChainLengthWithoutASampler() throws Exception {
        BeastXState state =
                new PhyloSpecRunner("Real x ~ Normal(mean=0.0, sd=1.0)").buildState("test");

        assertEquals(McmcSamplerSpec.DEFAULT_CHAIN_LENGTH, state.getSamplerSpec().chainLength());
    }

    @Test
    public void usesTheDefaultLoggingIntervalWithoutLogEvery() throws Exception {
        String source = """
            Real x ~ Normal(mean=0.0, sd=1.0)

            mcmc {
                Logger screenLogger = screenLogger()
                Logger fileLogger = fileLogger(file="target/test-default-interval.log")
            }
            """;

        BeastXState state =
                new PhyloSpecRunner(source).buildState("test");

        assertEquals(LoggerSpec.DEFAULT_LOG_EVERY, state.screenLoggerSpecs.getFirst().logEvery());
        assertEquals(LoggerSpec.DEFAULT_LOG_EVERY, state.fileLoggerSpecs.getFirst().logEvery());
    }

    @Test
    public void rejectsNonPositiveLoggingInterval() throws Exception {
        String source = """
            Real x ~ Normal(mean=0.0, sd=1.0)

            mcmc {
                Logger screenLogger = screenLogger(logEvery=0)
            }
            """;

        PhyloSpecRunnerException exception = assertThrows(
                PhyloSpecRunnerException.class,
                () -> new PhyloSpecRunner(source).buildState("test")
        );

        assertTrue(
                exception.getMessage().contains("Wrong argument type"),
                exception.getMessage()
        );
    }

    @Test
    public void parameterNamesReplaceTheLoggedColumnLabels() throws Exception {
        String source = """
            Real x ~ Normal(mean=0.0, sd=1.0)
            Real y ~ Normal(mean=0.0, sd=1.0)

            mcmc {
                Logger fileLogger = fileLogger(
                    file="target/test-fileLogger-parameter-names.log",
                    parameters=[x, y],
                    parameterNames=["first", "second"]
                )
            }
            """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("test");

        MCLogger logger =
                assertInstanceOf(MCLogger.class, new MCMCBuilder().buildLoggers(model).getFirst());

        assertEquals(
                List.of("posterior", "prior", "likelihood", "first", "second"),
                columnLabels(logger)
        );

        String xml = new StateXmlGenerator().toXml(model);

        assertTrue(xml.contains("<column label=\"first\">"), xml);
        assertTrue(xml.contains("<column label=\"second\">"), xml);
    }

    @Test
    public void loggersAlwaysLogThePosteriorPriorAndLikelihood() throws Exception {
        String source = """
            Real x ~ Normal(mean=0.0, sd=1.0)
            Real y ~ Normal(mean=0.0, sd=1.0)

            mcmc {
                Logger fileLogger = fileLogger(
                    file="target/test-fileLogger-distributions.log",
                    parameters=[y]
                )
            }
            """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("test");

        MCLogger logger =
                assertInstanceOf(MCLogger.class, new MCMCBuilder().buildLoggers(model).getFirst());

        assertEquals(List.of("posterior", "prior", "likelihood", "y"), columnLabels(logger));

        assertEquals(
                List.of("column:", "prior", "likelihood", "parameter:y"),
                xmlLoggedReferences(new StateXmlGenerator().toXml(model))
        );
    }

    @Test
    public void loggerWithoutParametersLogsTreeStatisticsAndAllParameters() throws Exception {
        String source = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            PositiveReal birthRate ~ LogNormal(logMean=0.0, logSd=1.0)
            Real twiceTheBirthRate = 2.0 * birthRate

            Tree tree ~ Yule(
                birthRate=birthRate,
                taxa=taxa(data)
            )

            mcmc {
                Logger fileLogger = fileLogger(file="target/test-fileLogger-everything.log")
            }
            """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("test");

        MCLogger logger =
                assertInstanceOf(MCLogger.class, new MCMCBuilder().buildLoggers(model).getFirst());

        List<String> labels = columnLabels(logger);

        assertEquals(List.of("posterior", "prior", "likelihood"), labels.subList(0, 3));
        assertTrue(
                labels.containsAll(List.of("tree.height", "tree.treeLength", "birthRate", "twiceTheBirthRate")),
                labels.toString()
        );

        assertEquals(
                List.of(
                        "column:",
                        "prior",
                        "likelihood",
                        "treeHeightStatistic:tree.height",
                        "treeLengthStatistic:tree.treeLength",
                        "parameter:birthRate"
                ),
                xmlLoggedReferences(new StateXmlGenerator().toXml(model))
        );
    }

    private static List<String> columnLabels(MCLogger logger) {
        List<String> labels =
                new ArrayList<>();

        for (int i = 0; i < logger.getColumnCount(); i++) {
            labels.add(logger.getColumnLabel(i));
        }

        return labels;
    }

    /**
     * Returns what the file logger of the given XML logs, as {@code element:idref}. The prior is
     * returned as {@code prior}.
     */
    private static List<String> xmlLoggedReferences(String xml) throws Exception {
        var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));

        var loggers = document.getElementsByTagName("log");

        List<String> references =
                new ArrayList<>();

        for (int i = 0; i < loggers.getLength(); i++) {
            Element logger = (Element) loggers.item(i);

            if (!logger.hasAttribute("fileName")) {
                continue;
            }

            var children = logger.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                if (children.item(j) instanceof Element child) {
                    String idref = child.getAttribute("idref");
                    references.add(child.getTagName().equals(idref) ? idref : child.getTagName() + ":" + idref);
                }
            }
        }

        return references;
    }

    @Test
    public void rejectsParameterNamesThatDoNotMatchTheParameters() throws Exception {
        String source = """
            Real x ~ Normal(mean=0.0, sd=1.0)

            mcmc {
                Logger screenLogger = screenLogger(parameters=[x], parameterNames=["first", "second"])
            }
            """;

        PhyloSpecRunnerException exception = assertThrows(
                PhyloSpecRunnerException.class,
                () -> new PhyloSpecRunner(source).buildState("test")
        );

        assertTrue(
                exception.getMessage().contains("The logger has 1 parameters but 2 parameter names"),
                exception.getMessage()
        );
    }

    @Test
    public void buildMCMCUsesConfiguredRandomSeed() throws Exception {
        PhyloSpecRunner runner = new PhyloSpecRunner("Real x ~ LogNormal(logMean=0.0, logSd=1.0)");
        runner.setSeed(24680L);

        BeastXModel model =
                runner.buildModel("test");

        new MCMCBuilder().build(model);

        assertEquals(24680L, dr.math.MathUtils.getSeed());
    }

    @Test
    public void defaultFileLoggerIncludesPosteriorPriorLikelihoodWhenBuiltFromModel() throws Exception {
        String source = """
            PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)

            mcmc {
                Logger fileLogger = fileLogger(
                    logEvery=1000,
                    file="target/test-fileLogger-default-model.log"
                )
            }
            """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("test");

        List<Logger> loggers =
                new MCMCBuilder().buildLoggers(model);

        assertEquals(1, loggers.size());

        MCLogger logger =
                assertInstanceOf(MCLogger.class, loggers.getFirst());

        assertEquals(1000, logger.getLogEvery());
        assertTrue(logger.getColumnCount() >= 4);

        List<String> labels =
                new ArrayList<>();

        for (int i = 0; i < logger.getColumnCount(); i++) {
            labels.add(logger.getColumnLabel(i));
        }

        assertTrue(labels.contains("posterior"), labels.toString());
        assertTrue(labels.contains("prior"), labels.toString());
        assertTrue(labels.contains("likelihood"), labels.toString());
        assertTrue(labels.contains("x"), labels.toString());
    }

    @Test
    public void runnerOptionsApplyChainLengthAndOutputPrefix() throws Exception {
        String source = """
            PositiveReal x ~ LogNormal(
                logMean=0.0,
                logSd=1.0
            )
            """;

        Path outputDirectory =
                Path.of("target", "runner-mcmc-output", "scalar-" + System.nanoTime());

        RunnerOptions options =
                RunnerOptions.builder("scalarMCMC")
                        .mode(RunMode.EXECUTE_MCMC)
                        .chainLengthOverride(10)
                        .outputPrefix(outputDirectory, "scalar")
                        .build();

        BeastXRunResult result =
                new PhyloSpecRunner(source).run(options);

        Path logPath =
                outputDirectory.resolve("scalar.log");

        assertTrue(result.hasMCMC());
        assertTrue(result.executed());
        assertEquals(10, result.beastState().getSamplerSpec().chainLength());
        assertEquals(outputDirectory.resolve("scalar").toString(), result.beastState().outputPrefix);

        assertTrue(Files.exists(logPath), "Expected default parameter log to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected parameter log to be non-empty.");
    }

    @Test
    public void runnerOptionsWriteDefaultParameterAndTreeLogs() throws Exception {
        String source = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Taxa taxa = taxa(data)

            Tree tree ~ Yule(
                birthRate=1.0,
                taxa=taxa
            )
            """;

        Path outputDirectory =
                Path.of("target", "runner-mcmc-output", "tree-" + System.nanoTime());

        RunnerOptions options =
                RunnerOptions.builder("treeMCMC")
                        .mode(RunMode.EXECUTE_MCMC)
                        .chainLengthOverride(10)
                        .outputPrefix(outputDirectory, "tree")
                        .build();

        BeastXRunResult result =
                new PhyloSpecRunner(source).run(options);

        Path logPath =
                outputDirectory.resolve("tree.log");

        Path treePath =
                outputDirectory.resolve("tree.trees");

        assertTrue(result.hasMCMC());
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected default parameter log to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected parameter log to be non-empty.");

        assertTrue(Files.exists(treePath), "Expected default tree log to be written.");
        assertTrue(Files.size(treePath) > 0, "Expected tree log to be non-empty.");

        String treeLog =
                Files.readString(treePath);

        assertTrue(
                treeLog.contains("STATE_"),
                "Expected tree log to contain sampled STATE trees."
        );
    }

    @Test
    public void runResultExposesDefaultFileLogPathFromOutputPrefix() throws Exception {
        String source = """
            PositiveReal x ~ LogNormal(
                logMean=0.0,
                logSd=1.0
            )
            """;

        Path outputDirectory =
                Path.of("target", "runner-artifacts", "scalar-" + System.nanoTime());

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                RunnerOptions.builder("scalarArtifactRun")
                                        .mode(RunMode.EXECUTE_MCMC)
                                        .chainLengthOverride(10)
                                        .outputPrefix(outputDirectory, "scalar")
                                        .build()
                        );

        Path expectedLogPath =
                outputDirectory.resolve("scalar.log");

        assertTrue(result.executed());
        assertTrue(result.hasFileLogs());
        assertTrue(result.hasOutputFiles());
        assertEquals(List.of(expectedLogPath), result.fileLogPaths());
        assertEquals(expectedLogPath, result.firstFileLogPath().orElseThrow());
        assertTrue(result.treeLogPaths().isEmpty());

        assertTrue(Files.exists(expectedLogPath));
        assertTrue(Files.size(expectedLogPath) > 0);
    }

    @Test
    public void runResultExposesDefaultFileAndTreeLogPathsFromOutputPrefix() throws Exception {
        String source = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Taxa taxa = taxa(data)

            Tree tree ~ Yule(
                birthRate=1.0,
                taxa=taxa
            )
            """;

        Path outputDirectory =
                Path.of("target", "runner-artifacts", "tree-" + System.nanoTime());

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                RunnerOptions.builder("treeArtifactRun")
                                        .mode(RunMode.EXECUTE_MCMC)
                                        .chainLengthOverride(10)
                                        .outputPrefix(outputDirectory, "tree")
                                        .build()
                        );

        Path expectedLogPath =
                outputDirectory.resolve("tree.log");

        Path expectedTreePath =
                outputDirectory.resolve("tree.trees");

        assertTrue(result.executed());
        assertEquals(List.of(expectedLogPath), result.fileLogPaths());
        assertEquals(List.of(expectedTreePath), result.treeLogPaths());
        assertEquals(List.of(expectedLogPath, expectedTreePath), result.outputPaths());

        assertEquals(expectedLogPath, result.firstFileLogPath().orElseThrow());
        assertEquals(expectedTreePath, result.firstTreeLogPath().orElseThrow());

        assertTrue(Files.exists(expectedLogPath));
        assertTrue(Files.size(expectedLogPath) > 0);

        assertTrue(Files.exists(expectedTreePath));
        assertTrue(Files.size(expectedTreePath) > 0);
    }

    @Test
    public void runResultExposesExplicitFileLoggerPath() throws Exception {
        Path outputDirectory =
                Path.of("target", "runner-artifacts", "explicit-" + System.nanoTime());

        Path explicitLogPath =
                outputDirectory.resolve("explicit.log");

        String source = """
            PositiveReal x ~ LogNormal(
                logMean=0.0,
                logSd=1.0
            )

            mcmc {
                Logger fileLogger = fileLogger(
                    logEvery=5,
                    file="%s",
                    parameters=[x]
                )
            }
            """.formatted(explicitLogPath.toString().replace("\\", "\\\\"));

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                RunnerOptions.builder("explicitArtifactRun")
                                        .mode(RunMode.EXECUTE_MCMC)
                                        .chainLengthOverride(10)
                                        .build()
                        );

        assertTrue(result.executed());
        assertEquals(List.of(explicitLogPath), result.fileLogPaths());
        assertEquals(explicitLogPath, result.firstFileLogPath().orElseThrow());
        assertTrue(result.treeLogPaths().isEmpty());

        assertTrue(Files.exists(explicitLogPath));
        assertTrue(Files.size(explicitLogPath) > 0);
    }

    @Test
    public void buildsScaleOperatorForPositiveRealParameter() throws Exception {
        String source =
                """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(1, operators.size());
        assertTrue(containsOperator(operators, ScaleOperator.class));
    }

    @Test
    public void buildsRandomWalkOperatorForRealParameter() throws Exception {
        String source =
                """
                Real x ~ Normal(mean=0.0, sd=1.0)
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(1, operators.size());
        assertTrue(containsOperator(operators, RandomWalkOperator.class));
    }

    @Test
    public void buildsDeltaExchangeOperatorForSimplexParameter() throws Exception {
        String source =
                """
                Simplex frequencies ~ Dirichlet(concentration=[1.0, 1.0, 1.0, 1.0])
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(1, operators.size());
        assertTrue(containsOperator(operators, DeltaExchangeOperator.class));
    }

    @Test
    public void buildsBitFlipOperatorForBernoulliParameter() throws Exception {
        String source =
                """
                Boolean success ~ Bernoulli(p=0.25)
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(1, operators.size());
        assertTrue(containsOperator(operators, BitFlipOperator.class));
    }

    @Test
    public void buildsDefaultTreeOperatorsForStochasticTree() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(8, operators.size());
        assertTrue(containsScaleOperatorForVariable(operators, "tree.allInternalNodeHeights"));
        assertTrue(containsOperator(operators, UniformNodeHeightOperator.class));
        assertTrue(containsOperator(operators, RandomWalkNodeHeightOperator.class));
        assertTrue(containsOperator(operators, SubtreeSlideOperator.class));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
    }

    @Test
    public void buildsParameterAndTreeOperatorsForPhylogeneticModel() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                PositiveReal kappa ~ LogNormal(logMean=0.0, logSd=1.0)
                Simplex baseFrequencies ~ Dirichlet(concentration=[1.0, 1.0, 1.0, 1.0])

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                QMatrix qMatrix = hky(
                    kappa=kappa,
                    baseFrequencies=baseFrequencies
                )

                Alignment observed = data
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(10, operators.size());

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsOperator(operators, DeltaExchangeOperator.class));

        assertTrue(containsScaleOperatorForVariable(operators, "tree.allInternalNodeHeights"));
        assertTrue(containsOperator(operators, UniformNodeHeightOperator.class));
        assertTrue(containsOperator(operators, RandomWalkNodeHeightOperator.class));
        assertTrue(containsOperator(operators, SubtreeSlideOperator.class));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
    }

    private List<MCMCOperator> buildOperators(String source) throws Exception {
        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXModel model =
                runner.buildModel("test");

        BeastXState beastState =
                model.beastState;

        return new OperatorBuilder().build(beastState);
    }

    private boolean containsOperator(
            List<MCMCOperator> operators,
            Class<? extends MCMCOperator> operatorClass
    ) {
        return operators.stream()
                .anyMatch(operatorClass::isInstance);
    }

    private boolean containsScaleOperatorForVariable(
            List<MCMCOperator> operators,
            String variableId
    ) {
        return operators.stream()
                .filter(ScaleOperator.class::isInstance)
                .map(ScaleOperator.class::cast)
                .map(ScaleOperator::getVariable)
                .anyMatch(variable -> variableId.equals(variable.getId()));
    }

    @Test
    public void buildsTreeClockUpDownOperatorForStrictClockModel() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                Rate clockRate ~ LogNormal(logMean=0.0, logSd=1.0)

                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=jc69(),
                    branchRates~StrictClock(clockRate=clockRate, tree=tree)
                ) observed as data
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsScaleOperatorForVariable(operators, "tree.allInternalNodeHeights"));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
        assertTrue(containsOperator(operators, UpDownOperator.class));
    }

    @Test
    public void buildsTreeClockUpDownOperatorForStrictClockModelWithRenamedClockRate() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                Rate molecularRate ~ LogNormal(logMean=0.0, logSd=1.0)

                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=jc69(),
                    branchRates~StrictClock(clockRate=molecularRate, tree=tree)
                ) observed as data
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsScaleOperatorForVariable(operators, "tree.allInternalNodeHeights"));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
        assertTrue(containsOperator(operators, UpDownOperator.class));
    }

}
