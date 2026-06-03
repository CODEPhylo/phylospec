package tiling.xml;

import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.speciation.SpeciationModel;
import dr.evomodel.tree.TreeModel;
import dr.evomodel.tree.TMRCAStatistic;
import dr.inference.distribution.UniformDistributionModel;
import dr.inference.model.Statistic;
import dr.util.Attribute;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.BetaDistributionModel;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.math.distributions.Distribution;
import dr.evomodel.branchmodel.HomogeneousBranchModel;
import dr.evomodel.substmodel.SubstitutionModel;
import tiling.model.BeastXPhyloCTMCLikelihoodSpec;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.xml.builders.BeastXSiteModelXmlBuilder;
import tiling.xml.builders.BeastXAlignmentXmlBuilder;
import tiling.xml.builders.BeastXSubstitutionModelXmlBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeastXXmlPlanBuilder {
    private final BeastXAlignmentXmlBuilder alignmentXmlBuilder =
            new BeastXAlignmentXmlBuilder();

    private final BeastXSubstitutionModelXmlBuilder substitutionModelXmlBuilder =
            new BeastXSubstitutionModelXmlBuilder();

    private final BeastXSiteModelXmlBuilder siteModelXmlBuilder =
            new BeastXSiteModelXmlBuilder();

    public BeastXXmlPlan build(BeastXModel model) {
        validateSupportedModel(model);

        BeastXState state =
                model.beastState;

        BeastXXmlPlan plan =
                new BeastXXmlPlan();

        addStateParameters(plan, state);
        addTreeDefinitions(plan, state);
        addBranchRateModels(plan, state);
        addScalarPriors(plan, state);
        addTreePriors(plan, state);
        addCalibrationPriors(plan, state);
        addOperators(plan, state);
        addLoggers(plan, state);

        return plan;
    }

    public BeastXXmlPlan buildPhyloCTMCComponentLayer(BeastXModel model) {
        BeastXXmlPlan plan =
                new BeastXXmlPlan();

        addPhyloCTMCDataDefinitions(plan, model.beastState);
        addPhyloCTMCSubstitutionModels(plan, model.beastState);
        addPhyloCTMCSiteRateModels(plan, model.beastState);

        return plan;
    }

    private void addPhyloCTMCDataDefinitions(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<BeastXPhyloCTMCLikelihoodSpec> likelihoodSpecs =
                new ArrayList<>();

        for (dr.inference.model.Likelihood likelihood : state.likelihoodDistributions) {
            if (likelihood instanceof BeastXPhyloCTMCLikelihoodSpec spec) {
                likelihoodSpecs.add(spec);
            }
        }

        likelihoodSpecs.sort(Comparator.comparing(BeastXXmlPlanBuilder::likelihoodId));

        for (BeastXPhyloCTMCLikelihoodSpec spec : likelihoodSpecs) {
            String alignmentId =
                    likelihoodId(spec) + "_alignment";

            String patternsId =
                    likelihoodId(spec) + "_patterns";

            List<BeastXXmlElement> elements =
                    alignmentXmlBuilder.buildAlignmentAndPatterns(
                            spec.getObservedAlignment(),
                            alignmentId,
                            patternsId
                    );

            plan.add(
                    BeastXXmlPlan.Section.ALIGNMENTS,
                    elements.get(0)
            );

            plan.add(
                    BeastXXmlPlan.Section.PATTERN_LISTS,
                    elements.get(1)
            );
        }
    }

    private void addPhyloCTMCSubstitutionModels(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<BeastXPhyloCTMCLikelihoodSpec> likelihoodSpecs =
                new ArrayList<>();

        for (dr.inference.model.Likelihood likelihood : state.likelihoodDistributions) {
            if (likelihood instanceof BeastXPhyloCTMCLikelihoodSpec spec) {
                likelihoodSpecs.add(spec);
            }
        }

        likelihoodSpecs.sort(Comparator.comparing(BeastXXmlPlanBuilder::likelihoodId));

        for (BeastXPhyloCTMCLikelihoodSpec spec : likelihoodSpecs) {
            if (!(spec.getBranchModel() instanceof HomogeneousBranchModel branchModel)) {
                throw unsupported(
                        "Only homogeneous branch substitution models are supported for PhyloCTMC XML export at this stage."
                );
            }

            SubstitutionModel substitutionModel =
                    branchModel.getRootSubstitutionModel();

            String substitutionModelId =
                    likelihoodId(spec) + "_substitutionModel";

            plan.addAll(
                    BeastXXmlPlan.Section.SUBSTITUTION_SITE_MODELS,
                    substitutionModelXmlBuilder.buildSubstitutionModel(
                            substitutionModel,
                            substitutionModelId
                    )
            );
        }
    }

    private void addPhyloCTMCSiteRateModels(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<BeastXPhyloCTMCLikelihoodSpec> likelihoodSpecs =
                new ArrayList<>();

        for (dr.inference.model.Likelihood likelihood : state.likelihoodDistributions) {
            if (likelihood instanceof BeastXPhyloCTMCLikelihoodSpec spec) {
                likelihoodSpecs.add(spec);
            }
        }

        likelihoodSpecs.sort(Comparator.comparing(BeastXXmlPlanBuilder::likelihoodId));

        for (BeastXPhyloCTMCLikelihoodSpec spec : likelihoodSpecs) {
            String likelihoodId =
                    likelihoodId(spec);

            String substitutionModelId =
                    likelihoodId + "_substitutionModel";

            String siteRateModelId =
                    likelihoodId + "_siteRateModel";

            plan.add(
                    BeastXXmlPlan.Section.SUBSTITUTION_SITE_MODELS,
                    siteModelXmlBuilder.buildSiteRateModel(
                            spec.getSiteRateModel(),
                            siteRateModelId,
                            substitutionModelId
                    )
            );
        }
    }

    private static String likelihoodId(dr.inference.model.Likelihood likelihood) {
        String id =
                likelihood.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot serialize unnamed BEAST X likelihood."
            );
        }

        return id;
    }

    private void validateSupportedModel(BeastXModel model) {
        BeastXState state =
                model.beastState;

        validateCalibrationPriors(state);
        validateLikelihoodExportBoundary(state);

        if (state.priorDistributions.isEmpty() && state.treePriorDistributions.isEmpty()) {
            throw unsupported("At least one scalar prior or tree prior is required.");
        }

        if (
                state.screenLoggerSpecs.isEmpty()
                        && state.fileLoggerSpecs.isEmpty()
                        && state.treeLoggerSpecs.isEmpty()
        ) {
            throw unsupported("At least one logger is required for XML MCMC execution.");
        }

        validateScalarPriors(state);
        validateTreePriors(state);
    }

    private void validateLikelihoodExportBoundary(BeastXState state) {
        if (state.likelihoodDistributions.isEmpty()) {
            return;
        }

        boolean hasBranchRateModel =
                !state.treeClockRateParameters.isEmpty();

        if (hasBranchRateModel) {
            throw unsupported(
                    "PhyloCTMC likelihood XML export is not supported yet. "
                            + "StrictClock branch-rate XML can currently be exported as a standalone branch-rate model, "
                            + "but full XML execution of PhyloCTMC still requires alignment, pattern list, "
                            + "substitution model, site model, branch-rate model, and tree-likelihood XML serialization."
            );
        }

        throw unsupported(
                "Likelihood XML export is not supported yet. "
                        + "Current XML export supports scalar priors, tree priors, calibration priors, "
                        + "strict-clock branch-rate definitions, operators, and loggers."
        );
    }

    private void validateCalibrationPriors(BeastXState state) {
        for (AbstractDistributionLikelihood calibrationPrior : state.calibrationPriorDistributions) {
            if (!(calibrationPrior instanceof DistributionLikelihood distributionLikelihood)) {
                throw unsupported("Only DistributionLikelihood calibration priors are supported.");
            }

            Distribution distribution =
                    distributionLikelihood.getDistribution();

            if (!(distribution instanceof UniformDistributionModel)) {
                throw unsupported("Only uniform root/MRCA calibration priors are supported.");
            }

            if (calibrationPrior.getDataList().size() != 1) {
                throw unsupported("Calibration prior XML export requires exactly one statistic data element.");
            }

            Attribute<double[]> data =
                    calibrationPrior.getDataList().get(0);

            if (!(data instanceof TMRCAStatistic)) {
                throw unsupported("Only TMRCAStatistic calibration prior data is supported.");
            }
        }
    }

    private void addCalibrationPriors(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<AbstractDistributionLikelihood> calibrationPriors =
                new ArrayList<>(state.calibrationPriorDistributions);

        calibrationPriors.sort(Comparator.comparing(BeastXXmlPlanBuilder::distributionLikelihoodId));

        for (AbstractDistributionLikelihood calibrationPrior : calibrationPriors) {
            plan.add(
                    BeastXXmlPlan.Section.STATISTICS,
                    tmrcaStatisticDefinition(calibrationPrior)
            );

            plan.add(
                    BeastXXmlPlan.Section.MCMC_PRIOR,
                    calibrationPriorDefinition(calibrationPrior)
            );
        }
    }

    private BeastXXmlElement tmrcaStatisticDefinition(
            AbstractDistributionLikelihood calibrationPrior
    ) {
        TMRCAStatistic statistic =
                calibrationStatistic(calibrationPrior);

        BeastXXmlElement element =
                BeastXXmlElement.element("tmrcaStatistic")
                        .withId(statisticId(statistic))
                        .withAttribute("name", statistic.getStatisticName())
                        .withAttribute("absolute", "false")
                        .withChild(treeReference((TreeModel) statistic.getTree()));

        Set<String> leafSet =
                statistic.getLeafSet();

        if (leafSet == null || leafSet.isEmpty()) {
            return element;
        }

        BeastXXmlElement taxa =
                BeastXXmlElement.element("taxa")
                        .withId(statisticId(statistic) + "_taxa");

        List<String> taxonIds =
                new ArrayList<>(leafSet);

        taxonIds.sort(String::compareTo);

        for (String taxonId : taxonIds) {
            taxa =
                    taxa.withChild(
                            BeastXXmlElement.ref("taxon", taxonId)
                    );
        }

        return element.withChild(
                BeastXXmlElement.element("mrca")
                        .withChild(taxa)
        );
    }

    private BeastXXmlElement calibrationPriorDefinition(
            AbstractDistributionLikelihood calibrationPrior
    ) {
        if (!(calibrationPrior instanceof DistributionLikelihood distributionLikelihood)) {
            throw unsupported("Only DistributionLikelihood calibration priors are supported.");
        }

        Distribution distribution =
                distributionLikelihood.getDistribution();

        if (!(distribution instanceof UniformDistributionModel uniformDistribution)) {
            throw unsupported("Only uniform calibration priors are supported.");
        }

        TMRCAStatistic statistic =
                calibrationStatistic(calibrationPrior);

        String priorId =
                distributionLikelihoodId(calibrationPrior);

        return BeastXXmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        BeastXXmlElement.element("distribution")
                                .withChild(
                                        BeastXXmlElement.element("uniformDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withChild(
                                                        BeastXXmlElement.element("lower")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_lower",
                                                                                uniformDistribution.getLower(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                                .withChild(
                                                        BeastXXmlElement.element("upper")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_upper",
                                                                                uniformDistribution.getUpper(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                )
                )
                .withChild(
                        BeastXXmlElement.element("data")
                                .withChild(
                                        BeastXXmlElement.ref("tmrcaStatistic", statisticId(statistic))
                                )
                );
    }

    private static TMRCAStatistic calibrationStatistic(
            AbstractDistributionLikelihood calibrationPrior
    ) {
        if (calibrationPrior.getDataList().size() != 1) {
            throw unsupported("Calibration prior XML export requires exactly one statistic data element.");
        }

        Attribute<double[]> data =
                calibrationPrior.getDataList().get(0);

        if (data instanceof TMRCAStatistic statistic) {
            return statistic;
        }

        throw unsupported("Only TMRCAStatistic calibration prior data is supported.");
    }

    private static String distributionLikelihoodId(AbstractDistributionLikelihood likelihood) {
        String id =
                likelihood.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X distribution likelihood.");
        }

        return id;
    }

    private static String statisticId(Statistic statistic) {
        String id =
                statistic.getId();

        if (id == null || id.isBlank()) {
            id =
                    statistic.getStatisticName();
        }

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X statistic.");
        }

        return id;
    }

    private void validateScalarPriors(BeastXState state) {
        for (Map.Entry<Parameter, AbstractDistributionLikelihood> entry : state.priorDistributions.entrySet()) {
            Parameter parameter =
                    entry.getKey();

            AbstractDistributionLikelihood likelihood =
                    entry.getValue();

            if (parameter.getDimension() != 1) {
                throw unsupported("Only scalar parameters are supported.");
            }

            if (!(likelihood instanceof DistributionLikelihood distributionLikelihood)) {
                throw unsupported("Only DistributionLikelihood scalar priors are supported.");
            }

            Distribution distribution =
                    distributionLikelihood.getDistribution();

            if (
                    !(distribution instanceof LogNormalDistributionModel)
                            && !(distribution instanceof BetaDistributionModel)
            ) {
                throw unsupported("Only LogNormal and Beta scalar priors are supported.");
            }
        }
    }

    private void validateTreePriors(BeastXState state) {
        for (AbstractModelLikelihood treePrior : state.treePriorDistributions.values()) {
            if (treePrior instanceof SpeciationLikelihood speciationLikelihood) {
                SpeciationModel speciationModel =
                        speciationLikelihood.getSpeciationModel();

                if (!(speciationModel instanceof BirthDeathGernhard08Model)) {
                    throw unsupported("Only Yule and BirthDeath speciation tree priors are supported.");
                }
            } else if (treePrior instanceof CoalescentLikelihood coalescentLikelihood) {
                DemographicModel demographicModel =
                        coalescentLikelihood.getDemoModel();

                if (!(demographicModel instanceof ConstantPopulationModel)) {
                    throw unsupported("Only constant-population Coalescent tree priors are supported.");
                }
            } else {
                throw unsupported("Only SpeciationLikelihood and CoalescentLikelihood tree priors are supported.");
            }
        }
    }

    private void addStateParameters(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.sort(Comparator.comparing(BeastXXmlPlanBuilder::parameterId));

        for (Parameter parameter : parameters) {
            plan.add(
                    BeastXXmlPlan.Section.PARAMETERS,
                    parameterDefinition(parameter)
            );
        }
    }

    private void addTreeDefinitions(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<Map.Entry<TreeModel, AbstractModelLikelihood>> treeEntries =
                new ArrayList<>(state.treePriorDistributions.entrySet());

        treeEntries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        Set<String> emittedTaxonIds =
                new HashSet<>();

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : treeEntries) {
            TreeModel treeModel =
                    entry.getKey();

            addTaxonDefinitions(plan, treeModel, emittedTaxonIds);

            plan.add(
                    BeastXXmlPlan.Section.STARTING_TREES,
                    startingTreeDefinition(treeModel)
            );

            plan.add(
                    BeastXXmlPlan.Section.TREE_MODELS,
                    treeModelDefinition(treeModel)
            );

            plan.add(
                    BeastXXmlPlan.Section.TREE_PRIOR_MODELS,
                    treePriorModelDefinition(state, entry.getValue())
            );
        }
    }

    private void addBranchRateModels(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<Map.Entry<TreeModel, List<Parameter>>> entries =
                new ArrayList<>(state.treeClockRateParameters.entrySet());

        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        for (Map.Entry<TreeModel, List<Parameter>> entry : entries) {
            TreeModel treeModel =
                    entry.getKey();

            List<Parameter> clockRateParameters =
                    entry.getValue();

            if (clockRateParameters.size() != 1) {
                throw unsupported(
                        "Only one strict-clock rate parameter per tree is supported for XML export."
                );
            }

            plan.add(
                    BeastXXmlPlan.Section.BRANCH_RATE_MODELS,
                    strictClockBranchRatesDefinition(
                            state,
                            treeModel,
                            clockRateParameters.getFirst()
                    )
            );
        }
    }

    private BeastXXmlElement strictClockBranchRatesDefinition(
            BeastXState state,
            TreeModel treeModel,
            Parameter clockRateParameter
    ) {
        String id =
                treeId(treeModel) + "_strictClockBranchRates";

        return BeastXXmlElement.element("strictClockBranchRates")
                .withId(id)
                .withChild(
                        parameterElement(
                                state,
                                "rate",
                                clockRateParameter,
                                id + "_rate",
                                clockRateParameter.getParameterValue(0),
                                0.0,
                                null
                        )
                );
    }

    private void addTaxonDefinitions(
            BeastXXmlPlan plan,
            TreeModel treeModel,
            Set<String> emittedTaxonIds
    ) {
        for (int i = 0; i < treeModel.getTaxonCount(); i++) {
            String taxonId =
                    treeModel.getTaxonId(i);

            if (taxonId == null || taxonId.isBlank()) {
                throw new IllegalArgumentException("Cannot serialize unnamed BEAST X taxon.");
            }

            if (!emittedTaxonIds.add(taxonId)) {
                continue;
            }

            plan.add(
                    BeastXXmlPlan.Section.TAXA,
                    BeastXXmlElement.element("taxon")
                            .withId(taxonId)
            );
        }
    }

    private BeastXXmlElement startingTreeDefinition(TreeModel treeModel) {
        return BeastXXmlElement.element("newick")
                .withId(startingTreeId(treeModel))
                .withAttribute("units", "years")
                .withAttribute("usingDates", "false")
                .withAttribute("usingHeights", "false")
                .withText(ensureTrailingSemicolon(treeModel.getNewick()));
    }

    private BeastXXmlElement treeModelDefinition(TreeModel treeModel) {
        String id =
                treeId(treeModel);

        return BeastXXmlElement.element("treeModel")
                .withId(id)
                .withChild(BeastXXmlElement.ref("newick", startingTreeId(treeModel)))
                .withChild(
                        BeastXXmlElement.element("rootHeight")
                                .withChild(
                                        BeastXXmlElement.element("parameter")
                                                .withId(id + ".rootHeight")
                                )
                )
                .withChild(
                        BeastXXmlElement.element("nodeHeights")
                                .withAttribute("internalNodes", "true")
                                .withAttribute("rootNode", "false")
                                .withChild(
                                        BeastXXmlElement.element("parameter")
                                                .withId(id + ".internalNodeHeights")
                                )
                )
                .withChild(
                        BeastXXmlElement.element("nodeHeights")
                                .withAttribute("internalNodes", "true")
                                .withAttribute("rootNode", "true")
                                .withChild(
                                        BeastXXmlElement.element("parameter")
                                                .withId(id + ".allInternalNodeHeights")
                                )
                );
    }

    private BeastXXmlElement treePriorModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        if (treePrior instanceof SpeciationLikelihood) {
            BirthDeathGernhard08Model birthDeathModel =
                    getBirthDeathModel(treePrior);

            if (isYuleCompatible(birthDeathModel)) {
                return yuleModelDefinition(state, treePrior, birthDeathModel);
            }

            return birthDeathModelDefinition(state, treePrior, birthDeathModel);
        }

        if (treePrior instanceof CoalescentLikelihood) {
            return constantPopulationModelDefinition(state, treePrior);
        }

        throw unsupported("Only SpeciationLikelihood and CoalescentLikelihood tree priors are supported.");
    }

    private BeastXXmlElement yuleModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior,
            BirthDeathGernhard08Model yuleModel
    ) {
        return BeastXXmlElement.element("yuleModel")
                .withId(treePriorModelId(treePrior))
                .withAttribute("units", "years")
                .withChild(
                        parameterElement(
                                state,
                                "birthRate",
                                birthDeathVariable(yuleModel, 0),
                                priorId(treePrior) + "_birthRate",
                                yuleModel.getR(),
                                0.0,
                                null
                        )
                );
    }

    private BeastXXmlElement birthDeathModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior,
            BirthDeathGernhard08Model birthDeathModel
    ) {
        return BeastXXmlElement.element("birthDeathModel")
                .withId(treePriorModelId(treePrior))
                .withAttribute("type", "LABELED")
                .withAttribute("units", "years")
                .withChild(
                        parameterElement(
                                state,
                                "birthMinusDeathRate",
                                birthDeathVariable(birthDeathModel, 0),
                                priorId(treePrior) + "_birthMinusDeathRate",
                                birthDeathModel.getR(),
                                0.0,
                                null
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "relativeDeathRate",
                                birthDeathVariable(birthDeathModel, 1),
                                priorId(treePrior) + "_relativeDeathRate",
                                birthDeathModel.getA(),
                                0.0,
                                1.0
                        )
                )
                .withChild(
                        parameterElement(
                                state,
                                "sampleProbability",
                                birthDeathVariable(birthDeathModel, 2),
                                priorId(treePrior) + "_sampleProbability",
                                birthDeathModel.getRho(),
                                0.0,
                                1.0
                        )
                );
    }

    private BeastXXmlElement constantPopulationModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        ConstantPopulationModel constantPopulationModel =
                getConstantPopulationModel(treePrior);

        Parameter populationSize =
                constantPopulationVariable(constantPopulationModel);

        return BeastXXmlElement.element("constantSize")
                .withId(treePriorModelId(treePrior))
                .withAttribute("units", "years")
                .withChild(
                        parameterElement(
                                state,
                                "populationSize",
                                populationSize,
                                priorId(treePrior) + "_populationSize",
                                populationSize.getParameterValue(0),
                                0.0,
                                null
                        )
                );
    }

    private BeastXXmlElement parameterElement(
            BeastXState state,
            String elementName,
            Parameter parameter,
            String fallbackId,
            double fallbackValue,
            Double lower,
            Double upper
    ) {
        BeastXXmlElement child;

        if (parameter != null && state.stateNodes.containsKey(parameter)) {
            child =
                    parameterReference(parameter);
        } else {
            child =
                    inlineParameterDefinition(
                            fallbackId,
                            fallbackValue,
                            lower,
                            upper
                    );
        }

        return BeastXXmlElement.element(elementName)
                .withChild(child);
    }

    private void addScalarPriors(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<Map.Entry<Parameter, AbstractDistributionLikelihood>> entries =
                new ArrayList<>(state.priorDistributions.entrySet());

        entries.sort(Comparator.comparing(entry -> parameterId(entry.getKey())));

        for (Map.Entry<Parameter, AbstractDistributionLikelihood> entry : entries) {
            DistributionLikelihood likelihood =
                    (DistributionLikelihood) entry.getValue();

            Distribution distribution =
                    likelihood.getDistribution();

            if (distribution instanceof LogNormalDistributionModel logNormalDistribution) {
                plan.add(
                        BeastXXmlPlan.Section.MCMC_PRIOR,
                        logNormalPrior(entry.getKey(), likelihood, logNormalDistribution)
                );
            } else if (distribution instanceof BetaDistributionModel betaDistribution) {
                plan.add(
                        BeastXXmlPlan.Section.MCMC_PRIOR,
                        betaPrior(entry.getKey(), likelihood, betaDistribution)
                );
            } else {
                throw unsupported("Only LogNormal and Beta scalar priors are supported.");
            }
        }
    }

    private BeastXXmlElement logNormalPrior(
            Parameter parameter,
            DistributionLikelihood likelihood,
            LogNormalDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return BeastXXmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        BeastXXmlElement.element("distribution")
                                .withChild(
                                        BeastXXmlElement.element("logNormalDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withChild(
                                                        BeastXXmlElement.element("mu")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_mu",
                                                                                distribution.getMu(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                                .withChild(
                                                        BeastXXmlElement.element("precision")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_precision",
                                                                                distribution.getPrecision(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                )
                )
                .withChild(
                        BeastXXmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
    }

    private BeastXXmlElement betaPrior(
            Parameter parameter,
            DistributionLikelihood likelihood,
            BetaDistributionModel distribution
    ) {
        String priorId =
                likelihood.getId();

        return BeastXXmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        BeastXXmlElement.element("distribution")
                                .withChild(
                                        BeastXXmlElement.element("betaDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withChild(
                                                        betaShapeParameter(
                                                                "alpha",
                                                                betaDistributionVariable(distribution, 0),
                                                                priorId + "_alpha"
                                                        )
                                                )
                                                .withChild(
                                                        betaShapeParameter(
                                                                "beta",
                                                                betaDistributionVariable(distribution, 1),
                                                                priorId + "_beta"
                                                        )
                                                )
                                )
                )
                .withChild(
                        BeastXXmlElement.element("data")
                                .withChild(parameterReference(parameter))
                );
    }

    private BeastXXmlElement betaShapeParameter(
            String elementName,
            Parameter parameter,
            String fallbackId
    ) {
        return BeastXXmlElement.element(elementName)
                .withChild(
                        inlineParameterDefinition(
                                fallbackId,
                                parameter.getParameterValue(0),
                                0.0,
                                null
                        )
                );
    }

    private void addTreePriors(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<Map.Entry<TreeModel, AbstractModelLikelihood>> entries =
                new ArrayList<>(state.treePriorDistributions.entrySet());

        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : entries) {
            plan.add(
                    BeastXXmlPlan.Section.MCMC_PRIOR,
                    treePrior(entry.getKey(), entry.getValue())
            );
        }
    }

    private BeastXXmlElement treePrior(
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        if (treePrior instanceof SpeciationLikelihood) {
            return speciationTreePrior(treeModel, treePrior);
        }

        if (treePrior instanceof CoalescentLikelihood) {
            return coalescentTreePrior(treeModel, treePrior);
        }

        throw unsupported("Only SpeciationLikelihood and CoalescentLikelihood tree priors are supported.");
    }

    private BeastXXmlElement speciationTreePrior(
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        BirthDeathGernhard08Model birthDeathModel =
                getBirthDeathModel(treePrior);

        String modelTag =
                isYuleCompatible(birthDeathModel)
                        ? "yuleModel"
                        : "birthDeathModel";

        return BeastXXmlElement.element("speciationLikelihood")
                .withId(priorId(treePrior))
                .withChild(
                        BeastXXmlElement.element("model")
                                .withChild(
                                        BeastXXmlElement.ref(modelTag, treePriorModelId(treePrior))
                                )
                )
                .withChild(
                        BeastXXmlElement.element("speciesTree")
                                .withChild(treeReference(treeModel))
                );
    }

    private BeastXXmlElement coalescentTreePrior(
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        return BeastXXmlElement.element("coalescentLikelihood")
                .withId(priorId(treePrior))
                .withChild(
                        BeastXXmlElement.element("model")
                                .withChild(
                                        BeastXXmlElement.ref("constantSize", treePriorModelId(treePrior))
                                )
                )
                .withChild(
                        BeastXXmlElement.element("populationTree")
                                .withChild(treeReference(treeModel))
                );
    }

    private void addOperators(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        addParameterOperators(plan, state);
        addTreeOperators(plan, state);
    }

    private void addParameterOperators(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.sort(Comparator.comparing(BeastXXmlPlanBuilder::parameterId));

        for (Parameter parameter : parameters) {
            if (hasFiniteLowerAndUpperBounds(parameter)) {
                plan.add(
                        BeastXXmlPlan.Section.OPERATORS,
                        randomWalkOperator(state, parameter)
                );
            } else {
                plan.add(
                        BeastXXmlPlan.Section.OPERATORS,
                        scaleOperator(state, parameter)
                );
            }
        }
    }

    private BeastXXmlElement scaleOperator(
            BeastXState state,
            Parameter parameter
    ) {
        String id =
                parameterId(parameter);

        return BeastXXmlElement.element("scaleOperator")
                .withId(id + "_scale")
                .withAttribute("scaleFactor", format(state.operatorConfig.parameterScaleFactor))
                .withAttribute("weight", format(state.operatorConfig.parameterOperatorWeight))
                .withChild(parameterReference(parameter));
    }

    private BeastXXmlElement randomWalkOperator(
            BeastXState state,
            Parameter parameter
    ) {
        String id =
                parameterId(parameter);

        return BeastXXmlElement.element("randomWalkOperator")
                .withId(id + "_randomWalk")
                .withAttribute("windowSize", format(state.operatorConfig.randomWalkWindowSize))
                .withAttribute("weight", format(state.operatorConfig.parameterOperatorWeight))
                .withAttribute("boundaryCondition", "reflecting")
                .withChild(parameterReference(parameter));
    }

    private void addTreeOperators(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        List<TreeModel> trees =
                new ArrayList<>(state.treePriorDistributions.keySet());

        trees.sort(Comparator.comparing(BeastXXmlPlanBuilder::treeId));

        for (TreeModel treeModel : trees) {
            String id =
                    treeId(treeModel);

            plan.add(
                    BeastXXmlPlan.Section.OPERATORS,
                    treeOperator(
                            "narrowExchange",
                            id + "_narrowExchange",
                            state.operatorConfig.treeNarrowExchangeWeight,
                            treeModel
                    )
            );

            plan.add(
                    BeastXXmlPlan.Section.OPERATORS,
                    treeOperator(
                            "wideExchange",
                            id + "_wideExchange",
                            state.operatorConfig.treeWideExchangeWeight,
                            treeModel
                    )
            );

            plan.add(
                    BeastXXmlPlan.Section.OPERATORS,
                    subtreeSlideOperator(state, treeModel)
            );

            plan.add(
                    BeastXXmlPlan.Section.OPERATORS,
                    treeOperator(
                            "wilsonBalding",
                            id + "_wilsonBalding",
                            state.operatorConfig.treeWilsonBaldingWeight,
                            treeModel
                    )
            );
        }
    }

    private BeastXXmlElement treeOperator(
            String elementName,
            String id,
            double weight,
            TreeModel treeModel
    ) {
        return BeastXXmlElement.element(elementName)
                .withId(id)
                .withAttribute("weight", format(weight))
                .withChild(treeReference(treeModel));
    }

    private BeastXXmlElement subtreeSlideOperator(
            BeastXState state,
            TreeModel treeModel
    ) {
        String id =
                treeId(treeModel);

        return BeastXXmlElement.element("subtreeSlide")
                .withId(id + "_subtreeSlide")
                .withAttribute("weight", format(state.operatorConfig.treeSubtreeSlideWeight))
                .withAttribute("size", format(state.operatorConfig.treeSubtreeSlideSize))
                .withAttribute("gaussian", "true")
                .withChild(treeReference(treeModel));
    }

    private void addLoggers(
            BeastXXmlPlan plan,
            BeastXState state
    ) {
        int loggerIndex =
                1;

        for (BeastXState.ScreenLoggerSpec spec : state.screenLoggerSpecs) {
            plan.add(
                    BeastXXmlPlan.Section.MCMC_LOGGERS,
                    parameterLogger(
                            "screenLogger" + loggerIndex,
                            spec.logEvery,
                            null,
                            getLoggedParameters(state, spec.parameterNames)
                    )
            );

            loggerIndex++;
        }

        for (BeastXState.FileLoggerSpec spec : state.fileLoggerSpecs) {
            plan.add(
                    BeastXXmlPlan.Section.MCMC_LOGGERS,
                    parameterLogger(
                            "fileLogger" + loggerIndex,
                            spec.logEvery,
                            spec.fileName,
                            getLoggedParameters(state, spec.parameterNames)
                    )
            );

            loggerIndex++;
        }

        int treeLoggerIndex =
                1;

        for (BeastXState.TreeLoggerSpec spec : state.treeLoggerSpecs) {
            plan.add(
                    BeastXXmlPlan.Section.MCMC_LOGGERS,
                    treeLogger(
                            "treeLogger" + treeLoggerIndex,
                            spec.logEvery,
                            spec.fileName,
                            getLoggedTrees(state, spec.treeNames)
                    )
            );

            treeLoggerIndex++;
        }
    }

    private BeastXXmlElement parameterLogger(
            String id,
            long logEvery,
            String fileName,
            List<Parameter> parameters
    ) {
        BeastXXmlElement logger =
                BeastXXmlElement.element("log")
                        .withId(id)
                        .withAttribute("logEvery", logEvery);

        if (fileName != null) {
            logger =
                    logger.withAttribute("fileName", fileName)
                            .withAttribute("overwrite", "true");
        }

        for (Parameter parameter : parameters) {
            logger =
                    logger.withChild(parameterReference(parameter));
        }

        return logger;
    }

    private BeastXXmlElement treeLogger(
            String id,
            long logEvery,
            String fileName,
            List<TreeModel> treeModels
    ) {
        BeastXXmlElement logger =
                BeastXXmlElement.element("logTree")
                        .withId(id)
                        .withAttribute("logEvery", logEvery)
                        .withAttribute("fileName", fileName)
                        .withAttribute("overwrite", "true")
                        .withAttribute("nexusFormat", "true");

        for (TreeModel treeModel : treeModels) {
            logger =
                    logger.withChild(treeReference(treeModel));
        }

        return logger;
    }

    private List<Parameter> getLoggedParameters(
            BeastXState state,
            List<String> parameterNames
    ) {
        List<Parameter> parameters =
                new ArrayList<>();

        if (parameterNames == null) {
            parameters.addAll(state.stateNodes.keySet());
        } else {
            for (String parameterName : parameterNames) {
                Parameter parameter =
                        state.stateNodesByPhyloSpecName.get(parameterName);

                if (parameter == null) {
                    throw new IllegalArgumentException(
                            "No BEAST X state node named '" + parameterName + "' exists for XML logger."
                    );
                }

                parameters.add(parameter);
            }
        }

        parameters.sort(Comparator.comparing(BeastXXmlPlanBuilder::parameterId));

        return parameters;
    }

    private List<TreeModel> getLoggedTrees(
            BeastXState state,
            List<String> treeNames
    ) {
        List<TreeModel> trees =
                new ArrayList<>();

        if (treeNames == null) {
            trees.addAll(state.treePriorDistributions.keySet());
        } else {
            for (String treeName : treeNames) {
                TreeModel treeModel =
                        state.treeModelsByPhyloSpecName.get(treeName);

                if (treeModel == null) {
                    throw new IllegalArgumentException(
                            "No BEAST X tree model named '" + treeName + "' exists for XML tree logger."
                    );
                }

                trees.add(treeModel);
            }
        }

        trees.sort(Comparator.comparing(BeastXXmlPlanBuilder::treeId));

        return trees;
    }

    private BeastXXmlElement parameterDefinition(Parameter parameter) {
        BeastXXmlElement element =
                BeastXXmlElement.element("parameter")
                        .withId(parameterId(parameter))
                        .withAttribute("value", format(parameter.getParameterValue(0)));

        Bounds<Double> bounds =
                parameter.getBounds();

        if (bounds != null) {
            double lower =
                    bounds.getLowerLimit(0);

            double upper =
                    bounds.getUpperLimit(0);

            if (Double.isFinite(lower)) {
                element =
                        element.withAttribute("lower", format(lower));
            }

            if (Double.isFinite(upper)) {
                element =
                        element.withAttribute("upper", format(upper));
            }
        }

        return element;
    }

    private BeastXXmlElement inlineParameterDefinition(
            String id,
            double value,
            Double lower,
            Double upper
    ) {
        BeastXXmlElement element =
                BeastXXmlElement.element("parameter")
                        .withId(id)
                        .withAttribute("value", format(value));

        if (lower != null) {
            element =
                    element.withAttribute("lower", format(lower));
        }

        if (upper != null) {
            element =
                    element.withAttribute("upper", format(upper));
        }

        return element;
    }

    private BeastXXmlElement parameterReference(Parameter parameter) {
        return BeastXXmlElement.ref("parameter", parameterId(parameter));
    }

    private BeastXXmlElement treeReference(TreeModel treeModel) {
        return BeastXXmlElement.ref("treeModel", treeId(treeModel));
    }

    private static BirthDeathGernhard08Model getBirthDeathModel(AbstractModelLikelihood treePrior) {
        if (!(treePrior instanceof SpeciationLikelihood speciationLikelihood)) {
            throw unsupported("Only SpeciationLikelihood tree priors can be serialized as Yule or BirthDeath XML.");
        }

        SpeciationModel speciationModel =
                speciationLikelihood.getSpeciationModel();

        if (!(speciationModel instanceof BirthDeathGernhard08Model birthDeathModel)) {
            throw unsupported("Only Yule and BirthDeath tree priors are supported.");
        }

        return birthDeathModel;
    }

    private static ConstantPopulationModel getConstantPopulationModel(AbstractModelLikelihood treePrior) {
        if (!(treePrior instanceof CoalescentLikelihood coalescentLikelihood)) {
            throw unsupported("Only CoalescentLikelihood tree priors can be serialized as coalescent XML.");
        }

        DemographicModel demographicModel =
                coalescentLikelihood.getDemoModel();

        if (!(demographicModel instanceof ConstantPopulationModel constantPopulationModel)) {
            throw unsupported("Only constant-population Coalescent tree priors are supported.");
        }

        return constantPopulationModel;
    }

    private static Parameter birthDeathVariable(
            BirthDeathGernhard08Model birthDeathModel,
            int variableIndex
    ) {
        if (variableIndex >= birthDeathModel.getVariableCount()) {
            return null;
        }

        Variable<?> variable =
                birthDeathModel.getVariable(variableIndex);

        if (variable instanceof Parameter parameter) {
            return parameter;
        }

        return null;
    }

    private static Parameter betaDistributionVariable(
            BetaDistributionModel distribution,
            int variableIndex
    ) {
        if (variableIndex >= distribution.getVariableCount()) {
            throw unsupported("Beta XML export requires alpha and beta parameters.");
        }

        Variable<?> variable =
                distribution.getVariable(variableIndex);

        if (variable instanceof Parameter parameter) {
            return parameter;
        }

        throw unsupported("Beta XML export requires alpha and beta parameters.");
    }

    private static Parameter constantPopulationVariable(ConstantPopulationModel populationModel) {
        if (populationModel.getVariableCount() < 1) {
            throw unsupported("Constant-population Coalescent XML export requires a population size parameter.");
        }

        Variable<?> variable =
                populationModel.getVariable(0);

        if (!(variable instanceof Parameter parameter)) {
            throw unsupported("Constant-population Coalescent XML export requires a population size parameter.");
        }

        return parameter;
    }

    private static boolean isYuleCompatible(BirthDeathGernhard08Model model) {
        return model.isYule()
                || (
                approximatelyZero(model.getA())
                        && approximatelyOne(model.getRho())
        );
    }

    private static boolean hasFiniteLowerAndUpperBounds(Parameter parameter) {
        Bounds<Double> bounds =
                parameter.getBounds();

        if (bounds == null) {
            return false;
        }

        double lower =
                bounds.getLowerLimit(0);

        double upper =
                bounds.getUpperLimit(0);

        return Double.isFinite(lower) && Double.isFinite(upper);
    }

    private static boolean approximatelyZero(double value) {
        return Math.abs(value) < 1.0e-12;
    }

    private static boolean approximatelyOne(double value) {
        return Math.abs(value - 1.0) < 1.0e-12;
    }

    private static String parameterId(Parameter parameter) {
        String id =
                parameter.getId();

        if (id == null || id.isBlank()) {
            id =
                    parameter.getParameterName();
        }

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X parameter.");
        }

        return id;
    }

    private static String treeId(TreeModel treeModel) {
        String id =
                treeModel.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X tree model.");
        }

        return id;
    }

    private static String priorId(AbstractModelLikelihood prior) {
        String id =
                prior.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X prior.");
        }

        return id;
    }

    private static String startingTreeId(TreeModel treeModel) {
        return treeId(treeModel) + "_startingTree";
    }

    private static String treePriorModelId(AbstractModelLikelihood treePrior) {
        return priorId(treePrior) + "_model";
    }

    private static String ensureTrailingSemicolon(String newick) {
        String trimmed =
                newick.trim();

        if (trimmed.endsWith(";")) {
            return trimmed;
        }

        return trimmed + ";";
    }

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend BeastXXmlPlanBuilder before exporting this model class to XML."
        );
    }

    private static String format(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Cannot serialize NaN as a BEAST X XML number.");
        }

        if (value == Double.POSITIVE_INFINITY) {
            return "Infinity";
        }

        if (value == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        }

        return Double.toString(value);
    }
}