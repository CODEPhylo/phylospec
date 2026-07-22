package tiling.params;

import dr.inference.model.Parameter;
import org.phylospec.domain.Int;
import org.phylospec.types.IntVector;

import java.util.Collections;
import java.util.List;

/**
 * Wraps a BEAST X parameter as a PhyloSpec integer vector.
 * This is useful to validate the parameter domain during tiling.
 */
public class BeastXIntVectorParam<D extends Int> implements IntVector<D>, BeastXParam {

    private final Parameter parameter;
    private final D domain;

    public BeastXIntVectorParam(int[] values, D domain) {
        this(new Parameter.Default(toDoubleArray(values)), domain);
    }

    public BeastXIntVectorParam(Parameter parameter, D domain) {
        this.parameter = parameter;
        this.domain = domain;

        parameter.addBounds(new Parameter.DefaultBounds(
                domain.getUpper(),
                domain.getLower(),
                parameter.getDimension()
        ));
    }

    @Override
    public Parameter getParameter() {
        return this.parameter;
    }

    @Override
    public int get(int i) {
        return (int) Math.round(this.parameter.getParameterValue(i));
    }

    @Override
    public Integer get(int... idx) {
        if (idx.length != 1) {
            throw new IllegalArgumentException("IntVector requires exactly one index.");
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

    private static double[] toDoubleArray(int[] values) {
        double[] doubles = new double[values.length];

        for (int i = 0; i < values.length; i++) {
            doubles[i] = values[i];
        }

        return doubles;
    }
}