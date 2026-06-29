package tiling.params;

import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import org.phylospec.types.RealScalar;
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

    public static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
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