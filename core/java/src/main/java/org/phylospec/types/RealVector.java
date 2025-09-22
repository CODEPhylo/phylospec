package org.phylospec.types;

import org.phylospec.domain.Real;

//TODO in dev
public interface RealVector<P extends Real> extends Vector<P, Double> {


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