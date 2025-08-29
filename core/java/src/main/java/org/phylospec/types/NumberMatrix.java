package org.phylospec.types;

import org.phylospec.primitives.Primitive;

public interface NumberMatrix<P extends NumberScalar> extends NumberTensor {
    /**
     * Get the number of rows in the matrix.
     *
     * @return the number of rows
     */
    int rows();

    /**
     * Get the number of columns in the matrix.
     *
     * @return the number of columns
     */
    int cols();


    @Override
    default int rank(){ return 2; }

    @Override
    default int[] shape(){ return new int[]{ rows(), cols() }; }

    @Override
    default boolean isValid() {
        Primitive p = primitiveType();
        for (int r=0;r<rows();r++)
            for (int c=0;c<cols();c++)
                if (!p.isValid(get(r,c)))
                    return false;
        return true;
    }
}
