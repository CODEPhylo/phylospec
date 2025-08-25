package org.phylospec.types;

import org.phylospec.primitives.Int;

public interface IntVector<P extends Int> extends Tensor<P, Integer> {

    // TODO already has "long size()"
    int length();

    int get(int i);

    default int[] getIntArray() {
        int[] arr = new int[length()];
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