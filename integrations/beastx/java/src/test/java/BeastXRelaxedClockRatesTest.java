import dr.evolution.tree.NodeRef;
import dr.evomodel.branchratemodel.AbstractBranchRateModel;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tiling.BeastXState;
import tiling.model.BeastXPhyloCTMCLikelihoodSpec;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Relaxed-clock branch rates of BEAST X: each rate is clockRate times the base quantile of the branch's
 * category, without rescaling on the tree (as in BEAST 3). Proposals update the rates and a rejected
 * proposal restores them.
 */
class BeastXRelaxedClockRatesTest {
    private static final double TOLERANCE = 1e-12;

    @TempDir
    Path directory;

    @Test
    void categoryProposalsUpdateOnlyThatBranchAndRestoreAcceptedState() throws Exception {
        Fixture fixture = fixture();
        for (boolean accept : new boolean[]{true, false}) {
            double[] before = assertRatesMatchFormula(fixture);
            double categoryBefore = fixture.categories().getParameterValue(0);
            fixture.rates().storeModelState();
            fixture.categories().setParameterValue(0, (categoryBefore + 1) % fixture.categories().getDimension());
            double[] proposed = assertRatesMatchFormula(fixture);
            assertNotEquals(before[0], proposed[0], TOLERANCE);
            for (int index = 1; index < before.length; index++) {
                assertEquals(before[index], proposed[index], TOLERANCE,
                        "Without normalization, other branches keep their rates");
            }
            complete(fixture, accept);
            assertArrayEquals(accept ? proposed : before, assertRatesMatchFormula(fixture), TOLERANCE);
            assertEquals(accept ? (categoryBefore + 1) % fixture.categories().getDimension() : categoryBefore,
                    fixture.categories().getParameterValue(0));
        }
    }

    @Test
    void treeHeightProposalsKeepRatesAndRestoreAcceptedState() throws Exception {
        Fixture fixture = fixture();
        for (boolean accept : new boolean[]{true, false}) {
            double[] before = assertRatesMatchFormula(fixture);
            double heightBefore = fixture.tree().getNodeHeight(fixture.tree().getRoot());
            fixture.rates().storeModelState();
            fixture.tree().setNodeHeight(fixture.tree().getRoot(), heightBefore + 0.7);
            double[] proposed = assertRatesMatchFormula(fixture);
            assertArrayEquals(before, proposed, TOLERANCE, "Without normalization, branch times do not change rates");
            complete(fixture, accept);
            assertArrayEquals(accept ? proposed : before, assertRatesMatchFormula(fixture), TOLERANCE);
            assertEquals(accept ? heightBefore + 0.7 : heightBefore,
                    fixture.tree().getNodeHeight(fixture.tree().getRoot()));
        }
    }

    @Test
    void distributionProposalsRefreshQuantilesAndRestoreAcceptedState() throws Exception {
        Fixture fixture = fixture();
        for (boolean accept : new boolean[]{true, false}) {
            double[] before = assertRatesMatchFormula(fixture);
            double sigmaBefore = fixture.distribution().getSigma();
            double sigmaProposed = sigmaBefore + 0.2;
            fixture.rates().storeModelState();
            // Keep the base distribution's arithmetic mean equal to one.
            fixture.distribution().getMuParameter().setParameterValue(0, -sigmaProposed * sigmaProposed / 2.0);
            fixture.distribution().getSigmaParameter().setParameterValue(0, sigmaProposed);
            assertEquals(1.0, fixture.distribution().mean(), TOLERANCE);
            double[] proposed = assertRatesMatchFormula(fixture);
            assertNotEquals(before[0], proposed[0], TOLERANCE);
            complete(fixture, accept);
            assertArrayEquals(accept ? proposed : before, assertRatesMatchFormula(fixture), TOLERANCE);
            assertEquals(accept ? sigmaProposed : sigmaBefore, fixture.distribution().getSigma(), TOLERANCE);
            assertEquals(1.0, fixture.distribution().mean(), TOLERANCE);
        }
    }

    @Test
    void clockRateScalesAllRatesAndRestoresAcceptedState() throws Exception {
        Fixture fixture = fixture();
        for (boolean accept : new boolean[]{true, false}) {
            double[] before = assertRatesMatchFormula(fixture);
            double clockBefore = fixture.clockRate().getParameterValue(0);
            fixture.rates().storeModelState();
            fixture.clockRate().setParameterValue(0, clockBefore * 1.5);
            double[] proposed = assertRatesMatchFormula(fixture);
            for (int index = 0; index < before.length; index++) {
                assertEquals(1.5 * before[index], proposed[index], TOLERANCE);
            }
            complete(fixture, accept);
            assertArrayEquals(accept ? proposed : before, assertRatesMatchFormula(fixture), TOLERANCE);
            assertEquals(accept ? clockBefore * 1.5 : clockBefore, fixture.clockRate().getParameterValue(0));
        }
    }

    @Test
    void topologyProposalsKeepRatesAndRestoreAcceptedState() throws Exception {
        Fixture fixture = fixture();
        int aNumber = fixture.tree().getExternalNode(0).getNumber();
        int cNumber = fixture.tree().getExternalNode(2).getNumber();
        for (boolean accept : new boolean[]{true, false}) {
            double[] before = assertRatesMatchFormula(fixture);
            int[] topologyBefore = parents(fixture.tree());
            double[] heightsBefore = heights(fixture.tree());
            NodeRef a = fixture.tree().getNode(aNumber);
            NodeRef c = fixture.tree().getNode(cNumber);
            NodeRef parentA = fixture.tree().getParent(a);
            NodeRef parentC = fixture.tree().getParent(c);
            fixture.rates().storeModelState();
            fixture.tree().beginTreeEdit();
            fixture.tree().removeChild(parentA, a);
            fixture.tree().removeChild(parentC, c);
            fixture.tree().addChild(parentA, c);
            fixture.tree().addChild(parentC, a);
            fixture.tree().endTreeEdit();
            int[] proposedTopology = parents(fixture.tree());
            assertEquals(parentC.getNumber(), proposedTopology[aNumber]);
            assertEquals(parentA.getNumber(), proposedTopology[cNumber]);
            double[] proposed = assertRatesMatchFormula(fixture);
            assertArrayEquals(before, proposed, TOLERANCE, "Categories belong to nodes, so a swap keeps the rates");
            complete(fixture, accept);
            assertArrayEquals(accept ? proposed : before, assertRatesMatchFormula(fixture), TOLERANCE);
            // DefaultTreeModel restores by swapping node buffers. Check logical nodes,
            // not the Java objects that belonged to the discarded proposal buffer.
            assertArrayEquals(accept ? proposedTopology : topologyBefore, parents(fixture.tree()));
            assertArrayEquals(heightsBefore, heights(fixture.tree()), 0.0);
        }
    }

    private static int[] parents(DefaultTreeModel tree) {
        int[] parents = new int[tree.getNodeCount()];
        for (int i = 0; i < parents.length; i++) {
            NodeRef node = tree.getNode(i);
            parents[i] = tree.isRoot(node) ? -1 : tree.getParent(node).getNumber();
        }
        return parents;
    }

    private static double[] heights(DefaultTreeModel tree) {
        double[] heights = new double[tree.getNodeCount()];
        for (int i = 0; i < heights.length; i++) heights[i] = tree.getNodeHeight(tree.getNode(i));
        return heights;
    }

    private Fixture fixture() throws Exception {
        Path treeFile = directory.resolve("tree.nwk");
        Path alignmentFile = directory.resolve("alignment.nex");
        Files.writeString(treeFile, "((A:1,B:1):1,(C:0.5,D:0.5):1.5);");
        Files.writeString(alignmentFile, """
                #NEXUS
                Begin data;
                Dimensions ntax=4 nchar=4;
                Format datatype=dna missing=? gap=-;
                Matrix
                A ACGT
                B ACGA
                C ATGT
                D TCGT
                ;
                End;
                """);
        String source = """
                Alignment data = fromNexus("%s")
                Tree tree = fromTree(file="%s")
                PositiveReal clockRate ~ LogNormal(logMean=-0.7, logSd=0.1)
                Vector<Rate> rates ~ RelaxedClock(
                    clockRate=clockRate,
                    base=LogNormal(logMean=-0.08, logSd=0.4),
                    tree=tree
                )
                Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=jc69(), branchRates=rates) observed as data
                """.formatted(unixPath(alignmentFile), unixPath(treeFile));
        BeastXState state = new PhyloSpecRunner(source).buildState("relaxed-clock-rates");
        BeastXState.RelaxedClockSpec clock = state.treeRelaxedClockModels.values().iterator().next();
        BeastXPhyloCTMCLikelihoodSpec likelihood = assertInstanceOf(
                BeastXPhyloCTMCLikelihoodSpec.class, state.likelihoodDistributions.getFirst());
        return new Fixture(
                assertInstanceOf(DefaultTreeModel.class, clock.treeModel()),
                clock.rateCategoriesParameter(),
                assertInstanceOf(LogNormalDistributionModel.class, clock.distributionModel()),
                clock.clockRateParameter(),
                assertInstanceOf(AbstractBranchRateModel.class, likelihood.getBranchRateModel()));
    }

    private static void complete(Fixture fixture, boolean accept) {
        if (accept) fixture.rates().acceptModelState();
        else fixture.rates().restoreModelState();
    }

    private static double[] assertRatesMatchFormula(Fixture fixture) {
        int count = fixture.tree().getNodeCount() - 1;
        int rootNumber = fixture.tree().getRoot().getNumber();
        double clockRate = fixture.clockRate().getParameterValue(0);
        double[] actual = new double[count];
        for (int i = 0; i < fixture.tree().getNodeCount(); i++) {
            NodeRef node = fixture.tree().getNode(i);
            if (fixture.tree().isRoot(node)) continue;
            int index = i < rootNumber ? i : i - 1;
            double category = fixture.categories().getParameterValue(index);
            double expected = clockRate * fixture.distribution().quantile((category + 0.5) / count);
            actual[index] = fixture.rates().getBranchRate(fixture.tree(), node);
            assertEquals(expected, actual[index], TOLERANCE, "Incorrect rate at node " + i);
        }
        return actual;
    }

    private static String unixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record Fixture(DefaultTreeModel tree, Parameter categories, LogNormalDistributionModel distribution,
                           Parameter clockRate, AbstractBranchRateModel rates) {}
}
