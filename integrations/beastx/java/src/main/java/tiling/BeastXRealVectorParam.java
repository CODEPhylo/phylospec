package tiling;

import dr.inference.model.Parameter;
import org.phylospec.domain.Real;
import org.phylospec.types.RealVector;

import java.util.Collections;
import java.util.List;

public class BeastXRealVectorParam<D extends Real> implements RealVector<D>, BeastXParam {

    private final Parameter parameter;
    private final D domain;

    public BeastXRealVectorParam(double[] values, D domain) {
        this(new Parameter.Default(values), domain);
    }

    public BeastXRealVectorParam(Parameter parameter, D domain) {
        this.parameter = parameter;
        this.domain = domain;
    }

    @Override
    public Parameter getParameter() {
        return this.parameter;
    }

    @Override
    public double get(int i) {
        return this.parameter.getParameterValue(i);
    }

    @Override
    public Double get(int... idx) {
        if (idx.length != 1) {
            throw new IllegalArgumentException("RealVector requires exactly one index.");
        }
        return get(idx[0]);
    }

    @Override
    public List<D> getElements() {
        return Collections.nCopies(Math.toIntExact(size()), this.domain);
    }

    @Override
    public long size() {
        return this.parameter.getDimension();
    }

    @Override
    public D domainType() {
        return this.domain;
    }
}
