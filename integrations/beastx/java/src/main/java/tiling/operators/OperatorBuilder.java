package tiling.operators;

import dr.evomodel.operators.FixedHeightSubtreePruneRegraftOperator;
import dr.evomodel.operators.SubtreeLeapOperator;
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

import java.util.Arrays;
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
            case RANDOM_WALK_LOGIT -> new RandomWalkOperator(
                    spec.parameter(), spec.tuning(),
                    RandomWalkOperator.BoundaryCondition.logit,
                    spec.weight(), AdaptationMode.DEFAULT);
            case DELTA_EXCHANGE -> deltaExchangeOperator(spec);
            case INTEGER_RANDOM_WALK -> new RandomWalkIntegerOperator(
                    spec.parameter(), (int) spec.tuning(), spec.weight());
            case INTEGER_SWAP -> swapOperator(spec);
            case INTEGER_UNIFORM -> uniformIntegerOperator(spec);
            case BIT_FLIP -> new BitFlipOperator(spec.parameter(), spec.weight(), false);
            case TREE_SUBTREE_LEAP -> new SubtreeLeapOperator(
                    spec.tree(), spec.weight(), spec.tuning(),
                    SubtreeLeapOperator.DistanceKernelType.NORMAL,
                    AdaptationMode.DEFAULT, 0.234);
            case TREE_FIXED_HEIGHT_SPR ->
                    new FixedHeightSubtreePruneRegraftOperator(spec.tree(), spec.weight());
            case TREE_CLOCK_UP_DOWN -> treeClockUpDownOperator(spec);
        };
    }

    private SwapOperator swapOperator(OperatorSpec spec) {
        SwapOperator operator = new SwapOperator(spec.parameter(), (int) spec.tuning());
        operator.setWeight(spec.weight());
        return operator;
    }

    private DeltaExchangeOperator deltaExchangeOperator(OperatorSpec spec) {
        int[] parameterWeights = new int[spec.parameter().getDimension()];
        Arrays.fill(parameterWeights, 1);
        return new DeltaExchangeOperator(
                spec.parameter(), parameterWeights, spec.tuning(), spec.weight(), false,
                AdaptationMode.DEFAULT);
    }

    private UniformIntegerOperator uniformIntegerOperator(OperatorSpec spec) {
        Bounds<Double> bounds = requiredBounds(spec.parameter());
        int lower = (int) Math.ceil(bounds.getLowerLimit(0));
        int upper = (int) Math.floor(bounds.getUpperLimit(0));
        return new UniformIntegerOperator(
                spec.parameter(), lower, upper, spec.weight(), (int) spec.tuning());
    }

    private UpDownOperator treeClockUpDownOperator(OperatorSpec spec) {
        DefaultTreeModel tree = defaultTree(spec);
        Parameter nodeHeights = tree.createNodeHeightsParameter(true, true, false);
        nodeHeights.setId(tree.getId() + ".allInternalNodeHeights");
        return new UpDownOperator(
                new Scalable[] {new Scalable.Default(nodeHeights)},
                new Scalable[] {new Scalable.Default(spec.parameter())},
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
            case RANDOM_WALK_LOGIT -> "RandomWalkOperator(parameter=%s, weight=%s, windowSize=%s, boundary=logit)"
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
            case TREE_SUBTREE_LEAP -> "SubtreeLeapOperator(tree=%s, weight=%s, size=%s)"
                    .formatted(tree, spec.weight(), spec.tuning());
            case TREE_FIXED_HEIGHT_SPR -> "FixedHeightSubtreePruneRegraftOperator(tree=%s, weight=%s)"
                    .formatted(tree, spec.weight());
            case TREE_CLOCK_UP_DOWN -> "UpDownOperator(up=[%s.allInternalNodeHeights], down=[%s], weight=%s, scaleFactor=%s)"
                    .formatted(tree, parameter, spec.weight(), spec.tuning());
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
