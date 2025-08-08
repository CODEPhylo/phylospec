package org.phylospec.types.impl;

import org.phylospec.types.Matrix;
import org.phylospec.types.PhyloSpecType;
import java.util.*;

/**
 * Immutable implementation of the Matrix type.
 * 
 * @param <T> the type of elements in this matrix
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public class MatrixImpl<T extends PhyloSpecType> implements Matrix<T> {
    private final List<List<T>> elements;
    private final int rows;
    private final int cols;
    
    /**
     * Constructs a Matrix from a 2D list of elements.
     * 
     * @param elements the matrix elements (list of rows)
     * @throws IllegalArgumentException if the matrix is invalid
     */
    public MatrixImpl(List<List<T>> elements) {
        if (elements == null || elements.isEmpty()) {
            throw new IllegalArgumentException("Matrix cannot be null or empty");
        }
        
        this.rows = elements.size();
        this.cols = elements.get(0) != null ? elements.get(0).size() : 0;
        
        // Deep copy to ensure immutability
        this.elements = new ArrayList<>(rows);
        for (List<T> row : elements) {
            if (row == null) {
                throw new IllegalArgumentException("Matrix row cannot be null");
            }
            this.elements.add(new ArrayList<>(row));
        }
        
        if (!isValid()) {
            throw new IllegalArgumentException("Invalid matrix structure or elements");
        }
    }
    
    @Override
    public int getRows() {
        return rows;
    }
    
    @Override
    public int getCols() {
        return cols;
    }
    
    @Override
    public T get(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new IndexOutOfBoundsException(
                String.format("Index (%d, %d) out of bounds for %dx%d matrix", 
                             row, col, rows, cols));
        }
        return elements.get(row).get(col);
    }
    
    @Override
    public List<List<T>> getElements() {
        // Return deep immutable copy
        List<List<T>> copy = new ArrayList<>(rows);
        for (List<T> row : elements) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        return Collections.unmodifiableList(copy);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Matrix)) return false;
        Matrix<?> other = (Matrix<?>) obj;
        return rows == other.getRows() && 
               cols == other.getCols() && 
               Objects.equals(elements, other.getElements());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(elements, rows, cols);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getTypeName()).append("[\n");
        for (int i = 0; i < rows; i++) {
            sb.append("  [");
            for (int j = 0; j < cols; j++) {
                if (j > 0) sb.append(", ");
                T element = elements.get(i).get(j);
                // Get the actual value for cleaner output
                if (element.getValue() instanceof Number) {
                    sb.append(String.format("%.4f", element.getValue()));
                } else {
                    sb.append(element.getValue());
                }
            }
            sb.append("]");
            if (i < rows - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
}
