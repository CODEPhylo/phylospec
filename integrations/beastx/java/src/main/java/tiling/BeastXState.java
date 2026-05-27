package tiling;

import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.loggers.Logger;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import org.phylospec.tiling.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeastXState {

    public final String runName;

    public final Map<Parameter, TypeToken<?>> stateNodes;
    public final Map<String, Parameter> stateNodesByPhyloSpecName;
    public final Map<Statistic, TypeToken<?>> calculationNodes;
    public final Map<String, Statistic> calculationNodesByPhyloSpecName;
    public final Map<String, TreeModel> treeModelsByPhyloSpecName;
    public final Map<Parameter, AbstractDistributionLikelihood> priorDistributions;
    public final Map<TreeModel, AbstractModelLikelihood> treePriorDistributions;
    public final List<AbstractDistributionLikelihood> calibrationPriorDistributions;
    public final List<Likelihood> likelihoodDistributions;
    public final Map<TreeModel, List<Parameter>> treeClockRateParameters;
    public final List<Logger> mcmcLoggers;
    public final List<ScreenLoggerSpec> screenLoggerSpecs;
    public final List<FileLoggerSpec> fileLoggerSpecs;
    public final List<TreeLoggerSpec> treeLoggerSpecs;

    public long chainLength = 1;

    private final Set<String> ids;

    public BeastXState(String runName) {
        this.runName = runName;
        this.stateNodes = new HashMap<>();
        this.stateNodesByPhyloSpecName = new HashMap<>();
        this.calculationNodes = new HashMap<>();
        this.calculationNodesByPhyloSpecName = new HashMap<>();
        this.treeModelsByPhyloSpecName = new HashMap<>();
        this.priorDistributions = new HashMap<>();
        this.treePriorDistributions = new HashMap<>();
        this.calibrationPriorDistributions = new ArrayList<>();
        this.likelihoodDistributions = new ArrayList<>();
        this.treeClockRateParameters = new HashMap<>();
        this.mcmcLoggers = new ArrayList<>();
        this.screenLoggerSpecs = new ArrayList<>();
        this.fileLoggerSpecs = new ArrayList<>();
        this.treeLoggerSpecs = new ArrayList<>();
        this.ids = new HashSet<>();
    }

    public String getAvailableID(String proposal) {
        if (!this.ids.contains(proposal)) {
            this.ids.add(proposal);
            return proposal;
        }

        int prefix = 2;
        while (this.ids.contains(proposal + "_" + prefix)) {
            prefix++;
        }

        proposal = proposal + "_" + prefix;
        this.ids.add(proposal);
        return proposal;
    }

    public void addStateNode(BeastXParam stateNode, TypeToken<?> typeToken, String id) {
        Parameter parameter = stateNode.getParameter();
        parameter.setId(this.getAvailableID(id));
        this.stateNodes.put(parameter, typeToken);
        this.stateNodesByPhyloSpecName.put(id, parameter);
    }

    public void addCalculationNode(
            Statistic statistic,
            TypeToken<?> typeToken,
            String id
    ) {
        statistic.setId(this.getAvailableID(id));
        this.calculationNodes.put(statistic, typeToken);
        this.calculationNodesByPhyloSpecName.put(id, statistic);
    }

    public void addPriorDistribution(
            BeastXParam stateNode,
            AbstractDistributionLikelihood distribution,
            String id
    ) {
        Parameter parameter = stateNode.getParameter();
        distribution.setId(this.getAvailableID(id));
        this.priorDistributions.put(parameter, distribution);
    }

    public void addTreePriorDistribution(
            TreeModel treeModel,
            AbstractModelLikelihood likelihood,
            String id
    ) {
        treeModel.setId(this.getAvailableID(id));
        likelihood.setId(this.getAvailableID(id + "_prior"));
        this.treePriorDistributions.put(treeModel, likelihood);
        this.treeModelsByPhyloSpecName.put(id, treeModel);
    }

    public void addCalibrationPriorDistribution(
            AbstractDistributionLikelihood likelihood,
            String id
    ) {
        likelihood.setId(this.getAvailableID(id));
        this.calibrationPriorDistributions.add(likelihood);
    }

    public void addLikelihoodDistribution(
            Likelihood likelihood,
            String id
    ) {
        likelihood.setId(this.getAvailableID(id));
        this.likelihoodDistributions.add(likelihood);
    }

    public void addTreeClockRateParameter(
            TreeModel treeModel,
            Parameter clockRateParameter
    ) {
        this.treeClockRateParameters
                .computeIfAbsent(treeModel, ignored -> new ArrayList<>())
                .add(clockRateParameter);
    }

    public void addMCMCLogger(Logger logger) {
        this.mcmcLoggers.add(logger);
    }

    public void addScreenLoggerSpec(long logEvery) {
        this.screenLoggerSpecs.add(new ScreenLoggerSpec(logEvery, null));
    }

    public void addScreenLoggerSpec(long logEvery, List<String> parameterNames) {
        this.screenLoggerSpecs.add(new ScreenLoggerSpec(logEvery, parameterNames));
    }

    public void addFileLoggerSpec(long logEvery, String fileName) {
        this.fileLoggerSpecs.add(new FileLoggerSpec(logEvery, fileName, null));
    }

    public void addFileLoggerSpec(long logEvery, String fileName, List<String> parameterNames) {
        this.fileLoggerSpecs.add(new FileLoggerSpec(logEvery, fileName, parameterNames));
    }

    public void addTreeLoggerSpec(long logEvery, String fileName, List<String> treeNames) {
        this.treeLoggerSpecs.add(new TreeLoggerSpec(logEvery, fileName, treeNames));
    }

    public static class ScreenLoggerSpec {
        public final long logEvery;
        public final List<String> parameterNames;

        public ScreenLoggerSpec(long logEvery, List<String> parameterNames) {
            this.logEvery = logEvery;
            this.parameterNames = parameterNames;
        }
    }

    public static class FileLoggerSpec {
        public final long logEvery;
        public final String fileName;
        public final List<String> parameterNames;

        public FileLoggerSpec(long logEvery, String fileName, List<String> parameterNames) {
            this.logEvery = logEvery;
            this.fileName = fileName;
            this.parameterNames = parameterNames;
        }
    }

    public static class TreeLoggerSpec {
        public final long logEvery;
        public final String fileName;
        public final List<String> treeNames;

        public TreeLoggerSpec(long logEvery, String fileName, List<String> treeNames) {
            this.logEvery = logEvery;
            this.fileName = fileName;
            this.treeNames = treeNames;
        }
    }
}