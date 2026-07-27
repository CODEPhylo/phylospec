# testBirthDeathModel10Taxa

## Purpose

This example is derived from the BEAST 3
`beast2vs1/testBirthDeathModel_10taxa.xml` example. It adds the first
five-path validation of the PhyloSpec `BirthDeath` tree-prior tile.

The original distributed XML is preserved as
`reference/beast3-original.xml`. The comparison uses the independently
maintained `reference/beast3-aligned.xml`.

## Controlled model

All five paths represent:

- ten taxa named `A` through `J`;
- a prior-only analysis (the one-site alignment supplies taxa only);
- `diversificationRate ~ LogNormal(logMean=0.0, logSd=1.0)`;
- a fixed turnover of `0.5`;
- complete sampling, with sampling probability fixed to its default `1.0`;
- `tree ~ BirthDeath(diversificationRate, turnover)`;
- a chain length of 10,000,000 and a logging interval of 1,000.

The proper LogNormal prior is added because the original regression example
estimates its birth-rate parameter without an explicit parameter prior. The
optional `samplingProbability` argument is omitted from the PhyloSpec source:
both backends use `1.0` by default, while the current BEAST 3 tile does not
accept the same value as a literal optional argument.

The trace columns common to all five paths are `prior`,
`diversificationRate`, tree height, and tree length. BEAST 3 additionally
exposes the empty `likelihood`, `posterior`, and component-prior values.
Operator schedules are engine-specific and are not the semantic target.

## Cross-engine tree-density convention

All backends now use the Gernhard `UNSCALED` tree-density convention:

- BEAST 3 uses the `BirthDeathGernhard08Model` default `UNSCALED` type;
- BEAST X direct construction explicitly uses `TreeType.UNSCALED`;
- BEAST X XML export explicitly writes `type="UNSCALED"`.

An earlier BEAST X implementation used `LABELED`, which added the constant
`9 * log(2) - logGamma(10)`, approximately `-6.5635`, for this ten-taxon
example. That coefficient did not change normalized posterior marginals for
the fixed taxon set, but it shifted the absolute tree-prior and posterior
logs. The unified convention allows all five paths to compare both marginal
shapes and absolute prior decomposition.

## Five paths

1. aligned BEAST 3 reference XML;
2. PhyloSpec to BEAST 3 direct;
3. PhyloSpec to BEAST 3 XML and external BEAST 3;
4. PhyloSpec to BEAST X direct;
5. PhyloSpec to BEAST X XML and external BEAST X.

Run all commands from the repository root:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

### 1. Aligned official BEAST 3 reference

```bash
mkdir -p validation/target/testBirthDeathModel10Taxa/beast3-reference

{
  /usr/bin/time -p "/Users/adm-hhua361/Desktop/beast3/bin/beast" \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testBirthDeathModel10Taxa/beast3-reference/" \
    "$PWD/validation/examples/testBirthDeathModel10Taxa/reference/beast3-aligned.xml"
} 2>&1 | tee \
  validation/target/testBirthDeathModel10Taxa/beast3-reference/testBirthDeathModel10Taxa-beast3-reference.run.log
```

### 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testBirthDeathModel10Taxa/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testBirthDeathModel10Taxa/testBirthDeathModel10Taxa.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testBirthDeathModel10Taxa/beast3-direct/testBirthDeathModel10Taxa-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testBirthDeathModel10Taxa/beast3-direct/testBirthDeathModel10Taxa-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testBirthDeathModel10Taxa/beast3-direct/testBirthDeathModel10Taxa-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testBirthDeathModel10Taxa/beast3-direct/testBirthDeathModel10Taxa-direct.run.log
```

### 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testBirthDeathModel10Taxa/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testBirthDeathModel10Taxa/testBirthDeathModel10Taxa.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testBirthDeathModel10Taxa/beast3-xml/testBirthDeathModel10Taxa-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testBirthDeathModel10Taxa/beast3-xml/testBirthDeathModel10Taxa-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testBirthDeathModel10Taxa/beast3-xml/testBirthDeathModel10Taxa-xml.operators.txt \
  verify
```

Run the generated XML:

```bash
{
  /usr/bin/time -p "/Users/adm-hhua361/Desktop/beast3/bin/beast" \
    -overwrite \
    -seed 1234 \
    "$PWD/validation/target/testBirthDeathModel10Taxa/beast3-xml/testBirthDeathModel10Taxa-beast3.xml"
} 2>&1 | tee \
  validation/target/testBirthDeathModel10Taxa/beast3-xml/testBirthDeathModel10Taxa-xml.run.log
```

### 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testBirthDeathModel10Taxa/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testBirthDeathModel10Taxa/testBirthDeathModel10Taxa.phylospec \
  -Dphylospec.validation.runName=testBirthDeathModel10Taxa-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testBirthDeathModel10Taxa/beastx-direct/testBirthDeathModel10Taxa-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testBirthDeathModel10Taxa/beastx-direct/testBirthDeathModel10Taxa-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testBirthDeathModel10Taxa/beastx-direct/testBirthDeathModel10Taxa-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testBirthDeathModel10Taxa/beastx-direct/testBirthDeathModel10Taxa-beastx-direct.run.log
```

### 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testBirthDeathModel10Taxa/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testBirthDeathModel10Taxa/testBirthDeathModel10Taxa.phylospec \
  -Dphylospec.validation.runName=testBirthDeathModel10Taxa-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testBirthDeathModel10Taxa/beastx-xml/testBirthDeathModel10Taxa-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testBirthDeathModel10Taxa/beastx-xml/testBirthDeathModel10Taxa-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testBirthDeathModel10Taxa/beastx-xml/testBirthDeathModel10Taxa-beastx-xml.operators.txt \
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
    -overwrite \
    -seed 1234 \
    "$PWD/validation/target/testBirthDeathModel10Taxa/beastx-xml/testBirthDeathModel10Taxa-beastx.xml"
} 2>&1 | tee \
  validation/target/testBirthDeathModel10Taxa/beastx-xml/testBirthDeathModel10Taxa-beastx-xml.run.log
```
