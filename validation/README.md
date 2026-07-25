# Cross-engine validation

This directory owns the project-level workflows and examples used to validate
PhyloSpec models across inference backends.

The framework is being introduced incrementally. It does not translate BEAST
XML into PhyloSpec, and it does not yet run all engines or calculate posterior
summaries.

## Layout

```text
validation/
├── README.md
├── examples/                       # manually maintained inputs
│   ├── testGTR/
│   │   ├── README.md
│   │   ├── testGTR.phylospec
│   │   └── reference/
│   │       └── beast2-aligned.xml
│   ├── testHKY/
│   │   ├── README.md
│   │   ├── testHKY.nex
│   │   ├── testHKY.phylospec
│   │   └── reference/
│   │       ├── beast2-original.xml
│   │       └── beast2-aligned.xml
│   ├── testCoalescent/
│   │   ├── README.md
│   │   ├── testCoalescent.nex
│   │   ├── testCoalescent.phylospec
│   │   └── reference/
│   │       ├── beast2-original.xml
│   │       └── beast2-aligned.xml
│   ├── testExponentialGrowth/
│   │   ├── README.md
│   │   ├── testExponentialGrowth.nex
│   │   ├── testExponentialGrowth.phylospec
│   │   └── reference/
│   │       ├── beast2-original.xml
│   │       └── beast2-aligned.xml
│   ├── testJukesCantor/
│   │   ├── README.md
│   │   ├── testJukesCantor.nex
│   │   ├── testJukesCantor.phylospec
│   │   └── reference/
│   │       ├── beast2-original.xml
│   │       ├── beast3-original.xml
│   │       └── beast2-aligned.xml
│   └── testRelaxedClock/
│       ├── README.md
│       ├── testRelaxedClock.nex
│       ├── testRelaxedClock.phylospec
│       └── reference/
│           ├── beast2-original.xml
│           ├── beast3-original.xml
│           └── beast2-aligned.xml
├── beast3/
│   └── java/                       # BEAST 3 validation Maven module
│       ├── pom.xml
│       └── src/main/java/
│           ├── Beast3DirectRun.java
│           ├── Beast3XmlExport.java
│           └── ValidationConfiguration.java
├── beastx/
│   └── java/                       # BEAST X validation Maven module
│       ├── pom.xml
│       └── src/main/java/
│           ├── BeastXDirectRun.java
│           ├── BeastXXmlExport.java
│           ├── BeastXValidationSupport.java
│           └── ValidationConfiguration.java
└── target/                         # generated results; ignored by Git
    ├── testGTR/
    │   ├── beast3-direct/
    │   ├── beast3-xml/
    │   ├── beastx-direct/
    │   └── beastx-xml/
    ├── testHKY/
    │   ├── beast3-direct/
    │   ├── beast3-xml/
    │   ├── beastx-direct/
    │   └── beastx-xml/
    ├── testCoalescent/
    │   ├── beast3-direct/
    │   ├── beast3-xml/
    │   ├── beastx-direct/
    │   └── beastx-xml/
    ├── testExponentialGrowth/
    │   ├── beast3-direct/
    │   ├── beast3-xml/
    │   ├── beastx-direct/
    │   └── beastx-xml/
    ├── testJukesCantor/
    │   ├── beast3-direct/
    │   ├── beast3-xml/
    │   ├── beastx-direct/
    │   └── beastx-xml/
    └── testRelaxedClock/
        ├── beast3-direct/
        ├── beast3-xml/
        ├── beastx-direct/
        └── beastx-xml/
```

The `examples` directory stores human-authored model inputs. Each backend owns
an independent Maven validation module, preventing backend dependencies and
runner classes from colliding. Generated XML, trace, tree, state, and
diagnostic files belong under `validation/target`.

## Current paths

The BEAST 3 module contains two comparison paths:

1. `PhyloSpec source -> BEAST 3 direct MCMC`;
2. `PhyloSpec source -> BEAST 3 XML -> external BEAST 3`.

The BEAST X module contains the corresponding two paths:

1. `PhyloSpec source -> BEAST X direct MCMC`;
2. `PhyloSpec source -> BEAST X XML -> external BEAST X`.

The current examples are `testGTR`, `testHKY`, `testCoalescent`,
`testExponentialGrowth`, `testJukesCantor`, and `testRelaxedClock`.
Example-specific model notes and commands are documented in each example's
`README.md`.

Each full MCMC path produces two complementary logs:

- the engine trace (`*.log`), which remains a tabular file readable by Tracer;
- the run log (`*.run.log`), which captures console diagnostics, final
  operator tuning/acceptance output, and wall-clock runtime.

These files must remain separate because adding diagnostics to the tabular
trace would make it invalid input for Tracer. Direct validation entry points
print `validation.mcmc.wall_clock_seconds` for the MCMC phase only. External
engine commands use `/usr/bin/time -p`, so their `real` value measures the
engine process and excludes XML export.

Before running a command that writes through `tee`, enable pipeline failure
propagation in the current zsh session:

```bash
set -o pipefail
```

Without this setting, `tee` can return success even when Maven or an inference
engine failed.

## Run the aligned testGTR reference with BEAST 2

```bash
mkdir -p validation/target/testGTR/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testGTR/beast2-reference/" \
    "$PWD/validation/examples/testGTR/reference/beast2-aligned.xml"
} 2>&1 | tee validation/target/testGTR/beast2-reference/testGTR-beast2.run.log
```

The trace is written by the reference XML as
`validation/target/testGTR/beast2-reference/primate-mtDNA.log`. The run log
preserves the final operator table and the external process runtime.

## Run testGTR directly through the BEAST 3 backend

This path constructs and runs the BEAST 3 object graph directly in memory. It
does not generate, serialize, or reparse XML.

Run this command from the repository root:

```bash
mkdir -p validation/target/testGTR/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testGTR/testGTR.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testGTR/beast3-direct/testGTR-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testGTR/beast3-direct/testGTR-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testGTR/beast3-direct/testGTR-direct.operators.txt \
  verify 2>&1 | tee validation/target/testGTR/beast3-direct/testGTR-direct.run.log
```

The current model runs for 5,000,000 states. This command therefore starts a
full MCMC and is not executed as part of ordinary project tests.

The direct run produces:

```text
validation/target/testGTR/beast3-direct/testGTR-direct.log
validation/target/testGTR/beast3-direct/testGTR-direct.trees
validation/target/testGTR/beast3-direct/testGTR-direct.state.xml
validation/target/testGTR/beast3-direct/testGTR-direct.operators.txt
validation/target/testGTR/beast3-direct/testGTR-direct.run.log
```

## Generate the testGTR BEAST 3 XML

Run this command from the repository root:

```bash
mkdir -p validation/target/testGTR/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testGTR/testGTR.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testGTR/beast3-xml/testGTR-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testGTR/beast3-xml/testGTR-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testGTR/beast3-xml/testGTR-xml.operators.txt \
  verify 2>&1 | tee validation/target/testGTR/beast3-xml/testGTR-xml.run.log
```

The generated files are:

```text
validation/target/testGTR/beast3-xml/testGTR-beast3.xml
validation/target/testGTR/beast3-xml/testGTR-xml.operators.txt
```

The XML contains the executed PhyloSpec source in its provenance comment.

Run the generated XML with an independently installed BEAST 3 application.
External execution is intentionally not wrapped by this Maven module.

```bash
{
  /usr/bin/time -p "/Users/adm-hhua361/Desktop/beast3/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    "$PWD/validation/target/testGTR/beast3-xml/testGTR-beast3.xml"
} 2>&1 | tee -a validation/target/testGTR/beast3-xml/testGTR-xml.run.log
```

## Run testGTR directly through the BEAST X backend

This path builds a materialized BEAST X PhyloCTMC model and runs its MCMC
directly in memory. It does not generate or reparse XML.

Run this command from the repository root:

```bash
mkdir -p validation/target/testGTR/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testGTR/testGTR.phylospec \
  -Dphylospec.validation.runName=testGTR-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testGTR/beastx-direct/testGTR-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testGTR/beastx-direct/testGTR-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testGTR/beastx-direct/testGTR-beastx-direct.operators.txt \
  verify 2>&1 | tee validation/target/testGTR/beastx-direct/testGTR-beastx-direct.run.log
```

The current model runs for 5,000,000 states. This command therefore starts a
full MCMC and is not executed as part of ordinary project tests.

The direct run produces:

```text
validation/target/testGTR/beastx-direct/testGTR-beastx-direct.log
validation/target/testGTR/beastx-direct/testGTR-beastx-direct.trees
validation/target/testGTR/beastx-direct/testGTR-beastx-direct.operators.txt
validation/target/testGTR/beastx-direct/testGTR-beastx-direct.run.log
```

## Generate the testGTR BEAST X XML

Run this command from the repository root:

```bash
mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testGTR/testGTR.phylospec \
  -Dphylospec.validation.runName=testGTR-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testGTR/beastx-xml/testGTR-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testGTR/beastx-xml/testGTR-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testGTR/beastx-xml/testGTR-beastx-xml.operators.txt \
  verify
```

The generated files are:

```text
validation/target/testGTR/beastx-xml/testGTR-beastx.xml
validation/target/testGTR/beastx-xml/testGTR-beastx-xml.operators.txt
```

The XML contains the executed PhyloSpec source in its provenance comment. If
the source does not define loggers, the validation module adds standard
screen, trace, and tree loggers using the configured `logEvery` value. The
default trace records the posterior, prior, likelihood, tree height, tree
length, and model parameters.

Run the generated XML with an independently installed BEAST X application.
External execution is intentionally not wrapped by this Maven module.

```bash
{
  /usr/bin/time -p java \
    -Djava.library.path=/usr/local/lib \
    --enable-native-access=ALL-UNNAMED \
    -jar \
    "$HOME/.m2/repository/dr/beast-mcmc/10.5.0/beast-mcmc-10.5.0.jar" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    "$PWD/validation/target/testGTR/beastx-xml/testGTR-beastx.xml"
} 2>&1 | tee validation/target/testGTR/beastx-xml/testGTR-beastx-xml.run.log
```

## Adding another example at this stage

Create:

```text
validation/examples/<example-id>/
├── README.md
├── <example-id>.phylospec
└── reference/
    └── beast2-aligned.xml
```

The aligned BEAST 2 XML is the fixed reference model for that example. The
PhyloSpec source must be written and reviewed manually against this reference.
To export it, reuse the same Maven profile and change only the source and
output properties. No new Java class is required.

## Planned later stages

After the five execution paths are stable for several examples, the framework
can be extended one step at a time:

1. define standard output manifests for each path;
2. add posterior comparison only after all execution paths are reliable.

These stages should remain separate Maven actions rather than being introduced
as one large command.
