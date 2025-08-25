package org.phylospec.types;

import org.phylospec.primitives.Real;


public interface RealMatrix<P extends Real> extends Tensor<P, Double> {
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

    /**
     * Get the element at the specified position.
     *
     * @param row the row index (0-based)
     * @param col the column index (0-based)
     * @return the element at position (row, col)
     * @throws IndexOutOfBoundsException if indices are out of range
     */
    double get(int row, int col);

    @Override
    default int rank(){ return 2; }

    @Override
    default int[] shape(){ return new int[]{ rows(), cols() }; }

    @Override
    default boolean isValid() {
        P p = getPrimitive();
        for (int r=0;r<rows();r++)
            for (int c=0;c<cols();c++)
                if (!p.isValid(get(r,c)))
                    return false;
        return true;
    }
}
