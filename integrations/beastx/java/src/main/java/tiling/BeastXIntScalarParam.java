package tiling;

import dr.inference.model.Parameter;
import org.phylospec.domain.Int;
import org.phylospec.types.IntScalar;

public class BeastXIntScalarParam<D extends Int> implements IntScalar<D>, BeastXParam {

    private final Parameter parameter;
    private final D domain;

    public BeastXIntScalarParam(int value, D domain) {
        this(new Parameter.Default(value), domain);
    }

    public BeastXIntScalarParam(Parameter parameter, D domain) {
        this.parameter = parameter;
        this.domain = domain;
    }

    @Override
    public Parameter getParameter() {
        return this.parameter;
    }

    @Override
    public Integer get() {
        return (int) Math.round(this.parameter.getParameterValue(0));
    }

    @Override
    public Integer get(int... idx) {
        if (idx.length != 0) {
            throw new IllegalArgumentException("IntScalar does not take indices.");
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
