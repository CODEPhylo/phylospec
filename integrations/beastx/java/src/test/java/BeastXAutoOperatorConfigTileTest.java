import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.summary.BeastXModelSummary;
import tiling.BeastXState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXAutoOperatorConfigTileTest {

    @Test
    public void readsAutoOperatorSettingsFromMCMCBlock() throws Exception {
        String source = """
                Real clockRate ~ LogNormal(logMean=0.0, logSd=1.0)

                mcmc {
                    Real parameterOperatorWeight = 2.0
                    Real parameterScaleFactor = 0.5
                    Real randomWalkWindowSize = 0.25

                    Real treeScaleWeight = 9.0
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
}