import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;
import tiling.BeastXState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code MultivariateNormal(mean, covariance)} in BEAST X, checked against the bivariate normal
 * density computed here from the covariance.
 */
public class BeastXMultivariateNormalTest {

    @Test
    public void diagonalCovarianceIsVarianceNotPrecision() throws Exception {
        // Variance 4 in each dimension. Read as a precision, the variance would be 0.25.
        assertLogDensity(new double[][]{{4.0, 0.0}, {0.0, 4.0}}, 2.0, 0.0);
    }

    @Test
    public void correlatedCovariance() throws Exception {
        assertLogDensity(new double[][]{{1.0, 0.25}, {0.25, 1.0}}, 1.0, -0.5);
        assertLogDensity(new double[][]{{2.0, -0.6}, {-0.6, 0.5}}, -1.0, 0.3);
    }

    @Test
    public void rejectsCovarianceThatIsNotPositiveDefinite() {
        PhyloSpecRunnerException error = assertThrows(
                PhyloSpecRunnerException.class,
                () -> buildState(new double[][]{{1.0, 2.0}, {2.0, 1.0}})
        );

        assertTrue(error.getMessage().contains("symmetric and positive definite"), error.getMessage());
    }

    private static void assertLogDensity(double[][] covariance, double x0, double x1) throws Exception {
        BeastXState state = buildState(covariance);

        Parameter x = state.stateNodesByPhyloSpecName.get("x");
        x.setParameterValue(0, x0);
        x.setParameterValue(1, x1);

        Likelihood prior = state.priorDistributions.get(x);
        prior.makeDirty();

        assertEquals(bivariateNormalLogDensity(covariance, x0, x1), prior.getLogLikelihood(), 1e-12);
    }

    private static BeastXState buildState(double[][] covariance) throws Exception {
        // Inline matrix literals are not yet accepted for covariance, so the matrix is a variable.
        String matrix = "[[" + covariance[0][0] + ", " + covariance[0][1] + "], ["
                + covariance[1][0] + ", " + covariance[1][1] + "]]";

        return new PhyloSpecRunner(
                "Matrix<Real> cov = " + matrix + "\n"
                        + "Vector<Real> x ~ MultivariateNormal(mean=[0.0, 0.0], covariance=cov)\n"
        ).buildState("test");
    }

    /** log N(x; 0, S) = -log(2 pi) - log|S| / 2 - x' S^-1 x / 2, with mean 0. */
    private static double bivariateNormalLogDensity(double[][] s, double x0, double x1) {
        double determinant = s[0][0] * s[1][1] - s[0][1] * s[1][0];
        double quadraticForm = (s[1][1] * x0 * x0 - 2.0 * s[0][1] * x0 * x1 + s[0][0] * x1 * x1) / determinant;

        return -Math.log(2.0 * Math.PI) - 0.5 * Math.log(determinant) - 0.5 * quadraticForm;
    }
}
