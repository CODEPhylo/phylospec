package org.phylospec.types;

import org.phylospec.domain.Int;


public interface IntMatrix<P extends Int> extends Matrix<P, Integer> {

    /**
     * Get the element at the specified position.
     *
     * @param row the row index (0-based)
     * @param col the column index (0-based)
     * @return the element at position (row, col)
     * @throws IndexOutOfBoundsException if indices are out of range
     */
    int get(int row, int col);

}
