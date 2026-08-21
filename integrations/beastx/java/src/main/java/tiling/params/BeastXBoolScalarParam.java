package tiling.params;

import dr.inference.model.Parameter;
import org.phylospec.domain.Bool;
import org.phylospec.types.BoolScalar;

/** Exposes a BEAST X numeric parameter as a PhyloSpec Boolean scalar. */
public class BeastXBoolScalarParam implements BoolScalar, BeastXParam {

    private final Parameter parameter;

    public BeastXBoolScalarParam(boolean value) {
        this(new Parameter.Default(value ? 1.0 : 0.0));
    }

    public BeastXBoolScalarParam(Parameter parameter) {
        this.parameter = parameter;
        parameter.addBounds(new Parameter.DefaultBounds(
                1.0,
                0.0,
                parameter.getDimension()
        ));
    }

    @Override
    public Parameter getParameter() {
        return this.parameter;
    }

    @Override
    public boolean get() {
        return this.parameter.getParameterValue(0) >= 0.5;
    }

    @Override
    public Boolean get(int... idx) {
        if (idx.length != 0) {
            throw new IllegalArgumentException("BoolScalar does not take indices.");
        }
        return get();
    }

    @Override
    public long size() {
        return 1;
    }

    @Override
    public Bool domainType() {
        return Bool.INSTANCE;
    }
}
