package org.phylospec.types;

import org.phylospec.primitives.Bool;

public interface BoolVector extends Tensor<Bool, Boolean> {

    boolean get(int i);

    default boolean[] getBooleanArray() {
        int length = Math.toIntExact(size());
        boolean[] arr = new boolean[length];
        for (int i = 0; i < length; i++) {
            arr[i] = get(i);
        }
        return arr;
    }

    @Override
    default int rank(){ return 1; }

    @Override
    default int[] shape(){
        return new int[]{ Math.toIntExact(size()) };
    }

    @Override
    default boolean isValid() {
        Bool p = primitiveType();
        for (int i=0; i<Math.toIntExact(size()); i++)
            if (!p.isValid(get(i)))
                return false;
        return true;
    }

}