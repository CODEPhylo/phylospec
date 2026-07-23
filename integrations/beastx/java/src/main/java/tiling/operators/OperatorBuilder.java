package tiling.operators;

import dr.evomodel.operators.ExchangeOperator;
import dr.evomodel.operators.SubtreeSlideOperator;
import dr.evomodel.operators.UniformNodeHeightOperator;
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
import org.phylospec.tiling.TypeToken;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import org.phylospec.types.Simplex;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class OperatorBuilder {

    private static final TypeToken<?> SIMPLEX =
            new TypeToken<Simplex>() {};

    private static final TypeToken<?> POSITIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends PositiveReal>>() {};

    private static final TypeToken<?> POSITIVE_REAL_VECTOR =
            new TypeToken<RealVector<? extends PositiveReal>>() {};

    private static final TypeToken<?> NON_NEGATIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends NonNegativeReal>>() {};

    public List<MCMCOperator> build(BeastXState beastState) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        operators.addAll(buildParameterOperators(beastState));
        operators.addAll(buildTreeOperators(beastState));
        operators.addAll(buildTreeClockJointOperators(beastState));

        return operators;
    }

    public List<String> summarize(BeastXState beastState) {
        List<String> summaries =
                new ArrayList<>();

        summaries.addAll(summarizeParameterOperators(beastState));
        summaries.addAll(summarizeTreeOperators(beastState));
        summaries.addAll(summarizeTreeClockJointOperators(beastState));

        return summaries;
    }

    private List<MCMCOperator> buildParameterOperators(BeastXState beastState) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        List<Map.Entry<Parameter, TypeToken<?>>> entries =
                new ArrayList<>(beastState.stateNodes.entrySet());

        entries.sort(Comparator.comparing(entry -> parameterId(entry.getKey())));

        for (Map.Entry<Parameter, TypeToken<?>> entry : entries) {
            operators.add(buildParameterOperator(
                    entry.getKey(),
                    entry.getValue(),
                    beastState.operatorConfig
            ));
        }

        return operators;
    }

    private List<String> summarizeParameterOperators(BeastXState beastState) {
        List<String> summaries =
                new ArrayList<>();

        List<Map.Entry<Parameter, TypeToken<?>>> entries =
                new ArrayList<>(beastState.stateNodes.entrySet());

        entries.sort(Comparator.comparing(entry -> parameterId(entry.getKey())));

        for (Map.Entry<Parameter, TypeToken<?>> entry : entries) {
            summaries.add(summarizeParameterOperator(
                    entry.getKey(),
                    entry.getValue(),
                    beastState.operatorConfig
            ));
        }

        return summaries;
    }

    private List<MCMCOperator> buildTreeOperators(BeastXState beastState) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        List<TreeModel> treeModels =
                new ArrayList<>(beastState.treePriorDistributions.keySet());

        treeModels.sort(Comparator.comparing(OperatorBuilder::treeId));

        for (TreeModel treeModel : treeModels) {
            operators.addAll(buildDefaultTreeOperators(
                    treeModel,
                    beastState.operatorConfig
            ));
        }

        return operators;
    }

    private List<String> summarizeTreeOperators(BeastXState beastState) {
        List<String> summaries =
                new ArrayList<>();

        List<TreeModel> treeModels =
                new ArrayList<>(beastState.treePriorDistributions.keySet());

        treeModels.sort(Comparator.comparing(OperatorBuilder::treeId));

        for (TreeModel treeModel : treeModels) {
            summaries.addAll(summarizeDefaultTreeOperators(
                    treeModel,
                    beastState.operatorConfig
            ));
        }

        return summaries;
    }

    private List<MCMCOperator> buildTreeClockJointOperators(BeastXState beastState) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        if (beastState.operatorConfig.treeClockUpDownWeight <= 0.0) {
            return operators;
        }

        List<Map.Entry<TreeModel, List<Parameter>>> entries =
                new ArrayList<>(beastState.treeClockRateParameters.entrySet());

        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        for (Map.Entry<TreeModel, List<Parameter>> entry : entries) {
            TreeModel treeModel =
                    entry.getKey();

            if (!beastState.treePriorDistributions.containsKey(treeModel)) {
                continue;
            }

            List<Parameter> clockRateParameters =
                    new ArrayList<>(entry.getValue());

            clockRateParameters.sort(Comparator.comparing(OperatorBuilder::parameterId));

            for (Parameter clockRateParameter : clockRateParameters) {
                TypeToken<?> typeToken =
                        beastState.stateNodes.get(clockRateParameter);

                if (typeToken != null && POSITIVE_REAL_SCALAR.isAssignableFrom(typeToken)) {
                    operators.add(buildTreeClockUpDownOperator(
                            treeModel,
                            clockRateParameter,
                            beastState.operatorConfig
                    ));
                }
            }
        }

        return operators;
    }

    private List<String> summarizeTreeClockJointOperators(BeastXState beastState) {
        List<String> summaries =
                new ArrayList<>();

        if (beastState.operatorConfig.treeClockUpDownWeight <= 0.0) {
            return summaries;
        }

        List<Map.Entry<TreeModel, List<Parameter>>> entries =
                new ArrayList<>(beastState.treeClockRateParameters.entrySet());

        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        for (Map.Entry<TreeModel, List<Parameter>> entry : entries) {
            TreeModel treeModel =
                    entry.getKey();

            if (!beastState.treePriorDistributions.containsKey(treeModel)) {
                continue;
            }

            List<Parameter> clockRateParameters =
                    new ArrayList<>(entry.getValue());

            clockRateParameters.sort(Comparator.comparing(OperatorBuilder::parameterId));

            for (Parameter clockRateParameter : clockRateParameters) {
                TypeToken<?> typeToken =
                        beastState.stateNodes.get(clockRateParameter);

                if (typeToken != null && POSITIVE_REAL_SCALAR.isAssignableFrom(typeToken)) {
                    summaries.add(summarizeTreeClockUpDownOperator(
                            treeModel,
                            clockRateParameter,
                            beastState.operatorConfig
                    ));
                }
            }
        }

        return summaries;
    }

    private MCMCOperator buildParameterOperator(
            Parameter parameter,
            TypeToken<?> typeToken,
            BeastXState.OperatorConfig config
    ) {
        if (SIMPLEX.isAssignableFrom(typeToken)) {
            return new DeltaExchangeOperator(
                    parameter,
                    config.parameterOperatorWeight
            );
        }

        if (POSITIVE_REAL_SCALAR.isAssignableFrom(typeToken)
                || POSITIVE_REAL_VECTOR.isAssignableFrom(typeToken)) {
            return new ScaleOperator(
                    parameter,
                    config.parameterScaleFactor,
                    AdaptationMode.DEFAULT,
                    config.parameterOperatorWeight
            );
        }

        return new RandomWalkOperator(
                parameter,
                config.randomWalkWindowSize,
                RandomWalkOperator.BoundaryCondition.reflecting,
                config.parameterOperatorWeight,
                AdaptationMode.DEFAULT
        );
    }

    private String summarizeParameterOperator(
            Parameter parameter,
            TypeToken<?> typeToken,
            BeastXState.OperatorConfig config
    ) {
        if (SIMPLEX.isAssignableFrom(typeToken)) {
            return "DeltaExchangeOperator(parameter=%s, weight=%s)".formatted(
                    parameter.getId(),
                    format(config.parameterOperatorWeight)
            );
        }

        if (POSITIVE_REAL_SCALAR.isAssignableFrom(typeToken)
                || POSITIVE_REAL_VECTOR.isAssignableFrom(typeToken)) {
            return "ScaleOperator(parameter=%s, weight=%s, scaleFactor=%s)".formatted(
                    parameter.getId(),
                    format(config.parameterOperatorWeight),
                    format(config.parameterScaleFactor)
            );
        }

        return "RandomWalkOperator(parameter=%s, weight=%s, windowSize=%s, boundary=reflecting)".formatted(
                parameter.getId(),
                format(config.parameterOperatorWeight),
                format(config.randomWalkWindowSize)
        );
    }

    private List<MCMCOperator> buildDefaultTreeOperators(
            TreeModel treeModel,
            BeastXState.OperatorConfig config
    ) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        operators.add(new UniformNodeHeightOperator(
                treeModel,
                config.treeNodeHeightWeight
        ));

        operators.add(new ExchangeOperator(
                ExchangeOperator.NARROW,
                treeModel,
                config.treeNarrowExchangeWeight
        ));

        operators.add(new ExchangeOperator(
                ExchangeOperator.WIDE,
                treeModel,
                config.treeWideExchangeWeight
        ));

        if (treeModel instanceof DefaultTreeModel defaultTreeModel) {
            operators.add(new SubtreeSlideOperator(
                    defaultTreeModel,
                    config.treeSubtreeSlideSize,
                    config.treeSubtreeSlideWeight,
                    true,
                    false,
                    false,
                    false,
                    AdaptationMode.DEFAULT,
                    0.234
            ));
        }

        operators.add(new WilsonBalding(
                treeModel,
                config.treeWilsonBaldingWeight
        ));

        return operators;
    }

    private List<String> summarizeDefaultTreeOperators(
            TreeModel treeModel,
            BeastXState.OperatorConfig config
    ) {
        List<String> summaries =
                new ArrayList<>();

        summaries.add("UniformNodeHeightOperator(tree=%s, weight=%s)".formatted(
                treeModel.getId(),
                format(config.treeNodeHeightWeight)
        ));

        summaries.add("ExchangeOperator(tree=%s, mode=narrow, weight=%s)".formatted(
                treeModel.getId(),
                format(config.treeNarrowExchangeWeight)
        ));

        summaries.add("ExchangeOperator(tree=%s, mode=wide, weight=%s)".formatted(
                treeModel.getId(),
                format(config.treeWideExchangeWeight)
        ));

        if (treeModel instanceof DefaultTreeModel) {
            summaries.add("SubtreeSlideOperator(tree=%s, weight=%s, size=%s)".formatted(
                    treeModel.getId(),
                    format(config.treeSubtreeSlideWeight),
                    format(config.treeSubtreeSlideSize)
            ));
        }

        summaries.add("WilsonBalding(tree=%s, weight=%s)".formatted(
                treeModel.getId(),
                format(config.treeWilsonBaldingWeight)
        ));

        return summaries;
    }

    private MCMCOperator buildTreeClockUpDownOperator(
            TreeModel treeModel,
            Parameter clockRateParameter,
            BeastXState.OperatorConfig config
    ) {
        if (!(treeModel instanceof DefaultTreeModel defaultTreeModel)) {
            throw new IllegalArgumentException(
                    "Tree-clock up/down operators require a DefaultTreeModel."
            );
        }

        Scalable[] up =
                new Scalable[] {
                        new Scalable.Default(clockRateParameter)
                };

        Parameter allInternalNodeHeights =
                defaultTreeModel.createNodeHeightsParameter(true, true, false);
        allInternalNodeHeights.setId(treeModel.getId() + ".allInternalNodeHeights");

        Scalable[] down =
                new Scalable[] {
                        new Scalable.Default(allInternalNodeHeights)
                };

        return new UpDownOperator(
                up,
                down,
                config.treeClockUpDownScaleFactor,
                config.treeClockUpDownWeight,
                AdaptationMode.DEFAULT
        );
    }

    private String summarizeTreeClockUpDownOperator(
            TreeModel treeModel,
            Parameter clockRateParameter,
            BeastXState.OperatorConfig config
    ) {
        return "UpDownOperator(up=[%s], down=[%s.allInternalNodeHeights], weight=%s, scaleFactor=%s)".formatted(
                clockRateParameter.getId(),
                treeModel.getId(),
                format(config.treeClockUpDownWeight),
                format(config.treeClockUpDownScaleFactor)
        );
    }

    private static String format(double value) {
        return Double.toString(value);
    }

    private static String parameterId(Parameter parameter) {
        String id =
                parameter.getId();

        return id == null ? "" : id;
    }

    private static String treeId(TreeModel treeModel) {
        String id =
                treeModel.getId();

        return id == null ? "" : id;
    }
}