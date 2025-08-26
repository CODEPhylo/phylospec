package org.phylospec.types;

import org.phylospec.primitives.Bool;

public interface BoolVector<P extends Bool> extends Tensor<P, Boolean> {

    // TODO already has "long size()"
    int length();

    boolean get(int i);

    default boolean[] getBooleanArray() {
        boolean[] arr = new boolean[length()];
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
        P p = primitiveType();
        for (int i=0; i<length(); i++)
            if (!p.isValid(get(i)))
                return false;
        return true;
    }

}