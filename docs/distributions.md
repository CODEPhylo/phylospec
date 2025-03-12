# PhyloSpec Distribution Signatures

This document defines the standard probability distributions in PhyloSpec. Each distribution is specified with its formal signature, parameters, return type, and constraints.

## 1. Statistical Distributions

### 1.1 Core Continuous Distributions

#### `Normal(mean: Real, sd: PositiveReal) -> Real`

Normal (Gaussian) distribution.

| Parameter | Type          | Description              | Default | Constraints |
|-----------|---------------|--------------------------|---------|-------------|
| `mean`    | `Real`        | Mean of the distribution | 0.0     | None        |
| `sd`      | `PositiveReal`| Standard deviation       | 1.0     | > 0         |

#### `LogNormal(meanlog: Real, sdlog: PositiveReal) -> PositiveReal`

Log-normal distribution for positive real values.

| Parameter | Type          | Description                  | Default | Constraints |
|-----------|---------------|------------------------------|---------|-------------|
| `meanlog` | `Real`        | Mean in log space            | 0.0     | None        |
| `sdlog`   | `PositiveReal`| Standard deviation in log space | 1.0  | > 0         |

#### `Gamma(shape: PositiveReal, rate: PositiveReal) -> PositiveReal`

Gamma distribution for positive real values.

| Parameter | Type          | Description        | Default | Constraints |
|-----------|---------------|--------------------|---------|-------------|
| `shape`   | `PositiveReal`| Shape parameter    | 1.0     | > 0         |
| `rate`    | `PositiveReal`| Rate parameter     | 1.0     | > 0         |

#### `Beta(alpha: PositiveReal, beta: PositiveReal) -> Probability`

Beta distribution for values in (0,1).

| Parameter | Type          | Description        | Default | Constraints |
|-----------|---------------|--------------------|---------|-------------|
| `alpha`   | `PositiveReal`| Alpha parameter    | 1.0     | > 0         |
| `beta`    | `PositiveReal`| Beta parameter     | 1.0     | > 0         |

#### `Exponential(rate: PositiveReal) -> PositiveReal`

Exponential distribution for rate parameters.

| Parameter | Type          | Description        | Default | Constraints |
|-----------|---------------|--------------------|---------|-------------|
| `rate`    | `PositiveReal`| Rate parameter     | 1.0     | > 0         |

#### `Uniform(lower: Real, upper: Real) -> Real`

Uniform distribution for bounded values.

| Parameter | Type          | Description        | Default | Constraints |
|-----------|---------------|--------------------|---------|-------------|
| `lower`   | `Real`        | Lower bound        | None    | < upper     |
| `upper`   | `Real`        | Upper bound        | None    | > lower     |

### 1.2 Multivariate Distributions

#### `Dirichlet(alpha: Vector<PositiveReal>) -> Simplex`

Dirichlet distribution for probability vectors.

| Parameter | Type                    | Description           | Default      | Constraints |
|-----------|-------------------------|-----------------------|--------------|-------------|
| `alpha`   | `Vector<PositiveReal>`  | Concentration parameters | [1.0,...,1.0] | All > 0 |

#### `MultivariateNormal(mean: Vector<Real>, covariance: Matrix<Real>) -> Vector<Real>`

Multivariate normal distribution for correlated values.

| Parameter    | Type             | Description           | Default | Constraints                 |
|--------------|-----------------|-----------------------|---------|----------------------------|
| `mean`       | `Vector<Real>`   | Mean vector           | None    | None                       |
| `covariance` | `Matrix<Real>`   | Covariance matrix     | None    | Symmetric positive definite |

### 1.3 Vector Variants

#### `NormalVector(mean: Real, sd: PositiveReal, dimension: PosInteger) -> Vector<Real>`

Vector of independently and identically distributed normal random variables.

| Parameter    | Type          | Description              | Default | Constraints |
|--------------|---------------|--------------------------|---------|-------------|
| `mean`       | `Real`        | Mean of each component   | 0.0     | None        |
| `sd`         | `PositiveReal`| Standard deviation       | 1.0     | > 0         |
| `dimension`  | `PosInteger`  | Vector dimension         | None    | > 0         |

#### `GammaVector(shape: PositiveReal, rate: PositiveReal, dimension: PosInteger) -> Vector<PositiveReal>`

Vector of independently and identically distributed gamma random variables.

| Parameter    | Type          | Description              | Default | Constraints |
|--------------|---------------|--------------------------|---------|-------------|
| `shape`      | `PositiveReal`| Shape parameter          | 1.0     | > 0         |
| `rate`       | `PositiveReal`| Rate parameter           | 1.0     | > 0         |
| `dimension`  | `PosInteger`  | Vector dimension         | None    | > 0         |

## 2. Tree Distributions

### 2.1 Tree Priors

#### `Yule(birthRate: PositiveReal) -> Tree`

Yule pure-birth process for trees.

| Parameter    | Type          | Description              | Default | Constraints |
|--------------|---------------|--------------------------|---------|-------------|
| `birthRate`  | `PositiveReal`| Birth rate parameter     | 1.0     | > 0         |

#### `BirthDeath(birthRate: PositiveReal, deathRate: PositiveReal, rootHeight: PositiveReal?) -> Tree`

Birth-death process for trees.

| Parameter    | Type            | Description              | Default | Constraints |
|--------------|-----------------|--------------------------|---------|-------------|
| `birthRate`  | `PositiveReal`  | Birth rate parameter     | None    | > deathRate |
| `deathRate`  | `PositiveReal`  | Death rate parameter     | None    | >= 0        |
| `rootHeight` | `PositiveReal?` | Height of the tree root  | None    | > 0         |

#### `Coalescent(populationSize: PositiveReal) -> Tree`

Coalescent process for population genetics.

| Parameter       | Type            | Description            | Default | Constraints |
|-----------------|-----------------|------------------------|---------|-------------|
| `populationSize`| `PositiveReal`  | Effective population size | 1.0  | > 0         |

#### `FossilBirthDeath(birthRate: PositiveReal, deathRate: PositiveReal, samplingRate: PositiveReal, rho: Probability) -> TimeTree`

Birth-death process with fossilization.

| Parameter      | Type            | Description                      | Default | Constraints |
|----------------|-----------------|----------------------------------|---------|-------------|
| `birthRate`    | `PositiveReal`  | Birth rate parameter             | None    | > 0         |
| `deathRate`    | `PositiveReal`  | Death rate parameter             | None    | >= 0        |
| `samplingRate` | `PositiveReal`  | Rate of fossil sampling          | None    | >= 0        |
| `rho`          | `Probability`   | Probability of sampling at present | None  | 0 <= rho <= 1 |

### 2.2 Constrained Tree Distributions

#### `ConstrainedYule(birthRate: PositiveReal, constraints: Vector<Constraint>) -> Tree`

Yule process with topological or temporal constraints.

| Parameter     | Type                  | Description              | Default | Constraints |
|---------------|----------------------|--------------------------|---------|-------------|
| `birthRate`   | `PositiveReal`       | Birth rate parameter     | 1.0     | > 0         |
| `constraints` | `Vector<Constraint>` | Tree constraints         | []      | None        |

## 3. Sequence Evolution Distributions

### 3.1 Phylogenetic Processes

#### `PhyloCTMC<A>(tree: Tree, Q: QMatrix, siteRates: Vector<PositiveReal>?, branchRates: Vector<PositiveReal>?) -> Alignment<A>`

Phylogenetic continuous-time Markov chain for sequence evolution.

| Parameter     | Type                    | Description                      | Default | Constraints |
|---------------|------------------------|----------------------------------|---------|-------------|
| `tree`        | `Tree`                 | Phylogenetic tree                | None    | None        |
| `Q`           | `QMatrix`              | Rate matrix                      | None    | Valid Q     |
| `siteRates`   | `Vector<PositiveReal>?`| Rate heterogeneity across sites  | None    | All > 0     |
| `branchRates` | `Vector<PositiveReal>?`| Rate heterogeneity across branches | None  | All > 0     |

Type parameter `A` specifies sequence alphabet (e.g., Nucleotide, AminoAcid).

#### `PhyloBM(tree: Tree, sigma: PositiveReal, rootValue: Real) -> Vector<Real>`

Phylogenetic Brownian motion for continuous trait evolution.

| Parameter    | Type          | Description              | Default | Constraints |
|--------------|---------------|--------------------------|---------|-------------|
| `tree`       | `Tree`        | Phylogenetic tree        | None    | None        |
| `sigma`      | `PositiveReal`| Rate parameter           | 1.0     | > 0         |
| `rootValue`  | `Real`        | Value at the root        | 0.0     | None        |

#### `PhyloOU(tree: Tree, sigma: PositiveReal, alpha: PositiveReal, optimum: Real) -> Vector<Real>`

Phylogenetic Ornstein-Uhlenbeck process for continuous trait evolution with selection.

| Parameter   | Type          | Description              | Default | Constraints |
|-------------|---------------|--------------------------|---------|-------------|
| `tree`      | `Tree`        | Phylogenetic tree        | None    | None        |
| `sigma`     | `PositiveReal`| Rate parameter           | 1.0     | > 0         |
| `alpha`     | `PositiveReal`| Selection strength       | 1.0     | > 0         |
| `optimum`   | `Real`        | Optimal trait value      | 0.0     | None        |

## 4. Mixture Distributions

#### `Mixture<T>(components: Vector<Distribution<T>>, weights: Simplex) -> T`

Mixture of distributions with the same return type.

| Parameter    | Type                    | Description                   | Default | Constraints |
|--------------|-------------------------|-------------------------------|---------|-------------|
| `components` | `Vector<Distribution<T>>`| Component distributions       | None    | Not empty   |
| `weights`    | `Simplex`               | Mixture weights               | None    | Sum to 1.0  |

#### `DiscreteGammaMixture(shape: PositiveReal, categories: PosInteger) -> Mixture<PositiveReal>`

Discretized gamma mixture for rate heterogeneity.

| Parameter    | Type          | Description              | Default | Constraints |
|--------------|---------------|--------------------------|---------|-------------|
| `shape`      | `PositiveReal`| Shape parameter          | 1.0     | > 0         |
| `categories` | `PosInteger`  | Number of categories     | 4       | > 0         |

## 5. Implementation Requirements

Language implementations must:

1. Support all required parameters with their appropriate types
2. Correctly handle optional parameters and default values
3. Enforce parameter constraints
4. Return values of the correct type
5. Support type parameterization where specified

### 5.1 Parameter Order

When a language uses positional parameters, they should follow the order listed in each signature.

### 5.2 Optional Parameters

Parameters marked with `?` or that have default values are optional. Implementations must handle their absence appropriately.

## Reference

For machine-readable definitions, see [distributions.json](../../schema/distributions.json) in the schema directory.
