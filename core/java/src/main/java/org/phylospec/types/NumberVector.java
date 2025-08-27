package org.phylospec.types;

import org.phylospec.primitives.Primitive;

public interface NumberVector<P extends Number> extends NumberTensor {

    // TODO already has "long size()"
    int length();

    @Override
    default int rank(){ return 1; }

    @Override
    default int[] shape(){
        return new int[]{ length() };
    }

    @Override
    default boolean isValid() {
        Primitive p = primitiveType();
        for (int i=0; i<length(); i++)
            if (!p.isValid(get(i)))
                return false;
        return true;
    }

}
