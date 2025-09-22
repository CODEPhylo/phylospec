package org.phylospec.types;

import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;

import java.util.Arrays;

public class RealParamImpl<D extends Real> implements RealScalar<D> {

    double value;
    // Domain instance to enforce constraints
    final protected D domain;

    public RealParamImpl(double value, D domain) {
        this.value = value;
        this.domain = domain;

        if (! isValid())
            throw new IllegalArgumentException("Initial value of " + value +
                    " is not valid for domain " + domain.getClass().getName());
    }

    @Override
    public double get() {
        return value;
    }

    @Override
    public Double get(int... idx) {
        // a scalar (rank 0 or size 1), only index 0 is valid.
        if (idx.length != 0) throw new IllegalArgumentException("Invalid argument for RealScalar ! "+ Arrays.toString(idx));
        return get();
    }

    @Override
    public D domainType() {
        return domain;
    }

    public static void main(String[] args) {

        RealScalar realScalar = new RealParamImpl(1, PositiveReal.INSTANCE);
        System.out.println(realScalar.get());

        realScalar = new RealParamImpl(-10.0, Real.INSTANCE);
        System.out.println(realScalar.get());

        int i = 0;
        realScalar = new RealParamImpl(i, NonNegativeReal.INSTANCE);
        System.out.println(realScalar.get());
//        scalar = new ScalarImpl(Boolean.FALSE);

        try {
            realScalar = new RealParamImpl(0, PositiveReal.INSTANCE);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

        try {
            realScalar = new RealParamImpl(Double.NaN, Real.INSTANCE);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

    }

}
