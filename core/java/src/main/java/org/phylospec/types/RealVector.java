package org.phylospec.types;

import org.phylospec.primitives.Real;

public interface RealVector<P extends Real> extends RealTensor<P> {

    // TODO already has "long size()"
    int length();

    double get(int i);

    default double[] getDoubleArray() {
        double[] arr = new double[length()];
        for (int i = 0; i < length(); i++) {
            arr[i] = get(i);
        }
        return arr;
    }

    @Override
    default int rank(){ return 1; }

    @Override
    default int[] shape(){
        return new int[]{ length() };
    }

    @Override
    default boolean isValid() {
        P p = getPrimitive();
        for (int i=0; i<length(); i++)
            if (!p.isValid(get(i)))
                return false;
        return true;
    }

}