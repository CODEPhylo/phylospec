# BEAST X Test Structure

This document summarizes the purpose of the current PhyloSpec BEAST X test suite.

## Core Runner Tests

These tests verify the basic `PhyloSpecRunner` API and make sure PhyloSpec scripts can be parsed, tiled, and converted into BEAST X model state.

- `PhyloSpecRunnerTest`

## Tiling Regression Tests

These tests scan `.phylospec` scripts under `src/test/java/tiling` and compare the actual tiling result with the expected metadata embedded in each script.

- `tiling.TilingScriptFilesTest`
- `tiling.BeastXStateScriptFilesTest`

These are broad regression tests. They are intentionally data-driven and cover many small tile examples.

## Representative Model Tests

These tests verify that non-trivial Bayesian phylogenetic models can be constructed from PhyloSpec into BEAST X model summaries.

- `BeastXRepresentativeModelsTest`
- `BeastXShowcaseModelsSmokeTest`
- `BeastXExpressivenessReportTest`

Representative scripts are organized into:

- `tiling/representative/coverage`
- `tiling/representative/showcase`

`coverage` examples focus on individual model dimensions.  
`showcase` examples demonstrate more complex, non-trivial model combinations.

## MCMC Tests

These tests verify MCMC configuration, logger construction, and direct runtime execution through `PhyloSpecRunner`.

- `BeastXMCMCConfigTileTest`
- `BeastXMCMCRepresentativeModelTest`
- `BeastXMCMCRunSmokeTest`
- `BeastXShowcaseRuntimeSmokeTest`

These tests cover:

- chain length
- screen logger
- file logger
- tree logger
- selected parameter logging
- direct `runMCMC` execution

## Materialization Tests

These tests verify whether constructed PhyloCTMC likelihood specifications can be materialized into BEAST X likelihood objects.

- `BeastXPhyloCTMCMaterializationSmokeTest`
- `BeastXMaterializedPhyloCTMCMCMCSmokeTest`

Some of these tests depend on the native BEAGLE library. If BEAGLE is not available locally, the relevant tests may be skipped.

## Operator Tests

These tests verify automatic MCMC operator construction for BEAST X state nodes and tree models.

- `BeastXOperatorBuilderTest`

## Feature-Specific Tests

These tests verify specific BEAST X feature extensions.

- `BeastXCalibrationPriorSmokeTest`
- `BeastXMatrixDimensionTileTest`
- `BeastXRPNCalculationSmokeTest`
- `BeastXTipAgeInitialTreeTest`

## Recommended Test Commands

Run fast construction and tiling checks:

```bash
mvn -pl integrations/beastx/java "-Dtest=BeastXRepresentativeModelsTest,TilingScriptFilesTest,BeastXStateScriptFilesTest" test
```

Run materialization checks:

```bash
mvn -pl integrations/beastx/java "-Dtest=BeastXPhyloCTMCMaterializationSmokeTest,BeastXMaterializedPhyloCTMCMCMCSmokeTest" test
```

Run the full BEAST X module test suite:

```bash
mvn -pl integrations/beastx/java test
```

## Notes

The current test suite intentionally separates construction-level support from runtime execution.

Construction-level tests verify that PhyloSpec scripts can be translated into valid BEAST X model structures.

Runtime tests verify that selected BEAST X models can actually execute MCMC and write logs.

BEAGLE-dependent likelihood materialization is environment-sensitive because it requires the native BEAGLE library.

