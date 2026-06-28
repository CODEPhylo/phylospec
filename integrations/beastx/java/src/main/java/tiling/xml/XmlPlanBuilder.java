package tiling.xml;

import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.MultivariateDistributionLikelihood;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Parameter;
import dr.math.distributions.DirichletDistribution;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.xml.builders.BranchRateModelXmlBuilder;
import tiling.xml.builders.CalibrationPriorXmlBuilder;
import tiling.xml.builders.DirichletPriorXmlBuilder;
import tiling.xml.builders.LoggerXmlBuilder;
import tiling.xml.builders.OperatorXmlBuilder;
import tiling.xml.builders.PhyloCTMCXmlBuilder;
import tiling.xml.builders.ScalarPriorXmlBuilder;
import tiling.xml.builders.StateParameterXmlBuilder;
import tiling.xml.builders.TreeModelXmlBuilder;
import tiling.xml.builders.TreePriorXmlBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds an XmlPlan from the BEAST X backend state.
 *
 * Selects the model components that can be exported to XML and prepares
 * the structured information used by the XML generators.
 */
public class XmlPlanBuilder {
    private final XmlExportValidator validator =
            new XmlExportValidator();

    private final ScalarPriorXmlBuilder scalarPriorXmlBuilder =
            new ScalarPriorXmlBuilder();

    private final TreePriorXmlBuilder treePriorXmlBuilder =
            new TreePriorXmlBuilder();

    private final BranchRateModelXmlBuilder branchRateModelXmlBuilder =
            new BranchRateModelXmlBuilder();

    private final OperatorXmlBuilder operatorXmlBuilder =
            new OperatorXmlBuilder();

    private final LoggerXmlBuilder loggerXmlBuilder =
            new LoggerXmlBuilder();

    private final TreeModelXmlBuilder treeModelXmlBuilder =
            new TreeModelXmlBuilder();

    private final StateParameterXmlBuilder stateParameterXmlBuilder =
            new StateParameterXmlBuilder();

    private final DirichletPriorXmlBuilder dirichletPriorXmlBuilder =
            new DirichletPriorXmlBuilder();

    private final CalibrationPriorXmlBuilder calibrationPriorXmlBuilder =
            new CalibrationPriorXmlBuilder();

    private final PhyloCTMCXmlBuilder phyloCTMCXmlBuilder =
            new PhyloCTMCXmlBuilder();

    public XmlPlan build(BeastXModel model) {
        validator.validate(model);

        BeastXState state =
                model.beastState;

        XmlPlan plan =
                new XmlPlan();

        addStateParameters(plan, state);
        addTreeDefinitions(plan, state);
        addBranchRateModels(plan, state);

        phyloCTMCXmlBuilder.addComponents(plan, state);

        addParameterPriors(plan, state);
        addTreePriors(plan, state);
        addCalibrationPriors(plan, state);
        addOperators(plan, state);
        addLoggers(plan, state);

        return plan;
    }

    public XmlPlan buildPhyloCTMCComponentLayer(BeastXModel model) {
        return phyloCTMCXmlBuilder.buildComponentLayer(model.beastState);
    }

    private void addCalibrationPriors(
            XmlPlan plan,
            BeastXState state
    ) {
        List<AbstractDistributionLikelihood> calibrationPriors =
                new ArrayList<>(state.calibrationPriorDistributions);

        calibrationPriors.sort(Comparator.comparing(calibrationPriorXmlBuilder::priorId));

        for (AbstractDistributionLikelihood calibrationPrior : calibrationPriors) {
            plan.add(
                    XmlPlan.Section.STATISTICS,
                    calibrationPriorXmlBuilder.buildStatistic(calibrationPrior)
            );

            plan.add(
                    XmlPlan.Section.MCMC_PRIOR,
                    calibrationPriorXmlBuilder.buildPrior(calibrationPrior)
            );
        }
    }

    private void addStateParameters(
            XmlPlan plan,
            BeastXState state
    ) {
        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.sort(Comparator.comparing(XmlPlanBuilder::parameterId));

        for (Parameter parameter : parameters) {
            plan.add(
                    XmlPlan.Section.PARAMETERS,
                    stateParameterXmlBuilder.buildParameter(parameter)
            );
        }
    }

    private void addTreeDefinitions(
            XmlPlan plan,
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

            treeModelXmlBuilder.addTreeDefinitions(
                    plan,
                    treeModel,
                    emittedTaxonIds
            );

            plan.add(
                    XmlPlan.Section.TREE_PRIOR_MODELS,
                    treePriorModelDefinition(state, entry.getValue())
            );
        }
    }

    private void addBranchRateModels(
            XmlPlan plan,
            BeastXState state
    ) {
        addStrictClockBranchRateModels(plan, state);
        addRelaxedClockBranchRateModels(plan, state);
    }

    private void addStrictClockBranchRateModels(
            XmlPlan plan,
            BeastXState state
    ) {
        List<Map.Entry<TreeModel, List<Parameter>>> entries =
                new ArrayList<>(state.treeClockRateParameters.entrySet());

        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        for (Map.Entry<TreeModel, List<Parameter>> entry : entries) {
            TreeModel treeModel =
                    entry.getKey();

            if (state.treeRelaxedClockModels.containsKey(treeModel)) {
                continue;
            }

            List<Parameter> clockRateParameters =
                    entry.getValue();

            if (clockRateParameters.size() != 1) {
                throw unsupported(
                        "Only one strict-clock rate parameter per tree is supported for XML export."
                );
            }

            plan.add(
                    XmlPlan.Section.BRANCH_RATE_MODELS,
                    branchRateModelXmlBuilder.buildStrictClockBranchRates(
                            state,
                            treeModel,
                            clockRateParameters.getFirst()
                    )
            );
        }
    }

    private void addRelaxedClockBranchRateModels(
            XmlPlan plan,
            BeastXState state
    ) {
        List<Map.Entry<TreeModel, BeastXState.RelaxedClockSpec>> entries =
                new ArrayList<>(state.treeRelaxedClockModels.entrySet());

        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        for (Map.Entry<TreeModel, BeastXState.RelaxedClockSpec> entry : entries) {
            TreeModel treeModel =
                    entry.getKey();

            BeastXState.RelaxedClockSpec spec =
                    entry.getValue();

            plan.add(
                    XmlPlan.Section.BRANCH_RATE_MODELS,
                    branchRateModelXmlBuilder.buildRelaxedClockBranchRates(
                            state,
                            treeModel,
                            spec
                    )
            );
        }
    }

    private XmlElement treePriorModelDefinition(
            BeastXState state,
            AbstractModelLikelihood treePrior
    ) {
        return treePriorXmlBuilder.buildModelDefinition(state, treePrior);
    }

    private void addParameterPriors(
            XmlPlan plan,
            BeastXState state
    ) {
        List<Map.Entry<Parameter, AbstractDistributionLikelihood>> entries =
                new ArrayList<>(state.priorDistributions.entrySet());

        entries.sort(Comparator.comparing(entry -> parameterId(entry.getKey())));

        for (Map.Entry<Parameter, AbstractDistributionLikelihood> entry : entries) {
            AbstractDistributionLikelihood likelihood =
                    entry.getValue();

            if (likelihood instanceof DistributionLikelihood distributionLikelihood) {
                plan.add(
                        XmlPlan.Section.MCMC_PRIOR,
                        scalarPriorXmlBuilder.buildPrior(entry.getKey(), distributionLikelihood)
                );

                continue;
            }

            if (likelihood instanceof MultivariateDistributionLikelihood multivariateLikelihood) {
                if (multivariateLikelihood.getDistribution() instanceof DirichletDistribution dirichletDistribution) {
                    plan.add(
                            XmlPlan.Section.MCMC_PRIOR,
                            dirichletPriorXmlBuilder.buildPrior(
                                    entry.getKey(),
                                    multivariateLikelihood,
                                    dirichletDistribution
                            )
                    );
                    continue;
                }
            }

            throw unsupported("Only scalar DistributionLikelihood and Dirichlet multivariate priors are supported.");
        }
    }

    private void addTreePriors(
            XmlPlan plan,
            BeastXState state
    ) {
        List<Map.Entry<TreeModel, AbstractModelLikelihood>> entries =
                new ArrayList<>(state.treePriorDistributions.entrySet());

        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : entries) {
            plan.add(
                    XmlPlan.Section.MCMC_PRIOR,
                    treePrior(entry.getKey(), entry.getValue())
            );
        }
    }

    private XmlElement treePrior(
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        return treePriorXmlBuilder.buildPrior(treeModel, treePrior);
    }

    private void addOperators(
            XmlPlan plan,
            BeastXState state
    ) {
        for (XmlElement operator : operatorXmlBuilder.buildOperators(state)) {
            plan.add(
                    XmlPlan.Section.OPERATORS,
                    operator
            );
        }
    }

    private void addLoggers(
            XmlPlan plan,
            BeastXState state
    ) {
        for (XmlElement logger : loggerXmlBuilder.buildLoggers(state)) {
            plan.add(
                    XmlPlan.Section.MCMC_LOGGERS,
                    logger
            );
        }
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

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend XmlPlanBuilder before exporting this model class to XML."
        );
    }
}