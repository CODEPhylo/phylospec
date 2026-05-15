package tiling;

import dr.inference.model.Parameter;
import org.phylospec.domain.Real;
import org.phylospec.types.RealScalar;

public class BeastXRealScalarParam<D extends Real> implements RealScalar<D>, BeastXParam {

    private final Parameter parameter;
    private final D domain;

    public BeastXRealScalarParam(double value, D domain) {
        this(new Parameter.Default(value), domain);
    }

    public BeastXRealScalarParam(Parameter parameter, D domain) {
        this.parameter = parameter;
        this.domain = domain;
    }

    @Override
    public Parameter getParameter() {
        return this.parameter;
    }

    @Override
    public double get() {
        return this.parameter.getParameterValue(0);
    }

    @Override
    public Double get(int... idx) {
        if (idx.length != 0) {
            throw new IllegalArgumentException("RealScalar does not take indices.");
        }
        return get();
    }

    @Override
    public long size() {
        return 1;
    }

    @Override
    public D domainType() {
        return this.domain;
    }
}
