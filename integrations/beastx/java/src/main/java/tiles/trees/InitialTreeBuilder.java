package tiles.trees;

import dr.evolution.tree.SimpleNode;
import dr.evolution.tree.SimpleTree;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.types.RealScalar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

final class InitialTreeBuilder {

    private static final double DEFAULT_INTERNAL_NODE_SPACING =
            1.0;

    private static final double COALESCENT_INITIAL_POPULATION_SCALE =
            2.0;

    private static final long COALESCENT_INITIAL_TREE_SEED =
            1L;

    private InitialTreeBuilder() {
    }

    static SimpleTree balancedTree(
            Taxa taxa,
            String modelName
    ) {
        return balancedTree(taxa, modelName, null);
    }

    static SimpleTree balancedTree(
            Taxa taxa,
            String modelName,
            RealScalar<? extends NonNegativeReal> rootAge
    ) {
        if (taxa.getTaxonCount() < 2) {
            throw new IllegalArgumentException(modelName + " requires at least two taxa.");
        }

        SimpleNode root =
                buildBalancedSubtree(
                        taxa,
                        0,
                        taxa.getTaxonCount(),
                        DEFAULT_INTERNAL_NODE_SPACING
                );

        double maxTipHeight =
                maxTipHeight(taxa);

        if (rootAge == null) {
            root.setHeight(Math.max(root.getHeight(), maxTipHeight + DEFAULT_INTERNAL_NODE_SPACING));
            return new SimpleTree(root);
        }

        double rootHeight =
                rootAge.get();

        if (rootHeight <= maxTipHeight) {
            throw new TileApplicationError(
                    "Root age must be greater than all tip ages.",
                    "Use a rootAge larger than the oldest sampled taxon age."
            );
        }

        root.setHeight(rootHeight);

        return new SimpleTree(root);
    }

    static SimpleTree coalescentTree(
            Taxa taxa,
            String modelName
    ) {
        if (taxa.getTaxonCount() < 2) {
            throw new IllegalArgumentException(modelName + " requires at least two taxa.");
        }

        List<SimpleNode> activeLineages =
                new ArrayList<>();

        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            Taxon taxon =
                    taxa.getTaxon(i);

            SimpleNode leaf =
                    new SimpleNode();

            leaf.setTaxon(taxon);
            leaf.setHeight(Math.max(0.0, taxon.getHeight()));
            activeLineages.add(leaf);
        }

        Random random =
                new Random(COALESCENT_INITIAL_TREE_SEED);

        activeLineages.sort(Comparator.comparingDouble(SimpleNode::getHeight));

        while (activeLineages.size() > 1) {
            int leftIndex =
                    random.nextInt(activeLineages.size());

            SimpleNode left =
                    activeLineages.remove(leftIndex);

            int rightIndex =
                    random.nextInt(activeLineages.size());

            SimpleNode right =
                    activeLineages.remove(rightIndex);

            int lineageCount =
                    activeLineages.size() + 2;

            double waitingTime =
                    expectedCoalescentWaitingTime(lineageCount);

            SimpleNode parent =
                    new SimpleNode();

            parent.addChild(left);
            parent.addChild(right);
            parent.setHeight(
                    Math.max(left.getHeight(), right.getHeight()) + waitingTime
            );

            activeLineages.add(parent);
            activeLineages.sort(Comparator.comparingDouble(SimpleNode::getHeight));
        }

        return new SimpleTree(activeLineages.get(0));
    }

    private static double expectedCoalescentWaitingTime(int lineageCount) {
        return 2.0 * COALESCENT_INITIAL_POPULATION_SCALE
                / (lineageCount * (lineageCount - 1));
    }

    private static SimpleNode buildBalancedSubtree(
            Taxa taxa,
            int from,
            int to,
            double internalNodeSpacing
    ) {
        if (to - from == 1) {
            Taxon taxon =
                    taxa.getTaxon(from);

            SimpleNode leaf =
                    new SimpleNode();

            leaf.setTaxon(taxon);
            leaf.setHeight(Math.max(0.0, taxon.getHeight()));

            return leaf;
        }

        int mid =
                from + (to - from) / 2;

        SimpleNode left =
                buildBalancedSubtree(
                        taxa,
                        from,
                        mid,
                        internalNodeSpacing
                );

        SimpleNode right =
                buildBalancedSubtree(
                        taxa,
                        mid,
                        to,
                        internalNodeSpacing
                );

        SimpleNode parent =
                new SimpleNode();

        parent.addChild(left);
        parent.addChild(right);
        parent.setHeight(
                Math.max(left.getHeight(), right.getHeight()) + internalNodeSpacing
        );

        return parent;
    }

    private static double maxTipHeight(Taxa taxa) {
        double max =
                0.0;

        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            max =
                    Math.max(max, taxa.getTaxon(i).getHeight());
        }

        return max;
    }
}
