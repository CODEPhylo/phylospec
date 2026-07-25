# testExponentialGrowth

## Purpose

This example extends the validated `testCoalescent` model by changing one
major component only: the constant-population function is replaced by an
exponential-population function with an estimated positive growth rate.

The source example is the `testExponentialGrowth.xml` file distributed with
BEAST 2.7.7. Its unmodified contents are preserved as:

- `reference/beast2-original.xml`

The manually written PhyloSpec model and the controlled BEAST 2 comparison
reference are:

- `testExponentialGrowth.phylospec`
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
- `growthRate ~ LogNormal(logMean=0.0, logSd=1.0)`;
- an estimated tree with an exponential-growth coalescent prior;
- a strict molecular clock with rate fixed to `1.0`;
- no gamma-distributed rate variation and no invariant-site category;
- a chain length of 10,000,000 and a logging interval of 1,000.

The BEAST 2 aligned XML declares the strict clock explicitly. PhyloSpec does
not declare a clock variable, so the BEAST 3 and BEAST X backends use their
fixed unit-rate clock semantics.

## Why the original XML is not the comparison reference

The distributed BEAST 2 example uses empirical alignment frequencies and does
not specify priors for kappa, population size, or growth rate. It places
`growthRate` in the state but supplies no operator for it and does not log it.
The file also contains unrelated legacy taxon and tree fragments, uses a
shorter chain, and uses a different logging interval. Those differences
prevent a controlled posterior comparison.

The aligned XML retains the alignment, HKY likelihood, estimated tree, and
exponential coalescent purpose of the original example while explicitly
matching the PhyloSpec target distribution. It should therefore be described
as an aligned validation model derived from the BEAST 2
`testExponentialGrowth.xml` example, not as a verbatim reproduction.

The common comparison restricts `growthRate` to `PositiveReal`. This matches
the current BEAST 3 exponential-population tile and is also accepted by the
BEAST X backend. Supporting negative growth rates is a separate backend
coverage question and is not part of this controlled comparison.

## Output layout

Generated files belong under:

```text
validation/target/testExponentialGrowth/
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
mkdir -p validation/target/testExponentialGrowth/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testExponentialGrowth/beast2-reference/" \
    "$PWD/validation/examples/testExponentialGrowth/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testExponentialGrowth/beast2-reference/testExponentialGrowth-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testExponentialGrowth/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testExponentialGrowth/testExponentialGrowth.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testExponentialGrowth/beast3-direct/testExponentialGrowth-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testExponentialGrowth/beast3-direct/testExponentialGrowth-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testExponentialGrowth/beast3-direct/testExponentialGrowth-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testExponentialGrowth/beast3-direct/testExponentialGrowth-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testExponentialGrowth/testExponentialGrowth.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testExponentialGrowth/beast3-xml/testExponentialGrowth-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testExponentialGrowth/beast3-xml/testExponentialGrowth-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testExponentialGrowth/beast3-xml/testExponentialGrowth-xml.operators.txt \
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
    "$PWD/validation/target/testExponentialGrowth/beast3-xml/testExponentialGrowth-beast3.xml"
} 2>&1 | tee \
  validation/target/testExponentialGrowth/beast3-xml/testExponentialGrowth-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testExponentialGrowth/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testExponentialGrowth/testExponentialGrowth.phylospec \
  -Dphylospec.validation.runName=testExponentialGrowth-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testExponentialGrowth/beastx-direct/testExponentialGrowth-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testExponentialGrowth/beastx-direct/testExponentialGrowth-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testExponentialGrowth/beastx-direct/testExponentialGrowth-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testExponentialGrowth/beastx-direct/testExponentialGrowth-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testExponentialGrowth/testExponentialGrowth.phylospec \
  -Dphylospec.validation.runName=testExponentialGrowth-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testExponentialGrowth/beastx-xml/testExponentialGrowth-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testExponentialGrowth/beastx-xml/testExponentialGrowth-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testExponentialGrowth/beastx-xml/testExponentialGrowth-beastx-xml.operators.txt \
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
    "$PWD/validation/target/testExponentialGrowth/beastx-xml/testExponentialGrowth-beastx.xml"
} 2>&1 | tee \
  validation/target/testExponentialGrowth/beastx-xml/testExponentialGrowth-beastx-xml.run.log
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
- `growthRate`;
- base frequencies;
- tree height;
- tree length.

Operator schedules and starting states may differ across engines. They affect
sampling efficiency but do not change the controlled target distribution.
