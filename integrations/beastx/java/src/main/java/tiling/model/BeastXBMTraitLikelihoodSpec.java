package tiling.model;

import dr.evolution.alignment.Alignment;
import dr.evolution.tree.NodeRef;
import dr.evolution.util.Taxon;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;

import java.util.HashSet;
import java.util.Set;

public class BeastXBMTraitLikelihoodSpec extends AbstractModelLikelihood {

    private final Alignment observedTraits;
    private final TreeModel treeModel;
    private final BranchRateModel branchRateModel;
    private final Parameter siteRates;
    private final Parameter rootValues;

    private boolean dirty = true;
    private double logLikelihood = Double.NaN;

    public BeastXBMTraitLikelihoodSpec(
            String id,
            Alignment observedTraits,
            TreeModel treeModel,
            BranchRateModel branchRateModel,
            Parameter siteRates,
            Parameter rootValues
    ) {
        super(id);
        this.observedTraits = observedTraits;
        this.treeModel = treeModel;
        this.branchRateModel = branchRateModel;
        this.siteRates = siteRates;
        this.rootValues = rootValues;

        ContinuousTraitValidation.validateObservedTraits(
                "PhyloBM",
                observedTraits,
                treeModel
        );

        ContinuousTraitValidation.requireSingleTraitParameter(
                "PhyloBM",
                siteRates,
                "siteRates"
        );

        ContinuousTraitValidation.requireSingleTraitParameter(
                "PhyloBM",
                rootValues,
                "rootValues"
        );

        this.addModel(treeModel);
        this.addModel(branchRateModel);
        this.addVariable(siteRates);

        if (rootValues != null) {
            this.addVariable(rootValues);
        }
    }

    @Override
    public Model getModel() {
        return this;
    }

    @Override
    public double getLogLikelihood() {
        if (dirty) {
            logLikelihood = calculateLogLikelihood();
            dirty = false;
        }

        return logLikelihood;
    }

    private double calculateLogLikelihood() {
        int n = observedTraits.getSequenceCount();

        if (n == 0) {
            return Double.NEGATIVE_INFINITY;
        }

        double siteRate = siteRates.getParameterValue(0);

        if (!(siteRate > 0.0) || Double.isNaN(siteRate)) {
            return Double.NEGATIVE_INFINITY;
        }

        double[] y = observedTraitValues();
        NodeRef[] tips = observedTipNodes();
        double[][] covariance = covarianceMatrix(tips, siteRate);

        double rootMean = rootValues == null
                ? estimateRootMean(y, covariance)
                : rootValues.getParameterValue(0);

        double[] mean = new double[n];

        for (int i = 0; i < n; i++) {
            mean[i] = rootMean;
        }

        return multivariateNormalLogDensity(y, mean, covariance);
    }

    private double[] observedTraitValues() {
        double[] values = new double[observedTraits.getSequenceCount()];

        for (int i = 0; i < values.length; i++) {
            Taxon taxon = observedTraits.getTaxon(i);
            values[i] = readTraitValue(i, taxon);
        }

        return values;
    }

    private NodeRef[] observedTipNodes() {
        NodeRef[] tips = new NodeRef[observedTraits.getSequenceCount()];

        for (int i = 0; i < tips.length; i++) {
            tips[i] = externalNodeForTaxon(observedTraits.getTaxon(i).getId());
        }

        return tips;
    }

    private NodeRef externalNodeForTaxon(String taxonId) {
        return ContinuousTraitValidation.externalNodeForTaxon(
                "PhyloBM",
                treeModel,
                taxonId
        );
    }

    private double[][] covarianceMatrix(NodeRef[] tips, double siteRate) {
        int n = tips.length;
        double[][] covariance = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                NodeRef mrca = mrca(tips[i], tips[j]);

                double sharedPathLength =
                        branchRateScaledPathLengthFromRoot(mrca);

                double value =
                        siteRate * sharedPathLength;

                if (i == j) {
                    value += 1e-10;
                }

                covariance[i][j] = value;
                covariance[j][i] = value;
            }
        }

        return covariance;
    }

    private double branchRateScaledPathLengthFromRoot(NodeRef node) {
        double length = 0.0;
        NodeRef current = node;

        while (!treeModel.isRoot(current)) {
            NodeRef parent = treeModel.getParent(current);
            double branchTime =
                    treeModel.getNodeHeight(parent) - treeModel.getNodeHeight(current);

            double branchRate =
                    branchRateModel.getBranchRate(treeModel, current);

            length += branchTime * branchRate;
            current = parent;
        }

        return length;
    }

    private NodeRef mrca(NodeRef first, NodeRef second) {
        Set<NodeRef> ancestors = new HashSet<>();

        NodeRef current = first;

        while (current != null) {
            ancestors.add(current);

            if (treeModel.isRoot(current)) {
                break;
            }

            current = treeModel.getParent(current);
        }

        current = second;

        while (current != null) {
            if (ancestors.contains(current)) {
                return current;
            }

            if (treeModel.isRoot(current)) {
                break;
            }

            current = treeModel.getParent(current);
        }

        return treeModel.getRoot();
    }

    private double estimateRootMean(double[] y, double[][] covariance) {
        double[][] cholesky = cholesky(covariance);

        if (cholesky == null) {
            return 0.0;
        }

        double[] ones = new double[y.length];

        for (int i = 0; i < ones.length; i++) {
            ones[i] = 1.0;
        }

        double[] invY = solveSPD(cholesky, y);
        double[] invOnes = solveSPD(cholesky, ones);

        double numerator = 0.0;
        double denominator = 0.0;

        for (int i = 0; i < y.length; i++) {
            numerator += ones[i] * invY[i];
            denominator += ones[i] * invOnes[i];
        }

        if (!(denominator > 0.0)) {
            return 0.0;
        }

        return numerator / denominator;
    }

    private double multivariateNormalLogDensity(
            double[] y,
            double[] mean,
            double[][] covariance
    ) {
        int n = y.length;
        double[][] cholesky = cholesky(covariance);

        if (cholesky == null) {
            return Double.NEGATIVE_INFINITY;
        }

        double[] residual = new double[n];

        for (int i = 0; i < n; i++) {
            residual[i] = y[i] - mean[i];
        }

        double[] solved = solveLower(cholesky, residual);

        double quadratic = 0.0;

        for (double value : solved) {
            quadratic += value * value;
        }

        double logDeterminant = 0.0;

        for (int i = 0; i < n; i++) {
            logDeterminant += 2.0 * Math.log(cholesky[i][i]);
        }

        return -0.5 * (
                n * Math.log(2.0 * Math.PI)
                        + logDeterminant
                        + quadratic
        );
    }

    private double[][] cholesky(double[][] matrix) {
        int n = matrix.length;
        double[][] lower = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = matrix[i][j];

                for (int k = 0; k < j; k++) {
                    sum -= lower[i][k] * lower[j][k];
                }

                if (i == j) {
                    if (!(sum > 0.0) || Double.isNaN(sum)) {
                        return null;
                    }

                    lower[i][j] = Math.sqrt(sum);
                } else {
                    lower[i][j] = sum / lower[j][j];
                }
            }
        }

        return lower;
    }

    private double[] solveSPD(double[][] lower, double[] rhs) {
        return solveUpperTranspose(lower, solveLower(lower, rhs));
    }

    private double[] solveLower(double[][] lower, double[] rhs) {
        int n = rhs.length;
        double[] x = new double[n];

        for (int i = 0; i < n; i++) {
            double sum = rhs[i];

            for (int j = 0; j < i; j++) {
                sum -= lower[i][j] * x[j];
            }

            x[i] = sum / lower[i][i];
        }

        return x;
    }

    private double[] solveUpperTranspose(double[][] lower, double[] rhs) {
        int n = rhs.length;
        double[] x = new double[n];

        for (int i = n - 1; i >= 0; i--) {
            double sum = rhs[i];

            for (int j = i + 1; j < n; j++) {
                sum -= lower[j][i] * x[j];
            }

            x[i] = sum / lower[i][i];
        }

        return x;
    }

    private double readTraitValue(int sequenceIndex, Taxon taxon) {
        return ContinuousTraitValidation.readTraitValue(
                "PhyloBM",
                observedTraits,
                sequenceIndex
        );
    }

    @Override
    public void makeDirty() {
        dirty = true;
        this.fireModelChanged();
    }

    @Override
    protected void handleModelChangedEvent(Model model, Object object, int index) {
        dirty = true;
        this.fireModelChanged(object, index);
    }

    @Override
    protected void handleVariableChangedEvent(
            Variable variable,
            int index,
            Variable.ChangeType type
    ) {
        dirty = true;
        this.fireModelChanged(variable, index);
    }

    @Override
    protected void storeState() {
    }

    @Override
    protected void restoreState() {
        dirty = true;
    }

    @Override
    protected void acceptState() {
    }
}