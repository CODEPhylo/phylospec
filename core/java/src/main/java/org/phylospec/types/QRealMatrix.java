package org.phylospec.types;

import org.phylospec.domain.Real;

/**
 * Q-Matrix (Rate Matrix) type - used in continuous-time Markov chains.
 * 
 * A Q-matrix (also called an instantaneous rate matrix or generator matrix)
 * describes the instantaneous rates of change between states in a continuous-time
 * Markov chain (CTMC).
 * 
 * Properties:
 * - Square matrix
 * - Off-diagonal elements are non-negative (rates of transition)
 * - Diagonal elements are negative (negative sum of rates leaving each state)
 * - Each row sums to zero (conservation of probability)
 * 
 * Common uses in phylogenetics:
 * - DNA substitution models (JC69, K80, HKY85, GTR)
 * - Protein substitution models (WAG, LG, JTT)
 * - Codon models (MG94, GY94)
 * - Morphological character evolution
 * 
 * The transition probability matrix P(t) is obtained by matrix exponentiation:
 * P(t) = exp(Q*t), where t is the branch length or time.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface QRealMatrix<P extends Real> extends SquareRealMatrix<P> {
    
    /**
     * {@inheritDoc}
     * 
     * A QMatrix is valid if:
     * - It is a valid square matrix
     * - Off-diagonal elements are non-negative
     * - Diagonal elements are non-positive
     * - Each row sums to zero (within numerical tolerance)
     * 
     * @return true if this is a valid rate matrix, false otherwise
     */
    @Override
    default boolean isValid() {
        if (!SquareRealMatrix.super.isValid()) {
            return false;
        }
        
        int n = rows();
        
        for (int i = 0; i < n; i++) {
            double rowSum = 0.0;
            
            for (int j = 0; j < n; j++) {
                double value = get(i, j);
                
                if (i != j) {
                    // Off-diagonal elements must be non-negative
                    if (value < 0) {
                        return false;
                    }
                } else {
                    // Diagonal elements should be negative or zero
                    if (value > 0) {
                        return false;
                    }
                }
                
                rowSum += value;
            }
            
            // Each row must sum to zero (within epsilon)
            if (Math.abs(rowSum) > 1e-10) {
                return false;
            }
        }
        
        return true;
    }
}
