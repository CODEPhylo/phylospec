# BEAST X Test Structure

This document explains how the PhyloSpec BEAST X tests are organized and what each layer is intended to prove.

The test suite should distinguish four different questions:

1. Can a PhyloSpec script be parsed, type-resolved, and tiled?
2. Can the tiles build a coherent `BeastXState` / `BeastXModel` intermediate representation?
3. Can selected models be converted into runnable BEAST X Java objects and execute MCMC?
4. Can selected models be exported to XML and parsed or executed through the BEAST X XML path?

These layers should stay conceptually separate. A tiling test passing does not necessarily prove MCMC execution, and an object-level MCMC test does not necessarily prove XML-level execution.

## Layer 1: Script Tiling Regression

These tests scan `.phylospec` scripts under `src/test/java/tiling` and compare actual tile selection with metadata embedded in each script.

- `tiling.TilingScriptFilesTest`

This layer answers:

- Does each script select the expected tile sequence?
- Does a script intentionally fail with the expected tiling or application error?

The expected metadata is stored directly in each `.phylospec` file:

```text
// EXPECTED_TILES
// TILING_SUCCESS
// EXPECTED_TILES
```

This layer is broad and data-driven. It is the best place for small tile examples.

## Layer 2: BEAST X State Construction

These tests verify that `.phylospec` scripts build the expected BEAST X intermediate state.

- `tiling.BeastXStateScriptFilesTest`
- `PhyloSpecRunnerTest`

This layer answers:

- Which state nodes were created?
- Which priors were attached?
- Which tree models, tree priors, likelihoods, and calculation nodes exist?
- Did a failed construction produce `NO_STATE` when expected?

The expected metadata is stored directly in `.phylospec` files:

```text
// EXPECTED BEASTX STATE
// SN: clockRate
// P: clockRate_prior
// TM: tree
// TP: tree_prior
// L: alignment_likelihood
// EXPECTED BEASTX STATE
```

This layer checks the backend intermediate representation, not full MCMC execution.

## Layer 3: Model Summary and Representative Coverage

These tests verify that non-trivial PhyloSpec models produce coherent `BeastXModelSummary` output.

- `BeastXRepresentativeModelsTest`
- `BeastXShowcaseModelsTest`
- `BeastXCoverageTableTest`

Representative scripts are organized under:

```text
src/test/java/tiling/representative/coverage
src/test/java/tiling/representative/showcase
```

Use `coverage` for focused examples that cover one model axis:

- substitution model
- tree prior
- clock model
- site model
- partitioning
- calibration
- MCMC configuration

Use `showcase` for larger, non-trivial model combinations:

- dated-tip FBD with relaxed clock and GTR
- partitioned GTR/HKY site-clock model
- joint molecular and discrete-trait model
- skyline/coalescent demographic model

This layer is intended for project-level confidence and communication. It should not be overloaded with every small tile edge case.

## Layer 4: BEAST X Object-Level MCMC Execution

These tests build BEAST X Java objects and call the BEAST X MCMC engine directly.

- `BeastXMCMCConfigTileTest`
- `BeastXMCMCRepresentativeModelTest`
- `BeastXMCMCRunTest`
- `BeastXShowcaseExecutionTest`

This layer answers:

- Can `PhyloSpecRunner` build a runnable BEAST X `MCMC` object?
- Does the MCMC run complete for selected models?
- Are parameter logs written?
- Are tree logs written?
- Are logs non-empty and do they contain expected columns or tree samples?

This is object-level execution:

```text
PhyloSpec script
-> BeastXState
-> BeastXModel
-> BEAST X Java objects
-> MCMC execution
-> .log / .trees output
```

It is not XML-level execution.

Execution outputs are generally written under:

```text
target/showcase-execution
target/mcmc-run-smoke
target/mcmc-logger-smoke
target/materialized-phylctmc-mcmc-smoke
```

Older output directory names may still contain `smoke`. New test names and reports should prefer `execution`.

## Layer 5: Materialized PhyloCTMC Execution

These tests verify whether `PhyloCTMC` likelihood specifications can be materialized into actual BEAST X likelihood objects.

- `BeastXPhyloCTMCMaterializationTest`
- `BeastXMaterializedPhyloCTMCMCMCTest`

This layer answers:

- Can `BeastXPhyloCTMCLikelihoodSpec` be materialized?
- Can the resulting likelihood object be evaluated?
- Can an MCMC run include a materialized likelihood?

Some tests depend on the native BEAGLE library. If BEAGLE is unavailable, BEAGLE-dependent checks may be skipped.

## Layer 6: XML Export and XML Execution

These tests validate the current BEAST X XML pipeline.

- `BeastXStateXmlGeneratorTest`
- `BeastXXmlTest`

This layer answers:

- Can selected `BeastXModel` objects be converted into a BEAST X XML plan?
- Can `BeastXStateXmlGenerator` write that plan as BEAST X XML?
- Can the generated XML be parsed by BEAST X XML infrastructure?
- Can selected XML models execute and write parameter or tree logs?

This is separate from object-level execution. Passing object-level execution does not prove XML-level execution.

The current XML pipeline is:

```text
PhyloSpec script
-> BeastXState
-> BeastXModel
-> BeastXXmlPlanBuilder
-> BeastXStateXmlGenerator
-> BEAST X XML
-> BeastXXmlRunner
-> BEAST X MCMC execution
```

The XML implementation classes are:

- `tiling.xml.XmlElement`
- `tiling.xml.XmlPlan`
- `tiling.xml.XmlPlanBuilder`
- `tiling.xml.StateXmlGenerator`
- `tiling.xml.XmlRunner`

The current XML generator supports selected prior-only and tree-prior models:

- scalar state parameters
- `LogNormal` scalar priors
- `Beta` scalar priors
- `Yule` tree priors
- `BirthDeath` tree priors
- constant-population `Coalescent` tree priors
- parameter loggers
- tree loggers
- XML parsing and execution through BEAST X

PhyloCTMC likelihood XML export is not implemented yet.

## Feature-Specific Tests

These tests focus on individual BEAST X backend features.

- `BeastXAutoOperatorConfigTileTest`
- `BeastXCalibrationPriorTest`
- `BeastXEnvTileTest`
- `BeastXMatrixDimensionTileTest`
- `BeastXOperatorBuilderTest`
- `BeastXRPNCalculationTest`
- `BeastXTipAgeInitialTreeTest`
- `BeastXResultSummaryTest`

These are useful when a feature has behavior that is easier to validate directly than through a `.phylospec` script alone.

Avoid creating a new Java test class for every small tile. Prefer `.phylospec` script tests unless direct Java inspection is genuinely needed.

## Script Directory Guide

Use this convention for `.phylospec` scripts:

```text
src/test/java/tiling/<feature>
```

Examples:

```text
tiling/input
tiling/functions
tiling/distributions
tiling/treepriors
tiling/substitutionmodels
tiling/sitemodels
tiling/branchmodels
tiling/mcmc
tiling/rpn
tiling/representative/coverage
tiling/representative/showcase
```

Small feature examples belong in feature folders. Larger models belong in `representative`.

## Naming Guidance

Prefer names that describe the validation level:

- `Tiling...` for tile selection tests
- `State...` for `BeastXState` construction tests
- `Model...` or `Representative...` for `BeastXModelSummary` tests
- `Execution...` for object-level BEAST X MCMC execution tests
- `Xml...` for XML export or XML execution tests

Avoid new test names using `Smoke`. It is not precise enough for this project.

## Recommended Commands

Run fast tiling and state checks:

```bash
mvn -pl integrations/beastx/java "-Dtest=TilingScriptFilesTest,BeastXStateScriptFilesTest" test
```

Run representative model construction checks:

```bash
mvn -pl integrations/beastx/java "-Dtest=BeastXRepresentativeModelsTest,BeastXShowcaseModelsTest" test
```

Run selected object-level MCMC execution checks:

```bash
mvn -pl integrations/beastx/java "-Dtest=BeastXShowcaseExecutionTest,BeastXMCMCRunTest" test
```

Run materialized PhyloCTMC checks:

```bash
mvn -pl integrations/beastx/java "-Dtest=BeastXPhyloCTMCMaterializationTest,BeastXMaterializedPhyloCTMCMCMCTest" test
```

Run XML-related checks:

```bash
mvn -pl integrations/beastx/java "-Dtest=BeastXStateXmlGeneratorTest,BeastXXmlTest" test
```

Run the full BEAST X module test suite:

```bash
mvn -pl integrations/beastx/java test
```

## Current Interpretation

The BEAST X backend should be evaluated across multiple levels:

- tile coverage
- intermediate model construction
- representative model structure
- object-level MCMC execution
- optional XML-level validation

The current implementation already supports many BEAST X model components and selected object-level MCMC execution. The next organizational goal is to keep tests layered, reduce duplicated Java test classes, and use representative models to demonstrate meaningful Bayesian phylogenetic workflows.
