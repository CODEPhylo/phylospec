package tiling.params;

import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import org.phylospec.types.Simplex;

/**
 * Converts backend-agnostic PhyloSpec values into BEAST X parameters.
 *
 * <p>Keeping this adapter in the BEAST X integration avoids leaking the
 * BEAST X {@link Parameter} API into core PhyloSpec types.</p>
 */
public final class BeastXParameters {

    private BeastXParameters() {
    }

    /**
     * A sampled parameter is used as it is, so the model follows it during MCMC; any other value
     * is copied into a new parameter. A value computed from a sampled tree, such as
     * {@code rootAge(tree)}, would then stay at its starting value, so it is rejected.
     */
    public static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        if (scalar instanceof BeastXStatisticRealScalar<?> statistic && statistic.isSampled()) {
            throw new TileApplicationError(
                    "BEAST X cannot use a value computed from a sampled tree here, such as rootAge(tree),"
                            + " mrca(...) or age(...): it would stay at its starting value.",
                    "Use a fixed value or a sampled variable instead."
            );
        }

        return new Parameter.Default(scalar.get());
    }

    public static Parameter toParameter(RealVector<?> vector) {
        if (vector instanceof BeastXRealVectorParam<?> beastXVector) {
            return beastXVector.getParameter();
        }

        return new Parameter.Default(vector.getDoubleArray());
    }

    public static Parameter toParameter(Simplex simplex) {
        if (simplex instanceof BeastXSimplexParam beastXSimplex) {
            return beastXSimplex.getParameter();
        }

        return new Parameter.Default(simplex.getDoubleArray());
    }

    public static Variable<Double> toVariable(RealScalar<?> scalar) {
        return toParameter(scalar);
    }
}