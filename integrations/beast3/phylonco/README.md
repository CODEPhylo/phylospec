# Phylonco integration (work in progress)

PhyloSpec components for the single-cell models of
[beast-phylonco](https://github.com/bioDS/beast-phylonco).

Motivating model: CRC09, a translation of `CRC09.lphy` (25 colorectal cancer cells, GT10
unphased genotypes, classic Bayesian skyline, read-count error model), in two forms:

- `examples/CRC09.phylospec`: the observation model as one `ReadCountModel(tree, qMatrix, ...)`
  statement, with genotypes, ploidy and coverage already integrated out.
- `examples/CRC09-explicit.phylospec`: every generative step as in `CRC09.lphy`. The latent
  genotypes, ploidy and coverage are separate draws, and the BEAST 3 engine integrates them out.

## Status

| Part | Component library | BEAST 3 tile |
|------|-------------------|--------------|
| `fromMpileup`, `ReadCount` type | done (`phylonco`) | done (`FromMpileupTile`, same counting rules as LPhy) |
| `taxa` / `numTaxa` / `numSites` for `ReadCount` | done (`phylospec.functions.accessor`) | done |
| `gt10` | done (`phylonco`) | done (`GT10Tile`) |
| `ReadCountModel(tree, qMatrix, ...)` (compact) | done (`phylonco`) | done (`ReadCountModelTile` -> `ReadCountTreeLikelihood`) |
| Explicit form: latent `PhyloCTMC(numSites)`, `Ploidy`, `CoverageModel`, `ReadCountModel(genotypes, ploidy, coverage, ...)` | done (core and `phylonco`) | done (`LatentReadCountTiles`, `GenotypeReadCountModelTile` -> the same `ReadCountTreeLikelihood`) |
| `RandomComposition`, Bayesian skyline overload of `SkylineCoalescent`, positive `ExponentialMarkovChain` | done (core) | done (`BeastCoreTileLibrary`) |

Both examples pass `phylospec check` (tested in `CRC09ModelTest`), and
`CRC09ModelTest` runs the same model structure, in both forms, as an MCMC on a small synthetic
mpileup (`src/test/resources/six-cells.mpileup`, 6 cells, 30 sites).

## Explicit and compact forms

`CRC09-explicit.phylospec` writes every generative step, with the same components as `CRC09.lphy`:

| LPhy (`CRC09.lphy`) | PhyloSpec (`examples/CRC09-explicit.phylospec`) |
| --- | --- |
| `A ~ PhyloCTMC(L=l, Q=Q, dataType=unphasedGenotype(), tree=psi)` | `genotypes ~ PhyloCTMC(tree=tree, qMatrix=qMatrix, numSites=numSites)` |
| `alpha ~ Ploidy(n=n, l=l, delta=delta)` | `ploidy ~ Ploidy(numCells=numCells, numSites=numSites, allelicDropout=delta)` |
| `cov ~ CoverageModel(s=s, t=t, alpha=alpha, v=v)` | `coverage ~ CoverageModel(ploidy=ploidy, meanAllelicCoverage=t, allelicCoverageVariance=v, cellSizeFactors=s)` |
| `r ~ ReadCountModel(epsilon=epsilon, w1=w1, w2=w2, D=A, coverage=cov, alpha=alpha)` | `reads ~ ReadCountModel(genotypes=genotypes, ploidy=ploidy, coverage=coverage, sequencingError=epsilon, homozygousOverdispersion=w1, heterozygousOverdispersion=w2)` |

The data type follows from the GT10 `qMatrix`, so `PhyloCTMC` needs no `dataType`.

The engine decides how to evaluate this. `genotypes`, `ploidy` and `coverage` are not sampled:
the latent draws are matched by `LatentReadCountTiles`, and `GenotypeReadCountModelTile`
builds phylonco's `ReadCountTreeLikelihood`, which integrates the genotypes out by pruning and
ploidy and coverage out per cell and site, as LPhyBEAST does. A latent variable used anywhere
else cannot be integrated out and is rejected, as is a coverage drawn with a different ploidy
than the one passed to `ReadCountModel`.

The compact `ReadCountModel(tree, qMatrix, sequencingError, allelicDropout, ...)`
(`examples/CRC09.phylospec`) states the integrated model in one statement and builds the same
likelihood. `CRC09ModelTest` checks that
both forms sample the same parameters and give the same fixed-state likelihood.

## Building locally

This module depends on beast-phylonco's `beast3` branch, which is not published yet. Build the
commit the tests and the LPhyBEAST reference were made with:

```bash
# 1. beast-phylonco at the pinned beast3 commit
git clone https://github.com/bioDS/beast-phylonco.git
cd beast-phylonco && git checkout --detach 7a7e51d44a51c00e7e81297a97a8bd8550ea3b9d
mvn -pl phylonco-beast -am install -DskipTests
# 2. PhyloSpec with this module
cd phylospec && mvn -P phylonco -pl integrations/beast3/phylonco -am test
```

The module is behind the `phylonco` Maven profile, and the pull-request CI builds only `core/java`
and `integrations/beast3/java`. Its tests, including the LPhyBEAST comparison, therefore run only
in a local build with the steps above.

From Java, running a model needs the library ids `phylonco` and `beast2`, e.g.
`new PhyloSpecRunner(source, List.of("phylonco", "beast2"))`.

## Running CRC09 with the CLI

**Data.** The CRC09 read counts are not public and are not distributed with PhyloSpec. Obtain
`CRC09_filtered.mpileup` from the data owners and place it at `examples/data/CRC09_filtered.mpileup`
(paths in a script are relative to the script's directory; `examples/data/` is ignored by git).
The file is a samtools mpileup that has already been filtered: `chrom pos ref`, then
`depth bases qualities` for each of the 25 cells, in the order of `cellNames` in the script.

**Build** the CLI distribution and this adapter bundle (see [the CLI guide](../../../docs/beast3-cli.md)
for the generic, non-macOS distribution):

```bash
mvn -pl integrations/beast3/java -am -Pcli-dist-macos-arm64 package -DskipTests
mvn -P phylonco -pl integrations/beast3/phylonco -am package -DskipTests
mkdir -p "$HOME/phylospec-cli"
unzip -q integrations/beast3/java/target/phylospec-beast3-0.0.1-SNAPSHOT-cli-macos-arm64.zip -d "$HOME/phylospec-cli"
```

The adapter bundle is the directory `integrations/beast3/phylonco/target/phylospec-adapter`
(also packaged as `target/phylospec-beast3-phylonco-0.0.1-SNAPSHOT-adapter.zip`).

**Check and run.** Load the bundle with `--adapter-path` and select it with `--library phylonco`:

```bash
PHYLOSPEC="$HOME/phylospec-cli/phylospec-beast3-0.0.1-SNAPSHOT/bin/phylospec"
ADAPTER="$PWD/integrations/beast3/phylonco/target/phylospec-adapter"
cd integrations/beast3/phylonco/examples
"$PHYLOSPEC" check CRC09.phylospec --adapter-path "$ADAPTER" --library phylonco
"$PHYLOSPEC" run CRC09.phylospec --adapter-path "$ADAPTER" --library phylonco --out "$PWD/results/crc09"
```

`CRC09-explicit.phylospec` runs the same way. Without `--adapter-path`, `use phylonco` fails
with a hint to load the bundle. Neither example has an `mcmc` block, so `run` uses the CLI
defaults; append one to set the chain length:

```
mcmc {
    Integer chainLength = 10000
    Integer defaultLogEvery = 100
}
```

On an Apple Silicon laptop, 10,000 iterations take about two minutes. In the first few thousand
iterations, several parameters (e.g. `t` and most `s`) may not have moved yet while the operators
tune; this is not a sign of a wiring problem, and convergence needs much longer chains.

## Fixed-state comparison with LPhyBEAST

The fixed-state regression in [CRC09ModelTest](src/test/java/tiles/phylonco/CRC09ModelTest.java)
compares both PhyloSpec forms with the
[LPhy version](src/test/reference/crc09-six-cells.lphy) on the same six cells and 30 sites.
Both languages translate to the native Phylonco read-count tree likelihood, integrating over
the unobserved genotypes. The [fixed states](src/test/resources/crc09-reference.properties)
specify the labelled tree, skyline groups and population sizes, GT10 parameters and all
read-count parameters. There are eight states: every split of the five coalescences into two
groups, different tree shapes and heights, and extreme but valid parameter values. At each
state the test compares the likelihood and every prior term, to 1e-8. For the first state:

| Log density at the first fixed state | PhyloSpec | LPhyBEAST |
| --- | ---: | ---: |
| Read-count tree likelihood | -878.8089507260435 | -878.8089507260435 |
| Total prior | 27.97213555097137 | 29.35842991209126 |

The explicit and the compact PhyloSpec forms give the same values.

There are four ordered ways to split five coalescent events between two nonempty groups.
PhyloSpec includes the uniform log mass of `-log(4)` for the selected composition.
LPhyBEAST has no prior term for the group sizes. All other prior terms agree. The constant
cancels in MCMC ratios when the numbers of tips and groups are fixed.

The test `everyMcmcStepKeepsThePosteriorConsistent` runs a short chain in BEAST's debug mode,
which recomputes the posterior from scratch after every step and stops if it differs from the
value BEAST computed incrementally.

### Regenerate the reference

From the PhyloSpec checkout, use Java 25 and Maven to build the pinned Phylonco
translator (LPhyBEAST 1.4.0-beta1). Its input and output files go in a temporary directory.

```sh
module="$PWD/integrations/beast3/phylonco"
work="$(mktemp -d)"
git clone https://github.com/bioDS/beast-phylonco.git "$work/beast-phylonco"
git -C "$work/beast-phylonco" checkout --detach 7a7e51d44a51c00e7e81297a97a8bd8550ea3b9d
cd "$work/beast-phylonco"
mvn -pl phylonco-lphybeast-launcher -am -Dmaven.repo.local="$work/m2" \
  -Dmaven.test.skip=true -Dassembly.skipAssembly=true install
mvn -pl phylonco-lphybeast-launcher -Dmaven.repo.local="$work/m2" \
  dependency:build-classpath -Dmdep.outputFile="$work/module-path.txt"
mp="$(cat "$work/module-path.txt")"

cd "$work"
cp "$module/src/test/resources/six-cells.mpileup" .
cp "$module/src/test/reference/crc09-six-cells.lphy" .
java -Djava.only=true -Dpicocli.disable.closures=true --module-path "$mp" \
  --add-modules ALL-MODULE-PATH -m lphy.beast/lphybeast.LPhyBeastMain \
  convert -seed 19 -l 2000 -o "$work/reference.xml" "$work/crc09-six-cells.lphy"
java -Djava.only=true --module-path "$mp" --add-modules ALL-MODULE-PATH \
  "$module/src/test/reference/ReadCrc09Reference.java" "$work/reference.xml" \
  "$module/src/test/resources/crc09-reference.properties" |
  grep -E '^[A-Za-z0-9]+\.(likelihood|prior\.[A-Za-z0-9]+)='
```

For each state, the evaluator replaces the generated initial state with the fixed values and
prints the native likelihood and every prior term from the XML, named after the PhyloSpec
parameters. These lines are the reference values at the end of the properties file.

## Mpileup reading

`fromMpileup` follows LPhy's `readMpileup` + `toReadCount` (no site filtering; CRC09 uses an
already filtered file). `.`/`,` count as the reference base, `^` plus its quality character,
`$`, indels (`+n`/`-n` and their bases), `*`, `<` and `>` are skipped, and A/C/G/T count in
either case. Lines with fewer than three columns per cell are skipped entirely (LPhy keeps their
position but drops their data, which shifts later sites). A line with more cell columns than
`names` is an error, so a short `names` list cannot silently drop cells, and a malformed indel
reports its line and cell.

## Design notes / open questions

- **One native likelihood for both forms.** beast-phylonco's `beast3` branch evaluates the
  whole observation model in one `ReadCountTreeLikelihood`: ploidy (alpha in {1, 2}, via
  allelic dropout) and coverage (negative binomial from t, v, s) are integrated out per cell
  and site, and the genotypes by Felsenstein pruning under GT10. Nothing but the tree and
  parameters is sampled. Both PhyloSpec forms call `ReadCountModelTile.treeLikelihood`.
- **Latent draws are matched, not sampled.** Each latent draw has a template tile that only
  collects its arguments (as `DrawnSiteRatesTile` does for drawn site rates). Statements that
  another statement refers to are not tiled on their own, so the draws need no state nodes.
- **Accessor overloads live in `phylospec.functions.accessor`.** `ComponentResolver.importNamespace`
  removes same-named generators from other namespaces, so putting `taxa(ReadCount)` in
  `phylonco` would shadow `taxa(Alignment)` after `use phylonco`.
- **GT10 rate keys.** phylonco's GT10 looks up the six rates by key (AC ... GT) on a
  `RealVectorParam`, so `GT10Tile` adds these keys to the `relativeRates` parameter.
- **Cell names in the skyline tree.** `taxa(readCounts)` exposes the cells in input order through
  the same taxon-list interface as `taxa(alignment)`. The skyline uses their taxon set.
  `ReadCountTreeLikelihood` reads the observed counts from the mpileup.
