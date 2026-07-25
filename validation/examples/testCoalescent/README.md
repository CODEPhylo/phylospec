# testCoalescent

## Purpose

This example extends the validated `testHKY` model by changing one major
component only: the Yule tree prior is replaced by a constant-population
coalescent prior with an estimated population size.

The source example is the `testCoalescent.xml` file distributed with
BEAST 2.7.7. Its unmodified contents are preserved as:

- `reference/beast2-original.xml`

The manually written PhyloSpec model and the controlled BEAST 2 comparison
reference are:

- `testCoalescent.phylospec`
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
- a strict molecular clock with rate fixed to `1.0`;
- no gamma-distributed rate variation and no invariant-site category;
- a chain length of 10,000,000 and a logging interval of 1,000.

The BEAST 2 aligned XML declares the strict clock explicitly. PhyloSpec does
not declare a clock variable, so the BEAST 3 and BEAST X backends use their
fixed unit-rate clock semantics.

## Why the original XML is not the comparison reference

The distributed BEAST 2 example uses empirical alignment frequencies and does
not specify priors for kappa or population size. It also uses a shorter chain
and a different logging interval. Those differences prevent a controlled
posterior comparison.

The aligned XML retains the alignment, HKY likelihood, estimated tree, and
constant coalescent purpose of the original example while explicitly matching
the PhyloSpec target distribution. It should therefore be described as an
aligned validation model derived from the BEAST 2 `testCoalescent.xml`
example, not as a verbatim reproduction.

## Output layout

Generated files belong under:

```text
validation/target/testCoalescent/
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
mkdir -p validation/target/testCoalescent/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testCoalescent/beast2-reference/" \
    "$PWD/validation/examples/testCoalescent/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testCoalescent/beast2-reference/testCoalescent-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testCoalescent/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testCoalescent/testCoalescent.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testCoalescent/beast3-direct/testCoalescent-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testCoalescent/beast3-direct/testCoalescent-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testCoalescent/beast3-direct/testCoalescent-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testCoalescent/beast3-direct/testCoalescent-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testCoalescent/testCoalescent.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testCoalescent/beast3-xml/testCoalescent-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testCoalescent/beast3-xml/testCoalescent-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testCoalescent/beast3-xml/testCoalescent-xml.operators.txt \
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
    "$PWD/validation/target/testCoalescent/beast3-xml/testCoalescent-beast3.xml"
} 2>&1 | tee \
  validation/target/testCoalescent/beast3-xml/testCoalescent-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testCoalescent/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testCoalescent/testCoalescent.phylospec \
  -Dphylospec.validation.runName=testCoalescent-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testCoalescent/beastx-direct/testCoalescent-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testCoalescent/beastx-direct/testCoalescent-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testCoalescent/beastx-direct/testCoalescent-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testCoalescent/beastx-direct/testCoalescent-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testCoalescent/testCoalescent.phylospec \
  -Dphylospec.validation.runName=testCoalescent-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testCoalescent/beastx-xml/testCoalescent-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testCoalescent/beastx-xml/testCoalescent-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testCoalescent/beastx-xml/testCoalescent-beastx-xml.operators.txt \
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
    "$PWD/validation/target/testCoalescent/beastx-xml/testCoalescent-beastx.xml"
} 2>&1 | tee \
  validation/target/testCoalescent/beastx-xml/testCoalescent-beastx-xml.run.log
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

Operator schedules and starting states may differ across engines. They affect
sampling efficiency but do not change the controlled target distribution.
