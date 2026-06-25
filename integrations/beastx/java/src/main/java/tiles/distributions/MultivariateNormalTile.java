package tiles.distributions;

import dr.inference.distribution.MultivariateDistributionLikelihood;
import dr.math.distributions.MultivariateNormalDistribution;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import org.phylospec.types.Tensor;
import tiling.params.BeastXRealVectorParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.model.ParameterAttribute;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class MultivariateNormalTile extends GeneratorTile<
        BoundDistribution<BeastXRealVectorParam<Real>, MultivariateDistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "MultivariateNormal";
    }

    GeneratorTileInput<RealVector<Real>, BeastXState> meanInput =
            new GeneratorTileInput<>(
                    "mean",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<List<?>, BeastXState> covarianceInput =
            new GeneratorTileInput<>(
                    "covariance",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public BoundDistribution<BeastXRealVectorParam<Real>, MultivariateDistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealVector<Real> mean =
                this.meanInput.apply(beastState, indexVariables);

        List<?> covariance =
                this.covarianceInput.apply(beastState, indexVariables);

        double[] meanValues =
                mean.getDoubleArray();

        double[][] covarianceValues =
                toMatrix(covariance);

        validateDimensions(meanValues, covarianceValues);

        MultivariateNormalDistribution distribution;

        try {
            distribution =
                    new MultivariateNormalDistribution(
                            meanValues,
                            covarianceValues
                    );
        } catch (RuntimeException exception) {
            throw new TileApplicationError(
                    "Cannot build MultivariateNormal distribution.",
                    "Check that covariance is square, symmetric, and positive definite."
            );
        }

        MultivariateDistributionLikelihood likelihood =
                new MultivariateDistributionLikelihood(distribution);

        BeastXRealVectorParam<Real> defaultState =
                new BeastXRealVectorParam<>(
                        meanValues.clone(),
                        Real.INSTANCE
                );

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(new ParameterAttribute(state.getParameter()))
        );
    }

    private static double[][] toMatrix(List<?> rows) {
        if (rows.isEmpty()) {
            throw new TileApplicationError(
                    "MultivariateNormal covariance matrix cannot be empty.",
                    "Use covariance such as [[1.0, 0.0], [0.0, 1.0]]."
            );
        }

        double[][] matrix =
                new double[rows.size()][];

        int expectedColumns =
                -1;

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            matrix[rowIndex] =
                    toRow(rows.get(rowIndex));

            if (expectedColumns < 0) {
                expectedColumns =
                        matrix[rowIndex].length;
            } else if (matrix[rowIndex].length != expectedColumns) {
                throw new TileApplicationError(
                        "MultivariateNormal covariance rows must have the same length.",
                        "Use a rectangular matrix such as [[1.0, 0.0], [0.0, 1.0]]."
                );
            }
        }

        return matrix;
    }

    private static double[] toRow(Object row) {
        if (row instanceof RealVector<?> vector) {
            int size =
                    Math.toIntExact(vector.size());

            double[] values =
                    new double[size];

            for (int i = 0; i < size; i++) {
                values[i] =
                        vector.get(i);
            }

            return values;
        }

        if (row instanceof Tensor<?, ?> tensor) {
            int[] shape =
                    tensor.shape();

            if (shape.length != 1) {
                throw new TileApplicationError(
                        "MultivariateNormal covariance rows must be vectors.",
                        "Use covariance such as [[1.0, 0.0], [0.0, 1.0]]."
                );
            }

            double[] values =
                    new double[shape[0]];

            for (int i = 0; i < shape[0]; i++) {
                values[i] =
                        toDouble(tensor.get(i));
            }

            return values;
        }

        if (row instanceof List<?> list) {
            double[] values =
                    new double[list.size()];

            for (int i = 0; i < list.size(); i++) {
                values[i] =
                        toDouble(list.get(i));
            }

            return values;
        }

        throw new TileApplicationError(
                "MultivariateNormal covariance rows must be vectors or lists.",
                "Use covariance such as [[1.0, 0.0], [0.0, 1.0]]."
        );
    }

    private static double toDouble(Object value) {
        if (value instanceof RealScalar<?> scalar) {
            return scalar.get();
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        throw new TileApplicationError(
                "MultivariateNormal covariance values must be real numbers.",
                "Use numeric covariance entries such as 1.0 or 0.25."
        );
    }

    private static void validateDimensions(
            double[] mean,
            double[][] covariance
    ) {
        if (mean.length == 0) {
            throw new TileApplicationError(
                    "MultivariateNormal mean vector cannot be empty.",
                    "Use a non-empty mean vector."
            );
        }

        if (covariance.length != mean.length) {
            throw new TileApplicationError(
                    "MultivariateNormal covariance row count must match mean dimension.",
                    "Use one covariance row for each mean entry."
            );
        }

        for (double[] row : covariance) {
            if (row.length != mean.length) {
                throw new TileApplicationError(
                        "MultivariateNormal covariance column count must match mean dimension.",
                        "Use a square covariance matrix with the same dimension as mean."
                );
            }
        }
    }
}
