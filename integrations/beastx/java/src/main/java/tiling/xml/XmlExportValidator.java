package tiling.xml;

import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
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

            if (parameter.getDimension() == 1) {
                validateScalarPrior(likelihood);
                continue;
            }

            validateDirichletPrior(parameter, likelihood);
        }
    }

    private void validateScalarPrior(AbstractDistributionLikelihood likelihood) {
        if (!(likelihood instanceof DistributionLikelihood distributionLikelihood)) {
            throw unsupported("Only DistributionLikelihood scalar priors are supported.");
        }

        Distribution distribution =
                distributionLikelihood.getDistribution();

        if (!scalarPriorXmlBuilder.supports(distribution)) {
            throw unsupported(
                    "Only Normal, LogNormal, Gamma, Exponential, Uniform, and Beta scalar priors are supported."
            );
        }
    }

    private void validateDirichletPrior(
            Parameter parameter,
            AbstractDistributionLikelihood likelihood
    ) {
        if (!(likelihood instanceof MultivariateDistributionLikelihood multivariateLikelihood)) {
            throw unsupported("Only Dirichlet multivariate priors are supported for non-scalar parameters.");
        }

        if (!(multivariateLikelihood.getDistribution() instanceof DirichletDistribution dirichletDistribution)) {
            throw unsupported("Only Dirichlet multivariate priors are supported for non-scalar parameters.");
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

    private void validateSpeciationTreePrior(SpeciationLikelihood speciationLikelihood) {
        SpeciationModel speciationModel =
                speciationLikelihood.getSpeciationModel();

        if (!(speciationModel instanceof BirthDeathGernhard08Model)) {
            throw unsupported("Only Yule and BirthDeath speciation tree priors are supported.");
        }
    }

    private void validateCoalescentTreePrior(CoalescentLikelihood coalescentLikelihood) {
        DemographicModel demographicModel =
                coalescentLikelihood.getDemoModel();

        if (!(demographicModel instanceof ConstantPopulationModel)) {
            throw unsupported("Only constant-population Coalescent tree priors are supported.");
        }
    }

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend XmlExportValidator before exporting this model class to XML."
        );
    }
}