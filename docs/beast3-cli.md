# PhyloSpec command line with BEAST 3

The Java entry point `runner.PhyloSpecCli` checks and runs a `.phylospec` script
with BEAST 3. The `check` and `run` commands share syntax, static-type, and backend
mapping checks. `run` also creates a result directory, executes MCMC, and records
its settings, status, and outputs. Only the BEAST 3 engine is supported by this
entry point.

## Build and prepare the command

Use Java **25** and Maven. The following commands use macOS/Linux shell syntax.
Run them in the same terminal session, starting at the repository root; check
that `java -version` and `mvn -version` report the intended Java installation.
Stop at any error before proceeding.

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

## Prepare, check, and run a script

This small prior-only example requires no input data. Create it outside the source
checkout so its inputs and results have their own directory:

```sh
PHYLOSPEC_PROJECT="$(mktemp -d "$HOME/phylospec-example.XXXXXX")"
cat > "$PHYLOSPEC_PROJECT/normal.phylospec" <<'EOF'
Real x ~ Normal(mean=0.0, sd=1.0)

mcmc {
    Integer chainLength = 30
    Integer defaultLogEvery = 10
    Integer randomSeed = 42
}
EOF

java --enable-native-access=ALL-UNNAMED -Djava.only=true \
  -cp "$PHYLOSPEC_CLASSPATH" runner.PhyloSpecCli \
  check "$PHYLOSPEC_PROJECT/normal.phylospec"
```

Continue when you see `Check passed`. A successful check confirms syntax, static
types, and available BEAST 3 mappings. It does not run MCMC or establish complete
input-data validity, successful native model initialization, or convergence.
Some checks can inspect inputs while resolving their types.

```sh
PHYLOSPEC_RUN="$PHYLOSPEC_PROJECT/results/run-01"
java --enable-native-access=ALL-UNNAMED -Djava.only=true \
  -cp "$PHYLOSPEC_CLASSPATH" runner.PhyloSpecCli \
  run "$PHYLOSPEC_PROJECT/normal.phylospec" --out "$PHYLOSPEC_RUN"
```

`run` reports four stages: checking, model construction and initialization, MCMC,
and output verification. Wait for `Run completed` and the result paths. The example
runs 30 MCMC steps with seed 42 and logs every 10 steps. This demonstrates execution
and output handling; it is too short to assess convergence or support scientific
conclusions.

## View the results

For the example above, a successful run produces:

```text
results/run-01/
  model.phylospec
  run.json
  console.log
  output/
    normal.log
    normal.state.xml
```

```sh
ls -lh "$PHYLOSPEC_RUN" "$PHYLOSPEC_RUN/output"
head -n 12 "$PHYLOSPEC_RUN/run.json"
head -n 6 "$PHYLOSPEC_RUN/output/normal.log"
tail -n 30 "$PHYLOSPEC_RUN/console.log"
```

| File | Contents |
| --- | --- |
| `output/normal.log` | Tab-separated MCMC samples; identify quantities by their column names. |
| `output/normal.state.xml` | Final MCMC state. The CLI does not yet provide a resume command. |
| `run.json` | Run status, runtime and selected component/library identities, effective settings, seeds, input references, and expected/generated output files. |
| `console.log` | Java/BEAST console output, original diagnostic messages, and failure details captured during the managed run. |
| `model.phylospec` | Snapshot of the script actually read. |

Models with tree logging also produce tree files in `output/`. This prior-only
example has no tree. The run record includes a script hash and hashes for input
files reported through the runner's input-path resolver. Input data are not copied;
the result directory is not a self-contained analysis archive.

Successful completion is recorded as `"status" : "succeeded"` together with
`"completionConfirmed" : true`. The status normally progresses through `preparing`
and `running` to `succeeded`, `failed`, or `cancelled`. After abrupt termination,
the record can remain nonterminal or become `unconfirmed`; this does not establish
that the process is still running. Failed/cancelled runs preserve partial files.

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
`console.log` captures Java/BEAST console streams, not low-level JVM or native-library
writes directly to the operating system's stderr.

Exit codes are `0` for success, `1` for check/run failure, `2` for invalid command
arguments, and `130` for cancellation caught by the CLI. OS termination signals may
produce a different nonzero code. Successful execution and output checks do not
establish MCMC convergence.

## Existing invocation without a subcommand

The previous Java invocation remains available for existing callers:

```sh
java --enable-native-access=ALL-UNNAMED -Djava.only=true \
  -cp "$PHYLOSPEC_CLASSPATH" runner.PhyloSpecCli \
  --library beast2 --run-name "$PHYLOSPEC_PROJECT/legacy-normal" \
  "$PHYLOSPEC_PROJECT/normal.phylospec"
```

Without `check` or `run`, `--library` is required and its exact order is retained.
This mode keeps the earlier output-prefix and script-output behavior; it does not
create a managed result directory, script snapshot, run record, or console log.
`--out` is unavailable in this mode. Use `run` for the result management described
in this guide.
