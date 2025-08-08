package org.phylospec.types;

import java.util.List;

/**
 * Matrix type - 2D array of values.
 * 
 * Represents a two-dimensional array of elements of the same type.
 * Used for transition matrices, covariance matrices, and alignment data.
 * 
 * @param <T> the type of elements in this matrix
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Matrix<T extends PhyloSpecType> extends PhyloSpecType {
    /**
     * Get the number of rows in the matrix.
     * 
     * @return the number of rows
     */
    int getRows();
    
    /**
     * Get the number of columns in the matrix.
     * 
     * @return the number of columns
     */
    int getCols();
    
    /**
     * Get the element at the specified position.
     * 
     * @param row the row index (0-based)
     * @param col the column index (0-based)
     * @return the element at position (row, col)
     * @throws IndexOutOfBoundsException if indices are out of range
     */
    T get(int row, int col);
    
    /**
     * Get all elements as a list of rows.
     * 
     * @return an unmodifiable list of rows, where each row is a list of elements
     */
    List<List<T>> getElements();
    
    /**
     * {@inheritDoc}
     * 
     * @return "Matrix"
     */
    @Override
    default String getTypeName() {
        return "Matrix";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A Matrix is valid if:
     * - It is not null and not empty
     * - All rows have the same number of columns
     * - All elements are valid
     * 
     * @return true if the matrix structure is valid, false otherwise
     */
    @Override
    default boolean isValid() {
        if (getElements() == null || getElements().isEmpty()) {
            return false;
        }
        
        int expectedCols = getCols();
        for (List<T> row : getElements()) {
            if (row == null || row.size() != expectedCols) {
                return false;
            }
            for (T element : row) {
                if (element == null || !element.isValid()) {
                    return false;
                }
            }
        }
        
        return getElements().size() == getRows();
    }
}
