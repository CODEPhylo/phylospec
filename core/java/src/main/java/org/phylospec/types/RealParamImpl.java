package org.phylospec.types;

import org.phylospec.primitives.Real;

public class RealParamImpl<P extends Real> implements RealScalar {

    double value;

    public RealParamImpl(double value) {
        this.value = value;
        if (!isValid()) {
            throw new IllegalArgumentException(
                    "..., but was: " + value);
        }
    }

    @Override
    public double get() {
        return value;
    }

    @Override
    public Double get(int... idx) {
        // a scalar (rank 0 or size 1), only index 0 is valid.
        if (idx[0] != 0) throw new IllegalArgumentException();
        return get();
    }

    @Override
    public Real primitiveType() {
        return Real.INSTANCE;
    }

    public static void main(String[] args) {

        RealScalar realScalar = new RealParamImpl(1);
        System.out.println(realScalar.get());

        realScalar = new RealParamImpl(-10.0);
        System.out.println(realScalar.get());

        int i = 2;
        realScalar = new RealParamImpl(i);
        System.out.println(realScalar.get());
//        scalar = new ScalarImpl(Boolean.FALSE);

        try {
            realScalar = new RealParamImpl(Double.POSITIVE_INFINITY);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

        try {
            realScalar = new RealParamImpl(Double.NaN);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

    }

}
