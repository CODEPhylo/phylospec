import dr.evomodel.branchmodel.HomogeneousBranchModel;
import dr.evomodel.operators.ExchangeOperator;
import dr.evomodel.operators.SubtreeSlideOperator;
import dr.evomodel.operators.UniformNodeHeightOperator;
import dr.evomodel.operators.WilsonBalding;
import dr.evomodel.substmodel.FrequencyModel;
import dr.inference.loggers.Logger;
import dr.inference.loggers.MCLogger;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.operators.*;
import dr.inference.operators.DeltaExchangeOperator;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.RandomWalkOperator;
import dr.inference.operators.ScaleOperator;
import dr.inference.operators.UpDownOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.mcmc.MCMCBuilder;
import tiling.model.BeastXPhyloCTMCLikelihoodSpec;
import tiling.operators.OperatorBuilder;
import tiling.runner.BeastXRunResult;
import tiling.runner.RunMode;
import tiling.runner.RunnerOptions;
import tiling.summary.BeastXModelSummary;
import tiling.BeastXState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXMCMCBuilderTest {

    @Test
    @Tag("integration-test")
    public void readsAutoOperatorSettingsFromMCMCBlock() throws Exception {
        String source = """
                Real clockRate ~ LogNormal(logMean=0.0, logSd=1.0)

                mcmc {
                    Real parameterOperatorWeight = 2.0
                    Real parameterScaleFactor = 0.5
                    Real randomWalkWindowSize = 0.25

                    Real treeScaleWeight = 9.0
                    Real treeScaleFactor = 0.4
                    Real treeNodeHeightWeight = 12.0
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
        assertEquals(0.4, state.operatorConfig.treeScaleFactor);
        assertEquals(12.0, state.operatorConfig.treeNodeHeightWeight);
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

                mcmc {
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

                mcmc {
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

                mcmc {
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

                mcmc {
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

                mcmc {
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
                    Integer chainLength = 1000
                }
                """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        assertEquals(1000, state.chainLength);
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
        assertEquals(List.of("x"), state.screenLoggerSpecs.getFirst().parameterNames());
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
        assertEquals(List.of("x"), state.fileLoggerSpecs.getFirst().parameterNames());
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
                    parameters=[x, "x_prior"]
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
        assertEquals(2, logger.getColumnCount());
        assertEquals("x", logger.getColumnLabel(0));
        assertEquals("x_prior", logger.getColumnLabel(1));
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
                    trees=[tree]
                )
            }
            """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        assertEquals(1, state.treeLoggerSpecs.size());
        assertEquals(1000, state.treeLoggerSpecs.getFirst().logEvery());
        assertEquals("target/test-trees.log", state.treeLoggerSpecs.getFirst().fileName());
        assertEquals(List.of("tree"), state.treeLoggerSpecs.getFirst().treeNames());
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
                    trees=[tree]
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
    public void buildsFileLoggerForSelectedTreeStatistics() throws Exception {
        String source = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Taxa taxa = taxa(data)

            Tree tree ~ Yule(
                birthRate=1.0,
                taxa=taxa
            )

            mcmc {
                Logger fileLogger = fileLogger(
                    logEvery=1000,
                    file="target/test-fileLogger-tree-statistics.log",
                    parameters=["tree.height", "tree.treeLength"]
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
        assertEquals(2, logger.getColumnCount());
        assertEquals("tree.height", logger.getColumnLabel(0));
        assertEquals("tree.treeLength", logger.getColumnLabel(1));
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
    public void readsRandomSeedFromMCMCBlock() throws Exception {
        String source = """
            mcmc {
                Int randomSeed = 12345
            }
            """;

        BeastXState state =
                new PhyloSpecRunner(source).buildState("test");

        assertEquals(12345L, state.randomSeed);
    }

    @Test
    public void rejectsNegativeRandomSeed() throws Exception {
        String source = """
            mcmc {
                Int randomSeed = -1
            }
            """;

        try {
            new PhyloSpecRunner(source).buildState("test");
        } catch (PhyloSpecRunnerException exception) {
            assertTrue(
                    exception.getMessage().contains("MCMC random seed must not be negative"),
                    exception.getMessage()
            );
            return;
        }

        throw new AssertionError("Expected negative randomSeed to fail.");
    }

    @Test
    public void buildMCMCUsesConfiguredRandomSeed() throws Exception {
        String source = """
            Real x ~ LogNormal(logMean=0.0, logSd=1.0)

            mcmc {
                Int randomSeed = 24680
            }
            """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("test");

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
    public void runnerOptionsApplyChainLengthLogEveryAndOutputPrefix() throws Exception {
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
                        .defaultLogEveryOverride(2)
                        .outputPrefix(outputDirectory, "scalar")
                        .build();

        BeastXRunResult result =
                new PhyloSpecRunner(source).run(options);

        Path logPath =
                outputDirectory.resolve("scalar.log");

        assertTrue(result.hasMCMC());
        assertTrue(result.executed());
        assertEquals(10, result.beastState().chainLength);
        assertEquals(2, result.beastState().defaultLogEvery);
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
                        .defaultLogEveryOverride(2)
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
                                        .defaultLogEveryOverride(5)
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
                                        .defaultLogEveryOverride(5)
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
                    parameters=[posterior, prior, likelihood, x, "x_prior"]
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

        assertEquals(5, operators.size());
        assertTrue(containsOperator(operators, UniformNodeHeightOperator.class));
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

        assertEquals(7, operators.size());

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsOperator(operators, DeltaExchangeOperator.class));

        assertTrue(containsOperator(operators, UniformNodeHeightOperator.class));
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
        assertTrue(containsOperator(operators, UniformNodeHeightOperator.class));
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
        assertTrue(containsOperator(operators, UniformNodeHeightOperator.class));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
        assertTrue(containsOperator(operators, UpDownOperator.class));
    }

    @Test
    public void buildsOperatorsForEstimatedPhyloBMParameters() throws Exception {
        String source =
                """
                Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
                Taxa taxa = taxa(molecularData)

                Alignment traitData = continuousTraitsFromTaxa(
                    taxa=taxa,
                    trait=parse(regex=".*_([01])$")
                )

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=1.0,
                    tree=tree
                )

                PositiveReal diffusionRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Real rootValue ~ Normal(
                    mean=0.0,
                    sd=1.0
                )

                Vector<Rate> siteRates = [diffusionRate]
                Vector<Real> rootValues = [rootValue]

                Alignment traits ~ PhyloBM(
                    tree=tree,
                    branchRates=branchRates,
                    siteRates=siteRates,
                    rootValues=rootValues
                ) observed as traitData
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(7, operators.size());

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsOperator(operators, RandomWalkOperator.class));

        assertTrue(containsOperator(operators, UniformNodeHeightOperator.class));
        assertTrue(containsOperator(operators, SubtreeSlideOperator.class));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
    }

    @Test
    public void buildsOperatorsForEstimatedPhyloOUParameters() throws Exception {
        String source =
                """
                Alignment molecularData = fromNexus("src/test/java/resources/binary-traits.nex")
                Taxa taxa = taxa(molecularData)

                Alignment traitData = continuousTraitsFromTaxa(
                    taxa=taxa,
                    trait=parse(regex=".*_([01])$")
                )

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                PositiveReal siteVariance ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                PositiveReal alpha ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Real optimum ~ Normal(
                    mean=0.0,
                    sd=1.0
                )

                Real rootValue ~ Normal(
                    mean=0.0,
                    sd=1.0
                )

                Vector<PositiveReal> siteVariances = [siteVariance]
                Vector<Real> siteOptima = [optimum]
                Vector<Real> rootValues = [rootValue]

                Alignment traits ~ PhyloOU(
                    tree=tree,
                    siteVariances=siteVariances,
                    selectionStrength=alpha,
                    siteOptima=siteOptima,
                    rootValues=rootValues
                ) observed as traitData
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(9, operators.size());

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsOperator(operators, RandomWalkOperator.class));

        assertTrue(containsOperator(operators, UniformNodeHeightOperator.class));
        assertTrue(containsOperator(operators, SubtreeSlideOperator.class));
        assertTrue(containsOperator(operators, ExchangeOperator.class));
        assertTrue(containsOperator(operators, WilsonBalding.class));
    }

    @Test
    public void fixedTreePhyloBMBuildsOnlyTraitParameterOperators() throws Exception {
        String source =
                """
                Tree tree = fromNewick("((taxon1_0:0.1,taxon2_1:0.1):0.2,(taxon3_0:0.1,taxon4_1:0.1):0.2);")
                Taxa taxa = taxa(tree)

                Alignment traitData = continuousTraitsFromTaxa(
                    taxa=taxa,
                    trait=parse(regex=".*_([01])$")
                )

                Vector<Rate> branchRates ~ StrictClock(
                    clockRate=1.0,
                    tree=tree
                )

                PositiveReal diffusionRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Real rootValue ~ Normal(
                    mean=0.0,
                    sd=1.0
                )

                Vector<Rate> siteRates = [diffusionRate]
                Vector<Real> rootValues = [rootValue]

                Alignment traits ~ PhyloBM(
                    tree=tree,
                    branchRates=branchRates,
                    siteRates=siteRates,
                    rootValues=rootValues
                ) observed as traitData
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(2, operators.size());

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsOperator(operators, RandomWalkOperator.class));

        assertFalse(containsOperator(operators, UniformNodeHeightOperator.class));
        assertFalse(containsOperator(operators, SubtreeSlideOperator.class));
        assertFalse(containsOperator(operators, ExchangeOperator.class));
        assertFalse(containsOperator(operators, WilsonBalding.class));
        assertFalse(containsOperator(operators, UpDownOperator.class));
    }

    @Test
    public void fixedTreePhyloOUBuildsOnlyTraitParameterOperators() throws Exception {
        String source =
                """
                Tree tree = fromNewick("((taxon1_0:0.1,taxon2_1:0.1):0.2,(taxon3_0:0.1,taxon4_1:0.1):0.2);")
                Taxa taxa = taxa(tree)

                Alignment traitData = continuousTraitsFromTaxa(
                    taxa=taxa,
                    trait=parse(regex=".*_([01])$")
                )

                PositiveReal siteVariance ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                PositiveReal alpha ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Real optimum ~ Normal(
                    mean=0.0,
                    sd=1.0
                )

                Real rootValue ~ Normal(
                    mean=0.0,
                    sd=1.0
                )

                Vector<PositiveReal> siteVariances = [siteVariance]
                Vector<Real> siteOptima = [optimum]
                Vector<Real> rootValues = [rootValue]

                Alignment traits ~ PhyloOU(
                    tree=tree,
                    siteVariances=siteVariances,
                    selectionStrength=alpha,
                    siteOptima=siteOptima,
                    rootValues=rootValues
                ) observed as traitData
                """;

        List<MCMCOperator> operators =
                buildOperators(source);

        assertEquals(4, operators.size());

        assertTrue(containsOperator(operators, ScaleOperator.class));
        assertTrue(containsOperator(operators, RandomWalkOperator.class));

        assertFalse(containsOperator(operators, UniformNodeHeightOperator.class));
        assertFalse(containsOperator(operators, SubtreeSlideOperator.class));
        assertFalse(containsOperator(operators, ExchangeOperator.class));
        assertFalse(containsOperator(operators, WilsonBalding.class));
        assertFalse(containsOperator(operators, UpDownOperator.class));
    }

    @Test
    public void stochasticHkyBaseFrequenciesAreUsedByLikelihoodFrequencyModel() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")

                Taxa taxa = taxa(data)

                PositiveReal kappa ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Simplex baseFrequencies ~ Dirichlet(
                    concentration=[1.0, 1.0, 1.0, 1.0]
                )

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=hky(
                        kappa=kappa,
                        baseFrequencies=baseFrequencies
                    )
                ) observed as data
                """;

        BeastXModel model =
                new PhyloSpecRunner(source)
                        .buildModel("stochasticHkyBaseFrequencies");

        Parameter baseFrequencies =
                model.beastState.stateNodesByPhyloSpecName.get("baseFrequencies");

        assertNotNull(baseFrequencies);

        Likelihood likelihood =
                model.beastState.likelihoodDistributions.getFirst();

        BeastXPhyloCTMCLikelihoodSpec phyloCTMC =
                assertInstanceOf(BeastXPhyloCTMCLikelihoodSpec.class, likelihood);

        HomogeneousBranchModel branchModel =
                assertInstanceOf(HomogeneousBranchModel.class, phyloCTMC.getBranchModel());

        FrequencyModel frequencyModel =
                branchModel.getRootFrequencyModel();

        assertSame(baseFrequencies, frequencyModel.getFrequencyParameter());
    }
}
