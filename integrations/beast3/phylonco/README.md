# Phylonco integration (work in progress)

PhyloSpec components for the single-cell models of
[beast-phylonco](https://github.com/bioDS/beast-phylonco).

Motivating model: `examples/CRC09.phylospec`, a translation of `CRC09.lphy`
(25 colorectal cancer cells, GT10 unphased genotypes, classic Bayesian skyline,
read-count error model with the genotypes integrated out).

## Status

| Part | Component library | BEAST 3 tile |
|------|-------------------|--------------|
| `fromMpileup`, `ReadCount` type | done (`phylonco`) | done (`FromMpileupTile`, same counting rules as LPhy) |
| `taxa` / `numTaxa` / `numSites` for `ReadCount` | done (`phylospec.functions.accessor`) | done |
| `gt10` | done (`phylonco`) | done (`GT10Tile`) |
| `ReadCountModel` | done (`phylonco`) | done (`ReadCountTreeLikelihood`) |
| `RandomComposition`, Bayesian skyline overload of `SkylineCoalescent`, positive `ExponentialMarkovChain` | done (core) | done (`BeastCoreTileLibrary`) |

`examples/CRC09.phylospec` passes the type checker with no errors or warnings, and
`CRC09ModelTest` runs the same model structure as an MCMC on a small synthetic mpileup
(`src/test/resources/six-cells.mpileup`, 6 cells, 30 sites).

## Building locally

This module depends on beast-phylonco's `beast3` branch, which is not published yet:

```bash
# 1. beast-phylonco, beast3 branch
cd beast-phylonco && git checkout beast3 && mvn -pl phylonco-beast -am install -DskipTests
# 2. PhyloSpec with this module
cd phylospec && mvn -P phylonco -pl integrations/beast3/phylonco -am test
```

Running a model needs the library ids `phylonco` and `beast2`, e.g.
`new PhyloSpecRunner(source, List.of("phylonco", "beast2"))`.

## Mpileup reading

`fromMpileup` follows LPhy's `readMpileup` + `toReadCount` (no site filtering; CRC09 uses an
already filtered file). `.`/`,` count as the reference base, `^` plus its quality character,
`$`, indels (`+n`/`-n` and their bases), `*`, `<` and `>` are skipped, and A/C/G/T count in
either case. Lines with fewer than three columns per cell are skipped entirely (LPhy keeps their
position but drops their data, which shifts later sites).

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
- **GT10 rate keys.** phylonco's GT10 looks up the six rates by key (AC ... GT) on a
  `RealVectorParam`, so `GT10Tile` adds these keys to the `relativeRates` parameter.
- **Placeholder alignment for taxa.** Tree tiles take their taxa from an alignment, so
  `taxa(readCounts)` returns a one-site placeholder alignment with the cell names. It never
  enters a likelihood; `ReadCountTreeLikelihood` builds its own scaffold from the read counts.
