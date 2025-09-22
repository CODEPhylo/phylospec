package org.phylospec.types;

import org.phylospec.domain.Int;

public interface IntVector<P extends Int> extends Vector<P, Integer> {

    int get(int i);

    default int[] getIntArray() {
        int length = Math.toIntExact(size());
        int[] arr = new int[length];
        for (int i = 0; i < length; i++) {
            arr[i] = get(i);
        }
        return arr;
    }

}