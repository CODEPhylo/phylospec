# Phylonco integration (work in progress)

PhyloSpec components for the single-cell models of
[beast-phylonco](https://github.com/bioDS/beast-phylonco).

Motivating model: `examples/CRC09.phylospec`, a translation of `CRC09.lphy`
(25 colorectal cancer cells, GT10 unphased genotypes, classic Bayesian skyline,
read-count error model with the genotypes integrated out).

## Status

| Part | Component library | BEAST 3 tile |
|------|-------------------|--------------|
| `fromMpileup`, `ReadCount` type | done (`phylonco`) | todo |
| `taxa` / `numTaxa` / `numSites` for `ReadCount` | done (`phylospec.functions.accessor`) | todo |
| `gt10` | done (`phylonco`) | todo |
| `ReadCountModel` | done (`phylonco`) | todo (`ReadCountTreeLikelihood` from beast-phylonco's `beast3` branch) |
| `RandomComposition`, Bayesian skyline overload of `SkylineCoalescent`, positive `ExponentialMarkovChain` | done (core) | done (`BeastCoreTileLibrary`, tested incl. a short MCMC run) |

`examples/CRC09.phylospec` passes the PhyloSpec type checker (core + this library)
with no errors or warnings.

## Design notes / open questions

- **ReadCountModel is one distribution on the tree.** The LPhy model has four statements
  (`PhyloCTMC` for the genotype alignment A, then `Ploidy`, `CoverageModel`, `ReadCountModel`).
  beast-phylonco's `beast3` branch evaluates all of it in one `ReadCountTreeLikelihood`:
  ploidy (alpha in {1, 2}, via allelic dropout) and coverage (negative binomial from t, v, s)
  are integrated out per cell and site, and the genotypes are integrated out by Felsenstein
  pruning under GT10. Nothing but the tree and parameters is sampled, so
  `ReadCountModel(tree, qMatrix, ...)` describes the same model and keeps the tile one-to-one.
- **Possible later change:** a form with an explicit genotype alignment
  (`Alignment A ~ PhyloCTMC(...)`, `ReadCountModel(genotypes=A, ...)`) reads closer to LPhy,
  but needs the tiling to match the two statements together. Not needed for CRC09.
- **Accessor overloads live in `phylospec.functions.accessor`.** `ComponentResolver.importNamespace`
  removes same-named generators from other namespaces, so putting `taxa(ReadCount)` in
  `phylonco` would shadow `taxa(Alignment)` after `use phylonco`.
- **Genotype character types** (`UnphasedGenotype`, ...) are not declared yet: `PhyloCTMC`
  returns `Alignment<Character>`, and the tile can infer the data type from `gt10`.
