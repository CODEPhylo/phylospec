# Gompertz F0 with the PhyloSpec CLI

This example estimates `initialPopulationSize`, `growthRate`, `initialProportion`,
`ancestralPopulationSize`, and the tree. It uses a Gompertz F0 coalescent, JC69,
and a fixed strict-clock rate of `0.01`. The four-taxon, 16-site alignment is
synthetic. The model and data come from the existing PhyloSpec UI PopFunc example;
only the input path changes so this example has its own data directory.

The chain has 30 steps, seed `12345`, and logs every step. This checks execution
and output, not posterior convergence or the PopFunc paper's analyses. A short
chain need not propose or accept an update to every parameter.

## Initial state

The BEAST 3 LogNormal Tiles use the prior median as the starting value:
`exp(logMean)`, or `exp(log(mean) - 0.5 * logSd * logSd)` for the real-space
parameterization. Beta retains its starting value of `0.5`. This example therefore
starts with `initialPopulationSize = exp(5)`, `growthRate = exp(-0.95)`,
`initialProportion = 0.5`, and `ancestralPopulationSize = exp(3)`.
These are starting values for sampling; the model, priors, and domains are unchanged.
If a median is outside the positive finite `double` range, only the starting value
is limited to the nearest representable endpoint; the prior parameters are retained.

Previously, both population sizes started at the hard-coded value `0.5`, for which
the native Gompertz implementation returned `NaN` at time zero. The regression
tests now use the example's actual defaults, without manually resetting its
parameters, and check `N(0) = initialPopulationSize`, population function values,
and acceptance/rejection state restoration against fresh native objects.
Prior medians do not guarantee valid joint initialization for every model or
parameter combination; this acceptance covers the supplied example.

## Run the packaged example

Use Java 25. Extract matching CLI and PopFunc adapter ZIPs, then set their paths:

```sh
PHYLOSPEC_DIR="/absolute/path/to/phylospec-beast3-0.0.1-SNAPSHOT"
POPFUNC_DIR="/absolute/path/to/phylospec-adapter"

"$PHYLOSPEC_DIR/bin/phylospec" check "$POPFUNC_DIR/examples/gompertz.phylospec" \
  --adapter-path "$POPFUNC_DIR" --library popfunc

"$PHYLOSPEC_DIR/bin/phylospec" run "$POPFUNC_DIR/examples/gompertz.phylospec" \
  --adapter-path "$POPFUNC_DIR" --library popfunc \
  --out "$PWD/results/gompertz"
```

The script resolves `data/four-taxa.nex` beside itself. The result directory must
be absent or empty. A successful run contains `model.phylospec`, `run.json`,
`console.log`, and `output/analysis.log`, `analysis.trees`, `analysis.state.xml`.
The trace includes all four population parameters, prior, likelihood, and
posterior, with samples 0 through 30. `run.json` records the actual provider JARs,
bundle hashes, input hash, settings, and completion status.

## Build and verify this example

From the PhyloSpec repository root, with Java 25, Maven, and the required BEAST
and PopFunc dependencies available:

```sh
mvn -Pcli-dist,popfunc -pl integrations/beast3/popfunc -am \
  -Djava.only=true -Djsonschema2pojo.skip=true -Dspotless.skip=true verify
```

This builds the CLI ZIP in `integrations/beast3/java/target/` and the adapter ZIP
in `integrations/beast3/popfunc/target/`. The adapter ZIP includes this `examples/`
directory. The regression tests check parameter bindings and numerical state
restoration; the distribution test extracts both ZIPs outside the repository and
executes the packaged `check` and `run` commands. The distribution test uses the
POSIX launcher and `unzip`, so it requires macOS or Linux; it does not establish
Windows support. Offline builds (`-o`) require cached dependencies.
