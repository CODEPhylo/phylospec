# PhyloSpec command line with BEAST 3

The CLI ZIP provides one command for checking and running a `.phylospec`
script with BEAST 3. It includes the PhyloSpec runtime, the base BEAST integration,
and their Java dependencies. The macOS Apple Silicon ZIP also includes BEAGLE's
native CPU libraries. Only BEAST 3 is supported by this entry point.
The examples demonstrate supported workflows; they do not establish support for
every model or constitute complete scientific analyses.

## First run: HKY and Yule

Follow steps 1–4 in the **same terminal session** on macOS or Linux. The commands
create separate installation and analysis directories under your home directory;
`mktemp -d` gives each a unique name. The directory paths are printed below so you
can find them later. Stop at any error before continuing to the next step.
The existing local acceptance was on macOS. Linux and Windows still require their
own execution checks; shell syntax or a supplied launcher alone is not platform
validation.

### 1. Install and check the launcher

Install Java **25** and make it available on `PATH`. If `JAVA_HOME` is set, it must
point to that Java installation: the launcher uses `JAVA_HOME` in preference to
`PATH`. Obtain a CLI ZIP from the project maintainers, or follow
[Build the distribution from source](#build-the-distribution-from-source).
There is no published download URL assumed by this guide. Once you have the ZIP,
running it does not require Maven or a source checkout.

Choose the ZIP for your platform:

| ZIP suffix | Included calculation backend |
| --- | --- |
| `-cli-macos-arm64.zip` | macOS Apple Silicon: bundled BEAGLE CPU. Requires ARM64 Java 25 or newer; no separate BEAGLE installation, Homebrew, or library-path configuration is needed. |
| `-cli.zip` | Generic Java distribution. BEAST can use its Java implementation without BEAGLE. Native BEAGLE libraries are not included. |

Set `PHYLOSPEC_ZIP` to your ZIP's actual location. These commands use the current
`0.0.1-SNAPSHOT` macOS Apple Silicon distribution. For the generic distribution,
including on Linux, replace `-cli-macos-arm64.zip` with `-cli.zip` before running
the commands. Change `PHYLOSPEC_VERSION` if your archive has a different version.

```sh
java -version
PHYLOSPEC_VERSION="0.0.1-SNAPSHOT"
PHYLOSPEC_ZIP="$HOME/Downloads/phylospec-beast3-$PHYLOSPEC_VERSION-cli-macos-arm64.zip"
PHYLOSPEC_INSTALL="$(mktemp -d "$HOME/phylospec-install.XXXXXX")"
unzip "$PHYLOSPEC_ZIP" -d "$PHYLOSPEC_INSTALL"
PHYLOSPEC_DIR="$PHYLOSPEC_INSTALL/phylospec-beast3-$PHYLOSPEC_VERSION"
chmod +x "$PHYLOSPEC_DIR/bin/phylospec"
"$PHYLOSPEC_DIR/bin/phylospec" --version
"$PHYLOSPEC_DIR/bin/phylospec" --help
printf 'Installation: %s\n' "$PHYLOSPEC_DIR"
```

`--version` should report PhyloSpec and the BEAST runtime; `--help` lists `check`
and `run`. The installation contains `bin/`, `lib/`, `examples/`, `USAGE.md`, and
`LICENSE`. The macOS Apple Silicon ZIP additionally contains `native/macos-arm64/`
with BEAGLE libraries, version/build information, and its license. Move the whole
installation directory together. The bundled backend uses the CPU; it does not
include GPU support or libraries for other platforms. The generic ZIP still runs
this example without a separate BEAGLE installation.

### 2. Prepare your script and data

Copy the bundled script and alignment into your own analysis directory:

```sh
PHYLOSPEC_PROJECT="$(mktemp -d "$HOME/phylospec-hky-yule.XXXXXX")"
mkdir "$PHYLOSPEC_PROJECT/data"
cp "$PHYLOSPEC_DIR/examples/hky-yule.phylospec" "$PHYLOSPEC_PROJECT/hky-yule.phylospec"
cp "$PHYLOSPEC_DIR/examples/data/primate-mtDNA.nex" "$PHYLOSPEC_PROJECT/data/primate-mtDNA.nex"
cd "$PHYLOSPEC_PROJECT"
printf 'Analysis directory: %s\n' "$PHYLOSPEC_PROJECT"
ls -lh hky-yule.phylospec data/primate-mtDNA.nex
cat hky-yule.phylospec
```

Your files should have this layout:

```text
phylospec-hky-yule.<unique-name>/
  hky-yule.phylospec
  data/
    primate-mtDNA.nex
```

The script reads `fromNexus("data/primate-mtDNA.nex")`. That path is relative to the
**script's directory**, regardless of where the terminal is. Keep the script and
`data/` together when moving your analysis. You can edit this copy in your text
editor; no changes are needed for this first run.

### 3. Check and run

```sh
"$PHYLOSPEC_DIR/bin/phylospec" check "$PHYLOSPEC_PROJECT/hky-yule.phylospec"
```

Continue when you see `Check passed`:

```sh
PHYLOSPEC_RUN="$PHYLOSPEC_PROJECT/results/run-01"
"$PHYLOSPEC_DIR/bin/phylospec" run "$PHYLOSPEC_PROJECT/hky-yule.phylospec" \
  --out "$PHYLOSPEC_RUN"
```

`check` validates syntax, static types, and available BEAST mappings. It does not
run MCMC or validate all data and native initialization. Some checks can inspect
inputs while resolving their types. `run` reuses these checks,
builds the native model, and runs MCMC. Both default to the base library `beast2`
(the existing internal ID for this BEAST integration).

`run` reports four stages: checking, model construction and initialization, MCMC,
and output verification. It also reports the initialized likelihood backend for
this example: expect `BEAGLE` with a CPU resource when using the macOS Apple Silicon
ZIP. Wait for `Run completed` and the result paths. If BEAGLE is unavailable, a note
says BEAST will try its Java implementation; this note alone does not mean the run
failed or prove fallback succeeded. A successful Java fallback does not confirm
BEAGLE is working. Other warnings and errors remain visible.

A failed run reports its error and, when available, the directory containing its
run record and diagnostic log. It does not print `Run completed`. Check the reported
script/data path and read `console.log` for details. `--debug` adds raw BEAST output
and failure stack traces to the terminal.

### 4. View the results

```sh
ls -lh "$PHYLOSPEC_RUN" "$PHYLOSPEC_RUN/output"
head -n 12 "$PHYLOSPEC_RUN/run.json"
head -n 6 "$PHYLOSPEC_RUN/output/hky-yule.log"
tail -n 3 "$PHYLOSPEC_RUN/output/hky-yule.log"
```

In `run.json`, a successful run has `"status" : "succeeded"` and
`"completionConfirmed" : true`. The trace's first line names the columns; subsequent
lines are samples. For this script, expect **31 samples, numbered 0–30**, and 31
trees. Column order can vary; identify quantities by their column names.

`run.json` also records `runtime.likelihoodBackends` for likelihoods constructed by
the base `PhyloCTMC` adapter. Each entry identifies the actual `BEAGLE` or `Java`
implementation; BEAGLE entries include its version, resource number, name, and
flags. For the bundled CPU backend, expect version `4.0.1` and `PROCESSOR_CPU` in
the resource flags. To inspect the recorded backend:

```sh
sed -n '/"likelihoodBackends"/,/]/p' "$PHYLOSPEC_RUN/run.json"
```

This records the backend that initialized. Native library files, `--version`, and
a successful `check` alone do not confirm its activation. The reporting covers the
base `PhyloCTMC` mapping, not arbitrary likelihoods supplied by external adapters.

| File inside `results/run-01/` | What it contains |
| --- | --- |
| `output/hky-yule.log` | Tab-separated samples: posterior, prior, likelihood, `kappa`, tree summaries, and four base frequencies. |
| `output/hky-yule.trees` | Sampled trees in NEXUS format. |
| `output/hky-yule.state.xml` | Final MCMC state. The CLI does not yet provide a resume command. |
| `run.json` | Run status, effective settings, seed, input references, output paths, and actual base `PhyloCTMC` likelihood backends. |
| `console.log` | Java/BEAST console output, including original diagnostic messages and failure details. |
| `model.phylospec` | Snapshot of the script actually used. Input data are not copied into the result directory. |

To inspect the diagnostic log:

```sh
tail -n 30 "$PHYLOSPEC_RUN/console.log"
```

The example runs **30 MCMC steps with seed 12345 and logs every step**. This verifies
the setup and produces inspectable results; it does not establish convergence or
provide enough samples for scientific conclusions. Chain length, logging interval,
seed, and the `hky-yule` output prefix are in the script's `mcmc` block.

To run again, choose a new result directory. Existing results are never overwritten:

```sh
PHYLOSPEC_RUN="$PHYLOSPEC_PROJECT/results/run-02"
"$PHYLOSPEC_DIR/bin/phylospec" run "$PHYLOSPEC_PROJECT/hky-yule.phylospec" \
  --out "$PHYLOSPEC_RUN"
```

Use another unused name for later runs. In a new terminal session, set
`PHYLOSPEC_DIR` and `PHYLOSPEC_PROJECT` to the printed installation and analysis
paths, then set `PHYLOSPEC_RUN` to the result directory you want to inspect.

### What this example models

`hky-yule.phylospec` uses `data/primate-mtDNA.nex`: 12 primate
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

The trace's `baseFrequencies.1` through `.4` columns correspond to A/C/G/T,
respectively. A short chain need not update every coordinate; the four frequencies
are sampled jointly as a simplex whose entries sum to one.

## Other quick examples and Windows launcher

`jc69-coalescent.phylospec` uses JC69, a constant-population coalescent, and a
synthetic alignment. `normal.phylospec` is a prior-only example with no input file.
Both run 30 steps with a fixed seed and log every 10 steps. To try JC69 with the
same installation:

```sh
"$PHYLOSPEC_DIR/bin/phylospec" check "$PHYLOSPEC_DIR/examples/jc69-coalescent.phylospec"
"$PHYLOSPEC_DIR/bin/phylospec" run "$PHYLOSPEC_DIR/examples/jc69-coalescent.phylospec" \
  --out "$PHYLOSPEC_PROJECT/results/jc69-demo"
```

The Windows launcher is supplied in the generic ZIP; Windows execution needs its
own platform validation. The tutorial above uses macOS/Linux shell syntax. After
extracting the generic ZIP and copying the script and `data/` to an analysis folder, the equivalent
Command Prompt commands are:

```bat
set "PHYLOSPEC_DIR=C:\path\phylospec-beast3-0.0.1-SNAPSHOT"
set "PHYLOSPEC_PROJECT=C:\path\my-hky-analysis"
"%PHYLOSPEC_DIR%\bin\phylospec.cmd" --version
"%PHYLOSPEC_DIR%\bin\phylospec.cmd" --help
"%PHYLOSPEC_DIR%\bin\phylospec.cmd" check "%PHYLOSPEC_PROJECT%\hky-yule.phylospec"
"%PHYLOSPEC_DIR%\bin\phylospec.cmd" run "%PHYLOSPEC_PROJECT%\hky-yule.phylospec" --out "%PHYLOSPEC_PROJECT%\results\run-01"
```

## Run records and completion

The script snapshot is the source actually read. `run.json` records its hash,
runtime and selected component/library identities, effective settings, seeds,
and expected/generated output files. Input hashes cover files reported through
the runner's input-path resolver. Input data are not copied, so the result
directory is not a self-contained analysis archive.

When local adapter bundles are supplied, `runtime.adapterBundles` records their
paths, JAR hashes, sizes, and whether each JAR was added or skipped. This inventory
is recorded before execution; it is not a list of every class used. Selected
providers and their actual code sources are recorded separately under `libraries`.
Bundle files are not copied into the result directory.

Status normally progresses through `preparing` and `running` to `succeeded`,
`failed`, or `cancelled`. After abrupt termination, the record can remain
nonterminal or become `unconfirmed`; this does not establish that the process is
still running. Failed/cancelled runs preserve partial files. Confirm success using
both `"status" : "succeeded"` and `"completionConfirmed" : true`.

## Paths and command options

Put a script wherever convenient. Relative input filenames resolve from the
**script's directory**, regardless of the terminal's working directory. For example,
`fromNexus("data/alignment.nex")` looks for a `data/` directory beside the script.

`--out` names the exact run directory. A relative value resolves from the terminal's
working directory. The directory must be absent or empty; an existing result is
never overwritten. Without `--out`, a unique directory is created beside the script
at `runs/<script-name>-<unique-id>/`.

`--run-name` changes the output prefix within `output/`; the default is the script's
filename without `.phylospec`. A script `outputPrefix` overrides it. Explicit logger
filenames also resolve inside `output/`. Absolute output paths, paths escaping this
directory, and conflicting output filenames are rejected. To repeat the example,
choose a new `--out` directory, such as `results/run-02`.

| Option | Meaning |
| --- | --- |
| `--engine beast3` | Select the supported engine; `beast3` is already the default. |
| `--library <id[,id...]>` | Select tile libraries available on the runtime classpath, in preference order. |
| `--adapter-path <directory>` | For `check`/`run`: load JARs directly inside an unpacked local adapter bundle. Repeat for additional bundles. |
| `--out <directory>` | For `run`: exact directory for this run's record, logs, and outputs. |
| `--run-name <prefix>` | For `run`: relative output prefix, unless the script sets `outputPrefix`. |
| `--debug` | Show raw BEAST console output and failure stack traces in the terminal. |
| `-h`, `--help` | Show commands and options. |
| `--version` | Show PhyloSpec and loaded BEAST runtime versions. |
| `--` | Treat the remaining argument as the script filename, including a name beginning with `-`. |

`check` and `run` default to `beast2`, the base integration's existing internal
library ID. When libraries are explicitly selected, their order is preserved and
`beast2` is appended only if absent. `--library` selects an available provider; it
does not download models or add JARs to the classpath.

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

The PopFunc bundle includes `examples/gompertz.phylospec`, its data, and
`examples/README.md`. That guide explains the model definition, Java mapping,
package registration, fixed native dependency, build, and CLI loading steps.
In a source checkout, it is at `integrations/beast3/popfunc/examples/README.md`.

## Diagnostics and failure handling

Syntax, type, and mapping diagnostics report the stage and source position when
available. Warnings remain visible even when checking succeeds. A failed run reports
the error and, when available, paths to its run record and console log; it does not
print `Run completed`. Failures before a run directory is prepared may have no
`run.json`.

The terminal omits BEAST's routine object-wrapping diagnostics. If optional BEAGLE
loading fails, it explains that BEAST will try its Java implementation. This note
alone does not mean the analysis failed or prove that fallback succeeded. Original
messages remain in `console.log`; other warnings and errors remain visible.
Use `--debug` to also see unfiltered output and stack traces in the terminal.
The macOS Apple Silicon launcher locates its bundled CPU libraries inside the
installation and selects their native-library path; the generic launcher retains
BEAST's optional native discovery and Java fallback. The native ZIP checks its
platform, Java architecture, and required library files before starting the CLI.
If a bundled file is missing, extract a complete archive instead of interpreting
a Java fallback as successful native installation.
`console.log` captures Java/BEAST console streams, not low-level JVM or native-library
writes directly to the operating system's stderr.

Exit codes are `0` for success, `1` for check/run failure, `2` for invalid command
arguments, and `130` for cancellation caught by the CLI. OS termination signals may
produce a different nonzero code. Successful execution and output checks do not
establish MCMC convergence.

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

The Maven `-Djava.only=true` setting applies to its regression-test JVMs; it is not
automatically passed to the separate JVM launched by the archive tests. The
generic installed launcher retains BEAST's normal backend discovery.

A minimal acceptance pass checks help/version, runs all three bundled examples from
another working directory (including paths with spaces), verifies successful
`run.json` and nonempty output files, rejects an existing output directory, and
reports a missing input file clearly. Run the existing CLI regression tests as
well. Linux and Windows execution require their own platform checks; do not infer
them from a successful macOS run.

### Build the macOS Apple Silicon BEAGLE CPU ZIP

Build on macOS Apple Silicon with ARM64 Java 25, Maven, CMake, and the Xcode
command-line tools:

```sh
mvn -pl integrations/beast3/java -am -Pcli-dist,cli-dist-macos-arm64 \
  -Djsonschema2pojo.skip=true -Dspotless.skip=true verify
```

Do not add `-Djava.only=true` to this native acceptance command: the BEAGLE tests
must initialize the native CPU backend and fail if they fall back to Java. The
profile adds `HkyYuleBeagleIT`, which reuses the fixed-state numerical comparisons
and acceptance/rejection checks against an independent Java reference. Archive
acceptance also checks the backend recorded by the installed CLI after moving
the installation directory, and rejects an installation with a missing CPU plugin.

The command builds both the generic ZIP and
`phylospec-beast3-0.0.1-SNAPSHOT-cli-macos-arm64.zip` in the integration's `target/`
directory; with both profiles enabled, archive acceptance targets the native ZIP.
Run the generic `cli-dist` command above separately to validate the generic ZIP.
The build uses
`integrations/beast3/java/src/main/native/beagle/build-macos-arm64.sh` to download
and checksum a pinned BEAGLE 4.0.1 source archive
(`bf35ad4a28e5f03edaa10dfd77c1b54eee0d9595`), apply the checked-in macOS build/loading
patch, and compile only the JNI bridge, core library, and CPU plugin. The first
build needs network access for this source archive; no existing BEAGLE installation
is used. The ZIP includes the patch, source revision/checksum, and MIT license.
These native build tools are needed by distributors, not by users of the ZIP.

The native libraries resolve dependencies and the CPU plugin from their own
directory, so the extracted installation can be moved without setting loader
environment variables. The native compilation target is macOS 11.0; that target
alone does not establish compatibility with every macOS release or Java build.
Previous local acceptance used macOS 26.5 on Apple Silicon; it does not establish
that a later source snapshot has been retested. Older macOS releases,
downloaded-archive Gatekeeper handling, and other machines still need separate
release acceptance. GPU and other-platform native bundles are outside this scope.
The base HKY example's native acceptance does not establish PopFunc-plus-BEAGLE
compatibility or long-chain convergence.

## Run the Java entry point directly

Developers can also invoke `runner.PhyloSpecCli` without a ZIP. These commands
preserve the source workflow and build a runtime classpath from the same checkout.
Use Java **25** and Maven, starting at the repository root in one terminal session.

```sh
cd /absolute/path/to/phylospec
PHYLOSPEC_SOURCE="$PWD"
java -version
mvn -version

mvn -pl integrations/beast3/java -am \
  -Djava.only=true -Djsonschema2pojo.skip=true -Dspotless.skip=true install

mvn -f "$PHYLOSPEC_SOURCE/integrations/beast3/java/pom.xml" \
  dependency:build-classpath -DincludeScope=runtime \
  "-Dmdep.outputFile=$PHYLOSPEC_SOURCE/integrations/beast3/java/target/runtime-classpath.txt"

PHYLOSPEC_CLASSPATH="$PHYLOSPEC_SOURCE/integrations/beast3/java/target/classes:$(cat "$PHYLOSPEC_SOURCE/integrations/beast3/java/target/runtime-classpath.txt")"

java --enable-native-access=ALL-UNNAMED -Djava.only=true \
  -cp "$PHYLOSPEC_CLASSPATH" runner.PhyloSpecCli --version
java --enable-native-access=ALL-UNNAMED -Djava.only=true \
  -cp "$PHYLOSPEC_CLASSPATH" runner.PhyloSpecCli --help
```

The first Maven command builds and tests the integration and its upstream modules,
then installs their JARs into your local Maven repository. This makes the matching
PhyloSpec dependencies available to the second command, which writes the runtime
classpath. Maven resolves the BEAST dependency version declared by the project.
The build uses the checked-in schema classes and leaves source formatting unchanged.
If you use a separate Maven cache with `-Dmaven.repo.local=...`, use the same cache
for both commands.

`-Djava.only=true` selects BEAST's Java likelihood implementation; no separate
BEAGLE installation is needed for this workflow. It is a JVM property, so the
Maven setting does not carry over to subsequent `java` commands. The commands above
set it explicitly for both. `--enable-native-access=ALL-UNNAMED` permits JNI access
if needed by the loaded runtime; it does not itself select or install an accelerator.

The help text uses `phylospec` as the command name. In this source workflow, replace
that name with the full `java ... runner.PhyloSpecCli` invocation shown above.

For example, with the classpath prepared above:

```sh
java --enable-native-access=ALL-UNNAMED -Djava.only=true \
  -cp "$PHYLOSPEC_CLASSPATH" runner.PhyloSpecCli \
  check "$PHYLOSPEC_SOURCE/integrations/beast3/java/src/main/distribution/examples/hky-yule.phylospec"
```

## Existing invocation without a subcommand

The previous Java invocation remains available for existing callers. After
preparing the classpath above, use a fresh directory for its output:

```sh
PHYLOSPEC_LEGACY_RUN="$(mktemp -d "$HOME/phylospec-legacy.XXXXXX")"
java --enable-native-access=ALL-UNNAMED -Djava.only=true \
  -cp "$PHYLOSPEC_CLASSPATH" runner.PhyloSpecCli \
  --library beast2 --run-name "$PHYLOSPEC_LEGACY_RUN/normal" \
  "$PHYLOSPEC_SOURCE/integrations/beast3/java/src/main/distribution/examples/normal.phylospec"
```

Without `check` or `run`, `--library` is required and its exact order is retained.
This mode keeps the earlier output-prefix and script-output behavior; it does not
create a managed result directory, script snapshot, run record, or console log.
`--out` is unavailable in this mode. Use `run` for the result management described
in this guide. `--adapter-path` is supported only by the `check` and `run`
subcommands.
