package org.phylospec.types;

import org.phylospec.primitives.UnitInterval;

/**
 * Stochastic matrix - probability transition matrix for discrete-time Markov chains.
 * 
 * A stochastic matrix (also called a probability matrix or transition matrix) represents
 * the probabilities of transitioning between states in a single time step.
 * 
 * Properties:
 * - All entries are probabilities in [0, 1]
 * - Each row sums to 1 (representing all possible transitions from a state)
 * - Entry (i,j) represents P(state j | state i) - the probability of transitioning
 *   from state i to state j in one time step
 * 
 * Common uses in phylogenetics:
 * - Discrete-time evolution models
 * - DNA/protein substitution probabilities after a fixed time period
 * - Character state transition probabilities
 * 
 * Related to Q-matrices by: P(t) = exp(Q*t), where Q is the rate matrix
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface StochasticRealMatrix<P extends UnitInterval> extends RealMatrix<P> {

    /**
     * {@inheritDoc}
     * 
     * A StochasticMatrix is valid if:
     * - It is a valid matrix of probabilities
     * - Each row sums to 1 (within numerical tolerance)
     * 
     * @return true if this is a valid stochastic matrix, false otherwise
     */
    @Override
    default boolean isValid() {
        if (!RealMatrix.super.isValid()) {
            return false;
        }
        
        // Check that each row sums to 1
        for (int i = 0; i < rows(); i++) {
            double rowSum = 0.0;
            for (int j = 0; j < cols(); j++) {
                rowSum += get(i, j);
            }
            if (Math.abs(rowSum - 1.0) > 1e-10) {
                return false;
            }
        }
        
        return true;
    }
}
