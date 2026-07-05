package tiling;

import dr.evomodel.branchratemodel.DiscretizedBranchRates;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.ParametricDistributionModel;
import dr.inference.loggers.Logger;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import org.phylospec.tiling.TypeToken;
import tiling.model.StartingTreeSpec;
import tiling.params.BeastXParam;
import tiling.xml.XmlPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Intermediate state produced while evaluating BEAST X tiles.
 *
 * Each tile adds the BEAST X objects or export metadata it creates, such as
 * state nodes, priors, tree priors, likelihoods, operators, loggers, and XML
 * generation information.
 *
     * This state is later consumed by `BeastXModel`, `MCMCBuilder`, or
 * `StateXmlGenerator`.
 */
public class BeastXState {

    public final String runName;

    // BEAST X parameters that are part of the MCMC state.
    public final Map<Parameter, TypeToken<?>> stateNodes;
    public final Map<String, Parameter> stateNodesByPhyloSpecName;

    // Deterministic or derived calculation nodes created by helper tiles.
    public final Map<Statistic, TypeToken<?>> calculationNodes;
    public final Map<String, Statistic> calculationNodesByPhyloSpecName;

    // Tree models keyed by their original PhyloSpec variable names.
    public final Map<String, TreeModel> treeModelsByPhyloSpecName;
    public final Map<Parameter, AbstractDistributionLikelihood> priorDistributions;
    public final Map<TreeModel, AbstractModelLikelihood> treePriorDistributions;
    public final Map<TreeModel, StartingTreeSpec> startingTreeSpecs;
    public final List<AbstractDistributionLikelihood> calibrationPriorDistributions;
    public final List<Likelihood> likelihoodDistributions;

    // Prior and likelihood components collected during tiling.
    public final Map<TreeModel, List<Parameter>> treeClockRateParameters;
    public final Map<TreeModel, RelaxedClockSpec> treeRelaxedClockModels;

    // Clock models associated with each tree.
    public final List<Logger> mcmcLoggers;
    public final List<ScreenLoggerSpec> screenLoggerSpecs;
    public final List<FileLoggerSpec> fileLoggerSpecs;
    public final List<TreeLoggerSpec> treeLoggerSpecs;

    // Logger configuration collected from MCMC logger tiles.
    public final OperatorConfig operatorConfig;
    public final XmlPlan xmlPlan;

    public long chainLength = 1;
    public Long randomSeed = null;
    public String outputPrefix = null;
    public long defaultLogEvery = 1;

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
        this.startingTreeSpecs = new HashMap<>();
        this.calibrationPriorDistributions = new ArrayList<>();
        this.likelihoodDistributions = new ArrayList<>();
        this.treeClockRateParameters = new HashMap<>();
        this.treeRelaxedClockModels = new HashMap<>();
        this.mcmcLoggers = new ArrayList<>();
        this.screenLoggerSpecs = new ArrayList<>();
        this.fileLoggerSpecs = new ArrayList<>();
        this.treeLoggerSpecs = new ArrayList<>();
        this.operatorConfig = new OperatorConfig();
        this.xmlPlan = new XmlPlan();
        this.ids = new HashSet<>();
    }

    /*
    * Returns a unique BEAST X object ID based on the proposed name.
    *
    * If the proposed ID has already been used, a numeric suffix is appended,
    * */
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

    /*
    * Registers a BEAST X parameter as an MCMC state node.
    *
    * The parameter is stored both by BEAST X object identity and by its original
    * PhyloSpec variable name, so later tiles can refer back to it.
    * */
    public void addStateNode(BeastXParam stateNode, TypeToken<?> typeToken, String id) {
        Parameter parameter = stateNode.getParameter();
        parameter.setId(this.getAvailableID(id));
        this.stateNodes.put(parameter, typeToken);
        this.stateNodesByPhyloSpecName.put(id, parameter);
    }

    // Registers a deterministic or derived statistic created during tiling.
    public void addCalculationNode(
            Statistic statistic,
            TypeToken<?> typeToken,
            String id
    ) {
        statistic.setId(this.getAvailableID(id));
        this.calculationNodes.put(statistic, typeToken);
        this.calculationNodesByPhyloSpecName.put(id, statistic);
    }

    // Associates a parameter state node with its prior distribution.
    public void addPriorDistribution(
            BeastXParam stateNode,
            AbstractDistributionLikelihood distribution,
            String id
    ) {
        Parameter parameter = stateNode.getParameter();
        distribution.setId(this.getAvailableID(id));
        this.priorDistributions.put(parameter, distribution);
    }

    // Registers a tree model and its tree prior likelihood.
    public TreeModel addTreePriorDistribution(
            TreeModel treeModel,
            AbstractModelLikelihood likelihood,
            String id
    ) {
        return addTreePriorDistribution(
                treeModel,
                likelihood,
                StartingTreeSpec.fixedNewick(),
                id
        );
    }

    public TreeModel addTreePriorDistribution(
            TreeModel treeModel,
            AbstractModelLikelihood likelihood,
            StartingTreeSpec startingTreeSpec,
            String id
    ) {
        if (this.treeModelsByPhyloSpecName.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Tree model already registered for PhyloSpec name: " + id
            );
        }

        treeModel.setId(this.getAvailableID(id));
        likelihood.setId(this.getAvailableID(id + "_prior"));
        this.treePriorDistributions.put(treeModel, likelihood);
        this.startingTreeSpecs.put(treeModel, startingTreeSpec);
        this.treeModelsByPhyloSpecName.put(id, treeModel);

        return treeModel;
    }

    // Adds a calibration prior, such as an MRCA or root-age calibration.
    public void addCalibrationPriorDistribution(
            AbstractDistributionLikelihood likelihood,
            String id
    ) {
        likelihood.setId(this.getAvailableID(id));
        this.calibrationPriorDistributions.add(likelihood);
    }

    // Adds a data likelihood component to the model.
    public void addLikelihoodDistribution(
            Likelihood likelihood,
            String id
    ) {
        likelihood.setId(this.getAvailableID(id));
        this.likelihoodDistributions.add(likelihood);
    }

    // Records a clock-rate parameter associated with a tree.
    public void addTreeClockRateParameter(
            TreeModel treeModel,
            Parameter clockRateParameter
    ) {
        this.treeClockRateParameters
                .computeIfAbsent(treeModel, ignored -> new ArrayList<>())
                .add(clockRateParameter);
    }

    // Records the relaxed-clock model associated with a tree.
    public void addTreeRelaxedClockModel(
            TreeModel treeModel,
            DiscretizedBranchRates relativeRelaxedClock,
            Parameter rateCategoriesParameter,
            ParametricDistributionModel distributionModel,
            Parameter clockRateParameter
    ) {
        this.treeRelaxedClockModels.put(
                treeModel,
                new RelaxedClockSpec(
                        treeModel,
                        relativeRelaxedClock,
                        rateCategoriesParameter,
                        distributionModel,
                        clockRateParameter
                )
        );
    }
    
    public void addScreenLoggerSpec(long logEvery, List<String> parameterNames) {
        this.screenLoggerSpecs.add(new ScreenLoggerSpec(logEvery, parameterNames));
    }

    public void addFileLoggerSpec(long logEvery, String fileName, List<String> parameterNames) {
        this.fileLoggerSpecs.add(new FileLoggerSpec(logEvery, fileName, parameterNames));
    }

    public void addTreeLoggerSpec(long logEvery, String fileName, List<String> treeNames) {
        this.treeLoggerSpecs.add(new TreeLoggerSpec(logEvery, fileName, treeNames));
    }

    // Complete BEAST X object bundle needed to represent a relaxed clock model.
    public record RelaxedClockSpec(
            TreeModel treeModel,
            DiscretizedBranchRates relativeRelaxedClock,
            Parameter rateCategoriesParameter,
            ParametricDistributionModel distributionModel,
            Parameter clockRateParameter
    ) {
    }
    /*
    * Default operator settings used when constructing BEAST X MCMC operators.
    *
    * These values can be overridden by operator-configuration tiles.
    * */
    public static class OperatorConfig {
        public double treeScaleFactor = 0.75;
        public double treeUniformNodeHeightWeight = 15.0;
        public double treeRandomWalkNodeHeightWeight = 15.0;
        public double treeRandomWalkNodeHeightSize = 0.05;

        public double parameterOperatorWeight = 1.0;
        public double parameterScaleFactor = 0.75;
        public double randomWalkWindowSize = 1.0;

        public double treeScaleWeight = 5.0;
        public double treeSubtreeSlideSize = 15.0;
        public double treeSubtreeSlideWeight = 15.0;
        public double treeNarrowExchangeWeight = 15.0;
        public double treeWideExchangeWeight = 5.0;
        public double treeWilsonBaldingWeight = 5.0;

        public double treeClockUpDownWeight = 5.0;
        public double treeClockUpDownScaleFactor = 0.75;

        // Updates one supported operator setting by name.
        public void set(String settingName, double value) {
            switch (settingName) {
                case "parameterOperatorWeight" -> this.parameterOperatorWeight = value;
                case "parameterScaleFactor" -> this.parameterScaleFactor = value;
                case "randomWalkWindowSize" -> this.randomWalkWindowSize = value;
                case "treeScaleWeight" -> this.treeScaleWeight = value;
                case "treeSubtreeSlideSize" -> this.treeSubtreeSlideSize = value;
                case "treeSubtreeSlideWeight" -> this.treeSubtreeSlideWeight = value;
                case "treeNarrowExchangeWeight" -> this.treeNarrowExchangeWeight = value;
                case "treeWideExchangeWeight" -> this.treeWideExchangeWeight = value;
                case "treeWilsonBaldingWeight" -> this.treeWilsonBaldingWeight = value;
                case "treeClockUpDownWeight" -> this.treeClockUpDownWeight = value;
                case "treeClockUpDownScaleFactor" -> this.treeClockUpDownScaleFactor = value;
                case "treeScaleFactor" -> this.treeScaleFactor = value;
                case "treeNodeHeightWeight" -> {
                    this.treeUniformNodeHeightWeight = value / 2.0;
                    this.treeRandomWalkNodeHeightWeight = value / 2.0;
                }
                case "treeUniformNodeHeightWeight" -> this.treeUniformNodeHeightWeight = value;
                case "treeRandomWalkNodeHeightWeight" -> this.treeRandomWalkNodeHeightWeight = value;
                case "treeRandomWalkNodeHeightSize" -> this.treeRandomWalkNodeHeightSize = value;
                default -> throw new IllegalArgumentException("Unsupported BEAST X operator setting: " + settingName);
            }
        }

        // Returns whether the given setting name is recognized by this config.
        public static boolean isSupportedSetting(String settingName) {
            return isWeight(settingName)
                    || isScaleFactor(settingName)
                    || isPositiveSetting(settingName);
        }

        public static boolean isWeight(String settingName) {
            return Set.of(
                    "parameterOperatorWeight",
                    "treeScaleWeight",
                    "treeSubtreeSlideWeight",
                    "treeNarrowExchangeWeight",
                    "treeWideExchangeWeight",
                    "treeWilsonBaldingWeight",
                    "treeClockUpDownWeight",
                    "treeNodeHeightWeight",
                    "treeUniformNodeHeightWeight",
                    "treeRandomWalkNodeHeightWeight"
            ).contains(settingName);
        }

        public static boolean isScaleFactor(String settingName) {
            return Set.of(
                    "parameterScaleFactor",
                    "treeClockUpDownScaleFactor",
                    "treeScaleFactor"
            ).contains(settingName);
        }

        public static boolean isPositiveSetting(String settingName) {
            return Set.of(
                    "randomWalkWindowSize",
                    "treeSubtreeSlideSize",
                    "treeRandomWalkNodeHeightSize"
            ).contains(settingName);
        }
    }

    public record ScreenLoggerSpec(long logEvery, List<String> parameterNames) {
    }

    public record FileLoggerSpec(long logEvery, String fileName, List<String> parameterNames) {
    }

    public record TreeLoggerSpec(long logEvery, String fileName, List<String> treeNames) {
    }
}
