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
        DELTA_EXCHANGE,
        INTEGER_RANDOM_WALK,
        INTEGER_SWAP,
        INTEGER_UNIFORM,
        TREE_NODE_HEIGHT_SCALE,
        TREE_ROOT_SCALE,
        TREE_UNIFORM_HEIGHT,
        TREE_RANDOM_WALK_HEIGHT,
        TREE_SUBTREE_SLIDE,
        TREE_NARROW_EXCHANGE,
        TREE_WIDE_EXCHANGE,
        TREE_WILSON_BALDING,
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
