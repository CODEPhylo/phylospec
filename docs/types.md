# PhyloSpec Type System

This document defines the type system that forms the foundation of PhyloSpec. All implementations must support these types and their relationships to be considered PhyloSpec-compatible.

## 1. Primitive Types

### 1.1 Basic Types

| Type      | Description                   | Example Values           | Constraints                | Package |
|-----------|-------------------------------|--------------------------|----------------------------|----------|
| `Real`    | Real-valued number            | 1.0, -0.5, 3.14159       | Must be finite (not NaN or Infinity) | phylospec.types |
| `Integer` | Integer-valued number         | 1, -5, 42                | Must be a whole number     | phylospec.types |
| `Boolean` | Logical value                 | true, false              | None                       | phylospec.types |
| `String`  | Text value                    | "human", "ACGT"          | Must not be null           | phylospec.types |

### 1.2 Restricted Types

| Type               | Base Type  | Description                       | Constraints                        | Package |
|--------------------|------------|-----------------------------------|------------------------------------|----------|
| `PositiveReal`     | `Real`     | Positive real number (> 0)        | Value > 0                          | phylospec.types |
| `NonNegativeReal`  | `Real`     | Non-negative real number (>= 0)  | Value ≥ 0                          | phylospec.types |
| `Probability`      | `Real`     | Probability value [0,1]           | 0.0 ≤ Value ≤ 1.0                  | phylospec.types |
| `NonNegativeInteger`  | `Integer`  | Non-negative integer (>= 0)            | Value >= 0                          | phylospec.types |
| `PositiveInteger`  | `NonNegativeInteger`  | Positive integer (> 0)            | Value > 0                          | phylospec.types |

### 1.3 Alias Types

| Type        | Alias for            |
|-------------|----------------------|
| `Rate`      | `NonNegativeReal`    |
| `Count`     | `NonNegativeInteger` |
| `Path`      | `String`             |
| `TaxonName` | `String`             |

## 2. Collection Types

### 2.1 Base Collections

| Type          | Description                    | Type Parameters         | Example                        | Package |
|---------------|--------------------------------|-------------------------|--------------------------------|----------|
| `Vector<T>`   | Generic ordered collection of elements | T: element type   | [1.0, 2.0, 3.0]                | phylospec.types |
| `Matrix<T>`   | Generic two-dimensional grid of values | T: element type   | [[1.0, 2.0], [3.0, 4.0]]       | phylospec.types |
| `List<T>`     | Ordered collection of elements | T: element type         | ["a", "b", "c"]                | phylospec.types |
| `Map<K,V>`    | Mappings from keys to values  | K: key, V: value type   | {"A": 0.25, "C": 0.25}         | phylospec.types |
| `Set<T>`      | Unordered collection with unique elements | T: element type | {"human", "chimp", "gorilla"}  | phylospec.types |

### 2.2 Specialized Types

| Type                  | Base Type                      | Description                             | Constraints                         | Package |
|-----------------------|-------------------------------|-----------------------------------------|-------------------------------------|----------|
| `SquareMatrix<T>`     | `Matrix<T>`                   | Square matrix with equal number of rows and columns | Number of rows equals columns | phylospec.types |
| `Simplex`             | `Vector<Probability>`         | Probability vector with elements that sum to 1.0 | Elements sum to 1.0 (within ε=1e-10) | phylospec.types |
| `Frequencies`         | `Vector<Probability>`         | Alias for `Simplex` | Elements sum to 1.0 (within ε=1e-10) | phylospec.types |
| `QMatrix`             | `SquareMatrix<Real>`          | Rate matrix for substitution models     | Rows sum to 0, off-diagonals ≥ 0    | phylospec.types |
| `StochasticMatrix`    | `Matrix<Probability>`         | Stochastic matrix - probability transition matrix | Each row sums to 1.0 | phylospec.types |

## 3. Biological Alphabets

### 3.1 Sequence Alphabets

| Type          | States                              | Ambiguous Codes                    | Description                | Package |
|---------------|-------------------------------------|------------------------------------|-----------------------------|----------|
| `Nucleotide`  | A, C, G, T                          | R, Y, S, W, K, M, B, D, H, V, N, -, ? | DNA/RNA nucleotide alphabet | phylospec.types |
| `AminoAcid`   | A, R, N, D, C, Q, E, G, H, I, L, K, M, F, P, S, T, W, Y, V | B, Z, X, *, -, ? | Standard amino acid alphabet | phylospec.types |

## 4. Sequence Types

| Type                   | Base Type                      | Properties                              | Description                          | Package |
|------------------------|-------------------------------|-----------------------------------------|--------------------------------------|----------|
| `Sequence<A>`          | -                             | length: Integer<br>alphabet: String     | Biological sequence with elements from alphabet A | phylospec.types |
| `DNASequence`          | `Sequence<Nucleotide>`        | (inherited from Sequence)               | DNA sequence                         | phylospec.types |
| `ProteinSequence`      | `Sequence<AminoAcid>`         | (inherited from Sequence)               | Protein sequence                     | phylospec.types |

## 5. Phylogenetic Types

### 5.1 Taxa Types

| Type           | Properties                                  | Description                            | Package |
|----------------|---------------------------------------------|----------------------------------------|----------|
| `Taxon`        | name: String<br>age: Real (optional)        | Taxonomic unit                        | phylospec.types |
| `TaxonSet`     | ntax: Integer<br>names: Vector<String> (accessor) | Collection of taxa               | phylospec.types |

### 5.2 Tree Types

| Type           | Base Type | Properties                                  | Description                            | Package |
|----------------|-----------|---------------------------------------------|----------------------------------------|----------|
| `TreeNode`     | -         | (implementation-specific)                   | Node in a phylogenetic tree           | phylospec.types |
| `Tree`         | -         | ntax: Integer<br>nBranches: Integer<br>nInternalNodes: Integer<br>taxa: TaxonSet (accessor) | Phylogenetic tree structure | phylospec.types |
| `TimeTree`     | Tree      | (inherits all Tree properties)              | Time-calibrated tree                  | phylospec.types |

### 5.3 Alignment Type

| Type           | Properties                                  | Description                            | Package |
|----------------|---------------------------------------------|----------------------------------------|----------|
| `Alignment`    | ntax: Integer<br>nchar: Integer<br>dataType: String<br>taxa: TaxonSet (accessor) | Multiple sequence alignment with extractable properties | phylospec.types |

## 6. Distribution Types

### 6.1 Abstract Distribution Type

| Type           | Properties                    | Description                            | Package |
|----------------|-------------------------------|----------------------------------------|----------|
| `Distribution` | generatedType: String         | Abstract type representing a probability distribution | phylospec.types |

### 6.2 Concrete Distribution Types

All concrete distribution types extend the base `Distribution` type with a specific generated type:

| Type                  | Extends                    | Description                            | Package |
|-----------------------|---------------------------|----------------------------------------|----------|
| `Normal`              | `Distribution<Real>`      | Normal distribution                    | phylospec.distributions |
| `LogNormal`           | `Distribution<PositiveReal>` | Log-normal distribution              | phylospec.distributions |
| `Gamma`               | `Distribution<PositiveReal>` | Gamma distribution                   | phylospec.distributions |
| `Beta`                | `Distribution<Probability>` | Beta distribution                     | phylospec.distributions |
| `Exponential`         | `Distribution<PositiveReal>` | Exponential distribution             | phylospec.distributions |
| `Uniform`             | `Distribution<Real>`      | Uniform distribution                   | phylospec.distributions |
| `Dirichlet`           | `Distribution<Simplex>`   | Dirichlet distribution                 | phylospec.distributions |
| `MultivariateNormal`  | `Distribution<Vector<Real>>` | Multivariate normal distribution    | phylospec.distributions |
| `Yule`                | `Distribution<Tree>`      | Yule tree distribution                 | phylospec.distributions |
| `BirthDeath`          | `Distribution<Tree>`      | Birth-death tree distribution          | phylospec.distributions |
| `Coalescent`          | `Distribution<Tree>`      | Coalescent tree distribution           | phylospec.distributions |
| `FossilBirthDeath`    | `Distribution<TimeTree>`  | Birth-death distribution with fossil sampling | phylospec.distributions |
| `PhyloCTMC`           | `Distribution<Alignment>` | Phylogenetic continuous-time Markov chain distribution | phylospec.distributions |
| `PhyloBM`             | `Distribution<Vector<Real>>` | Phylogenetic Brownian motion distribution | phylospec.distributions |
| `PhyloOU`             | `Distribution<Vector<Real>>` | Phylogenetic Ornstein-Uhlenbeck distribution | phylospec.distributions |
| `IID<T>`              | `Distribution<Vector<T>>` | IID distribution (parameterized)       | phylospec.distributions |
| `Mixture<T>`          | `Distribution<T>`         | Mixture distribution (parameterized)   | phylospec.distributions |

## 7. Type Hierarchies

### 7.1 Type Inheritance

```
Object (base for all types)
│
├── Real
│   ├── NonNegativeReal
│   │   ├── PositiveReal
│   │   └── Probability
│   └── Rate (alias for NonNegativeReal) 
│
├── Integer
│   ├── NonNegativeInteger
│   │   └── PositiveInteger
│   └── Count (alias for NonNegativeInteger)
│
├── Boolean
│
├── String
├── Path (alias for String)
├── TaxonName (alias for String)
│
├── Vector<T>
│   ├── Simplex (extends Vector<Probability>)
│   └── Frequencies (alias for Simplex)
│
├── Matrix<T>
│   ├── SquareMatrix<T>
│   │   └── QMatrix (extends SquareMatrix<Real>)
│   └── StochasticMatrix (extends Matrix<Probability>)
│
├── Sequence<A>
│   ├── DNASequence (type alias for Sequence<Nucleotide>)
│   └── ProteinSequence (type alias for Sequence<AminoAcid>)
│
├── Tree
│   └── TimeTree
│
└── Distribution<T> (abstract)
    ├── Normal (generates Real)
    ├── LogNormal (generates PositiveReal)
    ├── Gamma (generates PositiveReal)
    ├── Beta (generates Probability)
    ├── Exponential (generates PositiveReal)
    ├── Uniform (generates Real)
    ├── Dirichlet (generates Simplex)
    ├── MultivariateNormal (generates Vector<Real>)
    ├── Yule (generates Tree)
    ├── BirthDeath (generates Tree)
    ├── Coalescent (generates Tree)
    ├── FossilBirthDeath (generates TimeTree)
    ├── PhyloCTMC (generates Alignment)
    ├── PhyloBM (generates Vector<Real>)
    ├── PhyloOU (generates Vector<Real>)
    ├── IID<T> (generates Vector<T>)
    └── Mixture<T> (generates T)
```

### 7.2 Type Parameterization

Type parameters enable generic types to be specialized for particular element types:

```
Vector<T>           // Generic vector of any type T
Vector<Real>        // Vector of real values
Vector<Nucleotide>  // Vector of nucleotides

Matrix<T>           // Generic matrix of any type T
Matrix<Real>        // Matrix of real values
Matrix<Probability> // Matrix of probabilities

Sequence<A>         // Generic sequence with alphabet type A
Sequence<Nucleotide> // DNA sequence
Sequence<AminoAcid>  // Protein sequence

Distribution<T>     // Generic distribution generating type T
Distribution<Real>  // Distribution generating real values
Distribution<Tree>  // Distribution generating trees
```

## 8. Type Validation

### 8.1 Validation Rules

All types must validate their constraints at construction time. Invalid values should result in an error or exception.

| Type               | Validation Rule                                        |
|--------------------|-------------------------------------------------------|
| `Real`             | Must be finite (not NaN or Infinity)                  |
| `String`           | Must not be null                                      |
| `PositiveReal`     | Must be > 0 and finite                               |
| `NonNegativeReal`  | Must be ≥ 0 and finite                               |
| `Probability`      | Must be in [0, 1] and finite                         |
| `PositiveInteger`  | Must be > 0                                          |
| `NonNegativeInteger`| Must be >= 0                                          |
| `Vector<T>`        | All elements must be valid instances of T            |
| `Matrix<T>`        | Must be rectangular; all elements valid              |
| `SquareMatrix<T>`  | Must have equal rows and columns                     |
| `Simplex`          | Elements must sum to 1.0 (within ε=1e-10)           |
| `StochasticMatrix` | Each row must sum to 1.0 (within ε=1e-10)          |
| `QMatrix`          | Rows sum to 0; off-diagonals ≥ 0; diagonal ≤ 0      |

### 8.2 Numerical Tolerance

For floating-point comparisons, implementations should use a tolerance of ε=1e-10 for:
- Simplex sum validation
- Stochastic matrix row sum validation  
- Q-matrix row sum validation

## 9. Primitive Type Assignment

Certain PhyloSpec types can be directly assigned primitive values:

| PhyloSpec Type     | Accepted Primitives              | Example                    |
|--------------------|----------------------------------|----------------------------|
| `Real`             | Double, Float, Integer, Long     | `Real x = 3.14;`          |
| `Integer`          | Integer, Long                    | `Integer n = 42;`         |
| `Boolean`          | Boolean                          | `Boolean flag = true;`    |
| `String`           | String                           | `String name = "human";`  |
| `PositiveReal`     | Double, Float                    | `PositiveReal r = 2.5;`   |
| `NonNegativeReal`  | Double, Float, Integer, Long     | `NonNegativeReal x = 0;`  |
| `Probability`      | Double, Float                    | `Probability p = 0.95;`   |
| `PositiveInteger`  | Integer, Long                    | `PositiveInteger n = 5;`  |

Note: Primitive assignment is subject to constraint validation. For example, assigning -1.0 to a `PositiveReal` will fail validation.

## 10. Implementation Requirements

Language implementations must:

1. Support all primitive types and their constraints
2. Implement the core collection types with proper type parameterization
3. Support the biological alphabet types
4. Support the phylogenetic types with their properties
5. Implement the distribution type hierarchy
6. Enforce type constraints during model validation
7. Allow for type parameterization where specified
8. Use appropriate numerical tolerance for floating-point validations
9. Support primitive type assignment where specified
10. Provide proper package/namespace organization

## Reference

For machine-readable definitions, see the `phylospec-model-library.json` file in the schema directory.