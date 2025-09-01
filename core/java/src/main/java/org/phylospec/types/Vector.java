package org.phylospec.types;

import org.phylospec.primitives.Real;

public interface Vector<P extends Real> extends NumberVector<P, Double> {


    double get(int i);

    default double[] getDoubleArray() {
        int length = Math.toIntExact(size());
        double[] arr = new double[length];
        for (int i = 0; i < length; i++) {
            arr[i] = get(i);
        }
        return arr;
    }

}