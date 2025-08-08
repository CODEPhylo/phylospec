package org.phylospec.types.impl;

import org.phylospec.types.QMatrix;
import org.phylospec.types.Real;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable implementation of the QMatrix type.
 * A Q-matrix is a rate matrix for continuous-time Markov chains where:
 * - Off-diagonal elements are non-negative (rates)
 * - Diagonal elements are non-positive 
 * - Each row sums to zero
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class QMatrixImpl extends SquareMatrixImpl<Real> implements QMatrix {
    
    /**
     * Constructs a QMatrix from a 2D array of values.
     * 
     * @param values the matrix values
     * @throws IllegalArgumentException if the matrix doesn't satisfy Q-matrix constraints
     */
    public QMatrixImpl(double[][] values) {
        super(createRealMatrix(values));
        if (!isValid()) {
            throw new IllegalArgumentException(
                "Invalid Q-matrix: rows must sum to 0, off-diagonals must be >= 0, " +
                "diagonals must be <= 0");
        }
    }
    
    /**
     * Constructs a QMatrix from a list of Real rows.
     * 
     * @param elements the matrix elements
     * @throws IllegalArgumentException if the matrix doesn't satisfy Q-matrix constraints
     */
    public QMatrixImpl(List<List<Real>> elements) {
        super(elements);
        if (!isValid()) {
            throw new IllegalArgumentException(
                "Invalid Q-matrix: rows must sum to 0, off-diagonals must be >= 0, " +
                "diagonals must be <= 0");
        }
    }
    
    private static List<List<Real>> createRealMatrix(double[][] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Matrix values cannot be null or empty");
        }
        
        List<List<Real>> matrix = new ArrayList<>(values.length);
        for (double[] row : values) {
            if (row == null) {
                throw new IllegalArgumentException("Matrix row cannot be null");
            }
            List<Real> realRow = new ArrayList<>(row.length);
            for (double value : row) {
                realRow.add(new RealImpl(value));
            }
            matrix.add(realRow);
        }
        return matrix;
    }
    
    @Override
    public String getTypeName() {
        return "QMatrix";
    }
    
    /**
     * Creates a more detailed string representation showing the rate matrix structure.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("QMatrix[\n");
        int n = getRows();
        
        // Find max width for formatting
        int maxWidth = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                String formatted = String.format("%.4f", get(i, j).getValue());
                maxWidth = Math.max(maxWidth, formatted.length());
            }
        }
        
        // Print matrix with aligned columns
        for (int i = 0; i < n; i++) {
            sb.append("  [");
            for (int j = 0; j < n; j++) {
                if (j > 0) sb.append(", ");
                sb.append(String.format("%" + maxWidth + ".4f", get(i, j).getValue()));
            }
            sb.append("]");
            if (i < n - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
}
