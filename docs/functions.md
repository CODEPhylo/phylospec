# PhyloSpec Function Signatures

This document defines the standard functions in PhyloSpec. Each function is specified with its formal signature, parameters, return type, and constraints.

## 1. Substitution Model Functions

These functions create rate matrices for different evolutionary models.

### 1.1 Nucleotide Models

#### `JC69() -> QMatrix`

Jukes-Cantor model with equal rates between all nucleotides.

*No parameters*

#### `K80(kappa: PositiveReal) -> QMatrix`

Kimura 2-parameter model with different rates for transitions and transversions.

| Parameter | Type          | Description                  | Default | Constraints |
|-----------|---------------|------------------------------|---------|-------------|
| `kappa`   | `PositiveReal`| Transition/transversion ratio| 2.0     | > 0         |

#### `F81(baseFrequencies: Simplex) -> QMatrix`

Felsenstein 81 model with unequal base frequencies.

| Parameter        | Type      | Description         | Default                  | Constraints |
|------------------|-----------|---------------------|--------------------------|-------------|
| `baseFrequencies`| `Simplex` | Nucleotide frequencies | [0.25, 0.25, 0.25, 0.25] | Sum to 1.0 |

#### `HKY(kappa: PositiveReal, baseFrequencies: Simplex) -> QMatrix`

Hasegawa-Kishino-Yano model with transition/transversion bias and unequal base frequencies.

| Parameter        | Type          | Description                  | Default                  | Constraints |
|------------------|---------------|------------------------------|--------------------------|-------------|
| `kappa`          | `PositiveReal`| Transition/transversion ratio| 2.0                      | > 0         |
| `baseFrequencies`| `Simplex`     | Nucleotide frequencies       | [0.25, 0.25, 0.25, 0.25] | Sum to 1.0  |

#### `GTR(rateMatrix: Vector<PositiveReal>, baseFrequencies: Simplex) -> QMatrix`

General Time-Reversible model with a symmetric rate matrix and unequal base frequencies.

| Parameter        | Type                    | Description         | Default                  | Constraints      |
|------------------|-------------------------|---------------------|--------------------------|------------------|
| `rateMatrix`     | `Vector<PositiveReal>`  | Relative rates      | [1.0, 1.0, 1.0, 1.0, 1.0, 1.0] | Length = 6   |
| `baseFrequencies`| `Simplex`               | Nucleotide frequencies | [0.25, 0.25, 0.25, 0.25] | Sum to 1.0   |

### 1.2 Protein Models

#### `WAG(freqsModel: Boolean?) -> QMatrix`

Whelan And Goldman model for protein evolution.

| Parameter    | Type         | Description                       | Default | Constraints |
|--------------|--------------|-----------------------------------|---------|-------------|
| `freqsModel` | `Boolean?`   | Use frequencies from the model    | true    | None        |

#### `JTT(freqsModel: Boolean?) -> QMatrix`

Jones-Taylor-Thornton model for protein evolution.

| Parameter    | Type         | Description                       | Default | Constraints |
|--------------|--------------|-----------------------------------|---------|-------------|
| `freqsModel` | `Boolean?`   | Use frequencies from the model    | true    | None        |

#### `LG(freqsModel: Boolean?) -> QMatrix`

Le-Gascuel model for protein evolution.

| Parameter    | Type         | Description                       | Default | Constraints |
|--------------|--------------|-----------------------------------|---------|-------------|
| `freqsModel` | `Boolean?`   | Use frequencies from the model    | true    | None        |

### 1.3 Codon Models

#### `GY94(omega: PositiveReal, kappa: PositiveReal, codonFrequencies: Simplex) -> QMatrix`

Goldman-Yang 1994 model for codon evolution.

| Parameter        | Type            | Description                  | Default | Constraints        |
|------------------|-----------------|------------------------------|---------|-------------------|
| `omega`          | `PositiveReal`  | dN/dS ratio                  | 1.0     | > 0               |
| `kappa`          | `PositiveReal`  | Transition/transversion ratio| 2.0     | > 0               |
| `codonFrequencies`| `Simplex`      | Codon frequencies           | None    | Length = # codons  |

## 2. Rate Heterogeneity Functions

These functions create models for variation in evolutionary rates.

#### `DiscreteGamma(shape: PositiveReal, categories: PositiveInteger) -> Vector<PositiveReal>`

Discrete gamma-distributed rates across sites.

| Parameter    | Type             | Description                | Default | Constraints |
|--------------|------------------|----------------------------|---------|-------------|
| `shape`      | `PositiveReal`   | Shape parameter            | 1.0     | > 0         |
| `categories` | `PositiveInteger`| Number of discrete categories | 4     | > 0         |

#### `FreeRates(rates: Vector<PositiveReal>, weights: Simplex) -> Vector<PositiveReal>`

Freely varying rate categories.

| Parameter | Type                    | Description        | Default | Constraints                |
|-----------|-------------------------|--------------------|---------|---------------------------|
| `rates`   | `Vector<PositiveReal>`  | Rate values        | None    | All > 0                   |
| `weights` | `Simplex`               | Weights for each rate | None | Length matches rates     |

#### `InvariantSites(proportion: Probability) -> Vector<Real>`

Model with a proportion of invariant sites.

| Parameter    | Type          | Description                | Default | Constraints           |
|--------------|---------------|----------------------------|---------|----------------------|
| `proportion` | `Probability` | Proportion of invariant sites | 0.0  | [0, 1]               |

#### `StrictClock(rate: PositiveReal) -> Vector<PositiveReal>`

Strict molecular clock for branches.

| Parameter | Type           | Description    | Default | Constraints |
|-----------|----------------|----------------|---------|-------------|
| `rate`    | `PositiveReal` | Clock rate     | 1.0     | > 0         |

#### `UncorrelatedLognormal(mean: Real, stdev: PositiveReal) -> Vector<PositiveReal>`

Uncorrelated lognormal relaxed clock model for branch rates.

| Parameter | Type           | Description                | Default | Constraints |
|-----------|----------------|----------------------------|---------|-------------|
| `mean`    | `Real`         | Mean in log space          | 0.0     | None        |
| `stdev`   | `PositiveReal` | Standard deviation         | 0.5     | > 0         |

#### `UncorrelatedExponential(mean: PositiveReal) -> Vector<PositiveReal>`

Uncorrelated exponential relaxed clock model for branch rates.

| Parameter | Type           | Description    | Default | Constraints |
|-----------|----------------|----------------|---------|-------------|
| `mean`    | `PositiveReal` | Mean rate      | 1.0     | > 0         |

## 3. Tree Functions

These functions operate on phylogenetic trees.

#### `mrca(tree: Tree, taxa: TaxonSet) -> TreeNode`

Find the most recent common ancestor of a set of taxa.

| Parameter | Type       | Description        | Default | Constraints                  |
|-----------|------------|--------------------|---------|------------------------------|
| `tree`    | `Tree`     | Phylogenetic tree  | None    | None                         |
| `taxa`    | `TaxonSet` | Set of taxa        | None    | All taxa must be in the tree |

#### `treeHeight(tree: Tree) -> Real`

Get the height/depth of a tree (distance from root to furthest tip).

| Parameter | Type   | Description       | Default | Constraints |
|-----------|--------|-------------------|---------|-------------|
| `tree`    | `Tree` | Phylogenetic tree | None    | None        |

#### `nodeAge(tree: TimeTree, node: TreeNode) -> Real`

Get the age of a node in a time-calibrated tree.

| Parameter | Type       | Description          | Default | Constraints                |
|-----------|------------|----------------------|---------|---------------------------|
| `tree`    | `TimeTree` | Time-calibrated tree | None    | None                      |
| `node`    | `TreeNode` | Node in the tree     | None    | Node must be in the tree  |

#### `branchLength(tree: Tree, node: TreeNode) -> Real`

Get the length of the branch leading to a node.

| Parameter | Type       | Description      | Default | Constraints                    |
|-----------|------------|------------------|---------|-------------------------------|
| `tree`    | `Tree`     | Phylogenetic tree| None    | None                          |
| `node`    | `TreeNode` | Node in the tree | None    | Node must be in tree, not root|

#### `distanceMatrix(tree: Tree) -> Matrix<Real>`

Compute pairwise distances between all pairs of tips in the tree.

| Parameter | Type   | Description       | Default | Constraints |
|-----------|--------|-------------------|---------|-------------|
| `tree`    | `Tree` | Phylogenetic tree | None    | None        |

**Indexing**: The result can be indexed with two indices to get pairwise distances:
```
Matrix<Real> D = distanceMatrix(tree);
Real dist = D[i,j];  // Distance between tips i and j
```

#### `descendantTaxa(tree: Tree, node: TreeNode) -> TaxonSet`

Get all taxa descended from a node.

| Parameter | Type       | Description      | Default | Constraints                |
|-----------|------------|------------------|---------|---------------------------|
| `tree`    | `Tree`     | Phylogenetic tree| None    | None                      |
| `node`    | `TreeNode` | Node in the tree | None    | Node must be in the tree  |

## 4. Mathematical Functions

These functions perform mathematical operations on various types.

#### `vectorElement(vector: Vector<T>, index: Integer) -> T`

Extract an element from a vector.

| Parameter | Type        | Description     | Default | Constraints           |
|-----------|-------------|-----------------|---------|----------------------|
| `vector`  | `Vector<T>` | Source vector   | None    | None                 |
| `index`   | `Integer`   | Index to extract| None    | 0 <= index < length  |

**Note**: This function is equivalent to direct indexing (`vector[index]`).

#### `matrixElement(matrix: Matrix<T>, row: Integer, col: Integer) -> T`

Extract an element from a matrix.

| Parameter | Type        | Description      | Default | Constraints              |
|-----------|-------------|------------------|---------|-------------------------|
| `matrix`  | `Matrix<T>` | Source matrix    | None    | None                    |
| `row`     | `Integer`   | Row index        | None    | 0 <= row < rows         |
| `col`     | `Integer`   | Column index     | None    | 0 <= col < columns      |

**Note**: This function is equivalent to direct indexing (`matrix[row,col]`).

#### `scale(vector: Vector<Real>, factor: Real) -> Vector<Real>`

Scale all elements of a vector by a factor.

| Parameter | Type           | Description    | Default | Constraints |
|-----------|----------------|----------------|---------|-------------|
| `vector`  | `Vector<Real>` | Source vector  | None    | None        |
| `factor`  | `Real`         | Scaling factor | None    | None        |

#### `normalize(vector: Vector<Real>) -> Simplex`

Normalize a vector to sum to 1.0, creating a simplex.

| Parameter | Type           | Description   | Default | Constraints   |
|-----------|----------------|---------------|---------|--------------|
| `vector`  | `Vector<Real>` | Source vector | None    | All values ≥ 0 |

**Indexing**: The resulting Simplex can be indexed to access individual probabilities:
```
Simplex s = normalize([1.0, 2.0, 3.0]);
Probability p = s[0];  // Access first probability
```

#### `log(x: PositiveReal) -> Real`

Natural logarithm.

| Parameter | Type           | Description | Default | Constraints |
|-----------|----------------|-------------|---------|-------------|
| `x`       | `PositiveReal` | Input value | None    | > 0         |

#### `exp(x: Real) -> PositiveReal`

Exponential function.

| Parameter | Type   | Description | Default | Constraints |
|-----------|--------|-------------|---------|-------------|
| `x`       | `Real` | Input value | None    | None        |

#### `sum(vector: Vector<Real>) -> Real`

Sum all elements in a vector.

| Parameter | Type           | Description   | Default | Constraints |
|-----------|----------------|---------------|---------|-------------|
| `vector`  | `Vector<Real>` | Source vector | None    | None        |

#### `product(vector: Vector<Real>) -> Real`

Multiply all elements in a vector.

| Parameter | Type           | Description   | Default | Constraints |
|-----------|----------------|---------------|---------|-------------|
| `vector`  | `Vector<Real>` | Source vector | None    | None        |

## 5. Implementation Requirements

Language implementations must:

1. Support all function signatures with their parameters
2. Enforce parameter constraints
3. Return the correct type
4. Handle generic types appropriately
5. Apply default values where specified

### 5.1 Parameter Order

When a language uses positional parameters, they should follow the order listed in each signature.

### 5.2 Optional Parameters

Parameters that have default values are optional. Implementations must handle their absence appropriately.

### 5.3 Indexing

Functions that return indexable types (Vector, Matrix, Simplex) produce results that can be indexed according to the type system rules. See the [type system documentation](types.md) for details on indexing behavior.

## Reference

For machine-readable definitions, see [functions.json](../../schema/functions.json) in the schema directory.