package org.phylospec.types;

import org.phylospec.primitives.Int;

public interface IntVector<P extends Int> extends NumberVector {

    int get(int i);

    default int[] getIntArray() {
        int[] arr = new int[length()];
        for (int i = 0; i < length(); i++) {
            arr[i] = get(i);
        }
        return arr;
    }

}