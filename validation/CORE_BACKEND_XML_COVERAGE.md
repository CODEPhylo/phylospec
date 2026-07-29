# PhyloSpec Core, backend tile, and XML readiness review


## Scope and terminology

1. **PhyloSpec Core definition:** can the model or function be expressed in the
   PhyloSpec language?
2. **Backend tile implementation:** does the relevant PhyloSpec backend contain
   a registered mapping for that Core generator?
3. **Example/XML evidence:** has a concrete PhyloSpec example been exported,
   executed, and compared across the intended validation paths?

The presence of a tile confirms that the backend has code targeting a
corresponding engine class or representation. The absence of a tile does not,
by itself, establish whether the target engine lacks the model, whether it is
available through a package, or whether only the PhyloSpec mapping is missing.
Those cases are marked **target-engine availability to verify**.

## Executive summary

| Coverage level | PhyloSpec Core | PhyloSpec BEAST 3 tile system (`beast-base:2.8.0-SNAPSHOT`) | PhyloSpec BEAST X tile system |
|---|---:|---:|---:|
| Unique Core generators | 77 | 45 direct generator mappings (58.4%) | 71 direct generator mappings (92.2%) |
| Primary model components | 25 | 16 tiles (64.0%) | 25 tiles (100%) |
| Primary components shared by both backends | — | 16 | 16 |
| Primary components without a mapping in the BEAST 3 tile system | — | 9 | 0 |

Nine primary PhyloSpec Core models lack a mapping in the current
`integrations/beast3` tile system. Direct inspection of its current BEAST 3
Core dependency separates these into:

- **two confirmed PhyloSpec tile gaps:** `SkylineCoalescent` and
  `compoundPopulationFunction`;
- **seven rows with no current Core target found:** `lg`, `gy94`, `mk`,
  `PhyloBM`, `PhyloOU`, `FossilizedBirthDeath`, and
  `logisticPopulationFunction`.

The second group requires a package, alternative representation, or scope
decision before it should be counted as ordinary missing-tile work.

The complete 77-generator audit also shows six generators without a direct
mapping in either backend: `name`, `species`, `Mixture`, `DiscreteGamma`,
`Multinomial`, and `ExponentialMarkovChain`.

These counts describe registered direct generator mappings. Specialized AST or
template handling may support a narrower expression without constituting a
general direct generator mapping. For example, the current BEAST 3 tile system
contains an `OffsetTile` template, but no direct registered `Offset` generator
mapping.

# Table 1 — PhyloSpec Core and backend tile implementation matrix

## 1A. Primary model components

| Category | PhyloSpec Core component | Current BEAST 3 Core evidence (`beast-base:2.8.0-SNAPSHOT`) | BEAST X target evidence | PhyloSpec BEAST 3 tile | PhyloSpec BEAST X tile | Implementation gap / interpretation |
|---|---|---|---|---|---|---|
| Nucleotide substitution | `jc69` | Confirmed by current tile | Confirmed by current tile | `JC69Tile` | `JC69Tile` | No implementation gap |
| Nucleotide substitution | `k80` | Confirmed by current tile | Confirmed by current tile | `K80Tile` | `K80Tile` | No implementation gap |
| Nucleotide substitution | `f81` | Confirmed by current tile | Confirmed by current tile | `F81Tile` | `F81Tile` | No implementation gap |
| Nucleotide substitution | `hky` | Confirmed by current tile | Confirmed by current tile | `HKYTile` | `HKYTile` | No implementation gap |
| Nucleotide substitution | `gtr` | Confirmed by current tile | Confirmed by current tile | `GTRTile` | `GTRTile` | No implementation gap; constrained forms exposed a PhyloSpec BEAST X backend XML issue |
| Protein substitution | `wag` | Confirmed by current tile | Confirmed by current tile | `WAGTile` | `WAGTile` | No implementation gap |
| Protein substitution | `jtt` | Confirmed by current tile | Confirmed by current tile | `JTTTile` | `JTTTile` | No implementation gap |
| Protein substitution | `lg` | **Not found in current Core** | Confirmed by current tile | **No tile; no named Core target** | `LGTile` | Discuss a package, generic empirical-matrix representation, or out-of-scope classification before treating this as a tile task |
| Codon substitution | `gy94` | **Not found in current Core** | Confirmed by current tile | **No tile; no named Core target** | `GY94Tile` | Identify a package or alternative target representation before treating this as a tile task |
| Discrete-character substitution | `mk` | **Not found in current Core** | Confirmed by current tile | **No tile; no named Core target** | `MkTile` | Identify a morphology/discrete-trait package or alternative target before treating this as a tile task |
| Site-rate model | `DiscreteGammaInv` | Confirmed by current tile | Confirmed by current tile | `SiteModelTile` | `SiteModelTile` | No implementation gap |
| Clock model | `StrictClock` | Confirmed by current tile | Confirmed by current tile | `StrictClockTile` | `StrictClockTile` | Fixed tip-age use is covered; stochastic sampled tip ages are a separate Core/state-semantics gap |
| Clock model | `RelaxedClock` | Confirmed by current tile | Confirmed by current tile | `RelaxedClockTile` | `RelaxedClockTile` | LogNormal relaxed clock has validation evidence; do not generalize this to every relaxed-clock distribution |
| Evolutionary likelihood | `PhyloCTMC` | Confirmed by current tile | Confirmed by current tile | `PhyloCTMCTile` | `PhyloCTMCTile` | No implementation gap |
| Continuous-trait likelihood | `PhyloBM` | **Not found in current Core** | Confirmed by current tile | **No tile; no Core target** | `PhyloBMTile` | Requires a package/alternative implementation decision before any tile work |
| Continuous-trait likelihood | `PhyloOU` | **Not found in current Core** | Confirmed by current tile | **No tile; no Core target** | `PhyloOUTile` | Requires a package/alternative implementation decision before any tile work |
| Tree distribution | `Yule` | Confirmed by current tile | Confirmed by current tile | `YuleTile` | `YuleTile` | No implementation gap |
| Tree distribution | `BirthDeath` | Confirmed by current tile | Confirmed by current tile | `BirthDeathTile` | `BirthDeathTile` | Tiles exist; some optional-input and observed-tree semantics remain qualified |
| Tree distribution | `Coalescent` | Confirmed by current tile | Confirmed by current tile | `ConstantCoalescentTile`, `CoalescentTile` | `CoalescentTile`, `CoalescentPopulationFunctionTile` | Constant and exponential population cases are covered |
| Tree distribution | `SkylineCoalescent` | **Supported in Core:** `BayesianSkyline` | Confirmed by current tile | **Missing** | `SkylineCoalescentTile` | **Confirmed PhyloSpec BEAST 3 tile gap**; model mapping and shared operator policy need implementation/review |
| Tree distribution | `FossilizedBirthDeath` | **Not found in current Core**; FBD is package-based in BEAST 2 | Confirmed by current tile | **No tile; no Core target** | `FossilizedBirthDeathTile` | Decide whether the BEAST 3 backend will depend on an FBD package, then select a shared fossil-data example |
| Population function | `constantPopulationFunction` | Confirmed by current tile | Confirmed by current tile | `ConstantPopulationTile` | `ConstantPopulationFunctionTile` | No implementation gap |
| Population function | `exponentialPopulationFunction` | Confirmed by current tile | Confirmed by current tile | `ExponentialPopulationTile` | `ExponentialPopulationFunctionTile` | No implementation gap |
| Population function | `logisticPopulationFunction` | **Not found in current Core** | Confirmed by current tile | **No tile; no Core target** | `LogisticPopulationFunctionTile` | Requires a package/custom implementation or out-of-scope decision |
| Population function | `compoundPopulationFunction` | **Supported in Core:** `CompoundPopulationFunction` | Confirmed by current tile | **Missing** | `CompoundPopulationFunctionTile` | **Confirmed PhyloSpec BEAST 3 tile gap**; agree on argument/semantic mapping and implement the tile |

## 1B. Complete 77-generator Core audit

The table below records direct registered generator mappings. `—` means no
direct mapping was found in the backend's registered tile library.

In the classification column, **PhyloSpec BEAST 3 tile gap** means that the
mapping is missing from PhyloSpec's current `integrations/beast3` tile system.
It does **not** mean that BEAST 3 Core itself is missing the component; Core or
package availability is recorded separately.

| Core area | PhyloSpec Core generator | PhyloSpec BEAST 3 tile | PhyloSpec BEAST X tile | Gap classification |
|---|---|---|---|---|
| Math | `log` | `LogTile` / RPN handling | `LogTile` / RPN handling | Shared |
| Math | `exp` | `ExpTile` / RPN handling | `ExpTile` / RPN handling | Shared |
| Math | `sqrt` | `SqrtTile` | `SqrtTile` / RPN handling | Shared |
| Math | `linspace` | `LinSpaceTile` | `LinSpaceTile` | Shared |
| Math | `range` | `RangeTile` | `RangeTile` | Shared |
| Math | `repeat` | typed repeat tiles | typed repeat tiles | Shared |
| Input | `fromNexus` | `FromNexusTile` | `FromNexusTile` | Shared |
| Input | `fromFasta` | — | `FromFastaTile` | PhyloSpec BEAST 3 tile gap |
| Input | `fromTree` | `FromTreeTile` | `FromTreeTile` | Shared |
| Input | `fromCSV` | — | `FromCSVTile` | PhyloSpec BEAST 3 tile gap |
| Input | `discreteTraitsFromTaxa` | — | `DiscreteTraitsFromTaxaTile` | PhyloSpec BEAST 3 tile gap |
| Input | `continuousTraitsFromTaxa` | — | `ContinuousTraitsFromTaxaTile` | PhyloSpec BEAST 3 tile gap |
| Input | `env` | `EnvTile` | `EnvTile` | Shared |
| Input | `fromNewick` | `FromNewickTile` | `FromNewickTile` | Shared |
| Input | `parse` | `ParserTile` | `ParserTile` | Shared |
| Accessor | `taxa` | `AlignmentTaxaTile` | alignment/tree taxa tiles | Shared; BEAST X has broader direct coverage |
| Accessor | `taxon` | — | `TaxonTile` | PhyloSpec BEAST 3 tile gap |
| Alignment | `subset` | `SubsetTile` | `SubsetTile` | Shared |
| Accessor | `numBranches` | `NumBranchesTile` | `NumBranchesTile` | Shared |
| Accessor | `numTaxa` | alignment/tree tiles | alignment/tree tiles | Shared |
| Accessor | `numSites` | `NumSitesTile` | `NumSitesTile` | Shared |
| Accessor | `num` | vector/list tiles | vector/list tiles | Shared |
| Accessor | `rootAge` | — | `RootAgeTile` | PhyloSpec BEAST 3 tile gap; the PhyloSpec BEAST 3 tile system has specialized root-observation handling but no direct generator tile |
| Accessor | `age` | — | taxon/node age tiles | PhyloSpec BEAST 3 tile gap |
| Accessor | `mrca` | — | `MRCATile` | PhyloSpec BEAST 3 tile gap |
| Accessor | `numRows` | `NumRowsTile` | `NumRowsTile` | Shared |
| Accessor | `numCols` | `NumColsTile` | `NumColsTile` | Shared |
| Math | `sum` | — | `SumRealVectorTile` | PhyloSpec BEAST 3 tile gap; the PhyloSpec BEAST X tile currently covers only the real-vector overload directly |
| Accessor | `name` | — | — | Missing in both direct tile systems |
| Accessor | `species` | — | — | Missing in both direct tile systems |
| Distribution | `IID` | — | `IIDTile` | PhyloSpec BEAST 3 tile gap |
| Distribution | `Mixture` | — | — | Missing in both direct tile systems |
| Distribution | `Truncated` | — | `TruncatedTile` | PhyloSpec BEAST 3 tile gap |
| Distribution | `Offset` | specialized template only | `OffsetTile` | No general BEAST 2.8 direct mapping |
| Distribution | `Normal` | `NormalTile` | `NormalTile` | Shared |
| Distribution | `LogNormal` | standard/real-space tiles | standard/real-space tiles | Shared |
| Distribution | `Gamma` | `GammaTile` | `GammaTile` | Shared |
| Distribution | `DiscreteGamma` | — | — | No direct scalar-distribution tile in either system; this is distinct from `DiscreteGammaInv`, whose site-rate mapping is implemented by `SiteModelTile` in both systems |
| Distribution | `Beta` | `BetaTile` | `BetaTile` | Shared |
| Distribution | `Exponential` | `ExponentialTile` | `ExponentialTile` | Shared |
| Distribution | `Uniform` | `UniformTile` | `UniformTile` | Shared |
| Distribution | `DiscreteUniform` | `DiscreteUniformTile` | `DiscreteUniformTile` | Shared |
| Distribution | `Cauchy` | `CauchyTile` | `CauchyTile` | Shared |
| Distribution | `Dirichlet` | `DirichletTile` | `DirichletTile` | Shared |
| Distribution | `MultivariateNormal` | — | `MultivariateNormalTile` | PhyloSpec BEAST 3 tile gap |
| Distribution | `Bernoulli` | — | `BernoulliTile` | PhyloSpec BEAST 3 tile gap |
| Distribution | `Categorical` | — | `CategoricalTile` | PhyloSpec BEAST 3 tile gap |
| Distribution | `Binomial` | — | `BinomialTile` | PhyloSpec BEAST 3 tile gap |
| Distribution | `Multinomial` | — | — | No general Multinomial distribution target or direct tile in either system; BEAST X contains specialized multinomial likelihood/prior classes that are not equivalent to this Core generator, and the Core return type (`Distribution<NonNegativeInteger>`) should be reviewed because a multinomial draw normally produces a vector of category counts |
| Distribution | `Geometric` | — | `GeometricTile` | PhyloSpec BEAST 3 tile gap |
| Distribution | `Poisson` | `PoissonTile` | `PoissonTile` | Shared |
| Distribution | `ExponentialMarkovChain` | — | — | Missing in both direct tile systems |
| Tree distribution | `Yule` | `YuleTile` | `YuleTile` | Shared |
| Tree distribution | `BirthDeath` | `BirthDeathTile` | `BirthDeathTile` | Shared |
| Tree distribution | `Coalescent` | coalescent tiles | coalescent tiles | Shared |
| Tree distribution | `SkylineCoalescent` | — | `SkylineCoalescentTile` | **Confirmed PhyloSpec BEAST 3 tile gap:** current Core provides `BayesianSkyline` |
| Tree distribution | `FossilizedBirthDeath` | — | `FossilizedBirthDeathTile` | **No current BEAST 3 Core target found:** package/scope decision required |
| Evolutionary likelihood | `PhyloCTMC` | `PhyloCTMCTile` | `PhyloCTMCTile` | Shared |
| Continuous likelihood | `PhyloBM` | — | `PhyloBMTile` | **No current BEAST 3 Core target found:** package/alternative decision required |
| Continuous likelihood | `PhyloOU` | — | `PhyloOUTile` | **No current BEAST 3 Core target found:** package/alternative decision required |
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
| Substitution | `lg` | — | `LGTile` | **No named current BEAST 3 Core target found:** package/generic representation decision required |
| Substitution | `gy94` | — | `GY94Tile` | **No named current BEAST 3 Core target found:** package/alternative decision required |
| Substitution | `mk` | — | `MkTile` | **No named current BEAST 3 Core target found:** package/alternative decision required |
| Population function | `constantPopulationFunction` | `ConstantPopulationTile` | `ConstantPopulationFunctionTile` | Shared |
| Population function | `exponentialPopulationFunction` | `ExponentialPopulationTile` | `ExponentialPopulationFunctionTile` | Shared |
| Population function | `logisticPopulationFunction` | — | `LogisticPopulationFunctionTile` | **No current BEAST 3 Core target found:** package/custom implementation decision required |
| Population function | `compoundPopulationFunction` | — | `CompoundPopulationFunctionTile` | **Confirmed PhyloSpec BEAST 3 tile gap:** current Core provides `CompoundPopulationFunction` |

# Table 2 — Example/XML readiness and blocker tracker

## 2A. Current validation examples

This table distinguishes generation from validation. An example can have both
XML exports without yet having an accepted five-path comparison.

| Example | Main capability exercised | Reference | BEAST 2.8 XML export | BEAST X XML export | Current evidence status | Remaining qualification / action |
|---|---|---|---|---|---|---|
| `testGTR` | GTR substitution | BEAST 2 aligned | Generated | Generated | Validated | Standard unconstrained GTR; the separate named-rate XML fix applies to the constrained GTR examples below. |
| `testHKY` | HKY substitution | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testCoalescent` | Constant coalescent | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testExponentialGrowth` | Exponential population coalescent | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testJukesCantor` | JC69 substitution | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testRelaxedClock` | LogNormal relaxed clock | BEAST 2 aligned | Generated | Generated | Validated after merged backend fixes | Relative category rates and the global clock rate were normalized consistently across direct/XML paths; invalid generic category proposals were replaced with bounded integer operators (VAL-003/004). |
| `testSRD06` | Codon partitions and subset handling | BEAST 2 aligned | Generated | Generated | Validated after a PhyloSpec BEAST 3 tile fix | BEAST 3 filters `1-/1`, `1-/2`, and `1-/3` selected the full alignment; `SubsetTile` now emits the correct `1::3`, `2::3`, and `3::3` codon filters (VAL-006). |
| `testRestrictedGTR` | Constrained GTR | BEAST 2 aligned | Generated | Generated | Validated after a PhyloSpec BEAST X XML fix | The exporter assumed one fixed unit rate; it now writes AC/AG/AT/CG/CT/GT rates in canonical order and omits one valid reference rate (VAL-007). |
| `testTIM` | Constrained GTR/TIM | BEAST 2 aligned | Generated | Generated | Validated after a PhyloSpec BEAST X XML fix | The exporter now preserves the named constrained-rate identities in canonical AC/AG/AT/CG/CT/GT order (VAL-007). |
| `testTVM` | Constrained GTR/TVM | BEAST 2 aligned | Generated | Generated | Validated after a PhyloSpec BEAST X XML fix | The exporter now preserves the named constrained-rate identities in canonical AC/AG/AT/CG/CT/GT order (VAL-007). |
| `testTN93` | Constrained GTR/TN93 | BEAST 2 aligned | Generated | Generated | Validated after a PhyloSpec BEAST X XML fix | TN93 has several fixed transversion rates; the exporter now serializes them explicitly and selects one valid omitted reference rate (VAL-007). |
| `testSYM` | Constrained GTR/SYM | BEAST 2 aligned | Generated | Generated | Validated after a PhyloSpec BEAST X XML fix | The exporter now preserves the named constrained-rate identities in canonical AC/AG/AT/CG/CT/GT order (VAL-007). |
| `testMultipleAlignments` | Multiple alignments and indexed declarations | BEAST 2 aligned | Generated | Generated | Controlled coverage | BEAST X multi-tree logging is split into separate loggers by the validation wrapper; BEAST 3 retains indexed declarations to avoid duplicating three trees/priors into nine. The aligned reference also adds the missing population-size operator (VAL-008–010). |
| `testClassicRootCalibrationPrior` | Root calibration | BEAST 2 aligned | Generated | Generated | Controlled coverage | A positive lower bound rejected BEAST 3's initial tree, so the comparison uses `Uniform(0,10)`; BEAST 3 was also fixed to register the calibration under `prior`, not `likelihood` (VAL-012/013). |
| `testTipDates` | Fixed tip dates | BEAST 2 aligned | Generated | Generated | Validated for fixed metadata | Does not establish stochastic sampled-tip support |
| `testTipDates2` | Approximation of sampled-tip example | BEAST 2 aligned | Generated | Generated | Controlled coverage | PhyloSpec cannot place tip ages in the sampled state, so the benchmark fixes two tips at age `1.0`, fixes all others at `0.0`, and removes the sampled-tip prior/operator (VAL-014). |
| `testYuleOneSite` | Yule plus JC69 likelihood | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testSliceHKY` | Alignment slicing plus HKY | BEAST 2 aligned | Generated | Generated | Validated | None |
| `testSiteModelAlpha` | Gamma site-rate model | BEAST 3-derived/aligned | Generated | Generated | Validated | None |
| `testBirthDeathModel10Taxa` | Birth–death prior | BEAST 3-derived/aligned | Generated | Generated | Validated | None |
| `testBirthDeathAsYule` | Birth–death/Yule density agreement | BEAST 3-derived/aligned | Generated | Generated | Controlled coverage after a BEAST X operator fix | BEAST X cannot bind a tree distribution with `observed as`, so all paths sample the tree; a missing whole-tree scale operator was added to the BEAST X direct/XML schedules (VAL-017/018). |
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
| LG protein model | Present: `lg` | No tile and no named current BEAST 3 Core target | `LGTile` | Current Core cannot receive a direct named `LG` mapping | Discuss a package, generic empirical-matrix representation, or `N/A` classification |
| GY94 codon model | Present: `gy94` | No tile and no named current BEAST 3 Core target | `GY94Tile` | Current Core cannot receive a direct named `GY94` mapping | Identify a package/alternative target or classify the path as `N/A` |
| Mk discrete-character model | Present: `mk` | No tile and no named current BEAST 3 Core target | `MkTile` | Current Core cannot receive a direct named `Mk` mapping | Identify a morphology/discrete-trait package or classify the path as `N/A` |
| Brownian-motion trait likelihood | Present: `PhyloBM` | No tile and no current BEAST 3 Core target | `PhyloBMTile` | No shared Core path is available | Decide on a continuous-trait package/alternative implementation and supported trait scope |
| Ornstein–Uhlenbeck trait likelihood | Present: `PhyloOU` | No tile and no current BEAST 3 Core target | `PhyloOUTile` | No shared Core path is available | Decide on a package/alternative implementation or classify the path as `N/A` |
| Skyline coalescent | Present: `SkylineCoalescent` | Core provides `BayesianSkyline`, but the PhyloSpec tile is missing | `SkylineCoalescentTile` | **Confirmed PhyloSpec BEAST 3 tile gap**; operator policy also needs cross-backend review | Implement the mapping to `BayesianSkyline` and define operator policy |
| Fossilized birth–death | Present: `FossilizedBirthDeath` | No tile and no current BEAST 3 Core target; FBD is package-based in BEAST 2 | `FossilizedBirthDeathTile` | The current backend has no target class or declared FBD package dependency | Decide whether to add/migrate an FBD package dependency, then add a fossil example |
| Logistic population coalescent | Present: `logisticPopulationFunction` | No tile and no current BEAST 3 Core target | `LogisticPopulationFunctionTile` | Current Core cannot receive a direct logistic population-function mapping | Decide on a package/custom target or classify the path as `N/A` |
| Compound population coalescent | Present: `compoundPopulationFunction` | Core provides `CompoundPopulationFunction`, but the PhyloSpec tile is missing | `CompoundPopulationFunctionTile` | **Confirmed PhyloSpec BEAST 3 tile gap**; argument/semantic mapping is not yet implemented | Agree on the mapping semantics and implement the tile |

## 2C. Existing XML families that remain only partially reproducible

The two XML columns below describe the capabilities of the PhyloSpec
backends, not limitations of the underlying BEAST engines. “BEAST 3 XML”
means XML generated by the current PhyloSpec BEAST 3 backend, which targets
BEAST 2.8.

| XML family | PhyloSpec BEAST 3 XML (BEAST 2.8 target) | PhyloSpec BEAST X XML | PhyloSpec Core/shared limitation | Remaining backend-specific gap | Correct meeting description |
|---|---|---|---|---|---|
| Stochastic sampled tip ages | Fixed tip ages are generated and validated; stochastic sampled ages are not faithfully represented | Fixed tip ages are generated and validated; stochastic sampled ages are not faithfully represented | Shared Core/state semantics do not currently place tip ages in the sampled state | No backend-specific XML limitation should be claimed until the shared representation is defined | “Both backends support fixed tip ages; faithful stochastic sampled-tip reproduction first requires shared Core/state semantics.” |
| Generic multi-tree logging | The selected example generates XML while retaining indexed declarations to avoid duplicating three trees and priors into nine | The selected example generates XML by splitting multi-tree output into separate loggers in the validation wrapper | Generic indexed references and multi-tree logging intent are not materialised uniformly in every context | Generic BEAST X multi-tree logger construction remains incomplete | “The selected example generates XML for both backends, but BEAST X still uses focused handling rather than generic multi-tree logging.” |
| Generic indexed materialisation | The selected indexed declarations generate XML using focused handling | The selected indexed declarations generate XML using focused handling | Indexed declarations are not materialised generically in every Core context | Support beyond the selected cases has not been demonstrated for either backend | “Selected indexed cases generate XML for both backends; this is controlled coverage, not complete generic indexed-declaration support.” |
| Root calibration | The selected `Uniform(0,10)` calibration generates validated XML after correcting initialization and prior classification | The selected `Uniform(0,10)` calibration generates validated XML | Calibration initialization and classification are not yet handled generically for all bounds and contexts | The validated case establishes no remaining BEAST X gap; the qualification is in current PhyloSpec BEAST 3/shared handling | “The selected root-calibration interval generates validated XML for both backends, but broader PhyloSpec calibration handling remains qualified.” |
| Birth–death optional inputs | Validated using the shared/default sampling-probability behaviour because literal `samplingProbability=1.0` was rejected in the tested path | Birth–death XML is generated and validated | Optional-input normalization is not represented uniformly across backend targets | Literal optional-input handling in the PhyloSpec BEAST 3 path differs from the shared representation | “Birth–death XML is validated for both backends, but literal optional-input behaviour is not yet uniform.” |
| Birth–death with an observed tree | The backend can bind the Yule/birth–death distribution to an observed fixed tree before execution | Current XML coverage samples the tree; generic tree `observed as` binding is not implemented | The language can express the observation, but backend state handling must distinguish fixed trees from sampled trees | PhyloSpec BEAST X needs tree-specific observation binding, delayed or reconstructed likelihood creation, and suppression of tree operators for a fixed tree | “BEAST 3 XML supports evaluation on an observed fixed tree; current BEAST X XML instead samples the tree, so the two observation semantics are not yet equivalent.” |

# Suggested concise progress statement

> PhyloSpec Core currently defines 77 unique generators, including 25 primary
> model components. The PhyloSpec backend currently targeting BEAST 2.8 has 45
> direct generator mappings overall and covers 16 of the 25 primary model
> components. The BEAST X backend has 71 direct mappings overall and covers all
> 25 primary components. The immediate primary-model gap is therefore nine
> unmapped primary models in the BEAST 3 tile system. Current Core inspection
> confirms two direct tile tasks—SkylineCoalescent and
> compoundPopulationFunction—while the other seven lack a current Core target
> and require a package, alternative-representation, or scope decision.
> Separately, 21 examples have completed five-path
> evidence, four substitution examples are prepared but not yet accepted, and
> several completed examples remain explicitly qualified.
