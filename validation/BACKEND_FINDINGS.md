# PhyloSpec backend and validation findings

This is the canonical engineering log for backend and validation-framework
issues discovered by the cross-engine benchmark suite. Keep this file under
version control and update it whenever a benchmark exposes a backend semantic
error, XML-export error, operator error, logger error, or validation-framework
limitation.

The desktop entry `PhyloSpec-backend-validation-findings.md` points to this
file.

## Recording policy

For every new finding, record:

1. the example and affected execution path;
2. the observed symptom;
3. whether posterior semantics or only reporting/efficiency is affected;
4. the root cause;
5. the implemented fix or current workaround;
6. the regression test or rerun required;
7. the commit or pull request once available.

Status values:

- **merged**: present on the shared main branch;
- **fixed locally**: implemented on the current branch but not yet merged;
- **workaround**: validation can continue, but the generic backend still needs
  a focused fix;
- **open**: diagnosed but not yet corrected.

## Findings

### VAL-001 — BEAST X base-frequency dimension and wiring

- **Status:** merged
- **Area:** BEAST X direct model construction
- **Observed:** estimated nucleotide base-frequency vectors could be
  constructed or assigned with the wrong dimension/wiring, preventing a
  faithful four-component simplex comparison.
- **Impact:** model semantics and operator coverage.
- **Fix:** corrected assignment/model construction so the four-component
  frequency parameter is preserved and used by the substitution model.
- **Evidence:** comparison diagnostics and BEAST X model tests.
- **Commit:** `fca0144` (`Fix BEASTX base-frequency dimensions and update comparison tests`)

### VAL-002 — Generated XML lacked PhyloSpec provenance

- **Status:** merged
- **Area:** BEAST 3 and BEAST X XML export
- **Observed:** generated XML could only be identified by filenames and did
  not preserve the PhyloSpec source that produced it.
- **Impact:** provenance and reproducibility; posterior semantics unchanged.
- **Fix:** embed the executed PhyloSpec source as a safe XML comment in both
  backend exporters.
- **Evidence:** core provenance tests plus BEAST 3/BEAST X XML tests.
- **Commit:** `162029a` (`Add XML provenance support and backend tests`)

### VAL-003 — Relaxed-clock normalization and direct/XML parity

- **Status:** merged
- **Area:** BEAST 3 and BEAST X relaxed-clock construction
- **Example:** `testRelaxedClock`
- **Observed:** direct and XML paths could use incompatible interpretations of
  relaxed-clock category rates and the global clock rate. BEAST X could also
  restore an inconsistent likelihood state after rejected tree/category
  proposals.
- **Impact:** model semantics and MCMC correctness.
- **Fix:** normalize relative relaxed-clock rates, apply the global clock rate
  consistently, align direct/XML branch-rate construction, and add fixed-state
  branch-rate/likelihood parity coverage.
- **Evidence:** `BeastXRelaxedClockDirectXmlParityTest` and representative
  relaxed-clock model tests.
- **Commit:** `591f425` (`Fix relaxed-clock parity and category operators`)

### VAL-004 — Relaxed-clock categories used generic parameter operators

- **Status:** merged
- **Area:** BEAST 3 and BEAST X operator selection
- **Example:** `testRelaxedClock`
- **Observed:** integer-valued branch-rate categories were handled by generic
  parameter logic, producing invalid or ineffective proposals.
- **Impact:** sampling correctness/efficiency.
- **Fix:** select integer random-walk, swap, and bounded uniform category
  operators; obtain proposal bounds from the category parameter.
- **Evidence:** relaxed-clock category operator regression tests.
- **Commit:** `591f425`

### VAL-005 — BEAST X default XML trace omitted core posterior columns

- **Status:** merged
- **Area:** BEAST X XML logger
- **Observed:** generated BEAST X XML traces could omit `posterior`, `prior`,
  and `likelihood`, making direct/XML comparison impossible.
- **Impact:** reporting only; the MCMC target was still constructed.
- **Fix:** make the default XML logger emit the three compound values and the
  selected stochastic parameters.
- **Evidence:** `BeastXXmlDefaultLoggerTest`.
- **Commit:** `591f425`

### VAL-006 — BEAST 3 codon subset syntax selected the complete alignment

- **Status:** fixed locally
- **Area:** BEAST 3 `SubsetTile`
- **Example:** `testSRD06`
- **Observed:** filters `1-/1`, `1-/2`, and `1-/3` each selected all 898
  sites. The three likelihoods therefore evaluated the complete alignment
  three times.
- **Impact:** severe model-semantics error; not an operator or mixing issue.
- **Root cause:** the slash suffix was not BEAST iterator syntax.
- **Fix:** generate iterator filters `1::3`, `2::3`, and `3::3`; preserve
  one-based codon phase when explicit start/end bounds are supplied; validate
  range and codon-position inputs.
- **Evidence:** expected partition sizes `300/299/299` and pattern counts
  `207/163/109`; focused `SubsetTile` tests.
- **Current files:** `SubsetTile.java` and the `testSRD06` example notes.

### VAL-007 — BEAST X GTR XML exporter could not serialize TN93/restricted GTR

- **Status:** fixed locally
- **Area:** BEAST X substitution-model XML export
- **Examples:** `testTN93`, `testRestrictedGTR`, `testTIM`, `testTVM`,
  `testSYM`
- **Observed:** the exporter assumed exactly one fixed rate equal to `1.0`
  and relied on generic variable iteration order. TN93 has several fixed
  transversion rates and was rejected.
- **Impact:** XML export failure or incorrect named-rate mapping.
- **Fix:** extract AC/AG/AT/CG/CT/GT fields explicitly in canonical order;
  choose one fixed unit rate as BEAST X's omitted reference rate and serialize
  the remaining fixed unit rates explicitly.
- **Evidence:** `BeastXXmlTN93Test`, `BeastXXmlRestrictedGTRTest`, and
  five-path constrained-GTR examples.
- **Current file:** `SubstitutionModelXmlBuilder.java`.

### VAL-008 — Multiple BEAST X tree loggers were serialized as one logTree

- **Status:** workaround
- **Area:** BEAST X XML logger and validation wrapper
- **Example:** `testMultipleAlignments`
- **Observed:** several selected trees were written into one `<logTree>`,
  although BEAST X requires exactly one tree per tree logger. Direct loggers
  also reused one output filename.
- **Impact:** logging/export failure; posterior construction unchanged.
- **Workaround:** the validation wrapper emits one logger and one uniquely
  named `.trees` file per tree.
- **Remaining work:** move the generic behavior into the backend logger
  builder and add a focused multi-tree XML regression test.

### VAL-009 — Indexed declarations were materialized repeatedly in BEAST 3

- **Status:** workaround
- **Area:** BEAST 3 indexed declaration/materialization
- **Example:** `testMultipleAlignments`
- **Observed:** manually expanding three indexed declarations produced nine
  state trees and nine coalescent priors, while only three trees were connected
  to likelihoods.
- **Impact:** model-semantics error when explicit expansion is used.
- **Workaround:** retain indexed `tree[i]`, `branchRates[i]`, and
  `alignment[i]` declarations in the validation source.
- **Remaining work:** add a focused materialization regression test before
  changing generic expansion behavior.

### VAL-010 — Aligned BEAST 2 reference omitted a population-size operator

- **Status:** fixed locally
- **Area:** manually aligned reference XML
- **Example:** `testMultipleAlignments`
- **Observed:** `popSize.t:gene1` was present in the state and prior but had no
  operator, so it stayed fixed while PhyloSpec paths sampled population size.
- **Impact:** reference MCMC semantics and correlated posterior estimates.
- **Fix:** add the missing population-size `ScaleOperator`.
- **Evidence:** rerun the BEAST 2 reference and compare population size,
  clock rate, and tree summaries.

### VAL-011 — Ambiguous BEAST 2 Uniform class in aligned XML

- **Status:** fixed locally
- **Area:** manually aligned reference XML
- **Example:** `testClassicRootCalibrationPrior`
- **Observed:** `spec="Uniform"` resolved to the uniform distribution rather
  than the tree operator, which then rejected the `tree` input.
- **Impact:** XML parse failure only.
- **Fix:** use the fully qualified
  `beast.base.evolution.operator.Uniform` class.
- **Evidence:** BEAST 2 XML parses and starts successfully.

### VAL-012 — Positive root-calibration lower bound rejected the initial tree

- **Status:** fixed locally in the controlled example
- **Area:** cross-engine initial-state construction
- **Example:** `testClassicRootCalibrationPrior`
- **Observed:** BEAST 3 produced `rootCalibration = -Infinity` before MCMC
  because its automatically generated initial Yule tree was below the
  calibration lower bound.
- **Impact:** initialization failure; target semantics were otherwise valid.
- **Fix/workaround:** use the shared controlled calibration
  `Uniform(0.0, 10.0)` so the comparison does not depend on backend-specific
  starting-tree generation.
- **Remaining work:** consider a general calibration-aware starting-tree
  initializer if positive lower bounds must be supported directly.

### VAL-013 — BEAST 3 classified root calibration as likelihood

- **Status:** fixed locally
- **Area:** BEAST 3 prior construction and logging
- **Example:** `testClassicRootCalibrationPrior`
- **Observed:** all five posterior distributions agreed, but BEAST 3 shifted
  the constant root-calibration contribution (`-log(10)`) from `prior` to
  `likelihood`.
- **Impact:** prior/likelihood reporting semantics; posterior unchanged for
  this example.
- **Root cause:** `RootObservedBetweenTile` called
  `addLikelihoodDistribution`. The existing prior registry also allowed only
  one prior per state node, so adding calibration as a tree prior would have
  overwritten the Yule prior.
- **Fix:** store a list of prior distributions per state node, expose a
  flattened prior list to the runner/logger, and register root calibration as
  a second prior associated with the tree.
- **Regression coverage:** `rootObservedBetween.phylospec` requires both
  `tree_prior` and `rootCalibration` in the prior collection and no calibration
  likelihood. `BeastStateScriptFilesTest` passes all 104 cases. A validation
  export check confirms that generated BEAST 3 XML places `tree_prior`,
  `rootCalibration`, and `birthRate_prior` under `prior`, while `likelihood`
  contains only the alignment likelihood.
- **Known unrelated test state:** the full `TilingScriptFilesTest` currently
  has two pre-existing failures for `fromEmptyTree.phylospec` and
  `fromInvalidTree.phylospec`; the new calibration fixture passes.

### VAL-014 — Sampled tip ages are not represented in PhyloSpec

- **Status:** open
- **Area:** language semantics, dated-tree state, both backends, operators,
  and logging
- **Example:** `testTipDates2`
- **Observed:** the distributed BEAST 2 example places selected tip heights
  in the MCMC state, constrains them with a tip-only `MRCAPrior`, and updates
  them with `TipDatesRandomWalker`. PhyloSpec currently parses tip ages into
  fixed alignment metadata.
- **Impact:** missing model coverage. Treating parsed ages as if they were
  sampled would change the state space and target distribution.
- **Current workaround:** the controlled five-path benchmark fixes
  `Lemur_catta` and `M_fascicularis` at their original age of `1.0`; all other
  tip ages are fixed at `0.0`. The aligned BEAST 2 XML removes the uncertain
  tip-age density and operator.
- **Required design work:** define stochastic tip-age syntax; construct
  backend-specific mutable tip-height state; attach selected-taxon priors;
  select compatible proposal operators; expose comparable tip-age loggers;
  and add direct/XML parity tests.
- **Evidence required:** first validate the fixed-age `testTipDates2`
  comparison, then introduce a separate sampled-age fixture that confirms
  identical state membership, bounds, proposals, and posterior marginals.

### VAL-015 — BEAST 3 BirthDeath rejects a literal optional sampling probability

- **Status:** workaround
- **Area:** BEAST 3 optional generator-input binding
- **Example:** `testBirthDeathModel10Taxa`
- **Observed:** `samplingProbability=1.0` in a `BirthDeath` call fails during
  tiling with an unsupported-value error, although the tile accepts a
  stochastic unit-interval variable for the same optional input.
- **Impact:** input expressiveness only for this benchmark; posterior
  semantics are unchanged because both backends default the omitted sampling
  probability to `1.0`.
- **Workaround:** omit `samplingProbability` from the controlled PhyloSpec
  source and state the shared default explicitly in the example README.
- **Evidence:** BEAST 3 and BEAST X XML export both succeed after omission;
  generated BEAST X XML explicitly contains sample probability `1.0`.
- **Remaining work:** add focused literal-unit-interval input coverage before
  changing generic tile-input coercion.

### VAL-016 — BirthDeath tree-type coefficient differed across backends

- **Status:** fixed locally
- **Area:** BEAST 3 and BEAST X `BirthDeath` tree-prior semantics
- **Example:** `testBirthDeathModel10Taxa`
- **Observed:** BEAST 3 leaves `BirthDeathGernhard08Model` at its default
  `UNSCALED` tree type, while BEAST X explicitly constructs
  `TreeType.LABELED`.
- **Impact:** the tree-prior density and absolute prior/posterior logs differ
  by a taxon-count-dependent coefficient. For a fixed set of ten taxa the
  difference is the constant `9 * log(2) - logGamma(10)`, approximately
  `-6.5635`, so normalized parameter/tree marginal shapes are unchanged.
- **Root cause:** PhyloSpec does not currently declare a tree-type argument,
  and the two backend tiles independently selected different defaults.
- **Current validation rule:** compare both the marginal shapes and the
  absolute tree-prior decomposition. No engine-specific constant shift is
  expected after the fix.
- **Decision:** PhyloSpec `BirthDeath` uses the `UNSCALED` tree-density
  convention. This matches the BEAST 3 default and reference example, and it
  is consistent with the existing BEAST X `Yule` tile. `LABELED` is not a
  biologically different process for a fixed taxon set; it adds a
  taxon-count-dependent combinatorial coefficient relative to a different
  density measure.
- **Fix:** construct the BEAST X direct model with `TreeType.UNSCALED` and
  emit `type="UNSCALED"` in BEAST X XML.
- **Regression coverage:** the focused BEAST X tree-prior test checks both
  the direct model's zero unscaled tree coefficient and the exported XML
  attribute.
- **Five-path evidence:** the corrected `testBirthDeathModel10Taxa` runs all
  reach 10,000,000 states. The shared parameter and tree marginals no longer
  split by engine. BEAST X has lower ESS for this prior-only model, so its
  heavy-tail sample means remain noisier than BEAST 3.

### VAL-017 — BEAST X cannot bind a tree distribution with `observed as`

- **Status:** open; benchmark workaround applied
- **Area:** BEAST X observation binding for tree distributions
- **Example:** `testBirthDeathAsYule`
- **Observed:** BEAST 3 can bind `Tree tree ~ Yule(...) observed as treeData`
  when `treeData` is produced by `fromNewick`. BEAST X rejects the same
  distribution statement with `Unsupported operation`.
- **Impact:** a fixed-tree cross-engine density comparison cannot currently
  be expressed through one shared PhyloSpec source.
- **Workaround:** the controlled benchmark samples the tree in all five
  paths. The aligned BEAST 3 reference uses zero-death, complete-sampling
  Birth-Death while the four PhyloSpec paths use Yule.
- **Remaining work:** define whether observed tree distributions are an
  intended cross-backend language feature. If so, add BEAST X binding,
  state-membership, XML-export, and no-tree-operator regression tests.

### VAL-018 — BEAST X prior-only Yule tree scale mixes poorly

- **Status:** fixed and validated across all five paths
- **Area:** BEAST X default tree-operator schedule
- **Example:** `testBirthDeathAsYule`
- **Observed:** the three BEAST 3 paths agree with one another and the two
  BEAST X paths agree with one another, but the BEAST X tree-height and
  tree-length samples remain shifted toward their initial balanced-tree
  scale. BEAST X tree-height means are approximately `3.70–3.92`, compared
  with `3.13–3.18` for BEAST 3.
- **Sampling evidence:** posterior ESS is approximately `650–760` for BEAST X
  and `2,770–3,130` for BEAST 3. The BEAST X operator schedule contains node
  height, exchange, subtree-slide, and Wilson–Balding moves, but no explicit
  whole-tree scale operator. The BEAST X starting tree has height `4.0`.
- **Interpretation:** this is consistent with inefficient exploration of the
  global tree scale, not a direct/XML serialization disagreement. It is not
  yet proof that the target density differs.
- **Root cause found:** `treeScaleWeight` and `treeScaleFactor` were accepted
  by `BeastXState.OperatorConfig`, but neither the direct `OperatorBuilder`
  nor `OperatorXmlBuilder` used them. Consequently the BEAST X schedules had
  no joint scale move for all internal node heights.
- **Fix:** add a scale-all `ScaleOperator` over
  `tree.allInternalNodeHeights` to both direct and XML schedules, using the
  configured tree-scale weight and scale factor. Scaling a single element of
  this compound parameter is invalid because its node-height bounds depend on
  adjacent nodes. The XML therefore also declares `scaleAll="true"` and
  `ignoreBounds="true"`; the operator checks the resulting bounds after the
  joint move. Focused tests execute the direct proposal and verify the
  exported XML configuration.
- **Validation:** after regenerating and rerunning both BEAST X paths with
  seed `1234`, all five paths agree. The `birthRate` means span
  `1.628–1.691`, tree-height means span `3.076–3.181`, and tree-length means
  span `14.28–14.87`. BEAST X direct and XML generate the same operator
  schedule, and the new whole-tree scale operator has an acceptance
  probability of approximately `0.234` in both paths.
- **Conclusion:** the earlier displacement was caused by poor global
  tree-scale mixing rather than an observed cross-engine target-density
  difference.

## Current follow-up queue

1. Commit the locally fixed BEAST 3 subset semantics with focused tests.
2. Commit the locally fixed BEAST X constrained-GTR XML export with focused
   tests.
3. Move the multi-tree logger workaround from validation into the generic
   BEAST X backend.
4. Add a focused BEAST 3 indexed-materialization regression before changing
   expansion behavior.
5. Put VAL-013 in a small standalone PR after regenerating BEAST 3 XML and
   confirming the five-path prior/likelihood decomposition.
6. Design stochastic tip-age semantics and a cross-engine sampled-tip-date
   regression before claiming full coverage of BEAST 2 `testTipDates2.xml`.
7. Add focused literal optional-input coverage for BEAST 3 `BirthDeath`
   sampling probability before changing input coercion.
8. Decide whether observed tree distributions are a supported cross-backend
   feature, then add the BEAST X binding and XML-export regression described
   in VAL-017.
