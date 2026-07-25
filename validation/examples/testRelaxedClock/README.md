# testRelaxedClock

## Purpose

This example extends the validated `testCoalescent` model by changing one
major component only: the fixed strict clock is replaced by a normalized
uncorrelated lognormal relaxed clock.

The source examples are BEAST 2.7.7 `testRelaxedClock.xml` and the current
BEAST 3 `beast2vs1/testUCRelaxedClockLogNormal.xml`. Their unmodified contents
are preserved as:

- `reference/beast2-original.xml`
- `reference/beast3-original.xml`

The manually written PhyloSpec model and the controlled BEAST 2 comparison
reference are:

- `testRelaxedClock.phylospec`
- `reference/beast2-aligned.xml`

The validation workflow does not translate the original BEAST 2 XML into
PhyloSpec. The PhyloSpec source is maintained manually.

## Controlled model

All comparison paths represent:

- the six-taxon, 768-site mitochondrial alignment from the BEAST example;
- one HKY substitution model;
- `kappa ~ LogNormal(logMean=1.0, logSd=0.5)`;
- estimated base frequencies with a `Dirichlet(1,1,1,1)` prior;
- `populationSize ~ LogNormal(logMean=0.0, logSd=1.0)`;
- an estimated tree with a constant-population coalescent prior;
- a normalized uncorrelated lognormal relaxed clock;
- a mean clock rate fixed to `1.0`;
- a branch-rate distribution `LogNormal(mean=1.0, logSd=0.5)`;
- sampled integer branch-rate categories;
- no gamma-distributed rate variation and no invariant-site category;
- a chain length of 10,000,000 and a logging interval of 1,000.

The relaxed-clock standard deviation is deliberately fixed in this first
cross-engine validation. The current backend distribution tiles construct
the `LogNormal` base distribution from the supplied scalar values; they do
not yet preserve a stochastic `logSd` argument as a shared state parameter.
Estimating the relaxed-clock standard deviation therefore remains a separate
coverage task.

## Why the original XML is not the comparison reference

The distributed BEAST 2 example uses empirical alignment frequencies, omits
explicit priors for kappa and population size, and estimates `ucld.stdev`.
The BEAST 3 example uses a different fixed starting tree, different priors,
and a different relaxed-clock standard deviation. The originals also differ
in chain length, logger configuration, and operator weights. Those
differences prevent a controlled posterior comparison.

The aligned XML retains the alignment, HKY likelihood, estimated tree, and
constant coalescent purpose of the original example while explicitly matching
the normalized relaxed-clock target represented by PhyloSpec. It should
therefore be described as an aligned validation model derived from the source
examples, not as a verbatim reproduction.

## Relaxed-clock backend checks

Preparing this example exposed and corrected several backend inconsistencies:

- the BEAST 3 tile now enables branch-length-weighted rate normalization;
- BEAST 3 category state receives random-walk, swap, and uniform integer
  operators;
- BEAST X direct uses integer category operators instead of a continuous
  random-walk operator;
- BEAST X XML now includes category operators and tree operators;
- BEAST X direct and XML now use the same deterministic category
  initialization policy;
- BEAST X now keeps `DiscretizedBranchRates` unscaled and applies the target
  mean rate through `ScaledByTreeTimeBranchRateModel`. This wrapper listens
  directly to tree and rate-model changes and stores/restores its scale
  factor, avoiding inconsistent likelihood caches after rejected tree moves.

BEAST 3 and BEAST X still use engine-specific implementations of a discretized
uncorrelated relaxed clock. Matching posterior distributions, rather than
matching category labels, is the relevant validation criterion.

## Output layout

Generated files belong under:

```text
validation/target/testRelaxedClock/
├── beast2-reference/
├── beast3-direct/
├── beast3-xml/
├── beastx-direct/
└── beastx-xml/
```

Run all commands from the repository root:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

## 1. Aligned BEAST 2 reference

```bash
mkdir -p validation/target/testRelaxedClock/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testRelaxedClock/beast2-reference/" \
    "$PWD/validation/examples/testRelaxedClock/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testRelaxedClock/beast2-reference/testRelaxedClock-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testRelaxedClock/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testRelaxedClock/testRelaxedClock.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testRelaxedClock/beast3-direct/testRelaxedClock-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testRelaxedClock/beast3-direct/testRelaxedClock-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testRelaxedClock/beast3-direct/testRelaxedClock-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testRelaxedClock/beast3-direct/testRelaxedClock-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testRelaxedClock/testRelaxedClock.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testRelaxedClock/beast3-xml/testRelaxedClock-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testRelaxedClock/beast3-xml/testRelaxedClock-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testRelaxedClock/beast3-xml/testRelaxedClock-xml.operators.txt \
  verify
```

Run the generated XML externally:

```bash
{
  /usr/bin/time -p "/Users/adm-hhua361/Desktop/beast3/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    "$PWD/validation/target/testRelaxedClock/beast3-xml/testRelaxedClock-beast3.xml"
} 2>&1 | tee \
  validation/target/testRelaxedClock/beast3-xml/testRelaxedClock-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testRelaxedClock/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testRelaxedClock/testRelaxedClock.phylospec \
  -Dphylospec.validation.runName=testRelaxedClock-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testRelaxedClock/beastx-direct/testRelaxedClock-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testRelaxedClock/beastx-direct/testRelaxedClock-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testRelaxedClock/beastx-direct/testRelaxedClock-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testRelaxedClock/beastx-direct/testRelaxedClock-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testRelaxedClock/testRelaxedClock.phylospec \
  -Dphylospec.validation.runName=testRelaxedClock-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testRelaxedClock/beastx-xml/testRelaxedClock-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testRelaxedClock/beastx-xml/testRelaxedClock-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testRelaxedClock/beastx-xml/testRelaxedClock-beastx-xml.operators.txt \
  verify
```

Run the generated XML externally:

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
    "$PWD/validation/target/testRelaxedClock/beastx-xml/testRelaxedClock-beastx.xml"
} 2>&1 | tee \
  validation/target/testRelaxedClock/beastx-xml/testRelaxedClock-beastx-xml.run.log
```

The direct and external-engine commands run the full 10,000,000-state chain.
XML export and BEAST 2 `-validate` checks do not run the chain.

## Comparison traces

The primary shared traces are:

- `posterior`;
- `prior`;
- `likelihood`;
- `kappa`;
- `populationSize`;
- base frequencies;
- tree height;
- tree length.

The branch-rate category vectors are retained as diagnostics, but their
individual labels should not be compared across engines. Category labels are
latent implementation coordinates; the induced branch-rate model and shared
posterior summaries are the meaningful comparison targets.

Operator schedules and starting states may differ across engines. They affect
sampling efficiency but do not change the controlled target distribution.
