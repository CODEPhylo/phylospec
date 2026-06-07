import dr.inference.loggers.Logger;
import dr.inference.loggers.MCLogger;
import org.junit.jupiter.api.Test;
import tiling.mcmc.MCMCBuilder;
import tiling.BeastXModel;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
public class BeastXMCMCConfigTileTest {

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
        assertEquals(500, state.screenLoggerSpecs.getFirst().logEvery);
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
        assertEquals(500, state.screenLoggerSpecs.getFirst().logEvery);
        assertEquals(List.of("x"), state.screenLoggerSpecs.getFirst().parameterNames);
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
        assertEquals(1000, state.fileLoggerSpecs.getFirst().logEvery);
        assertEquals("output.log", state.fileLoggerSpecs.getFirst().fileName);
        assertEquals(List.of("x"), state.fileLoggerSpecs.getFirst().parameterNames);
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
                    trees=[tree]
                )
            }
            """;

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("test");

        assertEquals(1, state.treeLoggerSpecs.size());
        assertEquals(1000, state.treeLoggerSpecs.getFirst().logEvery);
        assertEquals("target/test-trees.log", state.treeLoggerSpecs.getFirst().fileName);
        assertEquals(List.of("tree"), state.treeLoggerSpecs.getFirst().treeNames);
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
}