# PhyloSpec Type System

This document defines the type system that forms the foundation of PhyloSpec. All implementations must support these types and their relationships to be considered PhyloSpec-compatible.

## 1. Primitive Types

### 1.1 Basic Types

| Type      | Description                   | Example Values           | Constraints                |
|-----------|-------------------------------|--------------------------|----------------------------|
| `Real`    | Real-valued number            | 1.0, -0.5, 3.14159       | None                       |
| `Integer` | Integer-valued number         | 1, -5, 42                | Must be a whole number     |
| `Boolean` | Logical value                 | true, false              | None                       |
| `String`  | Text value                    | "human", "ACGT"          | None                       |

### 1.2 Restricted Types

| Type           | Base Type  | Description                       | Constraints                        |
|----------------|------------|-----------------------------------|-----------------------------------|
| `PositiveReal` | `Real`     | Positive real number              | Value > 0                          |
| `Probability`  | `Real`     | Probability value                 | 0.0 ≤ Value ≤ 1.0                 |
| `NonNegReal`   | `Real`     | Non-negative real number          | Value ≥ 0                          |
| `PosInteger`   | `Integer`  | Positive integer                  | Value > 0                          |

## 2. Collection Types

### 2.1 Base Collections

| Type          | Description                    | Type Parameters         | Example                        |
|---------------|--------------------------------|-------------------------|--------------------------------|
| `Vector<T>`   | Ordered collection of values   | T: element type         | [1.0, 2.0, 3.0]                |
| `Matrix<T>`   | 2D grid of values              | T: element type         | [[1.0, 2.0], [3.0, 4.0]]       |
| `Map<K,V>`    | Key-value mapping              | K: key, V: value type   | {"A": 0.25, "C": 0.25}         |
| `Set<T>`      | Unordered collection (unique)  | T: element type         | {"human", "chimp", "gorilla"}  |

### 2.2 Common Type Aliases

| Alias          | Definition              | Description                             |
|----------------|------------------------|-----------------------------------------|
| `RealVector`   | `Vector<Real>`         | Vector of real values                   |
| `IntVector`    | `Vector<Integer>`      | Vector of integer values                |
| `StringVector` | `Vector<String>`       | Vector of strings                       |
| `RealMatrix`   | `Matrix<Real>`         | Matrix of real values                   |

## 3. Phylogenetic Types

### 3.1 Specialized Types

| Type          | Base Type       | Description                             | Constraints                         |
|---------------|----------------|-----------------------------------------|-------------------------------------|
| `Simplex`     | `RealVector`   | Probability vector                      | Elements sum to 1.0, all ≥ 0        |
| `QMatrix`     | `RealMatrix`   | Rate matrix for substitution models     | Rows sum to 0, off-diagonals ≥ 0    |
| `Alignment`   | N/A            | Multiple sequence alignment             | Sequences have same length          |
| `IndexSet`    | `Set<Integer>` | Set of integer indices                  | All values are non-negative         |

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
| `AminoAcid`   | A, R, N, D, C, Q, E, G, H, I, L, K, ... | Standard amino acids                    |
| `Codon`       | AAA, AAC, AAG, AAT, ...                | Nucleotide triplet                      |

## 4. Type Hierarchies

### 4.1 Type Inheritance

```
Real
├── PositiveReal
├── NonNegReal
└── Probability

Integer
└── PosInteger

Vector<T>
├── RealVector
│   └── Simplex
└── IntVector

Matrix<T>
└── RealMatrix
   └── QMatrix

Tree
└── TimeTree
```

### 4.2 Type Parameterization

Type parameters enable generic types to be specialized for particular element types:

```
Vector<T>           // Generic vector of any type T
Vector<Real>        // Vector of real values
Vector<Nucleotide>  // Vector of nucleotides
```

## 5. Type Conversion Rules

### 5.1 Implicit Conversions

Implicit conversions are automatically applied where safe:

| From Type      | To Type        | Condition                              |
|----------------|----------------|----------------------------------------|
| `Integer`      | `Real`         | Always valid                           |
| `PosInteger`   | `PositiveReal` | Always valid                           |
| `T`            | `T?`           | Any type to optional version of itself |
| `PositiveReal` | `NonNegReal`   | Always valid (positive is non-negative)|

### 5.2 Explicit Conversions

Explicit conversions must be requested by the modeler:

| From Type      | To Type        | Function            | Behavior                           |
|----------------|----------------|--------------------|------------------------------------|
| `Real`         | `Integer`      | `toInteger(r)`     | Truncate to integer (loses precision) |
| `RealVector`   | `Simplex`      | `toSimplex(v)`     | Normalize vector to sum to 1.0     |
| `RealVector`   | `QMatrix`      | `toQMatrix(v, dim)`| Convert vector to rate matrix      |

## 6. Implementation Requirements

Language implementations must:

1. Support all primitive types and their constraints
2. Implement the core collection types
3. Support the phylogenetic types with their properties and methods
4. Enforce type constraints during model validation
5. Allow for type parameterization where specified
6. Apply the defined conversion rules correctly

## 7. Type Extensions

Languages may extend the type system with additional types, but must maintain compatibility with the core types defined here. Extensions should be clearly documented as non-standard.

## Reference

For machine-readable definitions, see [types.json](../../schema/types.json) in the schema directory.
