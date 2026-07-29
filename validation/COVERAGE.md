# Cross-engine validation coverage

This document summarizes what the current validation suite actually
demonstrates. It distinguishes backend implementation, execution through an
example, and cross-engine semantic agreement. The presence of a tile alone is
not evidence that the tile has been validated.

## Status definitions

| Status | Meaning |
|---|---|
| **Validated** | The component was exercised through all five paths and the comparable posterior quantities were reviewed for agreement. |
| **Validated with local fix** | Five-path agreement was obtained after a backend fix on the current validation branch. |
| **Controlled coverage** | The example runs through five paths, but its original model was deliberately simplified or a validation-only workaround remains. |
| **Awaiting five-path result** | Both backend tiles and the validation example exist, and direct/XML construction succeeds, but posterior traces have not yet been accepted as agreeing. |
| **Implemented, not covered** | A backend tile exists, but none of the accepted or prepared examples demonstrates it. |
| **Unsupported/open** | The original model semantics cannot yet be represented faithfully in both backends. |

The five paths are:

1. an external BEAST 2 or BEAST 3 aligned reference;
2. PhyloSpec to BEAST 3 direct execution;
3. PhyloSpec to BEAST 3 XML and external execution;
4. PhyloSpec to BEAST X direct execution;
5. PhyloSpec to BEAST X XML and external execution.

## Validation checkpoints

The suite currently has 21 completed five-path examples and four additional
substitution-model examples prepared for execution. The projected column is
not a claim of validation: it becomes the final reported value only after
`testF81`, `testK80`, `testJTT`, and `testWAG` have five accepted traces each.

| Measure | Verified now | After all four prepared cases pass |
|---|---:|---:|
| Completed five-path examples | 21 | 25 |
| Prepared but not yet accepted | 4 | 0 |
| BEAST 2-derived references | 18 | 18 |
| BEAST 3-derived/aligned references | 3 | 7 |
| Posterior trace files reviewed | 105 | 125 |
| Complete console/runtime logs reviewed | 105 | 125 |
| BEAST 3 tile classes | 82 | 82 |
| BEAST X tile classes | 117 | 117 |

The example count is not a percentage of total PhyloSpec support. The source
example collections contain duplicates, operator-focused examples, and models
whose required components are not yet available in both backends. Coverage is
therefore reported by model capability below.

## Coverage denominator

The Core component library currently defines 77 unique generators. Of these,
25 are primary model components: tree distributions, evolutionary
likelihoods, clocks, site-rate models, substitution models, and population
functions. The other 52 are input, utility, summary, parser, and general
probability-distribution components.

The 25 primary components are the denominator for the model-coverage matrix:

| Coverage view | Verified now | After all four prepared cases pass |
|---|---:|---:|
| All Core primary model components | 12 / 25 (48%) | 16 / 25 (64%) |
| Components currently implemented by both backends | 12 / 16 (75%) | 16 / 16 (100%) |

Of the 12 exercised components, 11 have full or locally fixed five-path
evidence. `BirthDeath` remains controlled coverage because some original
example semantics still require workarounds. If all four prepared
substitution cases pass, 15 of the 16 shared components will have full or
locally fixed evidence and `BirthDeath` will remain the one controlled row.

## Unified Core component, backend tile, and example matrix

This is the primary project coverage table. A dash means that the Core
component cannot currently be translated by that backend. A component with
two backend tiles but no example is implemented, not validated.

| Core model component | BEAST 3 implementation | BEAST X implementation | Example evidence | Coverage/result | Missing evidence or reason |
|---|---|---|---|---|---|
| `jc69` | `JC69Tile` | `JC69Tile` | `testJukesCantor`, `testYuleOneSite`, `testClassicRootCalibrationPrior` | Validated | — |
| `hky` | `HKYTile` | `HKYTile` | `testHKY`, `testSliceHKY`, coalescent/date/site/partition examples | Validated | — |
| `gtr` | `GTRTile` | `GTRTile` | `testGTR`, `testRestrictedGTR`, `testTIM`, `testTVM`, `testTN93`, `testSYM` | Validated with local fix | Constrained forms exposed the BEAST X XML named-rate defect in VAL-007. |
| `f81` | `F81Tile` | `F81Tile` | `testF81` prepared | Awaiting five-path result | The source and both XML exports are ready; do not count as validated until the traces are compared. |
| `k80` | `K80Tile` | `K80Tile` | `testK80` prepared | Awaiting five-path result | The source and both XML exports are ready; do not count as validated until the traces are compared. |
| `jtt` | `JTTTile` | `JTTTile` | `testJTT` prepared | Awaiting five-path result | Uses a shared BEAST 3 protein alignment and empirical JTT frequencies. |
| `wag` | `WAGTile` | `WAGTile` | `testWAG` prepared | Awaiting five-path result | Uses the same protein model as `testJTT`, changing only the empirical matrix. |
| `lg` | — | `LGTile` | None | BEAST X only | No BEAST 3 tile, so five-path validation is blocked. |
| `gy94` | — | `GY94Tile` | None | BEAST X only | No BEAST 3 codon-model tile. |
| `mk` | — | `MkTile` | None | BEAST X only | No BEAST 3 discrete-trait model tile. |
| `DiscreteGammaInv` | `SiteModelTile`, `DrawnSiteRatesTile` | `SiteModelTile`, `DrawnSiteRatesTile` | `testSiteModelAlpha`, `testMultipleAlignments` | Validated | — |
| `StrictClock` | `StrictClockTile` | `StrictClockTile` | `testMultipleAlignments`, `testSiteModelAlpha`, `testTipDates`, `testTipDates2` | Validated for fixed tip ages | Stochastic sampled tip ages remain outside the shared state model (VAL-014). |
| `RelaxedClock` | `RelaxedClockTile` | `RelaxedClockTile` | `testRelaxedClock` | Validated | Normalization, state restoration, and category operators have regression coverage (VAL-003/004). |
| `PhyloCTMC` | `PhyloCTMCTile` | `PhyloCTMCTile` | 19 sequence-likelihood examples | Validated | Includes constant and gamma site rates, strict/relaxed clocks, and partitions. |
| `PhyloBM` | — | `PhyloBMTile` | None | BEAST X only | No BEAST 3 continuous-trait likelihood tile. |
| `PhyloOU` | — | `PhyloOUTile` | None | BEAST X only | No BEAST 3 OU likelihood tile. |
| `Yule` | `YuleTile` | `YuleTile` | substitution examples, `testYuleOneSite`, calibration, `testBirthDeathAsYule` | Validated | — |
| `BirthDeath` | `BirthDeathTile` | `BirthDeathTile` | `testBirthDeathModel10Taxa`, `testBirthDeathAsYule` | Controlled coverage | Literal optional inputs and BEAST X tree `observed as` require VAL-015/017 handling. |
| `Coalescent` | `CoalescentTile` | `CoalescentTile` | `testCoalescent`, `testExponentialGrowth`, relaxed/site/date/partition examples | Validated | Coverage currently uses constant and exponential population functions. |
| `SkylineCoalescent` | — | `SkylineCoalescentTile` | None | BEAST X only | No BEAST 3 skyline tile; shared operator policy is also not mature. |
| `FossilizedBirthDeath` | — | `FossilizedBirthDeathTile` | None | BEAST X only | No BEAST 3 tile and no shared fossil-data example. |
| `constantPopulationFunction` | `ConstantPopulationTile` | `ConstantPopulationFunctionTile` | `testCoalescent`, relaxed/site/date/partition examples | Validated | — |
| `exponentialPopulationFunction` | `ExponentialPopulationTile` | `ExponentialPopulationFunctionTile` | `testExponentialGrowth` | Validated | — |
| `logisticPopulationFunction` | — | `LogisticPopulationFunctionTile` | None | BEAST X only | No BEAST 3 tile. |
| `compoundPopulationFunction` | — | `CompoundPopulationFunctionTile` | None | BEAST X only | No BEAST 3 tile; confirm desired BEAST X team support and semantics. |

### Supporting Core components exercised by the same examples

These components are not included in the 25-component model denominator, but
they are necessary for the workflows above.

| Core/support capability | BEAST 3 tile | BEAST X tile | Example evidence | Result or limitation |
|---|---|---|---|---|
| `fromNexus` and parsed fixed ages/dates | `FromNexusTile` | `FromNexusTile` | All 21 examples; dated cases in `testTipDates`, `testSiteModelAlpha`, `testMultipleAlignments`, `testTipDates2` | Validated for fixed metadata; stochastic tip ages unsupported. |
| `taxa(alignment)` | `AlignmentTaxaTile` | `AlignmentTaxaTile` | All 21 examples | Validated |
| `subset` | `SubsetTile` | `SubsetTile` | GTR-family examples and `testSRD06` | Validated with local BEAST 3 iterator fix (VAL-006). |
| Indexed declarations | `IndexedStatementTile` | `IndexedStatementTile` | `testMultipleAlignments` | Controlled; generic materialization remains VAL-009. |
| `rootAge` plus `observed between` | `RootObservedBetweenTile` | `RootAgeTile`, `RootObservedBetweenTile` | `testClassicRootCalibrationPrior` | Controlled; calibration initialization and classification required VAL-012/013. |
| `LogNormal` | `LogNormalTile`, `LogNormalRealSpaceTile` | corresponding tiles | Most parameterized examples | Exercised across all five paths. |
| `Gamma` | `GammaTile` | `GammaTile` | `testMultipleAlignments` | Exercised |
| `Dirichlet` | `DirichletTile` | `DirichletTile` | HKY, GTR, coalescent, relaxed-clock and partition examples | Exercised |
| File/screen/tree loggers | `mcmc/*LoggerTile` | `mcmc/*LoggerTile` | All examples | Core traces validated; generic BEAST X multi-tree XML logging remains VAL-008. |
| Chain length and seed | `ChainLengthTile` plus validation configuration | `ChainLengthTile`, `RandomSeedTile` | All examples | Exercised |

### Example-level qualifications

The unified matrix records component coverage, while the following five
examples must remain visibly qualified:

| Example | Qualification |
|---|---|
| `testMultipleAlignments` | Multi-tree logging and indexed materialization still use validation workarounds (VAL-008/009). |
| `testClassicRootCalibrationPrior` | Uses a controlled calibration interval and local prior-classification fix (VAL-011–013). |
| `testTipDates2` | Represents a fixed-age approximation, not the original stochastic sampled-tip model (VAL-014). |
| `testBirthDeathModel10Taxa` | Uses literal-input/tree-type alignment handling from VAL-015/016. |
| `testBirthDeathAsYule` | Density agreement is established, but generic BEAST X tree `observed as` remains unsupported (VAL-017). |

All 21 currently completed examples have five posterior trace files and five
complete run logs. The four prepared substitution cases must not be included
in that statement until their traces have been reviewed. The qualified rows
above must not be cited as complete support for their original distributed
models.

## How to report completion of the four prepared cases

Once the comparable marginals agree for all five paths, make only these
status changes:

1. change the `f81`, `k80`, `jtt`, and `wag` rows from **Awaiting five-path
   result** to **Validated**;
2. use the right-hand checkpoint values: 25 completed examples, 125 reviewed
   traces/logs, 16/25 Core primary components, and 16/16 components shared by
   the two backends;
3. retain every unsupported and controlled row. Passing these four examples
   does not establish support for LG, GY94, Mk, PhyloBM, PhyloOU, skyline,
   fossilized birth-death, logistic population, or compound population
   models.

## Coverage conclusion and next gate

The current suite provides strong evidence for the common nucleotide
phylogenetic path:

- NEXUS input and taxa extraction;
- JC69, HKY, GTR, and several constrained-GTR parameterizations;
- constant and gamma-distributed site rates;
- strict and discretized relaxed clocks;
- Yule, birth-death, constant coalescent, and exponential coalescent priors;
- alignment slicing, codon partitions, multiple alignments, fixed tip dates,
  and a controlled root calibration.

The next useful work is not another arbitrary example. It is to:

1. turn the **fixed locally** findings into focused backend regression tests
   and small reviewable pull requests;
2. replace validation-only workarounds for multi-tree logging and indexed
   materialization;
3. decide the language semantics for stochastic tip ages;
4. select a new example only when it covers a currently uncovered tile in
   both backends.

Operator schedules are intentionally outside the pass criterion here.
Operators affect efficiency and convergence, while this table primarily
records model construction and target-density semantics. Operator parity
should become a separate coverage dimension once the shared selection policy
is mature.
