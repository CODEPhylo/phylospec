package tiling;

import beast.base.evolution.tree.TreeInterface;
import beast.base.evolution.tree.TreeUtils;

/** Native birth-death likelihood with dependency tracking for derived rate inputs. */
public final class LiveBirthDeathModel extends LiveBirthDeathGernhard08Model {

    @Override
    public void initAndValidate() {
        requireContemporaneousTips(treeInput.get());
        super.initAndValidate();
    }

    @Override
    public double calculateTreeLogLikelihood(TreeInterface tree) {
        requireContemporaneousTips(tree);
        return super.calculateTreeLogLikelihood(tree);
    }

    private static void requireContemporaneousTips(TreeInterface tree) {
        // Decimal Newick lengths can leave rounding-sized residual tip heights after conversion.
        // Scale the tolerance to the tree's units; do not rescale or alter the supplied dates.
        double tolerance = 8.0 * Math.ulp(tree.getRoot().getHeight());
        if (!TreeUtils.isUltrametric(tree, tolerance)) {
            throw new IllegalArgumentException(
                    "BirthDeath(birthRate, deathRate) currently requires contemporaneous tips; "
                            + "the native Gernhard08 model does not support dated tips.");
        }
    }
}
