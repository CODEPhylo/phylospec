package tiling.model;

import dr.evolution.alignment.Alignment;
import dr.evolution.tree.NodeRef;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;

import java.util.HashSet;
import java.util.Set;

/**
 * BEAST X model-likelihood adapter for PhyloSpec Ornstein-Uhlenbeck continuous
 * trait models.
 *
 * <p>This class evaluates observed continuous traits on a tree under an
 * Ornstein-Uhlenbeck process with a site variance, selection strength, optimum,
 * and optional root value. Keeping this BEAST X-specific likelihood setup in a
 * dedicated model class lets the corresponding PhyloSpec tiles stay focused on
 * model construction while the likelihood remains connected to BEAST X's model
 * and variable change notifications.</p>
 */
public class BeastXOUTraitLikelihoodSpec extends AbstractModelLikelihood {
    private final Alignment observedTraits;
    private final TreeModel treeModel;
    private final Parameter siteVariances;
    private final Parameter selectionStrength;
    private final Parameter siteOptima;
    private final Parameter rootValues;

    private boolean dirty = true;
    private double logLikelihood = Double.NaN;

    public BeastXOUTraitLikelihoodSpec(
            String id,
            Alignment observedTraits,
            TreeModel treeModel,
            Parameter siteVariances,
            Parameter selectionStrength,
            Parameter siteOptima,
            Parameter rootValues
    ) {
        super(id);
        this.observedTraits = observedTraits;
        this.treeModel = treeModel;
        this.siteVariances = siteVariances;
        this.selectionStrength = selectionStrength;
        this.siteOptima = siteOptima;
        this.rootValues = rootValues;

        ContinuousTraitValidation.validateObservedTraits(
                "PhyloOU",
                observedTraits,
                treeModel
        );

        ContinuousTraitValidation.requireSingleTraitParameter(
                "PhyloOU",
                siteVariances,
                "siteVariances"
        );

        ContinuousTraitValidation.requireSingleTraitParameter(
                "PhyloOU",
                selectionStrength,
                "selectionStrength"
        );

        ContinuousTraitValidation.requireSingleTraitParameter(
                "PhyloOU",
                siteOptima,
                "siteOptima"
        );

        ContinuousTraitValidation.requireSingleTraitParameter(
                "PhyloOU",
                rootValues,
                "rootValues"
        );

        this.addModel(treeModel);
        this.addVariable(siteVariances);
        this.addVariable(selectionStrength);
        this.addVariable(siteOptima);

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
        int n =
                observedTraits.getSequenceCount();

        if (n == 0) {
            return Double.NEGATIVE_INFINITY;
        }

        double variance =
                siteVariances.getParameterValue(0);

        double alpha =
                selectionStrength.getParameterValue(0);

        double optimum =
                siteOptima.getParameterValue(0);

        double rootValue =
                rootValues == null
                        ? optimum
                        : rootValues.getParameterValue(0);

        if (!(variance > 0.0) || !(alpha > 0.0)) {
            return Double.NEGATIVE_INFINITY;
        }

        double[] y =
                observedTraitValues();

        NodeRef[] tips =
                observedTipNodes();

        double[] mean =
                expectedMeans(tips, alpha, optimum, rootValue);

        double[][] covariance =
                covarianceMatrix(tips, variance, alpha);

        return multivariateNormalLogDensity(y, mean, covariance);
    }

    private double[] observedTraitValues() {
        double[] values =
                new double[observedTraits.getSequenceCount()];

        for (int i = 0; i < values.length; i++) {
            Taxon taxon =
                    observedTraits.getTaxon(i);

            values[i] =
                    readTraitValue(i, taxon);
        }

        return values;
    }

    private NodeRef[] observedTipNodes() {
        NodeRef[] tips =
                new NodeRef[observedTraits.getSequenceCount()];

        for (int i = 0; i < tips.length; i++) {
            Taxon taxon =
                    observedTraits.getTaxon(i);

            tips[i] =
                    externalNodeForTaxon(taxon.getId());
        }

        return tips;
    }

    private NodeRef externalNodeForTaxon(String taxonId) {
        return ContinuousTraitValidation.externalNodeForTaxon(
                "PhyloOU",
                treeModel,
                taxonId
        );
    }

    private double[] expectedMeans(
            NodeRef[] tips,
            double alpha,
            double optimum,
            double rootValue
    ) {
        double[] means =
                new double[tips.length];

        double rootHeight =
                treeModel.getNodeHeight(treeModel.getRoot());

        for (int i = 0; i < tips.length; i++) {
            double timeFromRoot =
                    rootHeight - treeModel.getNodeHeight(tips[i]);

            means[i] =
                    optimum + (rootValue - optimum) * Math.exp(-alpha * timeFromRoot);
        }

        return means;
    }

    private double[][] covarianceMatrix(
            NodeRef[] tips,
            double variance,
            double alpha
    ) {
        int n =
                tips.length;

        double[][] covariance =
                new double[n][n];

        double rootHeight =
                treeModel.getNodeHeight(treeModel.getRoot());

        for (int i = 0; i < n; i++) {
            double ti =
                    rootHeight - treeModel.getNodeHeight(tips[i]);

            for (int j = 0; j <= i; j++) {
                double tj =
                        rootHeight - treeModel.getNodeHeight(tips[j]);

                NodeRef mrca =
                        mrca(tips[i], tips[j]);

                double tm =
                        rootHeight - treeModel.getNodeHeight(mrca);

                double value =
                        variance / (2.0 * alpha)
                                * Math.exp(-alpha * (ti + tj - 2.0 * tm))
                                * (1.0 - Math.exp(-2.0 * alpha * tm));

                if (i == j) {
                    value += 1e-10;
                }

                covariance[i][j] = value;
                covariance[j][i] = value;
            }
        }

        return covariance;
    }

    private NodeRef mrca(NodeRef first, NodeRef second) {
        Set<NodeRef> ancestors =
                new HashSet<>();

        NodeRef current =
                first;

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

    private double multivariateNormalLogDensity(
            double[] y,
            double[] mean,
            double[][] covariance
    ) {
        int n =
                y.length;

        double[][] cholesky =
                cholesky(covariance);

        if (cholesky == null) {
            return Double.NEGATIVE_INFINITY;
        }

        double[] residual =
                new double[n];

        for (int i = 0; i < n; i++) {
            residual[i] =
                    y[i] - mean[i];
        }

        double[] solved =
                solveLower(cholesky, residual);

        double quadratic =
                0.0;

        for (double value : solved) {
            quadratic += value * value;
        }

        double logDeterminant =
                0.0;

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
        int n =
                matrix.length;

        double[][] lower =
                new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum =
                        matrix[i][j];

                for (int k = 0; k < j; k++) {
                    sum -= lower[i][k] * lower[j][k];
                }

                if (i == j) {
                    if (!(sum > 0.0) || Double.isNaN(sum)) {
                        return null;
                    }

                    lower[i][j] =
                            Math.sqrt(sum);
                } else {
                    lower[i][j] =
                            sum / lower[j][j];
                }
            }
        }

        return lower;
    }

    private double[] solveLower(double[][] lower, double[] rhs) {
        int n =
                rhs.length;

        double[] x =
                new double[n];

        for (int i = 0; i < n; i++) {
            double sum =
                    rhs[i];

            for (int j = 0; j < i; j++) {
                sum -= lower[i][j] * x[j];
            }

            x[i] =
                    sum / lower[i][i];
        }

        return x;
    }

    private double readTraitValue(int sequenceIndex, Taxon taxon) {
        return ContinuousTraitValidation.readTraitValue(
                "PhyloOU",
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