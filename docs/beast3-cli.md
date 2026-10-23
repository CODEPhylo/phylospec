# PhyloSpec command line with BEAST 3

This distribution provides one command for checking and running a `.phylospec`
script with BEAST 3. It includes the PhyloSpec runtime, the base BEAST integration,
and their Java dependencies. External model packages need their own PhyloSpec
adapters. The examples below test a small supported workflow; they do not establish
support for every model or constitute complete scientific analyses.

## Install and check the launcher

1. Install Java **25** and check `java -version`. Set `JAVA_HOME` to its installation
   directory, or make its `java` executable available on `PATH`.
2. Obtain a CLI ZIP built from this repository and extract it to a writable
   directory. No published release URL is assumed here. The unpacked directory
   contains `bin/`, `lib/`, `examples/`, `USAGE.md`, and `LICENSE`.
3. Run the launcher using its full path, or add the extracted `bin/` directory to
   `PATH`. An unpacked distribution does not require Maven or a source checkout.

On macOS or Linux, replace the path below with the extracted directory:

```sh
PHYLOSPEC_DIR="/absolute/path/phylospec-beast3-0.0.1-SNAPSHOT"
"$PHYLOSPEC_DIR/bin/phylospec" --version
"$PHYLOSPEC_DIR/bin/phylospec" --help
```

If the extraction tool did not preserve permissions, run
`chmod +x "$PHYLOSPEC_DIR/bin/phylospec"` once. The launcher finds its own `lib/`
directory, so commands can run from another working directory. Keep `bin/` and
`lib/` together when moving the installation.

On Windows, use Command Prompt:

```bat
set "PHYLOSPEC_DIR=C:\path\phylospec-beast3-0.0.1-SNAPSHOT"
"%PHYLOSPEC_DIR%\bin\phylospec.cmd" --version
"%PHYLOSPEC_DIR%\bin\phylospec.cmd" --help
```

The Windows launcher is supplied; actual platform testing must be reported
separately. No native accelerator is required for these bundled examples.

## Check and run an example

Start with the small synthetic alignment example:

```sh
"$PHYLOSPEC_DIR/bin/phylospec" check "$PHYLOSPEC_DIR/examples/jc69-coalescent.phylospec"
"$PHYLOSPEC_DIR/bin/phylospec" run "$PHYLOSPEC_DIR/examples/jc69-coalescent.phylospec" \
  --out "$PWD/results/jc69-demo"
```

Windows equivalents:

```bat
"%PHYLOSPEC_DIR%\bin\phylospec.cmd" check "%PHYLOSPEC_DIR%\examples\jc69-coalescent.phylospec"
"%PHYLOSPEC_DIR%\bin\phylospec.cmd" run "%PHYLOSPEC_DIR%\examples\jc69-coalescent.phylospec" --out "%CD%\results\jc69-demo"
```

`check` validates syntax, static types, and available BEAST mappings. It does not
run MCMC or validate all data and native initialization. `run` reuses these checks,
builds the native model, and runs MCMC. Both default to the base library `beast2`
(the existing internal ID for this BEAST integration).

The example uses JC69, a constant-population coalescent, and an observed synthetic
alignment. `normal.phylospec` is an even smaller prior-only example with no input
file. Both run 30 steps with a fixed seed and log every 10 steps. They are
installation checks, not chains long enough for posterior inference. Chain
length, logging interval, and seed are defined in the script's `mcmc` block.

### Primate HKY and Yule example

Use the same launcher for the bundled HKY and Yule model:

```sh
"$PHYLOSPEC_DIR/bin/phylospec" check "$PHYLOSPEC_DIR/examples/hky-yule.phylospec"
"$PHYLOSPEC_DIR/bin/phylospec" run "$PHYLOSPEC_DIR/examples/hky-yule.phylospec" \
  --out "$PWD/results/hky-yule-demo"
```

`examples/hky-yule.phylospec` uses `examples/data/primate-mtDNA.nex`: 12 primate
taxa and 898 mitochondrial DNA sites, attributed in the Nexus file to Hayasaka,
Gojobori and Horai (1988), *Molecular Biology and Evolution* 5:626–644.
The example uses all sites as one partition; the file's charset declarations and
PAUP constraints are not applied by this script.

The sampled quantities are the tree, HKY `kappa`, and the four A/C/G/T base
frequencies. Their priors are Yule with a fixed birth rate of `1.0`,
`LogNormal(logMean=1.0, logSd=0.5)`, and `Dirichlet([1,1,1,1])`, respectively.
The strict-clock rate is fixed at `1.0`; there is no absolute-time calibration,
Gamma site-rate variation, or invariant-site component. This preserves the model
from the existing HKY and Yule test example.

Keep the script and its `data/` directory together when moving them. Its `mcmc`
block explicitly sets 30 steps, seed `12345`, logging every step, and output prefix
`hky-yule`. These short settings describe an analysis setup, not a converged
posterior analysis. See the script for the complete model and parameter settings.
The result directory contains `model.phylospec`, `run.json`, `console.log`, and
`output/hky-yule.log`, `.trees`, and `.state.xml`. The trace records `kappa`, the
four `baseFrequencies.1` through `.4` values in A/C/G/T order, tree summaries,
and log posterior, log prior, and log likelihood. With these settings there are 31 logged
states (0 through 30). A short chain need not update every coordinate; the four
frequencies are sampled jointly as a simplex whose entries sum to one.

## Script locations and results

Put your script wherever convenient. Keep any relative input files beside it in
the structure used by the script: `data/tiny.nex` resolves relative to the script,
regardless of the terminal's working directory.

`--out` names the exact run directory. A relative value resolves from the terminal's
working directory. It must be absent or empty; an existing run is never overwritten.
Choose a new directory for another run. Without `--out`, the default is
`runs/<script-name>-<unique-id>/` beside the script.

For the example above, a successful run produces:

```text
results/jc69-demo/
  model.phylospec
  run.json
  console.log
  output/
    jc69-coalescent.log
    jc69-coalescent.trees
    jc69-coalescent.state.xml
```

The snapshot is the source actually read; input data are not copied. `run.json`
records source/input hashes, runtime and adapter information, effective settings,
seeds, status, and expected/generated files. `console.log` retains console output
and failure details. Native trace, tree, and state files stay in `output/`.

`--run-name` changes the relative output prefix. A script `outputPrefix` overrides
it. Explicit logger filenames also resolve inside `output/`; absolute paths and
paths escaping that directory are rejected.

Status progresses through `preparing` and `running` to `succeeded`, `failed`, or
`cancelled` when completion can be confirmed. After abrupt termination, the record
can remain nonterminal or become `unconfirmed`; this is not evidence that the
process is still running. Failed/cancelled runs preserve partial files. A successful
run confirms execution and expected output checks, not MCMC convergence. State
files are recorded, but this CLI does not provide a resume command.

Use `--debug` to also print stack traces in the terminal. Exit codes are `0` for
success, `1` for check/run failure, `2` for invalid command arguments, and `130`
for cancellation caught by the CLI. OS termination signals may produce a different
nonzero code. A failure before a run directory is prepared may have no `run.json`.

## Use a local extension

A local adapter bundle is an unpacked directory containing JARs for its PhyloSpec
adapter, component definitions, native model implementation, and dependencies.
A native BEAST model JAR alone does not supply its PhyloSpec mapping. For a prepared
PopFunc bundle and a script using its supported generators:

```sh
"$PHYLOSPEC_DIR/bin/phylospec" check /path/to/popfunc-model.phylospec \
  --adapter-path /path/to/phylospec-adapter --library popfunc
"$PHYLOSPEC_DIR/bin/phylospec" run /path/to/popfunc-model.phylospec \
  --adapter-path /path/to/phylospec-adapter --library popfunc \
  --out "$PWD/results/popfunc-demo"
```

`--adapter-path` locates the bundle; `--library` selects its adapter ID. Supplying a
directory does not automatically select every adapter in it. Paths are relative
to the terminal's working directory and must directly contain JARs; subdirectories
are not searched. Repeat `--adapter-path` for additional bundles. Libraries retain
the requested preference order, with `beast2` appended if absent.

The loader keeps the host PhyloSpec/BEAST runtime, skips bundle copies of it, and
reports detected conflicts or missing dependencies. Bundles must be compatible
with the host runtime. It loads local executable Java code; it does not download
or install packages. For a run, bundle paths and JAR hashes are recorded alongside
the actual selected providers. Restart the command after rebuilding an adapter.

## Build the distribution from source

Developers need Java 25 and Maven. From the repository root:

```sh
mvn -pl integrations/beast3/java -am -Pcli-dist \
  -Djava.only=true -Djsonschema2pojo.skip=true -Dspotless.skip=true verify
```

For version `0.0.1-SNAPSHOT`, the archive is
`integrations/beast3/java/target/phylospec-beast3-0.0.1-SNAPSHOT-cli.zip`.
The version in the filename follows the Maven project version. Dependencies must
already be cached for an offline build (`mvn -o ...`). Extract the ZIP into a fresh
directory and use its launcher for acceptance; running classes from the source
tree alone does not test distribution completeness.

The `cli-dist` profile builds the ZIP during `package` and runs the archive
acceptance tests during `verify`. Those acceptance tests currently require macOS
or Linux with `unzip`; they are explicitly skipped on Windows. The build command
uses the checked-in schema classes and leaves source formatting unchanged.
The ZIP retains dependencies as separate JARs, including their embedded metadata.
`LICENSE` covers PhyloSpec; dependencies retain their own licenses.

A minimal acceptance pass checks help/version, runs all three bundled examples from
another working directory (including paths with spaces), verifies successful
`run.json` and nonempty output files, rejects an existing output directory, and
reports a missing input file clearly. Run the existing CLI regression tests as
well. Local extension compatibility and Windows execution require their own
specific checks; do not infer them from a successful base-model run.
