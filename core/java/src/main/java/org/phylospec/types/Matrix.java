package org.phylospec.types;

import org.phylospec.primitives.Real;


public interface Matrix<P extends Real> extends NumberMatrix {

    /**
     * Get the element at the specified position.
     *
     * @param row the row index (0-based)
     * @param col the column index (0-based)
     * @return the element at position (row, col)
     * @throws IndexOutOfBoundsException if indices are out of range
     */
    double get(int row, int col);

}
