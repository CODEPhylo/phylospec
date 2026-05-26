package tiling;

import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Parameter;
import dr.inference.operators.MCMCOperator;
import org.phylospec.tiling.TypeToken;

import tiling.operators.BeastXOperatorBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class BeastXModelSummary {

    public final List<String> stateNodes;
    public final List<String> stateNodeTypes;
    public final List<String> parameterPriors;
    public final List<String> treeModels;
    public final List<String> treePriors;
    public final List<String> calibrationPriors;
    public final List<String> likelihoods;
    public final List<String> operators;

    public BeastXModelSummary(
            List<String> stateNodes,
            List<String> stateNodeTypes,
            List<String> parameterPriors,
            List<String> treeModels,
            List<String> treePriors,
            List<String> calibrationPriors,
            List<String> likelihoods,
            List<String> operators
    ) {
        this.stateNodes =
                stateNodes;

        this.stateNodeTypes =
                stateNodeTypes;

        this.parameterPriors =
                parameterPriors;

        this.treeModels =
                treeModels;

        this.treePriors =
                treePriors;

        this.calibrationPriors =
                calibrationPriors;

        this.likelihoods =
                likelihoods;

        this.operators =
                operators;
    }

    public static BeastXModelSummary from(BeastXModel model) {
        List<String> stateNodes =
                new ArrayList<>();

        List<String> stateNodeTypes =
                new ArrayList<>();

        for (Map.Entry<Parameter, TypeToken<?>> entry : model.beastState.stateNodes.entrySet()) {
            Parameter parameter =
                    entry.getKey();

            TypeToken<?> typeToken =
                    entry.getValue();

            stateNodes.add(parameter.getId());
            stateNodeTypes.add(parameter.getId() + ": " + typeToken.getType().getTypeName());
        }

        List<String> parameterPriors =
                new ArrayList<>();

        for (AbstractDistributionLikelihood prior : model.beastState.priorDistributions.values()) {
            parameterPriors.add(prior.getId());
        }

        List<String> treeModels =
                new ArrayList<>();

        List<String> treePriors =
                new ArrayList<>();

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : model.beastState.treePriorDistributions.entrySet()) {
            treeModels.add(entry.getKey().getId());
            treePriors.add(entry.getValue().getId());
        }

        List<String> calibrationPriors =
                new ArrayList<>();

        for (AbstractDistributionLikelihood calibrationPrior : model.beastState.calibrationPriorDistributions) {
            calibrationPriors.add(calibrationPrior.getId());
        }

        List<String> likelihoods =
                new ArrayList<>();

        for (AbstractModelLikelihood likelihood : model.beastState.likelihoodDistributions) {
            likelihoods.add(likelihood.getId());
        }

        List<String> operators =
                new ArrayList<>();

        for (MCMCOperator operator : new BeastXOperatorBuilder().build(model.beastState)) {
            operators.add(operator.getClass().getSimpleName());
        }

        return new BeastXModelSummary(
                sorted(stateNodes),
                sorted(stateNodeTypes),
                sorted(parameterPriors),
                sorted(treeModels),
                sorted(treePriors),
                sorted(calibrationPriors),
                sorted(likelihoods),
                sorted(operators)
        );
    }

    public String toReportString(String title) {
        return """
                %s
                state nodes: %s
                state node types: %s
                parameter priors: %s
                tree models: %s
                tree priors: %s
                calibration priors: %s
                likelihoods: %s
                operators: %s
                """.formatted(
                title,
                this.stateNodes,
                this.stateNodeTypes,
                this.parameterPriors,
                this.treeModels,
                this.treePriors,
                this.calibrationPriors,
                this.likelihoods,
                this.operators
        );
    }

    private static List<String> sorted(List<String> values) {
        return values.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}