# testJukesCantor

## Purpose

This example validates the smallest nucleotide substitution model currently
used in the comparison suite: Jukes-Cantor (JC69). It isolates substitution
model construction because JC69 has neither an estimated transition bias nor
estimated base frequencies.

The unmodified examples distributed with BEAST 2.7.7 and the current BEAST 3
`beast-base` repository are preserved as:

- `reference/beast2-original.xml`
- `reference/beast3-original.xml`

The manually written PhyloSpec model and controlled BEAST 2 reference are:

- `testJukesCantor.phylospec`
- `reference/beast2-aligned.xml`

The workflow does not translate either original XML into PhyloSpec.

## Controlled model

All five comparison paths represent:

- the six-taxon, 768-site mitochondrial alignment from the examples;
- a JC69 substitution model;
- an estimated tree with a Yule prior and birth rate fixed to `1.0`;
- a strict molecular clock with rate fixed to `1.0`;
- no gamma-distributed rate variation and no invariant-site category;
- a chain length of 10,000,000 and a logging interval of 1,000.

There are no estimated substitution-model parameters in JC69. The shared
stochastic state is therefore the tree.

## Why the original XMLs are not comparison references

Both distributed XMLs put only the sequence likelihood inside `posterior`.
Their constant-coalescent `RandomTree` objects generate starting trees but do
not define a tree prior. Consequently, the originals do not specify the same
proper Bayesian target used by the other validation examples.

The aligned model retains the original alignment, JC69 likelihood, estimated
tree, and fixed unit-rate clock semantics, while adding a Yule tree prior with
a fixed birth rate. It is an aligned validation model derived from
`testJukesCantor.xml`, not a verbatim reproduction.

## Output layout

Generated files belong under:

```text
validation/target/testJukesCantor/
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
mkdir -p validation/target/testJukesCantor/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testJukesCantor/beast2-reference/" \
    "$PWD/validation/examples/testJukesCantor/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testJukesCantor/beast2-reference/testJukesCantor-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testJukesCantor/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testJukesCantor/testJukesCantor.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testJukesCantor/beast3-direct/testJukesCantor-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testJukesCantor/beast3-direct/testJukesCantor-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testJukesCantor/beast3-direct/testJukesCantor-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testJukesCantor/beast3-direct/testJukesCantor-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testJukesCantor/testJukesCantor.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testJukesCantor/beast3-xml/testJukesCantor-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testJukesCantor/beast3-xml/testJukesCantor-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testJukesCantor/beast3-xml/testJukesCantor-xml.operators.txt \
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
    "$PWD/validation/target/testJukesCantor/beast3-xml/testJukesCantor-beast3.xml"
} 2>&1 | tee \
  validation/target/testJukesCantor/beast3-xml/testJukesCantor-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testJukesCantor/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testJukesCantor/testJukesCantor.phylospec \
  -Dphylospec.validation.runName=testJukesCantor-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testJukesCantor/beastx-direct/testJukesCantor-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testJukesCantor/beastx-direct/testJukesCantor-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testJukesCantor/beastx-direct/testJukesCantor-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testJukesCantor/beastx-direct/testJukesCantor-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testJukesCantor/testJukesCantor.phylospec \
  -Dphylospec.validation.runName=testJukesCantor-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testJukesCantor/beastx-xml/testJukesCantor-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testJukesCantor/beastx-xml/testJukesCantor-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testJukesCantor/beastx-xml/testJukesCantor-beastx-xml.operators.txt \
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
    "$PWD/validation/target/testJukesCantor/beastx-xml/testJukesCantor-beastx.xml"
} 2>&1 | tee \
  validation/target/testJukesCantor/beastx-xml/testJukesCantor-beastx-xml.run.log
```

The direct and external-engine commands run the full 10,000,000-state chain.

## Comparison traces

The primary shared traces are:

- `posterior`;
- `prior`;
- `likelihood`;
- tree height;
- tree length.

Operator schedules and starting trees may differ across engines. They affect
sampling efficiency but do not change the controlled target distribution.
