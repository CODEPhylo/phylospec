package tiling.operators;

import dr.evomodel.operators.ExchangeOperator;
import dr.evomodel.operators.NodeHeightScaleOperator;
import dr.evomodel.operators.SubtreeSlideOperator;
import dr.evomodel.operators.WilsonBalding;
import dr.evomodel.tree.DefaultTreeModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import dr.inference.operators.AdaptationMode;
import dr.inference.operators.DeltaExchangeOperator;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.RandomWalkOperator;
import dr.inference.operators.Scalable;
import dr.inference.operators.ScaleOperator;
import dr.inference.operators.UpDownOperator;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.TypeToken;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import org.phylospec.types.Simplex;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BeastXOperatorBuilder {

    private static final double PARAMETER_OPERATOR_WEIGHT = 1.0;
    private static final double PARAMETER_RANDOM_WALK_WINDOW_SIZE = 1.0;
    private static final double PARAMETER_SCALE_FACTOR = 0.75;

    private static final double TREE_SCALE_WEIGHT = 5.0;
    private static final double TREE_SUBTREE_SLIDE_SIZE = 15.0;
    private static final double TREE_SUBTREE_SLIDE_WEIGHT = 15.0;
    private static final double TREE_NARROW_EXCHANGE_WEIGHT = 15.0;
    private static final double TREE_WIDE_EXCHANGE_WEIGHT = 5.0;
    private static final double TREE_WILSON_BALDING_WEIGHT = 5.0;

    private static final double TREE_CLOCK_UP_DOWN_WEIGHT = 5.0;
    private static final double TREE_CLOCK_UP_DOWN_SCALE_FACTOR = 0.75;

    private static final TypeToken<?> SIMPLEX =
            new TypeToken<Simplex>() {};

    private static final TypeToken<?> POSITIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends PositiveReal>>() {};

    private static final TypeToken<?> POSITIVE_REAL_VECTOR =
            new TypeToken<RealVector<? extends PositiveReal>>() {};

    private static final TypeToken<?> NON_NEGATIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends NonNegativeReal>>() {};

    private static final TypeToken<?> UNIT_INTERVAL_REAL_SCALAR =
            new TypeToken<RealScalar<UnitInterval>>() {};

    public List<MCMCOperator> build(BeastXState beastState) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        operators.addAll(buildParameterOperators(beastState));
        operators.addAll(buildTreeOperators(beastState));
        operators.addAll(buildTreeClockJointOperators(beastState));

        return operators;
    }

    private List<MCMCOperator> buildParameterOperators(BeastXState beastState) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        for (Map.Entry<Parameter, TypeToken<?>> entry : beastState.stateNodes.entrySet()) {
            operators.add(buildParameterOperator(entry.getKey(), entry.getValue()));
        }

        return operators;
    }

    private List<MCMCOperator> buildTreeOperators(BeastXState beastState) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        for (TreeModel treeModel : beastState.treePriorDistributions.keySet()) {
            operators.addAll(buildDefaultTreeOperators(treeModel));
        }

        return operators;
    }

    private List<MCMCOperator> buildTreeClockJointOperators(BeastXState beastState) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        for (Map.Entry<TreeModel, List<Parameter>> entry : beastState.treeClockRateParameters.entrySet()) {
            TreeModel treeModel =
                    entry.getKey();

            if (!beastState.treePriorDistributions.containsKey(treeModel)) {
                continue;
            }

            for (Parameter clockRateParameter : entry.getValue()) {
                TypeToken<?> typeToken =
                        beastState.stateNodes.get(clockRateParameter);

                if (typeToken != null && POSITIVE_REAL_SCALAR.isAssignableFrom(typeToken)) {
                    operators.add(buildTreeClockUpDownOperator(treeModel, clockRateParameter));
                }
            }
        }

        return operators;
    }

    private MCMCOperator buildParameterOperator(Parameter parameter, TypeToken<?> typeToken) {
        if (SIMPLEX.isAssignableFrom(typeToken)) {
            return new DeltaExchangeOperator(parameter, PARAMETER_OPERATOR_WEIGHT);
        }

        if (POSITIVE_REAL_SCALAR.isAssignableFrom(typeToken)
                || POSITIVE_REAL_VECTOR.isAssignableFrom(typeToken)) {
            return new ScaleOperator(
                    parameter,
                    PARAMETER_SCALE_FACTOR,
                    AdaptationMode.DEFAULT,
                    PARAMETER_OPERATOR_WEIGHT
            );
        }

        return new RandomWalkOperator(
                parameter,
                PARAMETER_RANDOM_WALK_WINDOW_SIZE,
                RandomWalkOperator.BoundaryCondition.reflecting,
                PARAMETER_OPERATOR_WEIGHT,
                AdaptationMode.DEFAULT
        );
    }

    private List<MCMCOperator> buildDefaultTreeOperators(TreeModel treeModel) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        operators.add(buildTreeScaleOperator(treeModel));

        operators.add(new ExchangeOperator(
                ExchangeOperator.NARROW,
                treeModel,
                TREE_NARROW_EXCHANGE_WEIGHT
        ));

        operators.add(new ExchangeOperator(
                ExchangeOperator.WIDE,
                treeModel,
                TREE_WIDE_EXCHANGE_WEIGHT
        ));

        operators.add(new WilsonBalding(
                treeModel,
                TREE_WILSON_BALDING_WEIGHT
        ));

        if (treeModel instanceof DefaultTreeModel defaultTreeModel) {
            operators.add(new SubtreeSlideOperator(
                    defaultTreeModel,
                    TREE_SUBTREE_SLIDE_SIZE,
                    TREE_SUBTREE_SLIDE_WEIGHT,
                    true,
                    false,
                    false,
                    false,
                    AdaptationMode.DEFAULT,
                    0.234
            ));
        }

        return operators;
    }

    private MCMCOperator buildTreeScaleOperator(TreeModel treeModel) {
        NodeHeightScaleOperator operator =
                new NodeHeightScaleOperator(
                        treeModel,
                        PARAMETER_SCALE_FACTOR,
                        true,
                        AdaptationMode.DEFAULT
                );

        operator.setWeight(TREE_SCALE_WEIGHT);

        return operator;
    }

    private MCMCOperator buildTreeClockUpDownOperator(
            TreeModel treeModel,
            Parameter clockRateParameter
    ) {
        Scalable[] up =
                new Scalable[] {
                        new Scalable.Default(clockRateParameter)
                };

        Scalable[] down =
                new Scalable[] {
                        new TreeHeightScalable(treeModel)
                };

        return new UpDownOperator(
                up,
                down,
                TREE_CLOCK_UP_DOWN_SCALE_FACTOR,
                TREE_CLOCK_UP_DOWN_WEIGHT,
                AdaptationMode.DEFAULT
        );
    }
}