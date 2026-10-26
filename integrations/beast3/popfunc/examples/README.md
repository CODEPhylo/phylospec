# Gompertz F0: CLI example and developer walkthrough

This example estimates `initialPopulationSize`, `growthRate`, `initialProportion`,
`ancestralPopulationSize`, and the tree. It uses a Gompertz F0 coalescent, JC69,
and a fixed strict-clock rate of `0.01`. The four-taxon, 16-site alignment is
synthetic. The model and data come from the existing PhyloSpec UI PopFunc example;
only the input path changes so this example has its own data directory.

The chain has 30 steps, seed `12345`, and logs every step. This checks execution
and output, not posterior convergence or the PopFunc paper's analyses. A short
chain need not propose or accept an update to every parameter.

To use an existing bundle, start with [Run the packaged example](#run-the-packaged-example).
To understand or extend the adapter, follow [Add a model](#add-a-model),
[Configure a package once](#configure-a-package-once),
[Prepare dependencies](#prepare-dependencies), and
[Build and verify this example](#build-and-verify-this-example).

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

These three choices have different jobs:

| Choice | What it does |
| --- | --- |
| `--adapter-path "$POPFUNC_DIR"` | Makes the JARs directly inside the unpacked directory available to this command. It does not download, install, or automatically select a package. |
| `--library popfunc` | Selects the PopFunc mappings. The CLI also includes the base library, whose existing internal ID is `beast2`; this workflow runs BEAST 3. |
| `use popfunc.functions.coalescent` in the script | Allows short names such as `gompertzF0PopulationFunction`. It does not load JARs. |

The CLI discovers the registered provider, loads its component JSON and mappings,
and uses the same checking and execution pipeline as the base models. `check`
checks syntax, static types, and available mappings; native model construction
and MCMC happen during `run`. Repeat both adapter/library options for `run`.
After rebuilding, extract into a fresh directory and restart the command.

The script resolves `data/four-taxa.nex` beside itself. The result directory must
be absent or empty. A successful run contains `model.phylospec`, `run.json`,
`console.log`, and `output/analysis.log`, `analysis.trees`, `analysis.state.xml`.
The trace includes all four population parameters, prior, likelihood, and
posterior, with samples 0 through 30. `run.json` records the actual provider JARs,
bundle hashes, input hash, settings, and completion status.

## Add a model

Gompertz F0 is already implemented. Use the following files as a worked example;
do not add a second definition of it. Source paths in this section are relative
to `integrations/beast3/popfunc/` in a PhyloSpec checkout. The ZIP includes this
guide and the runnable example, but not the Java source tree.

The native PopFunc class computes the population function. The PhyloSpec adapter
describes its public interface and connects script parameters to that class:

```text
component JSON + Java mapping -> generated Tile -> native Gompertz object
```

A Tile is the small piece of code that builds an engine object for a PhyloSpec
expression. AGT generates this connecting code at compile time. The developer
still chooses the scientific meaning, parameter domains, and any special input
handling.

### 1. Define the script interface

In `src/main/resources/popfunc-components.json`, the generator
`popfunc.functions.coalescent.gompertzF0PopulationFunction` returns
`PopulationFunction`. Its current arguments are:

| Argument | Current type and constraint | Required | Native Java input field |
| --- | --- | --- | --- |
| `initialProportion` | `Probability`, plus `value > 0` | Yes | `f0Input` |
| `growthRate` | `PositiveReal` | Yes | `bInput` |
| `initialPopulationSize` | `PositiveReal` | Yes | `N0Input` |
| `ancestralPopulationSize` | `PositiveReal` | No | `NAInput`, plus an enable/disable input |

For a new model, write its name, namespace, description, return type, arguments,
and applicable constraints. Reuse an existing component if it already describes
the same model and parameterization; otherwise add a package-owned definition.
Gompertz uses its own `popfunc.functions.coalescent` namespace and reuses the
existing `PopulationFunction` type, so no core JSON change is needed.

The example script constructs this function with `=` and passes it to
`Tree tree ~ Coalescent(...)`. The function describes population size through
time; Coalescent supplies the tree distribution. Declaring the JSON interface
alone does not provide its BEAST implementation.

### 2. Connect the interface to the native class

`src/main/java/mappings/popfunc/PopulationFunctionMappings.java` contains this
mapping declaration inside the `PopulationFunctionMappings` class:

```java
@GeneratorMapping(
        component = "popfunc.functions.coalescent.gompertzF0PopulationFunction",
        implementation = popfunc.beast.evolution.populationmodel.GompertzGrowth_f0.class,
        output = PopulationFunction.class)
@InputMapping(
        argument = "initialProportion",
        input = "f0Input",
        adapter = PositiveProbabilityAdapter.class)
@InputMapping(argument = "growthRate", input = "bInput")
@InputMapping(argument = "initialPopulationSize", input = "N0Input")
@InputMapping(argument = "ancestralPopulationSize", input = "NAInput")
@InputMapping(
        argument = "ancestralPopulationSize",
        input = "indicatorParameterInput",
        fallback = IndicatorFallback.class)
public static final class GompertzF0 {}
```

The nested class is a declaration for the compiler; it contains no Gompertz
formula. `input` names a Java field such as `bInput`, not its BEAST input name
`b`. When a field already follows the argument name or `argumentNameInput`
convention, the processor can infer the connection without an explicit rename.

The special handling lives in `src/main/java/adapters/popfunc/`:

| Helper | Why Gompertz uses it |
| --- | --- |
| `PositiveProbabilityAdapter` | Checks positivity when connecting `initialProportion` to the native positive-real input. It preserves the original parameter object and its probability domain, so subsequent MCMC updates remain visible. |
| `AncestralIndicatorAdapter` | Produces indicator `1` when `ancestralPopulationSize` is supplied. The generated Tile separately connects the size to `NAInput`. Its `@AdapterMapping` lets the processor select this conversion by compatible Java types. |
| `IndicatorFallback` | Produces indicator `0` when that optional argument is absent. The generated Tile leaves `NAInput` unset. |

The indicator describes whether the argument was supplied; this example does
not sample it as a separate model-selection parameter. For another model, decide
explicitly what omission means and whether an adapter is needed. A suitable
registered adapter can be selected automatically when the match is unambiguous;
an explicit `adapter = ...`
can identify the intended conversion when necessary. Avoid copying a sampled
parameter's current number into a disconnected object: the native model must
continue to see its updates.

### 3. Reuse generation and supply an example

The annotation processor reads the JSON and mappings, checks the declared
connections, and generates `GompertzF0GeneratedTile` and a
`GeneratedTileRegistry` under `target/generated-sources/annotations/`. The Tile
obtains the parameter objects, creates `GompertzGrowth_f0`, connects its inputs,
and returns it. Edit the JSON/mapping source, not these generated files, and do
not commit `target/`.

The existing PopFunc provider reads the generated registry, so a new generated
Tile does not need a separate manual registry entry. It also has a
`configureState()` hook that obtains package-specific proposal operators from
native PopFunc models. Operators are added only when the state nodes they change
belong to the sampled state. This hook is handwritten package logic, not an
operator scheme inferred by AGT; a model with different needs may require extra
integration work.

For each new model, supply a small script, any required data, and tests for its
bindings and behavior. Reuse these existing examples of what to check:

| Existing file under `src/test/java/tiling/popfunc/` | Responsibility |
| --- | --- |
| `PopFuncCoverageTest.java` | Checks that package generators have implementations and generated Tiles are registered exactly once. |
| `PopFuncEquivalenceTest.java` | Compares Gompertz with directly constructed native objects, with and without ancestral size. |
| `GompertzStateTest.java` | Checks the four parameter bindings, actual initialization, numerical values, and accepted/rejected state restoration. |
| `GompertzCliDistributionIT.java` | Extracts the archives, runs the packaged example, and checks results and run records. |

Compilation and registration checks do not establish numerical equivalence by
themselves. The files above describe existing checks; this tutorial does not
extend their coverage to other models or parameterizations.

## Configure a package once

These pieces already exist for PopFunc. Reuse them when adding another model
whose types and runtime needs fit the existing package. A new package needs its
own IDs, resource names, dependencies, and provider declarations.

| File, relative to the PopFunc integration directory | Configuration supplied by the developer | What then happens automatically |
| --- | --- | --- |
| `src/main/java/tiles/popfunc/PopFuncTileLibrary.java` | ID `popfunc`, `@ComponentSource("/popfunc-components.json")`, access to the generated registry, and the package state hook | The registry includes new generated Tiles; the declared JSON is used at compilation and runtime. |
| `src/main/java/module-info.java` and `src/main/resources/META-INF/services/org.phylospec.tiling.TileLibrary` | Register `tiles.popfunc.PopFuncTileLibrary` for module and classpath use respectively | Java ServiceLoader discovers the provider. The current CLI uses the classpath registration. These declarations are maintained by hand. |
| `pom.xml` | Native dependency, annotation processor, generated package name, and `phylospec.adapter.bundle.skip=false` | Maven generates/compiles Tiles and uses the shared packaging steps from the root POM. |
| `src/main/java/utils/popfunc/PopFuncEngineSpec.java` and its POM execution | Package metadata and dependencies, including the base library's internal ID `beast2` | The build generates `META-INF/phylospec/engines/popfunc.json` from the actual mappings. |
| `src/assembly/adapter.xml` | Include JARs plus the example README, scripts, and data | Maven creates the adapter ZIP. |

The repository root POM enables this module through the `popfunc` profile.
Adding another model inside PopFunc does not require another profile or changes
to the generic CLI/Runner. A new module must be included in its own build setup.

## Prepare dependencies

These source-build steps are for adapter developers. A user with matching CLI
and adapter ZIPs needs Java 25 and the loading commands above; the adapter ZIP
already contains its runtime JAR dependencies.

Use a JDK 25 and Maven; `java -version` and `mvn -version` should both show Java
25. The archive commands below use a POSIX shell and `unzip` on macOS or Linux.
Use the following dependency baseline:

| Dependency | Current coordinate or source |
| --- | --- |
| BEAST 3 runtime | `io.github.compevol:beast-base:2.8.0-beta7` and `io.github.compevol:beast-pkgmgmt:2.8.0-beta7` |
| Native PopFunc models | `io.github.linguaphylo:popfunc-beast:0.1.0-SNAPSHOT` |
| Native PopFunc source | Public repository [LinguaPhylo/PopFunc](https://github.com/LinguaPhylo/PopFunc), commit [`527e214274620fb4c834802fcae21adfbf4f6a3b`](https://github.com/LinguaPhylo/PopFunc/commit/527e214274620fb4c834802fcae21adfbf4f6a3b) (`beast3 migration`) |
| PhyloSpec core, BEAST integration, and processor | Built from this checkout by Maven's `-am` option |

The pinned PopFunc commit's native module uses Java 25 and BEAST `2.8.0-beta7`.
Its Java sources match the source JAR accompanying the native PopFunc JAR used
in the existing Gompertz acceptance. The commit and its source download are
publicly accessible. This identifies the source baseline; it does not establish
a byte-for-byte reproducible binary build.

The BEAST artifacts are available from Maven Central. The PopFunc artifact at
the coordinate above was not available there when this baseline was checked;
build it from the fixed source below. The POM still uses `0.1.0-SNAPSHOT`, so the
source pin is part of this preparation procedure, not a version lock enforced
by Maven. Do not substitute a moving `main` branch or an unrelated local
SNAPSHOT with the same name.

Run the following sections in the same shell. Replace the three absolute paths:
`PHYLOSPEC_SOURCE` must point to this PhyloSpec checkout; `POPFUNC_SOURCE` must be
a new clone directory with an existing parent; `PHYLOSPEC_DEPS` must be an empty
or new Maven cache outside both source checkouts. Maven creates the cache as
needed. Using the same cache for both builds keeps this dependency separate from
other locally installed PopFunc SNAPSHOTs.

```sh
PHYLOSPEC_SOURCE="/absolute/path/to/phylospec"
POPFUNC_SOURCE="/absolute/path/to/PopFunc-527e214"
PHYLOSPEC_DEPS="/absolute/path/to/maven-popfunc-527e214"

git clone https://github.com/LinguaPhylo/PopFunc.git "$POPFUNC_SOURCE"
git -C "$POPFUNC_SOURCE" checkout --detach \
  527e214274620fb4c834802fcae21adfbf4f6a3b

mvn -f "$POPFUNC_SOURCE/pom.xml" \
  -Dmaven.repo.local="$PHYLOSPEC_DEPS" \
  -pl popfunc-beast -am install
```

Stop if any command fails. The Maven command builds the selected native module,
invokes its test phase, and installs it with its parent POM into `PHYLOSPEC_DEPS`.
This native revision has no test cases; the model checks run in the PhyloSpec
build below. `package` alone would not make the native artifacts available to
that build. This command does not build a PhyloSpec adapter or the LPhyBeast
module. Leave this pinned native checkout unchanged for the baseline build.
Older Java 17/BEAST 2.7 PopFunc instructions describe a different setup. The first
build needs network access to resolve dependencies into the fresh cache; do not
use `-o` until that cache is populated.

When sharing a build, record the PhyloSpec and PopFunc commits, any source changes,
BEAST version, and generated JAR hashes. A SNAPSHOT version string alone does not
identify its contents. This preparation and build workflow passed with new source
directories and a fresh isolated Maven cache on 2026-10-24; see
[Recorded clean-build acceptance](#recorded-clean-build-acceptance) for the exact
baseline, results, and scope.

The adapter for this tutorial is built from **this PhyloSpec checkout's**
`integrations/beast3/popfunc/`, separately from the native model module. A native
PopFunc JAR alone does not contain the PhyloSpec JSON, mappings, or provider.
Do not substitute the older `PopFunc/popfunc-phylospec` prototype for this adapter.
The build below produces the generic CLI ZIP; separate native BEAGLE installation
is not required for this workflow.

## Build and verify this example

After the native installation succeeds, use the same variables and Maven cache
to build this PhyloSpec checkout:

```sh
mvn -f "$PHYLOSPEC_SOURCE/pom.xml" \
  -Dmaven.repo.local="$PHYLOSPEC_DEPS" \
  -Pcli-dist,popfunc -pl integrations/beast3/popfunc -am \
  -Djava.only=true -Djsonschema2pojo.skip=true -Dspotless.skip=true verify
```

Keep `-Dmaven.repo.local="$PHYLOSPEC_DEPS"` on both Maven commands. Omitting it
here would select the default cache instead of the native artifact just built.

`popfunc` enables the adapter module; `-pl` selects it and `-am` also builds its
PhyloSpec dependencies. It does not enter a separate PopFunc source checkout;
prepare that native dependency first if it is not available. `cli-dist` enables
the CLI ZIP and archive acceptance tests. The two skip properties retain
checked-in schema classes and avoid unrelated formatting changes.
`java.only=true` selects Java likelihoods in Maven's regression-test JVMs. It is
not automatically inherited by the separate JVM started by the installed launcher
in the archive test. That launcher retains BEAST's default native discovery and
Java fallback behavior. The archive test checks execution and results; it does
not verify that BEAGLE was activated.

| Build stage | Output or action |
| --- | --- |
| `compile` | Generate and compile Tiles and their registry. |
| `process-classes` | Generate the engine capability JSON. |
| `package` | Build JARs, copy runtime dependencies and examples, and create both ZIPs. |
| `verify` | Complete the tests, including running the packaged CLI with this adapter. |

This builds the CLI ZIP in `integrations/beast3/java/target/` and the adapter ZIP
in `integrations/beast3/popfunc/target/`. The adapter ZIP includes this `examples/`
directory. The regression tests check parameter bindings and numerical state
restoration; the distribution test extracts both ZIPs outside the repository and
executes the packaged `check` and `run` commands. The distribution test uses the
POSIX launcher and `unzip`, so it requires macOS or Linux; it does not establish
Windows support. Offline builds (`-o`) require cached dependencies.

For the current PhyloSpec version, extract the two archives into separate fresh
directories after a successful build, keeping `PHYLOSPEC_SOURCE` set as above:

```sh
PHYLOSPEC_INSTALL="$(mktemp -d "${TMPDIR:-/tmp}/phylospec-gompertz.XXXXXX")"
unzip "$PHYLOSPEC_SOURCE/integrations/beast3/java/target/phylospec-beast3-0.0.1-SNAPSHOT-cli.zip" \
  -d "$PHYLOSPEC_INSTALL/cli"
unzip "$PHYLOSPEC_SOURCE/integrations/beast3/popfunc/target/phylospec-beast3-popfunc-0.0.1-SNAPSHOT-adapter.zip" \
  -d "$PHYLOSPEC_INSTALL/popfunc"
PHYLOSPEC_DIR="$PHYLOSPEC_INSTALL/cli/phylospec-beast3-0.0.1-SNAPSHOT"
POPFUNC_DIR="$PHYLOSPEC_INSTALL/popfunc/phylospec-adapter"
```

Archive names follow the PhyloSpec Maven project version. Use the `check`/`run`
commands in [Run the packaged example](#run-the-packaged-example) with these two
variables, omitting that section's placeholder assignments. Choose an unused
`--out` directory for each run. Keep the example script beside its `data/`
directory if copying it into a separate project.

The adapter ZIP contains the adapter JAR, the native PopFunc JAR, runtime
dependencies, and `examples/`. The CLI keeps its own PhyloSpec/BEAST runtime and
skips bundled copies of those host JARs; package dependencies must still be
compatible with that runtime. Loading a directory is not a compatibility upgrade.

## Recorded clean-build acceptance

The following results describe the historical source snapshot identified below.
They do not establish that a different checkout has been retested.

On 2026-10-24, the dependency preparation and `verify` commands above completed
successfully on macOS 26.5 (Apple Silicon), using Eclipse Temurin 25.0.3,
Maven 3.9.16, and BEAST `2.8.0-beta7`.

PopFunc was cloned from its public repository and checked out at
`527e214274620fb4c834802fcae21adfbf4f6a3b`. PhyloSpec code came from commit
`4f320c1924c49ba74fc4226d9322776d24b1f820`, exported with `git archive` into a
directory outside the working repository, with the then-current tutorial copied
in. No existing `target/` directories were copied. The Maven repository did not
exist before the run; both builds used the same new cache. The original working
repository and all copied tracked source files remained unchanged by the builds.

The native PopFunc build and installation succeeded. It reported `No tests to
run`; the following results are from the PhyloSpec build:

| Test group | Passed | Skipped | Failed or errored |
| --- | ---: | ---: | ---: |
| Core | 331 | 2 | 0 |
| Tile processor | 77 | 0 | 0 |
| BEAST integration unit tests | 396 | 0 | 0 |
| Base CLI distribution tests | 5 | 0 | 0 |
| PopFunc unit tests | 65 | 0 | 0 |
| Gompertz CLI distribution test | 1 | 0 | 0 |
| **Total (877 tests)** | **875** | **2** | **0** |

The two skips were existing disabled tests for the deprecated LPhy and Rev
converters. The passing PopFunc tests included 11 Gompertz state tests and six
native equivalence tests covering Gompertz F0, Gompertz T50, and Logistic.

The existing `GompertzCliDistributionIT` extracted the newly built CLI and adapter
ZIPs outside the repository, ran their real `check` and 30-step `run`, and checked
31 trace samples, 31 trees, finite parameter/probability values, result files,
and run-record metadata. The state tests checked all four population parameters,
their connections to the native model, and numerical behavior after acceptance
and rejection. The native JAR in the build output, new Maven cache, and adapter
ZIP had the same SHA-256:

```text
238f7c5222a7a88c66b40e43d3e5231824241a18dfef369dfc32945e890c3915
```

That hash identifies this run's artifact; it is not a requirement that a later
build produce an identical ZIP/JAR byte stream. This acceptance covers fresh
source directories and dependency cache on the same macOS machine, using the
generic CLI. The recorded archive-test assertions do not establish which
likelihood backend was active in its separate CLI process. They do not establish
another platform's support, the bundled-BEAGLE CLI plus PopFunc combination, or
long-chain convergence.
