package tiling.operators;

import dr.evomodel.operators.ExchangeOperator;
import dr.evomodel.operators.RandomWalkNodeHeightOperator;
import dr.evomodel.operators.SubtreeSlideOperator;
import dr.evomodel.operators.UniformNodeHeightOperator;
import dr.evomodel.operators.WilsonBalding;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import dr.inference.operators.AdaptationMode;
import dr.inference.operators.BitFlipOperator;
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
        return selected(state).stream().map(this::build).toList();
    }

    public List<String> summarize(BeastXState state) {
        return selected(state).stream().map(this::summarize).toList();
    }

    private List<OperatorSpec> selected(BeastXState state) {
        return new OperatorSelector().select(state).stream()
                .filter(spec -> spec.weight() > 0.0)
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
            case BIT_FLIP -> new BitFlipOperator(spec.parameter(), spec.weight(), false);
            case TREE_NODE_HEIGHT_SCALE -> nodeHeightScaleOperator(spec);
            case TREE_ROOT_SCALE -> rootScaleOperator(spec);
            case TREE_UNIFORM_HEIGHT ->
                    new UniformNodeHeightOperator(spec.tree(), spec.weight());
            case TREE_RANDOM_WALK_HEIGHT -> new RandomWalkNodeHeightOperator(
                    spec.tree(), spec.weight(), spec.tuning(),
                    AdaptationMode.DEFAULT, 0.234);
            case TREE_SUBTREE_SLIDE -> subtreeSlideOperator(spec);
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
        Bounds<Double> bounds = requiredBounds(spec.parameter());
        int lower = (int) Math.ceil(bounds.getLowerLimit(0));
        int upper = (int) Math.floor(bounds.getUpperLimit(0));
        return new UniformIntegerOperator(
                spec.parameter(), lower, upper, spec.weight(), (int) spec.tuning());
    }

    private ScaleOperator nodeHeightScaleOperator(OperatorSpec spec) {
        DefaultTreeModel tree = defaultTree(spec);
        Parameter nodeHeights = tree.createNodeHeightsParameter(true, true, false);
        nodeHeights.setId(tree.getId() + ".allInternalNodeHeights");
        return new ScaleOperator(
                nodeHeights,
                true,
                0,
                spec.tuning(),
                AdaptationMode.DEFAULT,
                spec.weight(),
                null,
                1.0,
                false);
    }

    private ScaleOperator rootScaleOperator(OperatorSpec spec) {
        DefaultTreeModel tree = defaultTree(spec);
        Parameter rootHeight = tree.createNodeHeightsParameter(false, true, false);
        rootHeight.setId(tree.getId() + ".rootHeight");
        return new ScaleOperator(
                rootHeight, spec.tuning(), AdaptationMode.DEFAULT, spec.weight());
    }

    private SubtreeSlideOperator subtreeSlideOperator(OperatorSpec spec) {
        return new SubtreeSlideOperator(
                defaultTree(spec), spec.tuning(), spec.weight(),
                true, false, false, false, AdaptationMode.DEFAULT, 0.234);
    }

    private UpDownOperator treeClockUpDownOperator(OperatorSpec spec) {
        DefaultTreeModel tree = defaultTree(spec);
        Parameter nodeHeights = tree.createNodeHeightsParameter(true, true, false);
        nodeHeights.setId(tree.getId() + ".allInternalNodeHeights");
        return new UpDownOperator(
                new Scalable[] {new Scalable.Default(spec.parameter())},
                new Scalable[] {new Scalable.Default(nodeHeights)},
                spec.tuning(), spec.weight(), AdaptationMode.DEFAULT);
    }

    private String summarize(OperatorSpec spec) {
        String parameter = spec.parameter() == null ? "" : parameterId(spec.parameter());
        String tree = spec.tree() == null ? "" : treeId(spec);
        return switch (spec.family()) {
            case SCALE -> "ScaleOperator(parameter=%s, weight=%s, scaleFactor=%s)"
                    .formatted(parameter, spec.weight(), spec.tuning());
            case RANDOM_WALK -> "RandomWalkOperator(parameter=%s, weight=%s, windowSize=%s, boundary=reflecting)"
                    .formatted(parameter, spec.weight(), spec.tuning());
            case DELTA_EXCHANGE -> "DeltaExchangeOperator(parameter=%s, weight=%s)"
                    .formatted(parameter, spec.weight());
            case INTEGER_RANDOM_WALK -> "RandomWalkIntegerOperator(parameter=%s, weight=%s, windowSize=%d)"
                    .formatted(parameter, spec.weight(), (int) spec.tuning());
            case INTEGER_SWAP -> "SwapOperator(parameter=%s, weight=%s, size=%d)"
                    .formatted(parameter, spec.weight(), (int) spec.tuning());
            case INTEGER_UNIFORM -> integerUniformSummary(spec, parameter);
            case BIT_FLIP -> "BitFlipOperator(parameter=%s, weight=%s)"
                    .formatted(parameter, spec.weight());
            case TREE_NODE_HEIGHT_SCALE -> "ScaleOperator(treeNodeHeights=%s.allInternalNodeHeights, weight=%s, scaleFactor=%s, scaleAll=true)"
                    .formatted(tree, spec.weight(), spec.tuning());
            case TREE_ROOT_SCALE -> "ScaleOperator(treeRoot=%s.rootHeight, weight=%s, scaleFactor=%s)"
                    .formatted(tree, spec.weight(), spec.tuning());
            case TREE_UNIFORM_HEIGHT -> "UniformNodeHeightOperator(tree=%s, weight=%s)"
                    .formatted(tree, spec.weight());
            case TREE_RANDOM_WALK_HEIGHT -> "RandomWalkNodeHeightOperator(tree=%s, weight=%s, size=%s)"
                    .formatted(tree, spec.weight(), spec.tuning());
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

    private String integerUniformSummary(OperatorSpec spec, String parameter) {
        Bounds<Double> bounds = requiredBounds(spec.parameter());
        return "UniformIntegerOperator(parameter=%s, weight=%s, count=%d, lower=%d, upper=%d)"
                .formatted(parameter, spec.weight(), (int) spec.tuning(),
                        (int) Math.ceil(bounds.getLowerLimit(0)),
                        (int) Math.floor(bounds.getUpperLimit(0)));
    }

    private static Bounds<Double> requiredBounds(Parameter parameter) {
        Bounds<Double> bounds = parameter.getBounds();
        if (bounds == null) {
            throw new IllegalArgumentException("Integer uniform operators require bounds.");
        }
        return bounds;
    }

    private static DefaultTreeModel defaultTree(OperatorSpec spec) {
        if (spec.tree() instanceof DefaultTreeModel tree) {
            return tree;
        }
        throw new IllegalArgumentException("Operator requires a DefaultTreeModel.");
    }

    private static String parameterId(Parameter parameter) {
        return parameter.getId() == null ? "" : parameter.getId();
    }

    private static String treeId(OperatorSpec spec) {
        return spec.tree().getId() == null ? "" : spec.tree().getId();
    }
}
