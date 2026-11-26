package tiling;

import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.ParametricDistributionModel;
import dr.inference.loggers.Logger;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.LoggerSpec;
import org.phylospec.tiling.mcmc.McmcSamplerSpec;
import org.phylospec.tiling.mcmc.OutputPaths;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import tiling.model.StartingTreeSpec;
import tiling.params.BeastXParam;
import tiling.xml.XmlPlan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Intermediate state produced while evaluating BEAST X tiles.
 *
 * <p>Each tile adds the BEAST X objects or export metadata it creates, such as
 * state nodes, priors, tree priors, likelihoods, operators, loggers, and XML
 * generation information.</p>
 *
 * <p>This state is later consumed by {@link BeastXModel}, {@link tiling.mcmc.MCMCBuilder}, or
 * {@link tiling.xml.StateXmlGenerator}.</p>
 */
public class BeastXState {

    public final String runName;
    private final Path inputDirectory;
    private final Consumer<Path> inputObserver;

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
    public final Map<TreeModel, AbstractModelLikelihood> observedTreeDistributions;
    public final Map<TreeModel, StartingTreeSpec> startingTreeSpecs;

    /** Trees whose prior is conditioned on a fixed rootAge: their root must not move. */
    public final Set<TreeModel> rootConditionedTrees;
    public final List<AbstractDistributionLikelihood> calibrationPriorDistributions;
    public final List<Likelihood> likelihoodDistributions;

    // Clock models associated with each tree.
    public final Map<TreeModel, List<Parameter>> treeClockRateParameters;
    public final Map<TreeModel, List<Parameter>> treeStrictClockRateParameters;
    public final Map<TreeModel, RelaxedClockSpec> treeRelaxedClockModels;

    // Logger configuration collected from MCMC logger tiles. Logged parameters and trees are
    // referred to by their PhyloSpec names.
    public final List<Logger> mcmcLoggers;
    public final List<ScreenLoggerSpec<String>> screenLoggerSpecs;
    public final List<FileLoggerSpec<String>> fileLoggerSpecs;
    public final List<TreeLoggerSpec<String>> treeLoggerSpecs;
    private final List<Integer> defaultFileTreeLoggerIndices = new ArrayList<>();

    public final OperatorConfig operatorConfig;
    public final XmlPlan xmlPlan;

    private McmcSamplerSpec samplerSpec;
    public Long randomSeed = null;
    public String outputPrefix = null;

    private final Set<String> ids;

    public BeastXState(String runName) {
        this(runName, Path.of(""), path -> {});
    }

    public BeastXState(String runName, Path inputDirectory, Consumer<Path> inputObserver) {
        this.runName = runName;
        this.inputDirectory = inputDirectory;
        this.inputObserver = inputObserver;
        this.stateNodes = new HashMap<>();
        this.stateNodesByPhyloSpecName = new HashMap<>();
        this.calculationNodes = new HashMap<>();
        this.calculationNodesByPhyloSpecName = new HashMap<>();
        this.treeModelsByPhyloSpecName = new HashMap<>();
        this.priorDistributions = new HashMap<>();
        this.treePriorDistributions = new HashMap<>();
        this.observedTreeDistributions = new HashMap<>();
        this.startingTreeSpecs = new HashMap<>();
        this.rootConditionedTrees = Collections.newSetFromMap(new IdentityHashMap<>());
        this.calibrationPriorDistributions = new ArrayList<>();
        this.likelihoodDistributions = new ArrayList<>();
        this.treeClockRateParameters = new HashMap<>();
        this.treeStrictClockRateParameters = new HashMap<>();
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

    /**
     * Sampled parameters whose prior is not a BEAST X model, such as a custom Distribution or a
     * truncated likelihood. BEAST X restores the value after a rejected proposal only for
     * parameters registered with a model, so the MCMC (in memory and in XML) registers these
     * through a dummy likelihood, which adds 0 to the posterior.
     */
    public List<Parameter> sampledParametersWithoutPriorModel() {
        List<Parameter> parameters = new ArrayList<>();

        for (Map.Entry<Parameter, AbstractDistributionLikelihood> entry : this.priorDistributions.entrySet()) {
            if (this.stateNodes.containsKey(entry.getKey()) && entry.getValue().getModel() == null) {
                parameters.add(entry.getKey());
            }
        }

        parameters.sort(Comparator.comparing(Parameter::getId));

        return parameters;
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

    /**
     * Registers a density evaluated on a fixed observed tree.
     *
     * <p>The tree is deliberately kept out of {@link #treePriorDistributions}:
     * that collection represents sampled trees and drives automatic tree
     * operator construction. The density still belongs to the model likelihood
     * and therefore contributes to the posterior.</p>
     */
    public TreeModel addObservedTreeDistribution(
            TreeModel treeModel,
            AbstractModelLikelihood likelihood,
            String treeId,
            String likelihoodId
    ) {
        if (this.treeModelsByPhyloSpecName.containsKey(treeId)) {
            throw new IllegalArgumentException(
                    "Tree model already registered for PhyloSpec name: " + treeId
            );
        }

        treeModel.setId(this.getAvailableID(treeId));
        likelihood.setId(this.getAvailableID(likelihoodId));

        this.observedTreeDistributions.put(treeModel, likelihood);
        this.treeModelsByPhyloSpecName.put(treeId, treeModel);
        this.startingTreeSpecs.put(treeModel, StartingTreeSpec.fixedNewick());

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

    // Records a strict-clock rate parameter and its general tree/clock association.
    public void addTreeStrictClockRateParameter(
            TreeModel treeModel,
            Parameter clockRateParameter
    ) {
        addTreeClockRateParameter(treeModel, clockRateParameter);
        this.treeStrictClockRateParameters
                .computeIfAbsent(treeModel, ignored -> new ArrayList<>())
                .add(clockRateParameter);
    }

    // Records the relaxed-clock model associated with a tree.
    public void addTreeRelaxedClockModel(
            TreeModel treeModel,
            BranchRateModel relativeRelaxedClock,
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
    
    /** A relative input belongs to the source file; the CLI also protects that file during export. */
    public Path resolveInput(String file) {
        Path path = inputDirectory.resolve(file);
        inputObserver.accept(path);
        return path;
    }

    public void setSamplerSpec(McmcSamplerSpec samplerSpec) {
        this.samplerSpec = samplerSpec;
    }

    /** Returns the declared sampler, or the default sampler if the model declares none. */
    public McmcSamplerSpec getSamplerSpec() {
        return this.samplerSpec != null ? this.samplerSpec : new McmcSamplerSpec();
    }

    /** Returns the prefix of the default output files. */
    public String getOutputPrefix() {
        return this.outputPrefix != null ? this.outputPrefix : this.runName;
    }

    /** Fill only missing logger kinds on the freshly built export state, before XML planning. */
    public void configureDefaultXmlLoggers(String fallbackPrefix) {
        String prefix = outputPrefix == null ? fallbackPrefix : outputPrefix;
        if (this.screenLoggerSpecs.isEmpty()) {
            this.addScreenLoggerSpec(new ScreenLoggerSpec<>(LoggerSpec.DEFAULT_LOG_EVERY, null, null));
        }
        if (this.fileLoggerSpecs.isEmpty()) {
            this.addFileLoggerSpec(new FileLoggerSpec<>(LoggerSpec.DEFAULT_LOG_EVERY, OutputPaths.getLogFile(prefix), null, null));
        }
        if (this.treeLoggerSpecs.isEmpty()) {
            this.treeLoggerSpecs.addAll(this.getDefaultTreeLoggerSpecs(prefix));
        }
    }

    /**
     * Returns the names of the distributions every screen and file logger writes to the XML. A model
     * without a likelihood still logs all three: its posterior equals its prior and its likelihood is 0.
     */
    public List<String> getXmlLoggedDistributionNames() {
        return List.of("posterior", "prior", "likelihood");
    }

    /**
     * Returns the names a screen or file logger without explicit parameters writes to the XML: the
     * statistics of every tree and all sampled parameters.
     */
    public List<String> getXmlDefaultLoggedParameterNames() {
        List<String> names = new ArrayList<>();

        for (String tree : this.treeModelsByPhyloSpecName.keySet().stream().sorted().toList()) {
            names.add(tree + ".height");
            names.add(tree + ".treeLength");
        }
        names.addAll(this.stateNodesByPhyloSpecName.keySet().stream().sorted().toList());

        return names;
    }

    /**
     * Returns the given logged names with every tree replaced by the names of its statistics.
     */
    public List<String> expandLoggedTrees(List<String> names) {
        List<String> expandedNames = new ArrayList<>();

        for (String name : names) {
            if (this.treeModelsByPhyloSpecName.containsKey(name)) {
                expandedNames.add(name + ".height");
                expandedNames.add(name + ".treeLength");
            } else {
                expandedNames.add(name);
            }
        }

        return expandedNames;
    }

    /**
     * Returns the tree loggers used when the model declares none. Every tree is logged if at least
     * one tree is sampled. A single tree is logged to {@code <prefix>.trees}, several trees to
     * {@code <prefix>-<tree>.trees} each.
     */
    public List<TreeLoggerSpec<String>> getDefaultTreeLoggerSpecs(String prefix) {
        List<TreeLoggerSpec<String>> specs = new ArrayList<>();
        if (this.treePriorDistributions.isEmpty()) {
            return specs;
        }

        List<String> trees = this.treeModelsByPhyloSpecName.keySet().stream().sorted().toList();
        for (String tree : trees) {
            String fileName = OutputPaths.getTreeFile(prefix, tree, trees.size());
            specs.add(new TreeLoggerSpec<>(LoggerSpec.DEFAULT_LOG_EVERY, fileName, tree));
        }

        return specs;
    }

    public void addScreenLoggerSpec(ScreenLoggerSpec<String> spec) {
        this.screenLoggerSpecs.add(spec);
    }

    public void addFileLoggerSpec(FileLoggerSpec<String> spec) {
        this.fileLoggerSpecs.add(spec);
    }

    public void addTreeLoggerSpec(TreeLoggerSpec<String> spec) {
        this.treeLoggerSpecs.add(spec);
    }

    /**
     * Adds a tree logger which writes to the default tree file. As soon as several loggers use
     * the default, each of them writes to the file of its tree instead, so that they do not clash.
     */
    public void addTreeLoggerSpecWithDefaultFile(int logEvery, String treeName) {
        this.defaultFileTreeLoggerIndices.add(this.treeLoggerSpecs.size());
        this.treeLoggerSpecs.add(new TreeLoggerSpec<>(logEvery, OutputPaths.getTreeFile(this.getOutputPrefix()), treeName));

        int count = this.defaultFileTreeLoggerIndices.size();
        for (int index : this.defaultFileTreeLoggerIndices) {
            TreeLoggerSpec<String> spec = this.treeLoggerSpecs.get(index);
            String fileName = OutputPaths.getTreeFile(this.getOutputPrefix(), spec.tree(), count);
            this.treeLoggerSpecs.set(index, new TreeLoggerSpec<>(spec.logEvery(), fileName, spec.tree()));
        }
    }

    /** Returns the PhyloSpec name under which the given tree is registered, or null if it is not. */
    public String getPhyloSpecName(TreeModel treeModel) {
        for (Map.Entry<String, TreeModel> entry : this.treeModelsByPhyloSpecName.entrySet()) {
            if (entry.getValue() == treeModel) {
                return entry.getKey();
            }
        }

        return null;
    }

    // Complete BEAST X object bundle needed to represent a relaxed clock model.
    public record RelaxedClockSpec(
            TreeModel treeModel,
            BranchRateModel relativeRelaxedClock,
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
        public double treeRootScaleWeight = 5.0;
        public double treeRootScaleFactor = 0.75;
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
                case "treeRootScaleWeight" -> this.treeRootScaleWeight = value;
                case "treeRootScaleFactor" -> this.treeRootScaleFactor = value;
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
                    "treeRootScaleWeight",
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
                    "treeScaleFactor",
                    "treeRootScaleFactor"
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
}
