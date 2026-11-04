# Phylonco integration (work in progress)

PhyloSpec components for the single-cell models of
[beast-phylonco](https://github.com/bioDS/beast-phylonco).

Motivating model: `examples/CRC09.phylospec`, a translation of `CRC09.lphy`
(25 colorectal cancer cells, GT10 unphased genotypes, classic Bayesian skyline,
latent genotype alignment, read-count error model).

## Status

| Part | Component library | BEAST 3 tile |
|------|-------------------|--------------|
| `fromMpileup`, `ReadCount` type | done (`phylonco`) | todo |
| `taxa` / `numTaxa` / `numSites` for `ReadCount` | done (`phylospec.functions.accessor`) | todo |
| `gt10` | done (`phylonco`) | todo |
| `ReadCountModel` | done (`phylonco`) | todo (`LikelihoodReadCountModel`, MATreeLikelihood, Gibbs operators) |
| `RandomComposition`, Bayesian skyline overload of `SkylineCoalescent`, `PhyloCTMC(numSites=...)`, positive `ExponentialMarkovChain` | done (core) | todo |

`examples/CRC09.phylospec` passes the PhyloSpec type checker (core + this library)
with no errors or warnings.

## Design notes / open questions

- **ReadCountModel is one distribution.** The LPhy model has three statements
  (`Ploidy`, `CoverageModel`, `ReadCountModel`), but BEAST's `LikelihoodReadCountModel`
  integrates out ploidy (alpha in {1, 2}, via allelic dropout) and evaluates coverage
  as a negative binomial from t, v and s. Neither alpha nor the coverage matrix is a
  sampled state, so a single PhyloSpec distribution matches what the engine computes
  and keeps the tile one-to-one.
- **Accessor overloads live in `phylospec.functions.accessor`.** `ComponentResolver.importNamespace`
  removes same-named generators from other namespaces, so putting `taxa(ReadCount)` in
  `phylonco` would shadow `taxa(Alignment)` after `use phylonco`.
- **Genotype character types** (`UnphasedGenotype`, ...) are not declared yet: `PhyloCTMC`
  returns `Alignment<Character>`, and the tile can infer the data type from `gt10`.
