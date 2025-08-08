package org.phylospec.types.impl;

import org.phylospec.types.Probability;
import org.phylospec.types.StochasticMatrix;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable implementation of the StochasticMatrix type.
 * A stochastic matrix is a probability transition matrix where each row sums to 1.0.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class StochasticMatrixImpl 
        extends MatrixImpl<Probability> implements StochasticMatrix {
    
    /**
     * Constructs a StochasticMatrix from a 2D array of probabilities.
     * 
     * @param values the probability values
     * @throws IllegalArgumentException if any row doesn't sum to 1.0
     */
    public StochasticMatrixImpl(double[][] values) {
        super(createProbabilityMatrix(values));
        if (!isValid()) {
            throw new IllegalArgumentException(
                "Invalid stochastic matrix: each row must sum to 1.0 (within tolerance 1e-10)");
        }
    }
    
    /**
     * Constructs a StochasticMatrix from a list of probability rows.
     * 
     * @param elements the probability matrix
     * @throws IllegalArgumentException if any row doesn't sum to 1.0
     */
    public StochasticMatrixImpl(List<List<Probability>> elements) {
        super(elements);
        if (!isValid()) {
            throw new IllegalArgumentException(
                "Invalid stochastic matrix: each row must sum to 1.0 (within tolerance 1e-10)");
        }
    }
    
    private static List<List<Probability>> createProbabilityMatrix(double[][] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Matrix values cannot be null or empty");
        }
        
        List<List<Probability>> matrix = new ArrayList<>(values.length);
        for (double[] row : values) {
            if (row == null) {
                throw new IllegalArgumentException("Matrix row cannot be null");
            }
            List<Probability> probRow = new ArrayList<>(row.length);
            for (double value : row) {
                probRow.add(new ProbabilityImpl(value));
            }
            matrix.add(probRow);
        }
        return matrix;
    }
    
    @Override
    public String getTypeName() {
        return "StochasticMatrix";
    }
}
