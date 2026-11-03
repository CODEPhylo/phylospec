import dr.evolution.tree.NodeRef;
import dr.evolution.tree.TreeUtils;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Parameter;
import dr.inference.operators.ScaleOperator;
import dr.math.MathUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tiling.BeastXState;
import tiling.operators.OperatorBuilder;

import static org.junit.jupiter.api.Assertions.*;

public class BeastXRootHeightOperatorTest {

    @Test
    public void rootScaleTargetsTheSingleActualRootHeight() throws Exception {
        Fixture fixture = fixture();
        DefaultTreeModel tree = fixture.tree();
        Parameter parameter = assertInstanceOf(Parameter.class, fixture.operator().getVariable());

        assertSame(tree.getRootHeightParameter(), parameter);
        assertEquals(1, parameter.getDimension());
        assertEquals("tree.rootHeight", parameter.getId());
        assertEquals(tree.getNodeHeight(tree.getRoot()), parameter.getParameterValue(0));
        assertEquals(Double.POSITIVE_INFINITY, parameter.getBounds().getUpperLimit(0));
        assertEquals(
                Math.max(
                        tree.getNodeHeight(tree.getChild(tree.getRoot(), 0)),
                        tree.getNodeHeight(tree.getChild(tree.getRoot(), 1))),
                parameter.getBounds().getLowerLimit(0));
        assertEquals(fixture.state().operatorConfig.treeRootScaleWeight, fixture.operator().getWeight());
        assertEquals(fixture.state().operatorConfig.treeRootScaleFactor, fixture.operator().getScaleFactor());
    }

    @Test
    public void proposalsMoveOnlyTheRootAndPreserveAcceptedOrRestoredStates() throws Exception {
        Fixture fixture = fixture();
        for (long seed = 1; seed <= 20; seed++) {
            assertRootProposal(fixture, seed, seed % 2 == 0);
        }
        assertEquals(10, fixture.operator().getAcceptCount());
        assertEquals(10, fixture.operator().getRejectCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void rootParameterStaysBoundAfterRootChangingTopologyMoves(boolean acceptTopology) throws Exception {
        Fixture fixture = fixture();
        DefaultTreeModel tree = fixture.tree();
        Parameter rootParameter = tree.getRootHeightParameter();
        int previousRootNumber = tree.getRoot().getNumber();
        double[] previousHeights = heights(tree);
        String previousTopology = TreeUtils.newickNoLengths(tree);

        tree.storeModelState();
        rotateRoot(tree);
        assertNotEquals(previousRootNumber, tree.getRoot().getNumber());
        assertNotEquals(previousTopology, TreeUtils.newickNoLengths(tree));
        assertTrue(tree.isTreeValid());
        assertSame(rootParameter, tree.getRootHeightParameter());
        assertSame(rootParameter, fixture.operator().getVariable());

        if (acceptTopology) {
            tree.acceptModelState();
        } else {
            tree.restoreModelState();
            // BEAST restores by swapping active/stored node arrays; node numbers,
            // topology, heights and Parameter identities are the stable contract.
            assertEquals(previousRootNumber, tree.getRoot().getNumber());
            assertEquals(previousTopology, TreeUtils.newickNoLengths(tree));
            assertArrayEquals(previousHeights, heights(tree));
        }

        assertSame(rootParameter, tree.getRootHeightParameter());
        assertRootProposal(fixture, 52, false);
        assertRootProposal(fixture, 53, true);
    }

    private static void assertRootProposal(Fixture fixture, long seed, boolean accept) {
        DefaultTreeModel tree = fixture.tree();
        NodeRef root = tree.getRoot();
        double[] before = heights(tree);
        tree.storeModelState();
        MathUtils.setSeed(seed);
        double logHastings = fixture.operator().operate();
        assertTrue(Double.isFinite(logHastings));
        assertSame(root, tree.getRoot());
        assertTrue(tree.isTreeValid());
        assertSame(tree.getRootHeightParameter(), fixture.operator().getVariable());
        assertNotEquals(before[root.getNumber()], tree.getNodeHeight(root));
        double[] proposed = heights(tree);
        for (int index = 0; index < tree.getNodeCount(); index++) {
            if (index != root.getNumber()) {
                assertEquals(before[index], proposed[index], "Only the actual root height may change");
            }
        }

        if (accept) {
            fixture.operator().accept(0.0);
            tree.acceptModelState();
        } else {
            fixture.operator().reject();
            tree.restoreModelState();
        }
        assertArrayEquals(accept ? proposed : before, heights(tree));
        assertSame(tree.getRootHeightParameter(), fixture.operator().getVariable());
    }

    // A deterministic root-changing rotation, using BEAST's normal tree-edit lifecycle.
    private static void rotateRoot(DefaultTreeModel tree) {
        NodeRef oldRoot = tree.getRoot();
        NodeRef child = tree.getChild(oldRoot, 0);
        NodeRef sibling = tree.getChild(oldRoot, 1);
        if (tree.isExternal(child)) {
            NodeRef swap = child;
            child = sibling;
            sibling = swap;
        }
        assertFalse(tree.isExternal(child));
        NodeRef grandchild = tree.getChild(child, 0);
        double rootHeight = tree.getNodeHeight(oldRoot);
        double lower = Math.max(tree.getNodeHeight(sibling), tree.getNodeHeight(grandchild));

        tree.beginTreeEdit();
        tree.removeChild(oldRoot, child);
        tree.removeChild(child, grandchild);
        tree.addChild(oldRoot, grandchild);
        tree.addChild(child, oldRoot);
        tree.setNodeHeight(oldRoot, (rootHeight + lower) / 2.0);
        tree.setNodeHeight(child, rootHeight + 1.0);
        tree.setRoot(child);
        tree.endTreeEdit();
    }

    private static double[] heights(DefaultTreeModel tree) {
        double[] heights = new double[tree.getNodeCount()];
        for (int index = 0; index < heights.length; index++) {
            heights[index] = tree.getNodeHeight(tree.getNode(index));
        }
        return heights;
    }

    private static Fixture fixture() throws Exception {
        MathUtils.setSeed(12345);
        BeastXState state = new PhyloSpecRunner("""
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa(data))
                """).buildState("rootHeightBinding");
        DefaultTreeModel tree = assertInstanceOf(
                DefaultTreeModel.class, state.treeModelsByPhyloSpecName.get("tree"));
        ScaleOperator operator = new OperatorBuilder().build(state).stream()
                .filter(ScaleOperator.class::isInstance)
                .map(ScaleOperator.class::cast)
                .filter(candidate -> "tree.rootHeight".equals(candidate.getVariable().getId()))
                .findFirst().orElseThrow();
        return new Fixture(state, tree, operator);
    }

    private record Fixture(BeastXState state, DefaultTreeModel tree, ScaleOperator operator) {}
}
