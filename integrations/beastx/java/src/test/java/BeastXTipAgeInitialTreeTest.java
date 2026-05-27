import dr.evomodel.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXTipAgeInitialTreeTest {

    @Test
    public void parsedNexusAgesSetInitialTreeTipHeights() throws Exception {
        String source = """
                Alignment data = fromNexus(
                    file="src/test/java/resources/dated-simple.nex",
                    age=parse(regex=".*_(\\d+(?:\\.\\d+)?)$")
                )
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                """;

        BeastXModel model =
                new PhyloSpecRunner(source).buildModel("test");

        DefaultTreeModel tree =
                (DefaultTreeModel) model.beastState.treeModelsByPhyloSpecName.get("tree");

        assertEquals(4, tree.getExternalNodeCount());

        double maxTipHeight =
                0.0;

        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            double tipHeight =
                    tree.getNodeHeight(tree.getExternalNode(i));

            maxTipHeight =
                    Math.max(maxTipHeight, tipHeight);
        }

        assertEquals(3.0, maxTipHeight);
        assertTrue(tree.getNodeHeight(tree.getRoot()) > maxTipHeight);
    }
}