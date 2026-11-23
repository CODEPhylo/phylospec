import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;

import java.util.Arrays;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * JC69 log-likelihood of DNA sequences on a tree by Felsenstein pruning, written independently of
 * BEAST X to check its tree likelihood.
 */
final class JukesCantorPruning {

    /** A single site-rate category with rate 1. */
    static final double[][] ONE_CATEGORY = {{1.0}, {1.0}};

    private JukesCantorPruning() {
    }

    /**
     * @param sequences      sequence of each taxon id
     * @param branchRate     multiplier of each branch length (branch rate times partition rate)
     * @param siteCategories site-rate category rates and their weights
     */
    static double logLikelihood(Tree tree, Map<String, String> sequences, ToDoubleFunction<NodeRef> branchRate,
                                double[][] siteCategories) {
        double[] rates = siteCategories[0];
        double[] weights = siteCategories[1];
        int siteCount = sequences.values().iterator().next().length();
        double logLikelihood = 0.0;
        for (int site = 0; site < siteCount; site++) {
            double siteLikelihood = 0.0;
            for (int category = 0; category < rates.length; category++) {
                double[] partials = partials(tree, tree.getRoot(), sequences, site, rates[category], branchRate);
                siteLikelihood += weights[category] * 0.25 * Arrays.stream(partials).sum();
            }
            logLikelihood += Math.log(siteLikelihood);
        }
        return logLikelihood;
    }

    private static double[] partials(Tree tree, NodeRef node, Map<String, String> sequences, int site,
                                     double siteRate, ToDoubleFunction<NodeRef> branchRate) {
        double[] partials = new double[4];
        if (tree.isExternal(node)) {
            char state = sequences.get(tree.getNodeTaxon(node).getId()).charAt(site);
            partials["ACGT".indexOf(state)] = 1.0;
            return partials;
        }
        Arrays.fill(partials, 1.0);
        for (int i = 0; i < tree.getChildCount(node); i++) {
            NodeRef child = tree.getChild(node, i);
            double[] below = partials(tree, child, sequences, site, siteRate, branchRate);
            double distance = tree.getBranchLength(child) * branchRate.applyAsDouble(child) * siteRate;
            double decay = Math.exp(-4.0 * distance / 3.0);
            for (int from = 0; from < 4; from++) {
                double sum = 0.0;
                for (int to = 0; to < 4; to++) {
                    sum += (from == to ? 0.25 + 0.75 * decay : 0.25 - 0.25 * decay) * below[to];
                }
                partials[from] *= sum;
            }
        }
        return partials;
    }
}
