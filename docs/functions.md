# PhyloSpec Function Signatures

This document defines the standard functions in PhyloSpec. Each function is specified with its formal signature, parameters, return type, and constraints.

## 1. Substitution Model Functions

These functions create rate matrices for different evolutionary models.

### 1.1 Nucleotide Models

#### `JC69() -> QMatrix`

Jukes-Cantor model (equal rates).

**Package:** `phylospec.functions.substitution`

*No parameters*

#### `K80(kappa: PositiveReal) -> QMatrix`

Kimura 2-parameter model.

**Package:** `phylospec.functions.substitution`

| Parameter | Type          | Description                  | Default | Required |
|-----------|---------------|------------------------------|---------|----------|
| `kappa`   | `PositiveReal`| Transition/transversion ratio| 2.0     | Yes      |

#### `F81(baseFrequencies: Simplex) -> QMatrix`

Felsenstein 81 model.

**Package:** `phylospec.functions.substitution`

| Parameter        | Type      | Description         | Default                  | Required | Dimension |
|------------------|-----------|---------------------|--------------------------|----------|-----------|
| `baseFrequencies`| `Simplex` | Nucleotide frequencies | [0.25, 0.25, 0.25, 0.25] | Yes    | 4         |

#### `HKY(kappa: PositiveReal, baseFrequencies: Simplex) -> QMatrix`

Hasegawa-Kishino-Yano model.

**Package:** `phylospec.functions.substitution`

| Parameter        | Type          | Description                  | Default                  | Required | Dimension |
|------------------|---------------|------------------------------|--------------------------|----------|-----------|
| `kappa`          | `PositiveReal`| Transition/transversion ratio| 2.0                      | Yes      | -         |
| `baseFrequencies`| `Simplex`     | Nucleotide frequencies       | [0.25, 0.25, 0.25, 0.25] | Yes      | 4         |

#### `GTR(rateMatrix: Vector<Real>, baseFrequencies: Simplex) -> QMatrix`

General Time-Reversible model.

**Package:** `phylospec.functions.substitution`

| Parameter        | Type                    | Description         | Default                         | Required | Dimension | Constraint |
|------------------|-------------------------|--------------------|---------------------------------|----------|-----------|------------|
| `rateMatrix`     | `Vector<Real>`          | Relative rate parameters | [1.0, 1.0, 1.0, 1.0, 1.0, 1.0] | Yes    | 6         | positive   |
| `baseFrequencies`| `Simplex`               | Nucleotide frequencies | [0.25, 0.25, 0.25, 0.25]     | Yes      | 4         | -          |

### 1.2 Protein Models

#### `WAG(baseFrequencies?: Simplex) -> QMatrix`

Whelan And Goldman model.

**Package:** `phylospec.functions.substitution`

| Parameter        | Type      | Description           | Default | Required | Dimension |
|------------------|-----------|----------------------|---------|----------|-----------|
| `baseFrequencies`| `Simplex` | Amino acid frequencies | None   | No       | 20        |

#### `JTT(baseFrequencies?: Simplex) -> QMatrix`

Jones-Taylor-Thornton model.

**Package:** `phylospec.functions.substitution`

| Parameter        | Type      | Description           | Default | Required | Dimension |
|------------------|-----------|----------------------|---------|----------|-----------|
| `baseFrequencies`| `Simplex` | Amino acid frequencies | None   | No       | 20        |

#### `LG(baseFrequencies?: Simplex) -> QMatrix`

Le-Gascuel model.

**Package:** `phylospec.functions.substitution`

| Parameter        | Type      | Description           | Default | Required | Dimension |
|------------------|-----------|----------------------|---------|----------|-----------|
| `baseFrequencies`| `Simplex` | Amino acid frequencies | None   | No       | 20        |

## 2. Rate Heterogeneity Functions

These functions create models for variation in evolutionary rates.

#### `DiscreteGamma(shape: PositiveReal, categories: PositiveInteger) -> Vector<Real>`

Discrete gamma-distributed rates across sites.

**Package:** `phylospec.functions.rateheterogeneity`

| Parameter    | Type             | Description                | Default | Required |
|--------------|------------------|----------------------------|---------|----------|
| `shape`      | `PositiveReal`   | Shape parameter            | 1.0     | Yes      |
| `categories` | `PositiveInteger`| Number of discrete categories | 4     | Yes      |

#### `StrictClock(rate: PositiveReal, tree?: Tree) -> Vector<Real>`

Strict molecular clock for branches.

**Package:** `phylospec.functions.clock`

| Parameter | Type           | Description              | Default | Required |
|-----------|----------------|--------------------------|---------|----------|
| `rate`    | `PositiveReal` | Clock rate               | 1.0     | Yes      |
| `tree`    | `Tree`         | Tree to apply clock to   | None    | No       |

#### `UncorrelatedLognormal(mean: Real, stdev: PositiveReal, tree?: Tree) -> Vector<Real>`

UCLN clock model for branch rates.

**Package:** `phylospec.functions.clock`

| Parameter | Type           | Description              | Default | Required |
|-----------|----------------|--------------------------|---------|----------|
| `mean`    | `Real`         | Mean in log space        | 0.0     | Yes      |
| `stdev`   | `PositiveReal` | Standard deviation       | 0.5     | Yes      |
| `tree`    | `Tree`         | Tree to apply clock to   | None    | No       |

## 3. I/O Functions

These functions handle input/output operations for phylogenetic data.

#### `nexus(file: String, id?: String) -> Alignment`

Load alignment from Nexus file.

**Package:** `phylospec.functions.io`

**File extensions:** `.nex`, `.nexus`, `.nxs`

| Parameter | Type     | Description                | Default | Required |
|-----------|----------|---------------------------|---------|----------|
| `file`    | `String` | Path to Nexus file        | None    | Yes      |
| `id`      | `String` | Identifier for the alignment | None  | No       |

#### `fasta(file: String) -> Alignment`

Load alignment from FASTA file.

**Package:** `phylospec.functions.io`

**File extensions:** `.fasta`, `.fas`, `.fa`

| Parameter | Type     | Description        | Default | Required |
|-----------|----------|--------------------|---------|----------|
| `file`    | `String` | Path to FASTA file | None    | Yes      |

#### `phylip(file: String, interleaved?: Boolean) -> Alignment`

Load alignment from PHYLIP file.

**Package:** `phylospec.functions.io`

**File extensions:** `.phy`, `.phylip`

| Parameter    | Type      | Description                           | Default | Required |
|--------------|-----------|---------------------------------------|---------|----------|
| `file`       | `String`  | Path to PHYLIP file                   | None    | Yes      |
| `interleaved`| `Boolean` | Whether the file is in interleaved format | false | No     |

## 4. Alignment Functions

These functions operate on sequence alignments.

#### `taxa(alignment: Alignment) -> TaxonSet`

Extract taxa from an alignment.

**Package:** `phylospec.functions.alignment`

| Parameter   | Type        | Description                    | Default | Required |
|-------------|-------------|--------------------------------|---------|----------|
| `alignment` | `Alignment` | Alignment to extract taxa from | None    | Yes      |

#### `ntaxa(alignment: Alignment) -> Integer`

Get number of taxa in an alignment.

**Package:** `phylospec.functions.alignment`

| Parameter   | Type        | Description                   | Default | Required |
|-------------|-------------|-------------------------------|---------|----------|
| `alignment` | `Alignment` | Alignment to count taxa from  | None    | Yes      |

#### `nchar(alignment: Alignment) -> Integer`

Get number of characters/sites in an alignment.

**Package:** `phylospec.functions.alignment`

| Parameter   | Type        | Description                        | Default | Required |
|-------------|-------------|------------------------------------|---------|----------|
| `alignment` | `Alignment` | Alignment to count characters from | None    | Yes      |

#### `dataType(alignment: Alignment) -> String`

Get the data type of an alignment.

**Package:** `phylospec.functions.alignment`

| Parameter   | Type        | Description          | Default | Required |
|-------------|-------------|----------------------|---------|----------|
| `alignment` | `Alignment` | Alignment to query   | None    | Yes      |

#### `subset(alignment: Alignment, sites: Vector<Integer>) -> Alignment`

Extract a subset of sites from an alignment.

**Package:** `phylospec.functions.alignment`

| Parameter   | Type              | Description            | Default | Required |
|-------------|-------------------|------------------------|---------|----------|
| `alignment` | `Alignment`       | Original alignment     | None    | Yes      |
| `sites`     | `Vector<Integer>` | Site indices to extract | None    | Yes      |

## 5. Taxa Functions

These functions work with taxonomic data.

#### `taxonset(names: Vector<String>) -> TaxonSet`

Create a taxon set from taxon names.

**Package:** `phylospec.functions.taxa`

| Parameter | Type            | Description        | Default | Required |
|-----------|-----------------|--------------------|---------|----------|
| `names`   | `Vector<String>`| Array of taxon names | None  | Yes      |

## 6. Tree Functions

These functions operate on phylogenetic trees.

#### `mrca(tree: Tree, taxa: TaxonSet) -> TreeNode`

Find most recent common ancestor of taxa.

**Package:** `phylospec.functions.tree`

| Parameter | Type       | Description       | Default | Required |
|-----------|------------|-------------------|---------|----------|
| `tree`    | `Tree`     | Phylogenetic tree | None    | Yes      |
| `taxa`    | `TaxonSet` | Set of taxa       | None    | Yes      |

#### `treeHeight(tree: Tree) -> Real`

Get height/depth of a tree.

**Package:** `phylospec.functions.tree`

| Parameter | Type   | Description       | Default | Required |
|-----------|--------|-------------------|---------|----------|
| `tree`    | `Tree` | Phylogenetic tree | None    | Yes      |

## 7. Mathematical Functions

These functions perform mathematical operations.

#### `log(x: PositiveReal) -> Real`

Natural logarithm.

**Package:** `phylospec.functions.math`

| Parameter | Type           | Description | Default | Required |
|-----------|----------------|-------------|---------|----------|
| `x`       | `PositiveReal` | Input value | None    | Yes      |

#### `exp(x: Real) -> PositiveReal`

Exponential function.

**Package:** `phylospec.functions.math`

| Parameter | Type   | Description | Default | Required |
|-----------|--------|-------------|---------|----------|
| `x`       | `Real` | Input value | None    | Yes      |

## 8. Implementation Requirements

Language implementations must:

1. Support all function signatures with their parameters
2. Enforce parameter constraints
3. Return the correct type
4. Handle generic types appropriately
5. Apply default values where specified
6. Support optional parameters (marked with `?` in the type)
7. Validate dimension constraints where specified
8. Implement proper I/O handling for file-based functions
9. Maintain package/namespace organization

### 8.1 Parameter Order

When a language uses positional parameters, they should follow the order listed in each signature.

### 8.2 Optional Parameters

Parameters with a `?` in their type or that have default values are optional. Implementations must handle their absence appropriately.

### 8.3 File I/O

Functions with `ioHints` are designed to load data from files. Implementations should:
- Validate file existence and readability
- Support the specified file extensions
- Handle file parsing errors gracefully
- Return appropriate error messages for malformed files

## Reference

For machine-readable definitions, see the `phylospec-model-library.json` file in the schema directory.