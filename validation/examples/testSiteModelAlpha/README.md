# testSiteModelAlpha

## Purpose

This example is derived from the current BEAST 3
`beast2vs1/testSiteModelAlpha.xml` example. It adds a controlled
cross-engine benchmark for an estimated discrete-gamma site-rate shape.

Unlike the earlier BEAST-2-derived examples, the first reference path runs an
aligned BEAST 3 XML. The other four paths remain the two PhyloSpec backend
paths for BEAST 3 and BEAST X.

## Controlled model

All five paths represent:

- the 21-taxon, 1,698-site dated influenza alignment from `Flu.nex`;
- one HKY substitution model;
- fixed empirical nucleotide frequencies;
- `kappa ~ LogNormal(logMean=1.0, logSd=0.5)`;
- four discrete-gamma site-rate categories;
- `gammaShape ~ LogNormal(logMean=-2.995732273554, logSd=0.5)`;
- a strict molecular clock;
- `clockRate ~ LogNormal(logMean=-7.824046010856, logSd=0.5)`;
- a constant-population coalescent tree prior;
- `populationSize ~ LogNormal(logMean=5.940171252721, logSd=1.0)`;
- a chain length of 10,000,000 and a logging interval of 1,000.

The distributed BEAST 3 XML is preserved as
`reference/beast3-original.xml`. The comparison reference is
`reference/beast3-aligned.xml`, which adds explicit proper priors, fixed
empirical frequencies, a shared chain length, and the common trace columns.

## Five paths

1. aligned BEAST 3 reference XML;
2. PhyloSpec to BEAST 3 direct;
3. PhyloSpec to BEAST 3 XML and external BEAST 3;
4. PhyloSpec to BEAST X direct;
5. PhyloSpec to BEAST X XML and external BEAST X.

The primary comparison traces are `posterior`, `prior`, `likelihood`,
`kappa`, `gammaShape`, `clockRate`, `populationSize`, tree height, and tree
length. Operator schedules remain engine-specific and are not the semantic
target of this benchmark.

## Commands

Run all commands from the repository root:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

### 1. Aligned official BEAST 3 reference

```bash
mkdir -p validation/target/testSiteModelAlpha/beast3-reference

{
  /usr/bin/time -p "/Users/adm-hhua361/Desktop/beast3/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testSiteModelAlpha/beast3-reference/" \
    "$PWD/validation/examples/testSiteModelAlpha/reference/beast3-aligned.xml"
} 2>&1 | tee \
  validation/target/testSiteModelAlpha/beast3-reference/testSiteModelAlpha-beast3-reference.run.log
```

### 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testSiteModelAlpha/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSiteModelAlpha/testSiteModelAlpha.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testSiteModelAlpha/beast3-direct/testSiteModelAlpha-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testSiteModelAlpha/beast3-direct/testSiteModelAlpha-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testSiteModelAlpha/beast3-direct/testSiteModelAlpha-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSiteModelAlpha/beast3-direct/testSiteModelAlpha-direct.run.log
```

### 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testSiteModelAlpha/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSiteModelAlpha/testSiteModelAlpha.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testSiteModelAlpha/beast3-xml/testSiteModelAlpha-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testSiteModelAlpha/beast3-xml/testSiteModelAlpha-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testSiteModelAlpha/beast3-xml/testSiteModelAlpha-xml.operators.txt \
  verify
```

Run the generated XML:

```bash
{
  /usr/bin/time -p "/Users/adm-hhua361/Desktop/beast3/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    "$PWD/validation/target/testSiteModelAlpha/beast3-xml/testSiteModelAlpha-beast3.xml"
} 2>&1 | tee \
  validation/target/testSiteModelAlpha/beast3-xml/testSiteModelAlpha-xml.run.log
```

### 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testSiteModelAlpha/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSiteModelAlpha/testSiteModelAlpha.phylospec \
  -Dphylospec.validation.runName=testSiteModelAlpha-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testSiteModelAlpha/beastx-direct/testSiteModelAlpha-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testSiteModelAlpha/beastx-direct/testSiteModelAlpha-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testSiteModelAlpha/beastx-direct/testSiteModelAlpha-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSiteModelAlpha/beastx-direct/testSiteModelAlpha-beastx-direct.run.log
```

### 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testSiteModelAlpha/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSiteModelAlpha/testSiteModelAlpha.phylospec \
  -Dphylospec.validation.runName=testSiteModelAlpha-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testSiteModelAlpha/beastx-xml/testSiteModelAlpha-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testSiteModelAlpha/beastx-xml/testSiteModelAlpha-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testSiteModelAlpha/beastx-xml/testSiteModelAlpha-beastx-xml.operators.txt \
  verify
```

Run the generated XML:

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
    "$PWD/validation/target/testSiteModelAlpha/beastx-xml/testSiteModelAlpha-beastx.xml"
} 2>&1 | tee \
  validation/target/testSiteModelAlpha/beastx-xml/testSiteModelAlpha-beastx-xml.run.log
```
