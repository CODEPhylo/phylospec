# PhyloSpec Type System

This document defines the type system that forms the foundation of PhyloSpec. All implementations must support these types and their relationships to be considered PhyloSpec-compatible.

## 1. Primitive Types

### 1.1 Basic Types

| Type      | Description                   | Example Values           | Constraints                |
|-----------|-------------------------------|--------------------------|----------------------------|
| `Real`    | Real-valued number            | 1.0, -0.5, 3.14159       | Must be finite (not NaN or Infinity) |
| `Integer` | Integer-valued number         | 1, -5, 42                | Must be a whole number     |
| `Boolean` | Logical value                 | true, false              | None                       |
| `String`  | Text value                    | "human", "ACGT"          | Must not be null           |

### 1.2 Restricted Types

| Type               | Base Type  | Description                       | Constraints                        |
|--------------------|------------|-----------------------------------|-----------------------------------|
| `PositiveReal`     | `Real`     | Positive real number              | Value > 0                          |
| `Probability`      | `Real`     | Probability value                 | 0.0 ≤ Value ≤ 1.0                 |
| `NonNegativeReal`  | `Real`     | Non-negative real number          | Value ≥ 0                          |
| `PositiveInteger`  | `Integer`  | Positive integer                  | Value > 0                          |

## 2. Collection Types

### 2.1 Base Collections

| Type          | Description                    | Type Parameters         | Example                        |
|---------------|--------------------------------|-------------------------|--------------------------------|
| `Vector<T>`   | Ordered collection of values   | T: element type         | [1.0, 2.0, 3.0]                |
| `Matrix<T>`   | 2D grid of values              | T: element type         | [[1.0, 2.0], [3.0, 4.0]]       |
| `Map<K,V>`    | Key-value mapping              | K: key, V: value type   | {"A": 0.25, "C": 0.25}         |
| `Set<T>`      | Unordered collection (unique)  | T: element type         | {"human", "chimp", "gorilla"}  |

### 2.2 Specialized Matrix Types

| Type                  | Base Type        | Description                             | Constraints                         |
|-----------------------|-----------------|-----------------------------------------|-------------------------------------|
| `SquareMatrix<T>`     | `Matrix<T>`     | Square matrix (n×n)                     | Number of rows equals columns       |
| `StochasticMatrix`    | `Matrix<Probability>` | Probability transition matrix      | Each row sums to 1.0               |

### 2.3 Common Type Aliases

| Alias          | Definition              | Description                             |
|----------------|------------------------|-----------------------------------------|
| `RealVector`   | `Vector<Real>`         | Vector of real values                   |
| `IntVector`    | `Vector<Integer>`      | Vector of integer values                |
| `StringVector` | `Vector<String>`       | Vector of strings                       |
| `RealMatrix`   | `Matrix<Real>`         | Matrix of real values                   |

## 3. Phylogenetic Types

### 3.1 Specialized Types

| Type          | Base Type              | Description                             | Constraints                         |
|---------------|------------------------|-----------------------------------------|-------------------------------------|
| `Simplex`     | `Vector<Probability>`  | Probability vector                      | Elements sum to 1.0 (within ε=1e-10) |
| `QMatrix`     | `SquareMatrix<Real>`   | Rate matrix for CTMCs                   | Rows sum to 0, off-diagonals ≥ 0    |
| `IndexSet`    | `Set<Integer>`         | Set of integer indices                  | All values are non-negative         |

### 3.2 Tree Types

| Type           | Properties                                  | Methods                                     | Description                            |
|----------------|---------------------------------------------|---------------------------------------------|----------------------------------------|
| `Taxon`        | name: `String`                              | `equals(t: Taxon): Boolean`                 | Taxonomic unit                        |
| `TaxonSet`     | taxa: `Set<Taxon>`                          | `contains(t: Taxon): Boolean`               | Collection of taxa                    |
| `TreeNode`     | parent: `TreeNode?`<br>children: `Vector<TreeNode>` | `isLeaf(): Boolean`<br>`isRoot(): Boolean` | Node in a phylogenetic tree          |
| `Tree`         | root: `TreeNode`<br>nodes: `Vector<TreeNode>`<br>taxa: `TaxonSet` | `getNode(id: String): TreeNode`<br>`getTips(): Vector<TreeNode>` | Phylogenetic tree structure           |
| `TimeTree`     | All `Tree` properties                        | `getAge(node: TreeNode): Real`<br>`getHeight(): Real` | Time-calibrated tree                 |

### 3.3 Sequence Types

| Type                   | Type Parameters                 | Properties                              | Description                          |
|------------------------|--------------------------------|----------------------------------------|--------------------------------------|
| `Sequence<A>`          | A: alphabet type               | data: `Vector<A>`<br>taxon: `Taxon`     | Biological sequence of type A        |
| `Alignment<A>`         | A: alphabet type               | sequences: `Vector<Sequence<A>>`        | Multiple sequence alignment          |
| `DNASequence`          | N/A (= `Sequence<Nucleotide>`) | data: vector of DNA nucleotides         | DNA sequence                         |
| `ProteinSequence`      | N/A (= `Sequence<AminoAcid>`)  | data: vector of amino acids             | Protein sequence                     |
| `CodonSequence`        | N/A (= `Sequence<Codon>`)      | data: vector of codons                  | Codon sequence                       |
| `DNAAlignment`         | N/A (= `Alignment<Nucleotide>`)| sequences: vector of DNA sequences      | DNA sequence alignment               |

### 3.4 Alphabets

| Type          | Values                                  | Description                             |
|---------------|----------------------------------------|-----------------------------------------|
| `Nucleotide`  | A, C, G, T/U                           | DNA/RNA nucleotide                      |
| `AminoAcid`   | A, R, N, D, C, Q, E, G, H, I, L, K, M, F, P, S, T, W, Y, V | Standard amino acids |
| `Codon`       | AAA, AAC, AAG, AAT, ...                | Nucleotide triplet                      |

## 4. Type Hierarchies

### 4.1 Type Inheritance

```
PhyloSpecType (base interface for all types)
│
├── Real
│   ├── PositiveReal
│   ├── NonNegativeReal
│   └── Probability
│
├── Integer
│   └── PositiveInteger
│
├── Boolean
│
├── String
│
├── Vector<T>
│   └── Simplex (extends Vector<Probability>)
│
└── Matrix<T>
    ├── SquareMatrix<T>
    │   └── QMatrix (extends SquareMatrix<Real>)
    └── StochasticMatrix (extends Matrix<Probability>)

Tree
└── TimeTree

Set<T>
└── IndexSet (extends Set<Integer>)
```

### 4.2 Type Parameterization

Type parameters enable generic types to be specialized for particular element types:

```
Vector<T>           // Generic vector of any type T
Vector<Real>        // Vector of real values
Vector<Nucleotide>  // Vector of nucleotides

Matrix<T>           // Generic matrix of any type T
Matrix<Real>        // Matrix of real values
Matrix<Probability> // Matrix of probabilities
```

## 5. Type Conversion Rules

### 5.1 Implicit Conversions

Implicit conversions are automatically applied where safe:

| From Type          | To Type              | Condition                              |
|--------------------|---------------------|----------------------------------------|
| `Integer`          | `Real`              | Always valid                           |
| `PositiveInteger`  | `PositiveReal`      | Always valid                           |
| `T`                | `T?`                | Any type to optional version of itself |
| `PositiveReal`     | `NonNegativeReal`   | Always valid (positive is non-negative)|

### 5.2 Explicit Conversions

Explicit conversions must be requested by the modeler:

| From Type              | To Type        | Function            | Behavior                           |
|------------------------|----------------|--------------------|------------------------------------|
| `Real`                 | `Integer`      | `toInteger(r)`     | Truncate to integer (loses precision) |
| `Vector<Real>`         | `Simplex`      | `toSimplex(v)`     | Normalize vector to sum to 1.0     |
| `Vector<Real>`         | `QMatrix`      | `toQMatrix(v, dim)`| Convert vector to rate matrix      |

## 6. Type Validation

### 6.1 Validation Rules

All types must validate their constraints at construction time. Invalid values should result in an error or exception.

| Type               | Validation Rule                                        |
|--------------------|-------------------------------------------------------|
| `Real`             | Must be finite (not NaN or Infinity)                  |
| `String`           | Must not be null                                      |
| `PositiveReal`     | Must be > 0 and finite                               |
| `NonNegativeReal`  | Must be ≥ 0 and finite                               |
| `Probability`      | Must be in [0, 1] and finite                         |
| `PositiveInteger`  | Must be > 0                                          |
| `Vector<T>`        | All elements must be valid instances of T            |
| `Matrix<T>`        | Must be rectangular; all elements valid              |
| `SquareMatrix<T>`  | Must have equal rows and columns                     |
| `Simplex`          | Elements must sum to 1.0 (within ε=1e-10)           |
| `StochasticMatrix` | Each row must sum to 1.0 (within ε=1e-10)          |
| `QMatrix`          | Rows sum to 0; off-diagonals ≥ 0; diagonal ≤ 0      |

### 6.2 Numerical Tolerance

For floating-point comparisons, implementations should use a tolerance of ε=1e-10 for:
- Simplex sum validation
- Stochastic matrix row sum validation  
- Q-matrix row sum validation

## 7. Indexing and Element Access

### 7.1 Indexable Types

The following types support element access through indexing:

| Type | Indexing Syntax | Return Type | Index Type | Notes |
|------|-----------------|-------------|------------|-------|
| `Vector<T>` | `v[i]` | `T` | `Integer` | 0-based indexing |
| `Matrix<T>` | `m[i,j]` or `m[i][j]` | `T` | `Integer, Integer` | Row, column indices |
| `Simplex` | `s[i]` | `Probability` | `Integer` | 0-based indexing |
| `StochasticMatrix` | `p[i,j]` | `Probability` | `Integer, Integer` | Row, column indices |
| `QMatrix` | `q[i,j]` | `Real` | `Integer, Integer` | Row, column indices |
| `Sequence<A>` | `seq[i]` | `A` | `Integer` | 0-based position |
| `Alignment<A>` | `aln[i,j]` | `A` | `Integer, Integer` | Sequence, position |
| `String` | `s[i]` | `Character` | `Integer` | 0-based position |

### 7.2 Indexing Rules

1. **Bounds Checking**: All indexing operations must check bounds and report errors for out-of-range indices
2. **0-Based**: All indices are 0-based (first element is at index 0)
3. **Return Types**: Indexing returns the element type, not a wrapped type
4. **Immutability**: Indexing provides read-only access; elements cannot be modified through indexing

### 7.3 Special Indexing Cases

```
# Examples of valid indexing
Simplex pi ~ Dirichlet(1.0, 1.0, 1.0, 1.0);
Real freqA = pi[0];  # First element (A frequency)

Matrix<Real> m = ...;
Real element = m[2,3];  # Row 2, column 3

# Slicing (optional feature)
Vector<Real> subvector = v[1:3];  # Elements 1 and 2
Matrix<Real> submatrix = m[0:2, 1:4];  # Rows 0-1, columns 1-3
```

### 7.4 Non-Indexable Types

The following types do NOT support direct indexing:
- Primitive types (`Real`, `Integer`, `Boolean`)
- `Map<K,V>` - Use key-based access instead
- `Set<T>` - Unordered, no positional access
- Tree types - Use navigation methods instead

## 8. Implementation Requirements

Language implementations must:

1. Support all primitive types and their constraints
2. Implement the core collection types
3. Support the phylogenetic types with their properties and methods
4. Enforce type constraints during model validation
5. Allow for type parameterization where specified
6. Apply the defined conversion rules correctly
7. Use appropriate numerical tolerance for floating-point validations

## 8. Type Extensions

Languages may extend the type system with additional types, but must maintain compatibility with the core types defined here. Extensions should be clearly documented as non-standard.

## Reference

For machine-readable definitions, see [types.json](../../schema/types.json) in the schema directory.