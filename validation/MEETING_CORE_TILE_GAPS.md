# PhyloSpec Core, backend tile, and XML readiness review


## Scope and terminology

This document separates three questions that were previously mixed together:

1. **PhyloSpec Core definition:** can the model or function be expressed in the
   PhyloSpec language?
2. **Backend tile implementation:** does the relevant PhyloSpec backend contain
   a registered mapping for that Core generator?
3. **Example/XML evidence:** has a concrete PhyloSpec example been exported,
   executed, and compared across the intended validation paths?

In the current repository, `integrations/beast3` is the PhyloSpec backend that
currently targets BEAST 2.8. To avoid implying that the backend is BEAST 3
itself, this document calls it the **PhyloSpec BEAST 2.8 backend** and records
the current module name in parentheses where needed.

The presence of a tile confirms that the backend has code targeting a
corresponding engine class or representation. The absence of a tile does not,
by itself, establish whether the target engine lacks the model, whether it is
available through a package, or whether only the PhyloSpec mapping is missing.
Those cases are marked **target-engine availability to verify**.

## Executive summary

| Coverage level | PhyloSpec Core | PhyloSpec BEAST 2.8 backend | PhyloSpec BEAST X backend |
|---|---:|---:|---:|
| Unique Core generators | 77 | 45 direct generator mappings (58.4%) | 71 direct generator mappings (92.2%) |
| Primary model components | 25 | 16 tiles (64.0%) | 25 tiles (100%) |
| Primary components shared by both backends | — | 16 | 16 |
| Primary components missing only from the BEAST 2.8 backend | — | 9 | 0 |

The nine primary model gaps in the PhyloSpec BEAST 2.8 backend are:
`lg`, `gy94`, `mk`, `PhyloBM`, `PhyloOU`, `SkylineCoalescent`,
`FossilizedBirthDeath`, `logisticPopulationFunction`, and
`compoundPopulationFunction`.

The complete 77-generator audit also shows six generators without a direct
mapping in either backend: `name`, `species`, `Mixture`, `DiscreteGamma`,
`Multinomial`, and `ExponentialMarkovChain`.

These counts describe registered direct generator mappings. Specialized AST or
template handling may support a narrower expression without constituting a
general direct generator mapping. For example, the BEAST 2.8 backend contains
an `OffsetTile` template, but no direct registered `Offset` generator mapping.

# Table 1 — PhyloSpec Core and backend tile implementation matrix

## 1A. Primary model components

This is the meeting-facing matrix for the 25 primary components. “Confirmed by
tile” means the current backend imports and constructs a target-engine
representation. For a missing tile, native/package availability still needs
to be checked separately.

| Category | PhyloSpec Core component | BEAST 2.8 target evidence | BEAST X target evidence | PhyloSpec BEAST 2.8 tile | PhyloSpec BEAST X tile | Implementation gap / interpretation |
|---|---|---|---|---|---|---|
| Nucleotide substitution | `jc69` | Confirmed by current tile | Confirmed by current tile | `JC69Tile` | `JC69Tile` | No implementation gap |
| Nucleotide substitution | `k80` | Confirmed by current tile | Confirmed by current tile | `K80Tile` | `K80Tile` | No implementation gap |
| Nucleotide substitution | `f81` | Confirmed by current tile | Confirmed by current tile | `F81Tile` | `F81Tile` | No implementation gap |
| Nucleotide substitution | `hky` | Confirmed by current tile | Confirmed by current tile | `HKYTile` | `HKYTile` | No implementation gap |
| Nucleotide substitution | `gtr` | Confirmed by current tile | Confirmed by current tile | `GTRTile` | `GTRTile` | No implementation gap; constrained forms exposed a PhyloSpec BEAST X backend XML issue |
| Protein substitution | `wag` | Confirmed by current tile | Confirmed by current tile | `WAGTile` | `WAGTile` | No implementation gap |
| Protein substitution | `jtt` | Confirmed by current tile | Confirmed by current tile | `JTTTile` | `JTTTile` | No implementation gap |
| Protein substitution | `lg` | Availability to verify independently | Confirmed by current tile | **Missing** | `LGTile` | Determine whether BEAST 2.8 support is Core/package-based; implement the PhyloSpec tile if a suitable target exists |
| Codon substitution | `gy94` | Availability to verify independently | Confirmed by current tile | **Missing** | `GY94Tile` | Determine BEAST 2.8 class/package and implement the mapping if supported |
| Discrete-character substitution | `mk` | Availability to verify independently | Confirmed by current tile | **Missing** | `MkTile` | Determine BEAST 2.8 class/package and implement the mapping if supported |
| Site-rate model | `DiscreteGammaInv` | Confirmed by current tile | Confirmed by current tile | `SiteModelTile` | `SiteModelTile` | No implementation gap |
| Clock model | `StrictClock` | Confirmed by current tile | Confirmed by current tile | `StrictClockTile` | `StrictClockTile` | Fixed tip-age use is covered; stochastic sampled tip ages are a separate Core/state-semantics gap |
| Clock model | `RelaxedClock` | Confirmed by current tile | Confirmed by current tile | `RelaxedClockTile` | `RelaxedClockTile` | LogNormal relaxed clock has validation evidence; do not generalize this to every relaxed-clock distribution |
| Evolutionary likelihood | `PhyloCTMC` | Confirmed by current tile | Confirmed by current tile | `PhyloCTMCTile` | `PhyloCTMCTile` | No implementation gap |
| Continuous-trait likelihood | `PhyloBM` | Availability to verify independently | Confirmed by current tile | **Missing** | `PhyloBMTile` | Determine whether BEAST 2.8 support is Core/package-based or unavailable |
| Continuous-trait likelihood | `PhyloOU` | Availability to verify independently | Confirmed by current tile | **Missing** | `PhyloOUTile` | Determine whether BEAST 2.8 has an equivalent model before treating this as a tile task |
| Tree distribution | `Yule` | Confirmed by current tile | Confirmed by current tile | `YuleTile` | `YuleTile` | No implementation gap |
| Tree distribution | `BirthDeath` | Confirmed by current tile | Confirmed by current tile | `BirthDeathTile` | `BirthDeathTile` | Tiles exist; some optional-input and observed-tree semantics remain qualified |
| Tree distribution | `Coalescent` | Confirmed by current tile | Confirmed by current tile | `ConstantCoalescentTile`, `CoalescentTile` | `CoalescentTile`, `CoalescentPopulationFunctionTile` | Constant and exponential population cases are covered |
| Tree distribution | `SkylineCoalescent` | Availability to verify independently | Confirmed by current tile | **Missing** | `SkylineCoalescentTile` | Missing BEAST 2.8 mapping; shared operator policy also needs review |
| Tree distribution | `FossilizedBirthDeath` | Availability/package to verify | Confirmed by current tile | **Missing** | `FossilizedBirthDeathTile` | Verify package dependency and select a shared fossil-data example before implementation is claimed complete |
| Population function | `constantPopulationFunction` | Confirmed by current tile | Confirmed by current tile | `ConstantPopulationTile` | `ConstantPopulationFunctionTile` | No implementation gap |
| Population function | `exponentialPopulationFunction` | Confirmed by current tile | Confirmed by current tile | `ExponentialPopulationTile` | `ExponentialPopulationFunctionTile` | No implementation gap |
| Population function | `logisticPopulationFunction` | Availability to verify independently | Confirmed by current tile | **Missing** | `LogisticPopulationFunctionTile` | Verify target class/package, then implement the BEAST 2.8 mapping |
| Population function | `compoundPopulationFunction` | Availability and intended semantics to verify | Confirmed by current tile | **Missing** | `CompoundPopulationFunctionTile` | Agree on cross-engine semantics before treating this as a straightforward tile port |

## 1B. Complete 77-generator Core audit

The table below records direct registered generator mappings. `—` means no
direct mapping was found in the backend's registered tile library.

In the classification column, **PhyloSpec BEAST 2.8 tile gap** means that the
mapping is missing from PhyloSpec's BEAST 2.8 tile system. It does **not** mean
that BEAST 2.8 itself is missing the component; native or package availability
must be verified separately.

| Core area | PhyloSpec Core generator | PhyloSpec BEAST 2.8 tile | PhyloSpec BEAST X tile | Gap classification |
|---|---|---|---|---|
| Math | `log` | `LogTile` / RPN handling | `LogTile` / RPN handling | Shared |
| Math | `exp` | `ExpTile` / RPN handling | `ExpTile` / RPN handling | Shared |
| Math | `sqrt` | `SqrtTile` | `SqrtTile` / RPN handling | Shared |
| Math | `linspace` | `LinSpaceTile` | `LinSpaceTile` | Shared |
| Math | `range` | `RangeTile` | `RangeTile` | Shared |
| Math | `repeat` | typed repeat tiles | typed repeat tiles | Shared |
| Input | `fromNexus` | `FromNexusTile` | `FromNexusTile` | Shared |
| Input | `fromFasta` | — | `FromFastaTile` | PhyloSpec BEAST 2.8 tile gap |
| Input | `fromTree` | `FromTreeTile` | `FromTreeTile` | Shared |
| Input | `fromCSV` | — | `FromCSVTile` | PhyloSpec BEAST 2.8 tile gap |
| Input | `discreteTraitsFromTaxa` | — | `DiscreteTraitsFromTaxaTile` | PhyloSpec BEAST 2.8 tile gap |
| Input | `continuousTraitsFromTaxa` | — | `ContinuousTraitsFromTaxaTile` | PhyloSpec BEAST 2.8 tile gap |
| Input | `env` | `EnvTile` | `EnvTile` | Shared |
| Input | `fromNewick` | `FromNewickTile` | `FromNewickTile` | Shared |
| Input | `parse` | `ParserTile` | `ParserTile` | Shared |
| Accessor | `taxa` | `AlignmentTaxaTile` | alignment/tree taxa tiles | Shared; BEAST X has broader direct coverage |
| Accessor | `taxon` | — | `TaxonTile` | PhyloSpec BEAST 2.8 tile gap |
| Alignment | `subset` | `SubsetTile` | `SubsetTile` | Shared |
| Accessor | `numBranches` | `NumBranchesTile` | `NumBranchesTile` | Shared |
| Accessor | `numTaxa` | alignment/tree tiles | alignment/tree tiles | Shared |
| Accessor | `numSites` | `NumSitesTile` | `NumSitesTile` | Shared |
| Accessor | `num` | vector/list tiles | vector/list tiles | Shared |
| Accessor | `rootAge` | — | `RootAgeTile` | PhyloSpec BEAST 2.8 tile gap; the PhyloSpec BEAST 2.8 tile system has specialized root-observation handling but no direct generator tile |
| Accessor | `age` | — | taxon/node age tiles | PhyloSpec BEAST 2.8 tile gap |
| Accessor | `mrca` | — | `MRCATile` | PhyloSpec BEAST 2.8 tile gap |
| Accessor | `numRows` | `NumRowsTile` | `NumRowsTile` | Shared |
| Accessor | `numCols` | `NumColsTile` | `NumColsTile` | Shared |
| Math | `sum` | — | `SumRealVectorTile` | PhyloSpec BEAST 2.8 tile gap; the PhyloSpec BEAST X tile currently covers only the real-vector overload directly |
| Accessor | `name` | — | — | Missing in both direct tile systems |
| Accessor | `species` | — | — | Missing in both direct tile systems |
| Distribution | `IID` | — | `IIDTile` | PhyloSpec BEAST 2.8 tile gap |
| Distribution | `Mixture` | — | — | Missing in both direct tile systems |
| Distribution | `Truncated` | — | `TruncatedTile` | PhyloSpec BEAST 2.8 tile gap |
| Distribution | `Offset` | specialized template only | `OffsetTile` | No general BEAST 2.8 direct mapping |
| Distribution | `Normal` | `NormalTile` | `NormalTile` | Shared |
| Distribution | `LogNormal` | standard/real-space tiles | standard/real-space tiles | Shared |
| Distribution | `Gamma` | `GammaTile` | `GammaTile` | Shared |
| Distribution | `DiscreteGamma` | — | — | Missing in both direct tile systems; site-rate handling is separate |
| Distribution | `Beta` | `BetaTile` | `BetaTile` | Shared |
| Distribution | `Exponential` | `ExponentialTile` | `ExponentialTile` | Shared |
| Distribution | `Uniform` | `UniformTile` | `UniformTile` | Shared |
| Distribution | `DiscreteUniform` | `DiscreteUniformTile` | `DiscreteUniformTile` | Shared |
| Distribution | `Cauchy` | `CauchyTile` | `CauchyTile` | Shared |
| Distribution | `Dirichlet` | `DirichletTile` | `DirichletTile` | Shared |
| Distribution | `MultivariateNormal` | — | `MultivariateNormalTile` | PhyloSpec BEAST 2.8 tile gap |
| Distribution | `Bernoulli` | — | `BernoulliTile` | PhyloSpec BEAST 2.8 tile gap |
| Distribution | `Categorical` | — | `CategoricalTile` | PhyloSpec BEAST 2.8 tile gap |
| Distribution | `Binomial` | — | `BinomialTile` | PhyloSpec BEAST 2.8 tile gap |
| Distribution | `Multinomial` | — | — | Missing in both direct tile systems |
| Distribution | `Geometric` | — | `GeometricTile` | PhyloSpec BEAST 2.8 tile gap |
| Distribution | `Poisson` | `PoissonTile` | `PoissonTile` | Shared |
| Distribution | `ExponentialMarkovChain` | — | — | Missing in both direct tile systems |
| Tree distribution | `Yule` | `YuleTile` | `YuleTile` | Shared |
| Tree distribution | `BirthDeath` | `BirthDeathTile` | `BirthDeathTile` | Shared |
| Tree distribution | `Coalescent` | coalescent tiles | coalescent tiles | Shared |
| Tree distribution | `SkylineCoalescent` | — | `SkylineCoalescentTile` | PhyloSpec BEAST 2.8 tile gap |
| Tree distribution | `FossilizedBirthDeath` | — | `FossilizedBirthDeathTile` | PhyloSpec BEAST 2.8 tile gap |
| Evolutionary likelihood | `PhyloCTMC` | `PhyloCTMCTile` | `PhyloCTMCTile` | Shared |
| Continuous likelihood | `PhyloBM` | — | `PhyloBMTile` | PhyloSpec BEAST 2.8 tile gap |
| Continuous likelihood | `PhyloOU` | — | `PhyloOUTile` | PhyloSpec BEAST 2.8 tile gap |
| Clock model | `StrictClock` | `StrictClockTile` | `StrictClockTile` | Shared |
| Clock model | `RelaxedClock` | `RelaxedClockTile` | `RelaxedClockTile` | Shared |
| Site-rate model | `DiscreteGammaInv` | `SiteModelTile` | `SiteModelTile` | Shared |
| Substitution | `jc69` | `JC69Tile` | `JC69Tile` | Shared |
| Substitution | `k80` | `K80Tile` | `K80Tile` | Shared |
| Substitution | `f81` | `F81Tile` | `F81Tile` | Shared |
| Substitution | `hky` | `HKYTile` | `HKYTile` | Shared |
| Substitution | `gtr` | `GTRTile` | `GTRTile` | Shared |
| Substitution | `wag` | `WAGTile` | `WAGTile` | Shared |
| Substitution | `jtt` | `JTTTile` | `JTTTile` | Shared |
| Substitution | `lg` | — | `LGTile` | PhyloSpec BEAST 2.8 tile gap |
| Substitution | `gy94` | — | `GY94Tile` | PhyloSpec BEAST 2.8 tile gap |
| Substitution | `mk` | — | `MkTile` | PhyloSpec BEAST 2.8 tile gap |
| Population function | `constantPopulationFunction` | `ConstantPopulationTile` | `ConstantPopulationFunctionTile` | Shared |
| Population function | `exponentialPopulationFunction` | `ExponentialPopulationTile` | `ExponentialPopulationFunctionTile` | Shared |
| Population function | `logisticPopulationFunction` | — | `LogisticPopulationFunctionTile` | PhyloSpec BEAST 2.8 tile gap |
| Population function | `compoundPopulationFunction` | — | `CompoundPopulationFunctionTile` | PhyloSpec BEAST 2.8 tile gap |

# Table 2 — Example/XML readiness and blocker tracker

## 2A. Current validation examples

This table distinguishes generation from validation. An example can have both
XML exports without yet having an accepted five-path comparison.

| Example | Main capability exercised | Reference | BEAST 2.8 XML export | BEAST X XML export | Current evidence status | Remaining qualification / action |
|---|---|---|---|---|---|---|
| `testGTR` | GTR substitution | BEAST 2 aligned | Generated | Generated | Validated after a PhyloSpec BEAST X backend correction | Move the correction from the validation branch into a focused reviewed change if not already merged |
| `testHKY` | HKY substitution | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testCoalescent` | Constant coalescent | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testExponentialGrowth` | Exponential population coalescent | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testJukesCantor` | JC69 substitution | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testRelaxedClock` | LogNormal relaxed clock | BEAST 2 aligned | Generated | Generated | Validated | Claim only LogNormal relaxed-clock coverage |
| `testSRD06` | Codon partitions and subset handling | BEAST 2 aligned | Generated | Generated | Validated after a PhyloSpec BEAST 2.8 backend iterator correction | Retain regression coverage for the subset fix |
| `testRestrictedGTR` | Constrained GTR | BEAST 2 aligned | Generated | Generated | Validated | Ensure constrained-rate identity remains covered |
| `testTIM` | Constrained GTR/TIM | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testTVM` | Constrained GTR/TVM | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testTN93` | Constrained GTR/TN93 | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testSYM` | Constrained GTR/SYM | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testMultipleAlignments` | Multiple alignments and indexed declarations | BEAST 2 aligned | Generated | Generated | Controlled coverage | Generic indexed materialisation and multi-tree logging still use validation-specific handling |
| `testClassicRootCalibrationPrior` | Root calibration | BEAST 2 aligned | Generated | Generated | Controlled coverage | Calibration initialization/classification required focused handling |
| `testTipDates` | Fixed tip dates | BEAST 2 aligned | Generated | Generated | Validated for fixed metadata | Does not establish stochastic sampled-tip support |
| `testTipDates2` | Approximation of sampled-tip example | BEAST 2 aligned | Generated | Generated | Controlled coverage | Fixed-age approximation does not reproduce the original stochastic sampled-tip model |
| `testYuleOneSite` | Yule plus JC69 likelihood | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testSliceHKY` | Alignment slicing plus HKY | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testSiteModelAlpha` | Gamma site-rate model | BEAST 3-derived/aligned | Generated | Generated | Validated | None |
| `testBirthDeathModel10Taxa` | Birth–death prior | BEAST 3-derived/aligned | Generated | Generated | Controlled coverage | Literal optional inputs and tree-type alignment required focused handling |
| `testBirthDeathAsYule` | Birth–death/Yule density agreement | BEAST 3-derived/aligned | Generated | Generated | Controlled coverage | Generic BEAST X tree `observed as` remains unsupported |
| `testF81` | F81 substitution | BEAST 3-derived/aligned | Generated | Generated | Prepared; not yet accepted as validated | Compare and accept all five posterior traces |
| `testK80` | K80 substitution | BEAST 3-derived/aligned | Generated | Generated | Prepared; not yet accepted as validated | Compare and accept all five posterior traces |
| `testJTT` | JTT protein substitution | BEAST 3-derived/aligned | Generated | Generated | Prepared; not yet accepted as validated | Compare and accept all five posterior traces |
| `testWAG` | WAG protein substitution | BEAST 3-derived/aligned | Generated | Generated | Prepared; not yet accepted as validated | Compare and accept all five posterior traces |

`testProteinModels` is a supporting data/example directory rather than an
additional accepted five-path benchmark, so it is not counted as a 26th
validation example.

## 2B. XML families not yet represented by a shared five-path example

These are the primary model capabilities for which the current repository
cannot yet provide a shared two-backend example. They should not all be
described simply as “unsupported”: the target-engine availability question
must be answered first for each missing BEAST 2.8 mapping.

| Missing XML/example family | PhyloSpec Core definition | BEAST 2.8 backend | BEAST X backend | Why a shared XML comparison is currently blocked | Decision or implementation needed |
|---|---|---|---|---|---|
| LG protein model | Present: `lg` | No direct tile | `LGTile` | No PhyloSpec-generated BEAST 2.8 form | Verify BEAST 2.8 Core/package class; then implement `LGTile` or record `N/A` |
| GY94 codon model | Present: `gy94` | No direct tile | `GY94Tile` | No PhyloSpec-generated BEAST 2.8 form | Verify target class/package; implement mapping if supported |
| Mk discrete-character model | Present: `mk` | No direct tile | `MkTile` | No PhyloSpec-generated BEAST 2.8 form | Verify target class/package; implement mapping if supported |
| Brownian-motion trait likelihood | Present: `PhyloBM` | No direct tile | `PhyloBMTile` | No shared BEAST 2.8 path | Determine Core/package/native availability and supported trait scope |
| Ornstein–Uhlenbeck trait likelihood | Present: `PhyloOU` | No direct tile | `PhyloOUTile` | No shared BEAST 2.8 path | Determine whether an equivalent BEAST 2.8 model exists |
| Skyline coalescent | Present: `SkylineCoalescent` | No direct tile | `SkylineCoalescentTile` | Missing model mapping; operator policy also needs cross-backend review | Verify target representation, implement mapping, and define operator policy |
| Fossilized birth–death | Present: `FossilizedBirthDeath` | No direct tile | `FossilizedBirthDeathTile` | Missing mapping and no selected shared fossil-data example | Verify package dependency, implement mapping, and add a fossil example |
| Logistic population coalescent | Present: `logisticPopulationFunction` | No direct tile | `LogisticPopulationFunctionTile` | No PhyloSpec-generated BEAST 2.8 population function | Verify target class/package and implement mapping |
| Compound population coalescent | Present: `compoundPopulationFunction` | No direct tile | `CompoundPopulationFunctionTile` | No BEAST 2.8 mapping and cross-engine semantics are not yet agreed | Decide intended semantics before implementation |

## 2C. Existing XML families that remain only partially reproducible

| XML capability | Current state | Exact blocker | Correct meeting description |
|---|---|---|---|
| Stochastic sampled tip ages | Only a fixed-age approximation is currently covered | Shared Core/state semantics for stochastic tip ages are unresolved | “Fixed tip ages are supported; the original stochastic sampled-tip model is not yet faithfully reproduced.” |
| Generic multi-tree logging | Validation examples run with focused handling | Generic BEAST X multi-tree XML logging is not complete | “The selected example runs, but general multi-tree logging remains an implementation gap.” |
| Generic indexed materialisation | Selected example runs with a workaround | Indexed declarations are not materialised generically in every context | “Controlled example coverage, not complete generic support.” |
| Root calibration | Selected controlled interval runs | Initialization and prior classification required focused corrections | “The selected calibration case is covered with qualifications.” |
| Birth–death optional/tree observations | Selected examples run | Literal optional inputs and generic BEAST X tree `observed as` semantics remain qualified | “Birth–death density evidence exists, but generic observation semantics are not complete.” |

# Recommended meeting decisions

1. Confirm that `integrations/beast3` should be described externally as the
   **PhyloSpec BEAST 2.8 backend**, or agree on a clearer module rename.
2. Agree on the authoritative inventory used for the “BEAST 2.8 Core” and
   “BEAST X” columns: core distribution only, or core plus named packages.
3. For each of the nine primary BEAST 2.8 backend gaps, classify it as:
   **implement tile**, **package-dependent**, **no target equivalent**, or
   **semantics/design decision required**.
4. Decide whether the immediate priority is all 25 primary components or the
   full 77-generator Core inventory. These are different completion targets.
5. Finish the four prepared trace comparisons without counting them as
   validated in advance.
6. Keep implementation coverage and validation coverage as separate metrics:
   a tile is not validation evidence, and an untested tile is not an
   unsupported model.

# Suggested concise progress statement

> PhyloSpec Core currently defines 77 unique generators, including 25 primary
> model components. The PhyloSpec backend currently targeting BEAST 2.8 has 45
> direct generator mappings overall and covers 16 of the 25 primary model
> components. The BEAST X backend has 71 direct mappings overall and covers all
> 25 primary components. The immediate primary-model gap is therefore nine
> missing BEAST 2.8 backend mappings, but we still need to distinguish mappings
> that should be implemented from models that are package-dependent or have no
> BEAST 2.8 equivalent. Separately, 21 examples have completed five-path
> evidence, four substitution examples are prepared but not yet accepted, and
> several completed examples remain explicitly qualified.
