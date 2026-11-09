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

## Fixed-state comparison with LPhyBEAST

The fixed-state regression in [CRC09ModelTest](src/test/java/tiles/phylonco/CRC09ModelTest.java)
compares the PhyloSpec model with the
[LPhy version](src/test/reference/crc09-six-cells.lphy) on the same six cells and 30 sites.
Both languages translate to the native Phylonco read-count tree likelihood, integrating over
the unobserved genotypes. The [fixed state](src/test/resources/crc09-reference.properties)
specifies the labelled tree, skyline groups and population sizes, GT10 parameters and all
read-count parameters. The reference is evaluated before any MCMC proposal.

| Log density at the fixed state | PhyloSpec | LPhyBEAST |
| --- | ---: | ---: |
| Read-count tree likelihood | -878.8089507260435 | -878.8089507260435 |
| Total prior | 27.97213555097137 | 29.35842991209126 |

There are four ordered ways to split five coalescent events between two nonempty groups.
PhyloSpec includes the uniform log mass of `-log(4)` for the selected composition.
LPhyBEAST leaves this constant out of its prior. The prior terms agree after accounting
for that mass. It cancels in MCMC ratios when the numbers of tips and groups are fixed.

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
  "$module/src/test/resources/crc09-reference.properties"
```

The evaluator replaces the generated initial state with the shared fixed values and
prints the native likelihood and prior from the XML, including their individual terms.

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
- **Cell names in the skyline tree.** `taxa(readCounts)` exposes the cells in input order through
  the same taxon-list interface as `taxa(alignment)`. The skyline uses their taxon set.
  `ReadCountTreeLikelihood` reads the observed counts from the mpileup.
