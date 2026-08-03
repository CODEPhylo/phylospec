package tiling.xml;

import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.coalescent.demographicmodel.ExponentialGrowthModel;
import dr.evomodel.coalescent.demographicmodel.LogisticGrowthModel;
import dr.evomodel.coalescent.demographicmodel.PiecewisePopulationModel;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.BirthDeathSerialSamplingModel;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.speciation.SpeciationModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.MultivariateDistributionLikelihood;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Parameter;
import dr.math.distributions.DirichletDistribution;
import dr.math.distributions.Distribution;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.xml.builders.CalibrationPriorXmlBuilder;
import tiling.xml.builders.DirichletPriorXmlBuilder;
import tiling.xml.builders.PhyloCTMCXmlBuilder;
import tiling.xml.builders.ScalarPriorXmlBuilder;

import java.util.Map;

/**
 * Validates that a BEAST X model only contains components supported by XML export.
 */
public class XmlExportValidator {
    private final ScalarPriorXmlBuilder scalarPriorXmlBuilder =
            new ScalarPriorXmlBuilder();

    private final DirichletPriorXmlBuilder dirichletPriorXmlBuilder =
            new DirichletPriorXmlBuilder();

    private final CalibrationPriorXmlBuilder calibrationPriorXmlBuilder =
            new CalibrationPriorXmlBuilder();

    private final PhyloCTMCXmlBuilder phyloCTMCXmlBuilder =
            new PhyloCTMCXmlBuilder();

    public void validate(BeastXModel model) {
        BeastXState state =
                model.beastState;

        validateCalibrationPriors(state);
        validateLikelihoodExportBoundary(state);
        validateNonEmptyModel(state);
        validateMCMCLoggerRequirement(state);
        validateParameterPriors(state);
        validateTreePriors(state);
        validateObservedTreeDistributions(state);
    }

    private void validateLikelihoodExportBoundary(BeastXState state) {
        phyloCTMCXmlBuilder.validateExportBoundary(state);
    }

    private void validateCalibrationPriors(BeastXState state) {
        for (AbstractDistributionLikelihood calibrationPrior : state.calibrationPriorDistributions) {
            if (!calibrationPriorXmlBuilder.supports(calibrationPrior)) {
                throw unsupported(
                        "Only uniform root/MRCA calibration priors backed by TMRCAStatistic are supported."
                );
            }
        }
    }

    private void validateNonEmptyModel(BeastXState state) {
        if (
                state.priorDistributions.isEmpty()
                        && state.treePriorDistributions.isEmpty()
                        && state.observedTreeDistributions.isEmpty()
                        && state.likelihoodDistributions.isEmpty()
        ) {
            throw unsupported("At least one scalar prior, tree prior, or likelihood is required.");
        }
    }

    private void validateMCMCLoggerRequirement(BeastXState state) {
        if (
                state.screenLoggerSpecs.isEmpty()
                        && state.fileLoggerSpecs.isEmpty()
                        && state.treeLoggerSpecs.isEmpty()
        ) {
            throw unsupported("At least one logger is required for XML MCMC execution.");
        }
    }

    private void validateParameterPriors(BeastXState state) {
        for (Map.Entry<Parameter, AbstractDistributionLikelihood> entry : state.priorDistributions.entrySet()) {
            Parameter parameter =
                    entry.getKey();

            AbstractDistributionLikelihood likelihood =
                    entry.getValue();

            if (likelihood instanceof DistributionLikelihood distributionLikelihood) {
                validateIndependentDistributionPrior(distributionLikelihood);
                continue;
            }

            validateDirichletPrior(parameter, likelihood);
        }
    }

    private void validateIndependentDistributionPrior(DistributionLikelihood likelihood) {
        Distribution distribution =
                likelihood.getDistribution();

        if (!scalarPriorXmlBuilder.supports(distribution)) {
            throw unsupported(
                    "Only Normal, LogNormal, Gamma, Exponential, Uniform, and Beta independent distribution priors are supported."
            );
        }
    }

    private void validateDirichletPrior(
            Parameter parameter,
            AbstractDistributionLikelihood likelihood
    ) {
        if (!(likelihood instanceof MultivariateDistributionLikelihood multivariateLikelihood)) {
            throw unsupported(
                    "Only independent DistributionLikelihood priors and Dirichlet multivariate priors are supported for parameters."
            );
        }

        if (!(multivariateLikelihood.getDistribution() instanceof DirichletDistribution dirichletDistribution)) {
            throw unsupported("Only Dirichlet multivariate priors are supported for non-scalar multivariate parameters.");
        }

        double[] counts =
                dirichletPriorXmlBuilder.counts(dirichletDistribution);

        if (counts.length != parameter.getDimension()) {
            throw unsupported("Dirichlet prior dimension must match the simplex parameter dimension.");
        }
    }

    private void validateTreePriors(BeastXState state) {
        for (AbstractModelLikelihood treePrior : state.treePriorDistributions.values()) {
            if (treePrior instanceof SpeciationLikelihood speciationLikelihood) {
                validateSpeciationTreePrior(speciationLikelihood);
            } else if (treePrior instanceof CoalescentLikelihood coalescentLikelihood) {
                validateCoalescentTreePrior(coalescentLikelihood);
            } else {
                throw unsupported("Only SpeciationLikelihood and CoalescentLikelihood tree priors are supported.");
            }
        }
    }

    private void validateObservedTreeDistributions(BeastXState state) {
        for (AbstractModelLikelihood distribution : state.observedTreeDistributions.values()) {
            if (distribution instanceof SpeciationLikelihood speciationLikelihood) {
                validateSpeciationTreePrior(speciationLikelihood);
                continue;
            }

            throw unsupported(
                    "Only observed Yule and BirthDeath tree distributions are supported."
            );
        }
    }

    private void validateSpeciationTreePrior(SpeciationLikelihood speciationLikelihood) {
        SpeciationModel speciationModel =
                speciationLikelihood.getSpeciationModel();

        if (
                speciationModel instanceof BirthDeathGernhard08Model
                        || speciationModel instanceof BirthDeathSerialSamplingModel
        ) {
            return;
        }

        throw unsupported("Only Yule, BirthDeath, and FossilizedBirthDeath speciation tree priors are supported.");
    }

    private void validateCoalescentTreePrior(CoalescentLikelihood coalescentLikelihood) {
        DemographicModel demographicModel =
                coalescentLikelihood.getDemoModel();

        if (
                demographicModel instanceof ConstantPopulationModel
                        || demographicModel instanceof ExponentialGrowthModel
                        || demographicModel instanceof LogisticGrowthModel
                        || demographicModel instanceof PiecewisePopulationModel
        ) {
            return;
        }

        throw unsupported(
                "Only constant-population, exponential-growth, logistic-growth, and piecewise-population Coalescent tree priors are supported."
        );
    }

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend XmlExportValidator before exporting this model class to XML."
        );
    }
}
