package tiling.operators;

import dr.evomodel.operators.ExchangeOperator;
import dr.evomodel.operators.SubtreeSlideOperator;
import dr.evomodel.operators.UniformNodeHeightOperator;
import dr.evomodel.operators.WilsonBalding;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import dr.inference.operators.AdaptationMode;
import dr.inference.operators.DeltaExchangeOperator;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.RandomWalkIntegerOperator;
import dr.inference.operators.RandomWalkOperator;
import dr.inference.operators.Scalable;
import dr.inference.operators.ScaleOperator;
import dr.inference.operators.SwapOperator;
import dr.inference.operators.UniformIntegerOperator;
import dr.inference.operators.UpDownOperator;
import tiling.BeastXState;

import java.util.List;

/** Materializes selected operator specifications for direct BEAST X execution. */
public final class OperatorBuilder {

    public List<MCMCOperator> build(BeastXState state) {
        return new OperatorSelector().select(state).stream()
                .filter(spec -> spec.weight() > 0.0)
                .map(this::build)
                .toList();
    }

    public List<String> summarize(BeastXState state) {
        return new OperatorSelector().select(state).stream()
                .filter(spec -> spec.weight() > 0.0)
                .map(this::summarize)
                .toList();
    }

    private MCMCOperator build(OperatorSpec spec) {
        return switch (spec.family()) {
            case SCALE -> new ScaleOperator(
                    spec.parameter(), spec.tuning(), AdaptationMode.DEFAULT, spec.weight());
            case RANDOM_WALK -> new RandomWalkOperator(
                    spec.parameter(), spec.tuning(),
                    RandomWalkOperator.BoundaryCondition.reflecting,
                    spec.weight(), AdaptationMode.DEFAULT);
            case DELTA_EXCHANGE -> new DeltaExchangeOperator(
                    spec.parameter(), null, spec.tuning(), spec.weight(), false,
                    AdaptationMode.DEFAULT);
            case INTEGER_RANDOM_WALK -> new RandomWalkIntegerOperator(
                    spec.parameter(), (int) spec.tuning(), spec.weight());
            case INTEGER_SWAP -> swapOperator(spec);
            case INTEGER_UNIFORM -> uniformIntegerOperator(spec);
            case TREE_SCALE -> treeScaleOperator(spec, false);
            case TREE_ROOT_SCALE -> treeScaleOperator(spec, true);
            case TREE_UNIFORM_HEIGHT -> new UniformNodeHeightOperator(spec.tree(), spec.weight());
            case TREE_SUBTREE_SLIDE -> new SubtreeSlideOperator(
                    defaultTree(spec), spec.tuning(), spec.weight(),
                    true, false, false, false, AdaptationMode.DEFAULT, 0.234);
            case TREE_NARROW_EXCHANGE -> new ExchangeOperator(
                    ExchangeOperator.NARROW, spec.tree(), spec.weight());
            case TREE_WIDE_EXCHANGE -> new ExchangeOperator(
                    ExchangeOperator.WIDE, spec.tree(), spec.weight());
            case TREE_WILSON_BALDING -> new WilsonBalding(spec.tree(), spec.weight());
            case TREE_CLOCK_UP_DOWN -> treeClockUpDownOperator(spec);
        };
    }

    private SwapOperator swapOperator(OperatorSpec spec) {
        SwapOperator operator = new SwapOperator(spec.parameter(), (int) spec.tuning());
        operator.setWeight(spec.weight());
        return operator;
    }

    private UniformIntegerOperator uniformIntegerOperator(OperatorSpec spec) {
        Bounds<Double> bounds = spec.parameter().getBounds();
        int lower = (int) Math.ceil(bounds.getLowerLimit(0));
        int upper = (int) Math.floor(bounds.getUpperLimit(0));
        return new UniformIntegerOperator(
                spec.parameter(), lower, upper, spec.weight(), (int) spec.tuning());
    }

    private ScaleOperator treeScaleOperator(OperatorSpec spec, boolean rootOnly) {
        DefaultTreeModel tree = defaultTree(spec);
        Parameter heights = rootOnly
                ? tree.getRootHeightParameter()
                : tree.createNodeHeightsParameter(true, true, false);
        heights.setId(OperatorSelector.treeId(tree)
                + (rootOnly ? ".rootHeight" : ".allInternalNodeHeights"));

        return new ScaleOperator(
                heights, !rootOnly, 0, spec.tuning(), AdaptationMode.DEFAULT,
                spec.weight(), null, 1.0, false);
    }

    private UpDownOperator treeClockUpDownOperator(OperatorSpec spec) {
        DefaultTreeModel tree = defaultTree(spec);
        Parameter heights = tree.createNodeHeightsParameter(true, true, false);
        heights.setId(OperatorSelector.treeId(tree) + ".allInternalNodeHeights");

        return new UpDownOperator(
                new Scalable[]{new Scalable.Default(spec.parameter())},
                new Scalable[]{new Scalable.Default(heights)},
                spec.tuning(), spec.weight(), AdaptationMode.DEFAULT);
    }

    private DefaultTreeModel defaultTree(OperatorSpec spec) {
        if (spec.tree() instanceof DefaultTreeModel tree) {
            return tree;
        }
        throw new IllegalArgumentException(spec.family() + " requires a DefaultTreeModel.");
    }

    private String summarize(OperatorSpec spec) {
        String parameter = spec.parameter() == null
                ? "" : OperatorSelector.parameterId(spec.parameter());
        String tree = spec.tree() == null ? "" : OperatorSelector.treeId(spec.tree());
        return switch (spec.family()) {
            case SCALE -> "ScaleOperator(parameter=%s, weight=%s, scaleFactor=%s)"
                    .formatted(parameter, spec.weight(), spec.tuning());
            case RANDOM_WALK -> "RandomWalkOperator(parameter=%s, weight=%s, windowSize=%s, boundary=reflecting)"
                    .formatted(parameter, spec.weight(), spec.tuning());
            case DELTA_EXCHANGE -> "DeltaExchangeOperator(parameter=%s, weight=%s, delta=%s)"
                    .formatted(parameter, spec.weight(), spec.tuning());
            case INTEGER_RANDOM_WALK -> "RandomWalkIntegerOperator(parameter=%s, weight=%s, windowSize=%s)"
                    .formatted(parameter, spec.weight(), (int) spec.tuning());
            case INTEGER_SWAP -> "SwapOperator(parameter=%s, weight=%s, size=%s)"
                    .formatted(parameter, spec.weight(), (int) spec.tuning());
            case INTEGER_UNIFORM -> "UniformIntegerOperator(parameter=%s, weight=%s, count=%s)"
                    .formatted(parameter, spec.weight(), (int) spec.tuning());
            case TREE_SCALE -> "ScaleTreeOperator(tree=%s, weight=%s, scaleFactor=%s)"
                    .formatted(tree, spec.weight(), spec.tuning());
            case TREE_ROOT_SCALE -> "ScaleRootOperator(tree=%s, weight=%s, scaleFactor=%s)"
                    .formatted(tree, spec.weight(), spec.tuning());
            case TREE_UNIFORM_HEIGHT -> "UniformNodeHeightOperator(tree=%s, weight=%s)"
                    .formatted(tree, spec.weight());
            case TREE_SUBTREE_SLIDE -> "SubtreeSlideOperator(tree=%s, weight=%s, size=%s)"
                    .formatted(tree, spec.weight(), spec.tuning());
            case TREE_NARROW_EXCHANGE -> "ExchangeOperator(tree=%s, mode=narrow, weight=%s)"
                    .formatted(tree, spec.weight());
            case TREE_WIDE_EXCHANGE -> "ExchangeOperator(tree=%s, mode=wide, weight=%s)"
                    .formatted(tree, spec.weight());
            case TREE_WILSON_BALDING -> "WilsonBalding(tree=%s, weight=%s)"
                    .formatted(tree, spec.weight());
            case TREE_CLOCK_UP_DOWN -> "UpDownOperator(up=[%s], down=[%s.allInternalNodeHeights], weight=%s, scaleFactor=%s)"
                    .formatted(parameter, tree, spec.weight(), spec.tuning());
        };
    }
}
