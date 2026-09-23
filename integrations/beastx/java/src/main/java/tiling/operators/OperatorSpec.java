package tiling.operators;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;

/** Describes one selected BEAST X operator independently of its output form. */
public record OperatorSpec(
        Family family,
        Parameter parameter,
        TreeModel tree,
        double weight,
        double tuning
) {
    public enum Family {
        SCALE,
        RANDOM_WALK,
        RANDOM_WALK_LOGIT,
        DELTA_EXCHANGE,
        INTEGER_RANDOM_WALK,
        INTEGER_SWAP,
        INTEGER_UNIFORM,
        BIT_FLIP,
        TREE_SUBTREE_LEAP,
        TREE_FIXED_HEIGHT_SPR,
        TREE_CLOCK_UP_DOWN
    }

    public OperatorSpec {
        if (family == null) {
            throw new IllegalArgumentException("family must not be null.");
        }
        if (weight < 0.0) {
            throw new IllegalArgumentException("weight must not be negative.");
        }
    }
}
