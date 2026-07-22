package tiling.params;

import dr.inference.model.Parameter;
import org.phylospec.domain.UnitInterval;
import org.phylospec.types.Simplex;

/**
 * Wraps a BEAST X vector parameter as a PhyloSpec simplex.
 * This is useful to validate the parameter domain during tiling.
 */
public class BeastXSimplexParam extends BeastXRealVectorParam<UnitInterval> implements Simplex {

    public BeastXSimplexParam(double[] values) {
        this(new Parameter.Default(values));
    }

    public BeastXSimplexParam(Parameter parameter) {
        super(parameter, UnitInterval.INSTANCE);

        parameter.addBounds(new Parameter.DefaultBounds(
                1.0,
                0.0,
                parameter.getDimension()
        ));
    }
}
