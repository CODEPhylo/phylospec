package org.phylospec.types;

import org.phylospec.primitives.Real;

public class RealParamImpl<P extends Real> implements Scalar {

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

        Scalar scalar = new RealParamImpl(1);
        System.out.println(scalar.get());

        scalar = new RealParamImpl(-10.0);
        System.out.println(scalar.get());

        int i = 2;
        scalar = new RealParamImpl(i);
        System.out.println(scalar.get());
//        scalar = new ScalarImpl(Boolean.FALSE);

        try {
            scalar = new RealParamImpl(Double.POSITIVE_INFINITY);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

        try {
            scalar = new RealParamImpl(Double.NaN);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

    }

}
