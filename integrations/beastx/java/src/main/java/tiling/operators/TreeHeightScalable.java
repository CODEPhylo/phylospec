package tiling.operators;

import dr.evolution.tree.NodeRef;
import dr.evomodel.tree.TreeModel;
import dr.inference.operators.Scalable;

public class TreeHeightScalable implements Scalable {

    private final TreeModel treeModel;

    public TreeHeightScalable(TreeModel treeModel) {
        this.treeModel =
                treeModel;
    }

    @Override
    public int scale(double scaleFactor, int nDims, boolean testBounds) {
        NodeRef[] nodes =
                this.treeModel.getNodes();

        double[] oldHeights =
                new double[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            oldHeights[i] =
                    this.treeModel.getNodeHeight(nodes[i]);
        }

        int scaledNodeCount =
                0;

        this.treeModel.beginTreeEdit();

        for (NodeRef node : nodes) {
            if (!this.treeModel.isExternal(node)) {
                double oldHeight =
                        this.treeModel.getNodeHeight(node);

                this.treeModel.setNodeHeight(
                        node,
                        oldHeight * scaleFactor
                );

                scaledNodeCount++;
            }
        }

        this.treeModel.endTreeEdit();

        if (testBounds && !testBounds()) {
            restoreHeights(nodes, oldHeights);
            return 0;
        }

        return scaledNodeCount;
    }

    @Override
    public boolean testBounds() {
        for (NodeRef node : this.treeModel.getNodes()) {
            double nodeHeight =
                    this.treeModel.getNodeHeight(node);

            if (nodeHeight < 0.0) {
                return false;
            }

            if (!this.treeModel.isRoot(node)) {
                NodeRef parent =
                        this.treeModel.getParent(node);

                double parentHeight =
                        this.treeModel.getNodeHeight(parent);

                if (nodeHeight > parentHeight) {
                    return false;
                }
            }

            for (int i = 0; i < this.treeModel.getChildCount(node); i++) {
                NodeRef child =
                        this.treeModel.getChild(node, i);

                double childHeight =
                        this.treeModel.getNodeHeight(child);

                if (childHeight > nodeHeight) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public String getName() {
        return this.treeModel.getId();
    }

    private void restoreHeights(NodeRef[] nodes, double[] oldHeights) {
        this.treeModel.beginTreeEdit();

        for (int i = 0; i < nodes.length; i++) {
            this.treeModel.setNodeHeight(
                    nodes[i],
                    oldHeights[i]
            );
        }

        this.treeModel.endTreeEdit();
    }
}
