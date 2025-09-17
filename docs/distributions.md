# PhyloSpec Distribution Signatures

This document defines the standard probability distributions in PhyloSpec. Each distribution is specified with its formal signature, parameters, return type, and constraints.

## 1. Statistical Distributions

### 1.1 Core Continuous Distributions

#### `Normal(mean: Real, sd: PositiveReal) ~ Real`

Normal (Gaussian) distribution.

**Package:** `phylospec.distributions`

| Parameter | Type          | Description              | Default | Required |
|-----------|---------------|--------------------------|---------|----------|
| `mean`    | `Real`        | Mean of the distribution | 0.0     | Yes      |
| `sd`      | `PositiveReal`| Standard deviation       | 1.0     | Yes      |

#### `LogNormal(meanlog: Real, sdlog: PositiveReal) ~ PositiveReal`

Log-normal distribution for positive real values.

**Package:** `phylospec.distributions`

| Parameter | Type          | Description                  | Default | Required |
|-----------|---------------|------------------------------|---------|----------|
| `meanlog` | `Real`        | Mean of the distribution in log space | 0.0 | Yes |
| `sdlog`   | `PositiveReal`| Standard deviation in log space | 1.0  | Yes      |

#### `Gamma(shape: PositiveReal, rate: PositiveReal) ~ PositiveReal`

Gamma distribution for positive real values.

**Package:** `phylospec.distributions`

| Parameter | Type          | Description        | Default | Required |
|-----------|---------------|--------------------|---------|----------|
| `shape`   | `PositiveReal`| Shape parameter    | 1.0     | Yes      |
| `rate`    | `PositiveReal`| Rate parameter     | 1.0     | Yes      |

#### `Beta(alpha: PositiveReal, beta: PositiveReal) ~ Probability`

Beta distribution for values in (0,1).

**Package:** `phylospec.distributions`

| Parameter | Type          | Description        | Default | Required |
|-----------|---------------|--------------------|---------|----------|
| `alpha`   | `PositiveReal`| Alpha parameter    | 1.0     | Yes      |
| `beta`    | `PositiveReal`| Beta parameter     | 1.0     | Yes      |

#### `Exponential(rate: PositiveReal) ~ PositiveReal`

Exponential distribution for rate parameters.

**Package:** `phylospec.distributions`

| Parameter | Type          | Description        | Default | Required |
|-----------|---------------|--------------------|---------|----------|
| `rate`    | `PositiveReal`| Rate parameter     | 1.0     | Yes      |

#### `Uniform(lower: Real, upper: Real) ~ Real`

Uniform distribution for bounded values.

**Package:** `phylospec.distributions`

| Parameter | Type          | Description        | Default | Required |
|-----------|---------------|--------------------|---------|----------|
| `lower`   | `Real`        | Lower bound        | None    | Yes      |
| `upper`   | `Real`        | Upper bound        | None    | Yes      |

### 1.2 Multivariate Distributions

#### `Dirichlet(alpha: Vector<Real>) ~ Simplex`

Dirichlet distribution for probability vectors.

**Package:** `phylospec.distributions`

| Parameter | Type             | Description              | Default            | Required | Constraint | Dimension |
|-----------|------------------|--------------------------|--------------------|----------|------------|-----------|
| `alpha`   | `Vector<Real>`   | Concentration parameters | [1.0, 1.0, 1.0, 1.0] | Yes    | positive   | target.dimension |

**Indexing**: The resulting Simplex can be indexed to access individual probabilities:
```
Simplex pi ~ Dirichlet([1.0, 1.0, 1.0, 1.0]);
Probability p = pi[0];  // Access first probability
```

#### `MultivariateNormal(mean: Vector<Real>, covariance: Matrix<Real>) ~ Vector<Real>`

Multivariate normal for correlated values.

**Package:** `phylospec.distributions`

| Parameter    | Type             | Description       | Default | Required | Dimension |
|--------------|------------------|-------------------|---------|----------|-----------|
| `mean`       | `Vector<Real>`   | Mean vector       | None    | Yes      | target.dimension |
| `covariance` | `Matrix<Real>`   | Covariance matrix | None    | Yes      | target.dimension |

## 2. Tree Distributions

### 2.1 Tree Priors

#### `Yule(birthRate: PositiveReal, taxa?: TaxonSet) ~ Tree`

Yule pure-birth process for trees.

**Package:** `phylospec.distributions.tree`

| Parameter    | Type          | Description              | Default | Required |
|--------------|---------------|--------------------------|---------|----------|
| `birthRate`  | `PositiveReal`| Birth rate parameter     | 1.0     | Yes      |
| `taxa`       | `TaxonSet`    | Taxa for the tree        | None    | No       |

#### `BirthDeath(birthRate: PositiveReal, deathRate: PositiveReal, rootHeight?: PositiveReal, taxa?: TaxonSet) ~ Tree`

Birth-death process for trees.

**Package:** `phylospec.distributions.tree`

| Parameter    | Type               | Description              | Default | Required |
|--------------|--------------------|--------------------------|---------|----------|
| `birthRate`  | `PositiveReal`     | Birth rate parameter     | None    | Yes      |
| `deathRate`  | `PositiveReal`     | Death rate parameter     | None    | Yes      |
| `rootHeight` | `PositiveReal`     | Height of the tree root  | None    | No       |
| `taxa`       | `TaxonSet`         | Taxa for the tree        | None    | No       |

#### `Coalescent(populationSize: PositiveReal, taxa?: TaxonSet) ~ Tree`

Coalescent process for population genetics.

**Package:** `phylospec.distributions.tree`

| Parameter       | Type            | Description                   | Default | Required |
|-----------------|-----------------|-------------------------------|---------|----------|
| `populationSize`| `PositiveReal`  | Effective population size     | 1.0     | Yes      |
| `taxa`          | `TaxonSet`      | Taxa for the tree             | None    | No       |

#### `FossilBirthDeath(birthRate: PositiveReal, deathRate: NonNegativeReal, samplingRate: NonNegativeReal, rho?: Probability, origin?: PositiveReal, taxa?: TaxonSet) ~ TimeTree`

Birth-death process with fossil sampling.

**Package:** `phylospec.distributions.tree`

| Parameter      | Type               | Description                           | Default | Required |
|----------------|--------------------|---------------------------------------|---------|----------|
| `birthRate`    | `PositiveReal`     | Birth (speciation) rate               | None    | Yes      |
| `deathRate`    | `NonNegativeReal`  | Death (extinction) rate               | None    | Yes      |
| `samplingRate` | `NonNegativeReal`  | Rate of fossil sampling               | None    | Yes      |
| `rho`          | `Probability`      | Probability of sampling at present    | 1.0     | No       |
| `origin`       | `PositiveReal`     | Time of origin                        | None    | No       |
| `taxa`         | `TaxonSet`         | Taxa for the tree (including fossils) | None    | No       |

## 3. Sequence Evolution Distributions

### 3.1 Phylogenetic Processes

#### `PhyloCTMC(tree: Tree, Q: QMatrix, siteRates?: Vector<Real>, branchRates?: Vector<Real>) ~ Alignment`

Phylogenetic continuous-time Markov chain.

**Package:** `phylospec.distributions.sequence`

| Parameter     | Type                    | Description                         | Default | Required | Constraint | Dimension |
|---------------|-------------------------|-------------------------------------|---------|----------|------------|-----------|
| `tree`        | `Tree`                  | Phylogenetic tree                   | None    | Yes      | -          | -         |
| `Q`           | `QMatrix`               | Rate matrix                         | None    | Yes      | -          | -         |
| `siteRates`   | `Vector<Real>`          | Rate heterogeneity across sites     | None    | No       | positive   | target.nchar |
| `branchRates` | `Vector<Real>`          | Rate heterogeneity across branches  | None    | No       | positive   | 2 * target.ntax - 2 |

#### `PhyloBM(tree: Tree, sigma: PositiveReal, rootValue: Real) ~ Vector<Real>`

Phylogenetic Brownian motion for the continuous evolution of a single trait.

**Package:** `phylospec.distributions.continuous`

| Parameter    | Type          | Description              | Default | Required |
|--------------|---------------|--------------------------|---------|----------|
| `tree`       | `Tree`        | Phylogenetic tree        | None    | Yes      |
| `sigma`      | `PositiveReal`| Rate of evolution        | 1.0     | Yes      |
| `rootValue`  | `Real`        | Trait value at the root  | 0.0     | Yes      |

#### `PhyloOU(tree: Tree, sigma: PositiveReal, alpha: PositiveReal, optimum: Real) ~ Vector<Real>`

Phylogenetic Ornstein-Uhlenbeck process for a univariate trait with selection.

**Package:** `phylospec.distributions.continuous`

| Parameter   | Type          | Description              | Default | Required |
|-------------|---------------|--------------------------|---------|----------|
| `tree`      | `Tree`        | Phylogenetic tree        | None    | Yes      |
| `sigma`     | `PositiveReal`| Rate of random evolution | 1.0     | Yes      |
| `alpha`     | `PositiveReal`| Selection strength       | 1.0     | Yes      |
| `optimum`   | `Real`        | Optimal trait value      | 0.0     | Yes      |

## 4. Special Distributions

#### `IID<T>(base: Distribution<T>, n: PositiveInteger) ~ Vector<T>`

Vector of independent and identically distributed random variables.

**Package:** `phylospec.distributions`

**Type Parameters:** `T` - The type of elements generated

**Primary Argument:** `x: Vector<T>` - Vector of IID random variables

| Parameter | Type                | Description                      | Default | Required |
|-----------|---------------------|----------------------------------|---------|----------|
| `base`    | `Distribution<T>`   | Base distribution for each component | None | Yes      |
| `n`       | `PositiveInteger`   | Number of independent draws      | None    | Yes      |

Example usage:
```
Vector<Real> x ~ IID(Normal(0, 1), 10);  // 10 independent standard normals
Vector<PositiveReal> rates ~ IID(Gamma(2, 2), 100);  // 100 independent gamma values
```

#### `Mixture<T>(components: List<Distribution<T>>, weights: Simplex) ~ T`

Mixture of distributions with the same return type.

**Package:** `phylospec.distributions.mixture`

**Type Parameters:** `T` - The type generated by all component distributions

| Parameter    | Type                        | Description                                  | Default | Required | Dimension |
|--------------|----------------------------|----------------------------------------------|---------|----------|-----------|
| `components` | `List<Distribution<T>>`    | Component distributions that all generate type T | None | Yes      | -         |
| `weights`    | `Simplex`                  | Mixture weights for each component           | None    | Yes      | components.length |

Example usage:
```
// Mixture of two normal distributions
Real x ~ Mixture([Normal(-2, 1), Normal(2, 1)], [0.3, 0.7]);

// Mixture of three exponential distributions
PositiveReal rate ~ Mixture([Exponential(1), Exponential(5), Exponential(10)], [0.2, 0.5, 0.3]);
```

## 5. Distribution Object Constructors

Each distribution also has a corresponding constructor function that creates a distribution object rather than sampling a value. These constructors have the same names and parameters as their sampling counterparts but return distribution objects:

- `Normal(mean: Real, sd: PositiveReal) -> Normal` - Returns a Normal distribution object
- `Gamma(shape: PositiveReal, rate: PositiveReal) -> Gamma` - Returns a Gamma distribution object
- `Coalescent(populationSize: PositiveReal, taxa?: TaxonSet) -> Coalescent` - Returns a Coalescent distribution object

These distribution objects can be used as arguments to other distributions or functions, such as:
```
Distribution<Real> prior = Normal(0, 1);
Vector<Real> x ~ IID(prior, 10);
```

## 6. Special Cases and Usage Notes

### 6.1 Indexing Distribution Results

When a distribution returns an indexable type, elements can be accessed directly:

```
// Simplex from Dirichlet
Simplex pi ~ Dirichlet([1.0, 1.0, 1.0, 1.0]);
Probability freqA = pi[0];  // First element

// Vector from MultivariateNormal
Vector<Real> x ~ MultivariateNormal(mu, Sigma);
Real x1 = x[0];  // First component

// Vector from PhyloBM
Vector<Real> traits ~ PhyloBM(tree, sigma, rootValue);
Real humanTrait = traits[0];  // Trait value for first taxon
```

### 6.2 Dimension Expressions

Some distributions use dimension expressions to ensure compatibility:

- `target.dimension` - Refers to the dimension of the variable being sampled
- `target.nchar` - For alignments, the number of characters/sites
- `target.ntax` - For alignments or trees, the number of taxa
- `2 * target.ntax - 2` - Number of branches in a rooted tree
- `components.length` - Length of the components list (for mixtures)

### 6.3 Primary Arguments

Some distributions define a "primary argument" which represents the random variable being modeled. This is mainly for documentation purposes and does not affect usage. For example, the `IID` distribution has `x: Vector<T>` as its primary argument, indicating that it models a vector of IID random variables.

## 7. Implementation Requirements

Language implementations must:

1. Support all required parameters with their appropriate types
2. Correctly handle optional parameters and default values
3. Enforce parameter constraints
4. Return values of the correct type
5. Support type parameterization for generic distributions (`IID<T>`, `Mixture<T>`)
6. Evaluate dimension expressions correctly
7. Allow indexing of returned collection types where appropriate
8. Implement both sampling (`~`) and constructor forms
9. Maintain proper package/namespace organization

### 7.1 Parameter Order

When a language uses positional parameters, they should follow the order listed in each signature.

### 7.2 Optional Parameters

Parameters marked with `?` or that have default values are optional. Implementations must handle their absence appropriately.

### 7.3 Type Safety

Generic distributions must ensure type consistency. For example, all components in a `Mixture<T>` must generate the same type `T`.

## Reference

For machine-readable definitions, see the `phylospec-model-library.json` file in the schema directory.