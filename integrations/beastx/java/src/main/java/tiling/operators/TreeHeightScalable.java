package tiling.operators;

import dr.evolution.tree.NodeRef;
import dr.evomodel.tree.TreeModel;
import dr.inference.operators.Scalable;

import java.util.IdentityHashMap;
import java.util.Map;

public class TreeHeightScalable implements Scalable {

    private final TreeModel treeModel;

    public TreeHeightScalable(TreeModel treeModel) {
        this.treeModel =
                treeModel;
    }

    @Override
    public int scale(double scaleFactor, int nDims, boolean testBounds) {
        if (!Double.isFinite(scaleFactor) || scaleFactor <= 0.0) {
            return 0;
        }

        NodeRef[] nodes =
                this.treeModel.getNodes();

        Map<NodeRef, Double> oldHeights =
                new IdentityHashMap<>();

        for (NodeRef node : nodes) {
            oldHeights.put(
                    node,
                    this.treeModel.getNodeHeight(node)
            );
        }

        Map<NodeRef, Double> proposedHeights =
                new IdentityHashMap<>();

        for (NodeRef node : nodes) {
            proposedHeight(
                    node,
                    oldHeights,
                    proposedHeights,
                    scaleFactor
            );
        }

        if (!proposedHeightsAreValid(proposedHeights)) {
            return 0;
        }

        int scaledNodeCount =
                0;

        this.treeModel.beginTreeEdit();

        for (NodeRef node : nodes) {
            if (!this.treeModel.isExternal(node)) {
                this.treeModel.setNodeHeight(
                        node,
                        proposedHeights.get(node)
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

            if (!Double.isFinite(nodeHeight) || nodeHeight < 0.0) {
                return false;
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

    private double proposedHeight(
            NodeRef node,
            Map<NodeRef, Double> oldHeights,
            Map<NodeRef, Double> proposedHeights,
            double scaleFactor
    ) {
        Double cached =
                proposedHeights.get(node);

        if (cached != null) {
            return cached;
        }

        double oldHeight =
                oldHeights.get(node);

        if (this.treeModel.isExternal(node)) {
            proposedHeights.put(node, oldHeight);
            return oldHeight;
        }

        double oldChildMax =
                Double.NEGATIVE_INFINITY;

        double proposedChildMax =
                Double.NEGATIVE_INFINITY;

        for (int i = 0; i < this.treeModel.getChildCount(node); i++) {
            NodeRef child =
                    this.treeModel.getChild(node, i);

            oldChildMax =
                    Math.max(
                            oldChildMax,
                            oldHeights.get(child)
                    );

            proposedChildMax =
                    Math.max(
                            proposedChildMax,
                            proposedHeight(
                                    child,
                                    oldHeights,
                                    proposedHeights,
                                    scaleFactor
                            )
                    );
        }

        double oldExcessHeight =
                oldHeight - oldChildMax;

        if (oldExcessHeight < 0.0) {
            proposedHeights.put(node, Double.NaN);
            return Double.NaN;
        }

        double proposedHeight =
                proposedChildMax + oldExcessHeight * scaleFactor;

        proposedHeights.put(node, proposedHeight);

        return proposedHeight;
    }

    private boolean proposedHeightsAreValid(
            Map<NodeRef, Double> proposedHeights
    ) {
        for (NodeRef node : this.treeModel.getNodes()) {
            double nodeHeight =
                    proposedHeights.get(node);

            if (!Double.isFinite(nodeHeight) || nodeHeight < 0.0) {
                return false;
            }

            for (int i = 0; i < this.treeModel.getChildCount(node); i++) {
                NodeRef child =
                        this.treeModel.getChild(node, i);

                double childHeight =
                        proposedHeights.get(child);

                if (childHeight > nodeHeight) {
                    return false;
                }
            }
        }

        return true;
    }

    private void restoreHeights(
            NodeRef[] nodes,
            Map<NodeRef, Double> oldHeights
    ) {
        this.treeModel.beginTreeEdit();

        for (NodeRef node : nodes) {
            this.treeModel.setNodeHeight(
                    node,
                    oldHeights.get(node)
            );
        }

        this.treeModel.endTreeEdit();
    }
}