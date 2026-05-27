package tiling;

import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Likelihood;
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

    public final long chainLength;
    public final List<String> screenLoggers;
    public final List<String> fileLoggers;
    public final List<String> treeLoggers;

    public BeastXModelSummary(
            List<String> stateNodes,
            List<String> stateNodeTypes,
            List<String> parameterPriors,
            List<String> treeModels,
            List<String> treePriors,
            List<String> calibrationPriors,
            List<String> likelihoods,
            List<String> operators,
            long chainLength,
            List<String> screenLoggers,
            List<String> fileLoggers,
            List<String> treeLoggers
    ) {
        this.stateNodes = stateNodes;
        this.stateNodeTypes = stateNodeTypes;
        this.parameterPriors = parameterPriors;
        this.treeModels = treeModels;
        this.treePriors = treePriors;
        this.calibrationPriors = calibrationPriors;
        this.likelihoods = likelihoods;
        this.operators = operators;
        this.chainLength = chainLength;
        this.screenLoggers = screenLoggers;
        this.fileLoggers = fileLoggers;
        this.treeLoggers = treeLoggers;
    }

    public static BeastXModelSummary from(BeastXModel model) {
        List<String> stateNodes = new ArrayList<>();
        List<String> stateNodeTypes = new ArrayList<>();

        for (Map.Entry<Parameter, TypeToken<?>> entry : model.beastState.stateNodes.entrySet()) {
            Parameter parameter = entry.getKey();
            TypeToken<?> typeToken = entry.getValue();

            stateNodes.add(parameter.getId());
            stateNodeTypes.add(parameter.getId() + ": " + typeToken.getType().getTypeName());
        }

        List<String> parameterPriors = new ArrayList<>();

        for (AbstractDistributionLikelihood prior : model.beastState.priorDistributions.values()) {
            parameterPriors.add(prior.getId());
        }

        List<String> treeModels = new ArrayList<>();
        List<String> treePriors = new ArrayList<>();

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : model.beastState.treePriorDistributions.entrySet()) {
            treeModels.add(entry.getKey().getId());
            treePriors.add(entry.getValue().getId());
        }

        List<String> calibrationPriors = new ArrayList<>();

        for (AbstractDistributionLikelihood calibrationPrior : model.beastState.calibrationPriorDistributions) {
            calibrationPriors.add(calibrationPrior.getId());
        }

        List<String> likelihoods = new ArrayList<>();

        for (Likelihood likelihood : model.beastState.likelihoodDistributions) {
            likelihoods.add(likelihood.getId());
        }

        List<String> operators = new ArrayList<>();

        for (MCMCOperator operator : new BeastXOperatorBuilder().build(model.beastState)) {
            operators.add(operator.getClass().getSimpleName());
        }

        List<String> screenLoggers = new ArrayList<>();

        for (BeastXState.ScreenLoggerSpec spec : model.beastState.screenLoggerSpecs) {
            screenLoggers.add(
                    "screenLogger(logEvery=%d, parameters=%s)".formatted(
                            spec.logEvery,
                            formatParameterNames(spec.parameterNames)
                    )
            );
        }

        List<String> fileLoggers = new ArrayList<>();

        for (BeastXState.FileLoggerSpec spec : model.beastState.fileLoggerSpecs) {
            fileLoggers.add(
                    "fileLogger(logEvery=%d, file=%s, parameters=%s)".formatted(
                            spec.logEvery,
                            spec.fileName,
                            formatParameterNames(spec.parameterNames)
                    )
            );
        }

        List<String> treeLoggers = new ArrayList<>();

        for (BeastXState.TreeLoggerSpec spec : model.beastState.treeLoggerSpecs) {
            treeLoggers.add(
                    "treeLogger(logEvery=%d, file=%s, trees=%s)".formatted(
                            spec.logEvery,
                            spec.fileName,
                            spec.treeNames
                    )
            );
        }

        return new BeastXModelSummary(
                sorted(stateNodes),
                sorted(stateNodeTypes),
                sorted(parameterPriors),
                sorted(treeModels),
                sorted(treePriors),
                sorted(calibrationPriors),
                sorted(likelihoods),
                sorted(operators),
                model.beastState.chainLength,
                sorted(screenLoggers),
                sorted(fileLoggers),
                sorted(treeLoggers)
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
                chain length: %d
                screen loggers: %s
                file loggers: %s
                tree loggers: %s
                """.formatted(
                title,
                this.stateNodes,
                this.stateNodeTypes,
                this.parameterPriors,
                this.treeModels,
                this.treePriors,
                this.calibrationPriors,
                this.likelihoods,
                this.operators,
                this.chainLength,
                this.screenLoggers,
                this.fileLoggers,
                this.treeLoggers
        );
    }

    private static String formatParameterNames(List<String> parameterNames) {
        if (parameterNames == null) {
            return "all state nodes";
        }

        return parameterNames.toString();
    }

    private static List<String> sorted(List<String> values) {
        return values.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}