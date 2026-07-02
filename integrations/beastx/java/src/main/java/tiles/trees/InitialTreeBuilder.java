package tiles.trees;

import dr.evolution.tree.SimpleNode;
import dr.evolution.tree.SimpleTree;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.types.RealScalar;

final class InitialTreeBuilder {

    private static final double DEFAULT_INTERNAL_NODE_SPACING =
            1.0;

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
