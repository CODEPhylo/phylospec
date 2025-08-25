//package org.phylospec.factory;
//
//import org.phylospec.types.*;
//import org.phylospec.types.impl.*;
//import java.util.List;
//import java.util.ArrayList;
//
///**
// * Factory class for creating PhyloSpec type instances.
// *
// * This class provides convenient static methods for creating all PhyloSpec types
// * with proper validation.
// *
// * @author PhyloSpec Contributors
// * @since 1.0
// */
//public final class PhyloSpecTypes {
//
//    // Private constructor to prevent instantiation
//    private PhyloSpecTypes() {
//        throw new AssertionError("PhyloSpecTypes should not be instantiated");
//    }
//
//    // ========== Basic Types ==========
//
//    /**
//     * Creates a Real number.
//     *
//     * @param value the real value
//     * @return a Real instance
//     * @throws IllegalArgumentException if value is NaN or infinite
//     */
//    public static Real real(double value) {
//        return new RealImpl(value);
//    }
//
//    /**
//     * Creates a PositiveReal number (> 0).
//     *
//     * @param value the positive real value
//     * @return a PositiveReal instance
//     * @throws IllegalArgumentException if value is not positive
//     */
//    public static PositiveReal positiveReal(double value) {
//        return new PositiveRealImpl(value);
//    }
//
//    /**
//     * Creates a NonNegativeReal number (>= 0).
//     *
//     * @param value the non-negative real value
//     * @return a NonNegativeReal instance
//     * @throws IllegalArgumentException if value is negative
//     */
//    public static NonNegativeReal nonNegativeReal(double value) {
//        return new NonNegativeRealImpl(value);
//    }
//
//    /**
//     * Creates a Probability value [0, 1].
//     *
//     * @param value the probability value
//     * @return a Probability instance
//     * @throws IllegalArgumentException if value is not in [0, 1]
//     */
//    public static Probability probability(double value) {
//        return new ProbabilityImpl(value);
//    }
//
//    /**
//     * Creates an Integer.
//     *
//     * @param value the integer value
//     * @return an Integer instance
//     */
//    public static Int integer(int value) {
//        return new IntImpl(value);
//    }
//
//    /**
//     * Creates a PositiveInteger (> 0).
//     *
//     * @param value the positive integer value
//     * @return a PositiveInteger instance
//     * @throws IllegalArgumentException if value is not positive
//     */
//    public static PositiveInt positiveInteger(int value) {
//        return new PositiveIntImpl(value);
//    }
//
//    /**
//     * Creates a Boolean.
//     *
//     * @param value the boolean value
//     * @return a Boolean instance
//     */
//    public static Bool bool(boolean value) {
//        return new BoolImpl(value);
//    }
//
//    /**
//     * Creates a String.
//     *
//     * @param value the string value
//     * @return a String instance
//     * @throws IllegalArgumentException if value is null
//     */
//    public static Str string(java.lang.String value) {
//        return new StrImpl(value);
//    }
//
//    // ========== Collection Types ==========
//
//    /**
//     * Creates a Vector from a list.
//     *
//     * @param <T> the element type
//     * @param elements the vector elements
//     * @return a Vector instance
//     */
//    public static <T extends PhyloSpecType> Vector<T> vector(List<T> elements) {
//        return new VectorImpl<>(elements);
//    }
//
//    /**
//     * Creates a Vector from varargs.
//     *
//     * @param <T> the element type
//     * @param elements the vector elements
//     * @return a Vector instance
//     */
//    @SafeVarargs
//    public static <T extends PhyloSpecType> Vector<T> vector(T... elements) {
//        return new VectorImpl<>(elements);
//    }
//
//    /**
//     * Creates a Simplex from probability values.
//     * Values must sum to 1.0.
//     *
//     * @param values the probability values
//     * @return a Simplex instance
//     * @throws IllegalArgumentException if values don't sum to 1.0
//     */
//    public static Simplex simplex(double... values) {
//        return new SimplexImpl(values);
//    }
//
//    /**
//     * Creates a Simplex from a list of probabilities.
//     *
//     * @param probabilities the probability values
//     * @return a Simplex instance
//     * @throws IllegalArgumentException if values don't sum to 1.0
//     */
//    public static Simplex simplex(List<Probability> probabilities) {
//        return new SimplexImpl(probabilities);
//    }
//
//    /**
//     * Creates a Matrix from a 2D list.
//     *
//     * @param <T> the element type
//     * @param elements the matrix elements
//     * @return a Matrix instance
//     */
//    public static <T extends PhyloSpecType> Matrix<T> matrix(List<List<T>> elements) {
//        return new MatrixImpl<>(elements);
//    }
//
//    /**
//     * Creates a real-valued Matrix from a 2D array.
//     *
//     * @param values the matrix values
//     * @return a Matrix of Real values
//     */
//    public static Matrix<Real> realMatrix(double[][] values) {
//        List<List<Real>> elements = new ArrayList<>();
//        for (double[] row : values) {
//            List<Real> realRow = new ArrayList<>();
//            for (double v : row) {
//                realRow.add(real(v));
//            }
//            elements.add(realRow);
//        }
//        return new MatrixImpl<>(elements);
//    }
//
//    /**
//     * Creates a SquareMatrix from a 2D list.
//     *
//     * @param <T> the element type
//     * @param elements the matrix elements
//     * @return a SquareMatrix instance
//     * @throws IllegalArgumentException if matrix is not square
//     */
//    public static <T extends PhyloSpecType> SquareMatrix<T> squareMatrix(List<List<T>> elements) {
//        return new SquareMatrixImpl<>(elements);
//    }
//
//    /**
//     * Creates a real-valued SquareMatrix from a 2D array.
//     *
//     * @param values the matrix values
//     * @return a SquareMatrix of Real values
//     * @throws IllegalArgumentException if matrix is not square
//     */
//    public static SquareMatrix<Real> realSquareMatrix(double[][] values) {
//        List<List<Real>> elements = new ArrayList<>();
//        for (double[] row : values) {
//            List<Real> realRow = new ArrayList<>();
//            for (double v : row) {
//                realRow.add(real(v));
//            }
//            elements.add(realRow);
//        }
//        return new SquareMatrixImpl<>(elements);
//    }
//
//    /**
//     * Creates a StochasticMatrix (probability transition matrix).
//     * Each row must sum to 1.0.
//     *
//     * @param values the probability values
//     * @return a StochasticMatrix instance
//     * @throws IllegalArgumentException if rows don't sum to 1.0
//     */
//    public static StochasticMatrix stochasticMatrix(double[][] values) {
//        return new StochasticMatrixImpl(values);
//    }
//
//    /**
//     * Creates a QMatrix (rate matrix for continuous-time Markov chains).
//     * Rows must sum to 0, off-diagonals must be non-negative.
//     *
//     * @param values the rate values
//     * @return a QMatrix instance
//     * @throws IllegalArgumentException if constraints are violated
//     */
//    public static QMatrix qMatrix(double[][] values) {
//        return new QMatrixImpl(values);
//    }
//
//    // ========== Phylogenetic Model Helpers ==========
//
//    /**
//     * Creates DNA base frequencies simplex.
//     *
//     * @param freqA frequency of A
//     * @param freqC frequency of C
//     * @param freqG frequency of G
//     * @param freqT frequency of T
//     * @return a Simplex instance
//     * @throws IllegalArgumentException if frequencies don't sum to 1.0
//     */
//    public static Simplex dnaFrequencies(double freqA, double freqC, double freqG, double freqT) {
//        return simplex(freqA, freqC, freqG, freqT);
//    }
//
//    /**
//     * Creates amino acid frequencies simplex (20 states).
//     *
//     * @param frequencies the 20 amino acid frequencies
//     * @return a Simplex instance
//     * @throws IllegalArgumentException if not exactly 20 values or don't sum to 1.0
//     */
//    public static Simplex aminoAcidFrequencies(double... frequencies) {
//        if (frequencies.length != 20) {
//            throw new IllegalArgumentException(
//                "Amino acid frequencies must have exactly 20 values, but got: " + frequencies.length);
//        }
//        return simplex(frequencies);
//    }
//
//    /**
//     * Creates a Jukes-Cantor Q-matrix.
//     * All substitution rates are equal.
//     *
//     * @param mu substitution rate
//     * @return a QMatrix instance
//     */
//    public static QMatrix jukesCantor(double mu) {
//        if (mu <= 0) {
//            throw new IllegalArgumentException("Substitution rate must be positive");
//        }
//        double rate = mu / 3.0;
//        return qMatrix(new double[][]{
//            {-mu,   rate,  rate,  rate},
//            {rate,  -mu,   rate,  rate},
//            {rate,  rate,  -mu,   rate},
//            {rate,  rate,  rate,  -mu}
//        });
//    }
//
//    /**
//     * Creates a Kimura 2-parameter (K80) Q-matrix.
//     *
//     * @param alpha transition rate (A<->G, C<->T)
//     * @param beta transversion rate (all others)
//     * @return a QMatrix instance
//     */
//    public static QMatrix kimura2Parameter(double alpha, double beta) {
//        if (alpha <= 0 || beta <= 0) {
//            throw new IllegalArgumentException("Rates must be positive");
//        }
//        return qMatrix(new double[][]{
//            {-(alpha + 2*beta), beta,  alpha, beta },
//            {beta,  -(alpha + 2*beta), beta,  alpha},
//            {alpha, beta,  -(alpha + 2*beta), beta },
//            {beta,  alpha, beta,  -(alpha + 2*beta)}
//        });
//    }
//
//    /**
//     * Creates an HKY85 Q-matrix.
//     *
//     * @param kappa transition/transversion ratio
//     * @param pi base frequencies (must sum to 1)
//     * @return a QMatrix instance
//     */
//    public static QMatrix hky85(double kappa, double[] pi) {
//        if (kappa <= 0) {
//            throw new IllegalArgumentException("Kappa must be positive");
//        }
//        if (pi.length != 4) {
//            throw new IllegalArgumentException("Base frequencies must have exactly 4 values");
//        }
//
//        // Validate frequencies sum to 1
//        double sum = 0;
//        for (double p : pi) {
//            if (p < 0 || p > 1) {
//                throw new IllegalArgumentException("Base frequencies must be in [0, 1]");
//            }
//            sum += p;
//        }
//        if (Math.abs(sum - 1.0) > 1e-10) {
//            throw new IllegalArgumentException("Base frequencies must sum to 1");
//        }
//
//        // Build HKY85 rate matrix
//        double[][] q = new double[4][4];
//
//        // Off-diagonal rates
//        q[0][1] = pi[1];        // A -> C
//        q[0][2] = kappa * pi[2]; // A -> G
//        q[0][3] = pi[3];        // A -> T
//
//        q[1][0] = pi[0];        // C -> A
//        q[1][2] = pi[2];        // C -> G
//        q[1][3] = kappa * pi[3]; // C -> T
//
//        q[2][0] = kappa * pi[0]; // G -> A
//        q[2][1] = pi[1];        // G -> C
//        q[2][3] = pi[3];        // G -> T
//
//        q[3][0] = pi[0];        // T -> A
//        q[3][1] = kappa * pi[1]; // T -> C
//        q[3][2] = pi[2];        // T -> G
//
//        // Set diagonal elements so rows sum to 0
//        for (int i = 0; i < 4; i++) {
//            double rowSum = 0;
//            for (int j = 0; j < 4; j++) {
//                if (i != j) rowSum += q[i][j];
//            }
//            q[i][i] = -rowSum;
//        }
//
//        return qMatrix(q);
//    }
//
//    // ========== Conversion Methods ==========
//
//    /**
//     * Normalizes a vector of non-negative values to create a Simplex.
//     *
//     * @param values the values to normalize
//     * @return a Simplex with normalized values
//     * @throws IllegalArgumentException if any value is negative or sum is 0
//     */
//    public static Simplex toSimplex(double... values) {
//        double sum = 0;
//        for (double v : values) {
//            if (v < 0) {
//                throw new IllegalArgumentException("Cannot normalize negative values to simplex");
//            }
//            sum += v;
//        }
//        if (sum == 0) {
//            throw new IllegalArgumentException("Cannot normalize zero vector to simplex");
//        }
//
//        double[] normalized = new double[values.length];
//        for (int i = 0; i < values.length; i++) {
//            normalized[i] = values[i] / sum;
//        }
//        return simplex(normalized);
//    }
//}
