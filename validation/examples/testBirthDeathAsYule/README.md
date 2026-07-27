# testBirthDeathAsYule

## Purpose

This example is derived from the BEAST 3
`beast2vs1/testBirthDeathAsYule.xml` regression example. It tests the
identity

```text
BirthDeath(turnover=0, samplingProbability=1) == Yule
```

under the same `UNSCALED` Gernhard tree-density convention.

This is deliberately stronger than running the same PhyloSpec source through
four backend paths:

- the aligned BEAST 3 reference samples a tree under
  `BirthDeathGernhard08Model`;
- the four PhyloSpec paths sample the tree under `Yule`.

Agreement therefore checks the semantic equivalence of two tree-prior
representations as well as direct/XML parity.

## Controlled adaptation

The upstream BEAST 3 example uses 100 taxa and a BEAST-3-only improper
`OneOnX` prior. PhyloSpec does not currently provide a portable `OneOnX`
distribution tile. The controlled five-path model therefore uses:

- ten taxa named `A` through `J`;
- one estimated ten-taxon tree;
- `birthRate ~ LogNormal(logMean=0.0, logSd=1.0)`;
- no sequence likelihood;
- a chain length of 10,000,000;
- a logging interval of 1,000.

The reference fixes relative death rate to `0.0` and sampling probability to
`1.0`. The PhyloSpec source uses `Yule`, for which those two values are
implicit.

The common comparison columns are `prior`, `birthRate`, tree height, and tree
length. `likelihood` is the constant zero where the backend logs the empty
likelihood component. Operator schedules remain engine-specific and are not
the semantic target.

The upstream example fixes its 100-tip tree. A first controlled adaptation
also attempted to bind `Yule` to a tree imported with `fromNewick`. BEAST 3
supports that observed-tree form, but the BEAST X backend does not currently
support `observed as` for a tree distribution. Sampling the tree in all five
paths keeps the target model portable without introducing a backend change
solely for this benchmark.

## Five paths

Run all commands from the repository root:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

### 1. Aligned BEAST 3 Birth-Death reference

```bash
mkdir -p validation/target/testBirthDeathAsYule/beast3-reference

{
  /usr/bin/time -p "/Users/adm-hhua361/Desktop/beast3/bin/beast" \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testBirthDeathAsYule/beast3-reference/" \
    "$PWD/validation/examples/testBirthDeathAsYule/reference/beast3-aligned.xml"
} 2>&1 | tee \
  validation/target/testBirthDeathAsYule/beast3-reference/testBirthDeathAsYule-beast3-reference.run.log
```

### 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testBirthDeathAsYule/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testBirthDeathAsYule/testBirthDeathAsYule.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testBirthDeathAsYule/beast3-direct/testBirthDeathAsYule-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testBirthDeathAsYule/beast3-direct/testBirthDeathAsYule-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testBirthDeathAsYule/beast3-direct/testBirthDeathAsYule-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testBirthDeathAsYule/beast3-direct/testBirthDeathAsYule-direct.run.log
```

### 3. PhyloSpec to BEAST 3 XML and external BEAST 3

Generate the XML:

```bash
mkdir -p validation/target/testBirthDeathAsYule/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testBirthDeathAsYule/testBirthDeathAsYule.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testBirthDeathAsYule/beast3-xml/testBirthDeathAsYule-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testBirthDeathAsYule/beast3-xml/testBirthDeathAsYule-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testBirthDeathAsYule/beast3-xml/testBirthDeathAsYule-xml.operators.txt \
  verify
```

Run the generated XML:

```bash
{
  /usr/bin/time -p "/Users/adm-hhua361/Desktop/beast3/bin/beast" \
    -overwrite \
    -seed 1234 \
    "$PWD/validation/target/testBirthDeathAsYule/beast3-xml/testBirthDeathAsYule-beast3.xml"
} 2>&1 | tee \
  validation/target/testBirthDeathAsYule/beast3-xml/testBirthDeathAsYule-xml.run.log
```

### 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testBirthDeathAsYule/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testBirthDeathAsYule/testBirthDeathAsYule.phylospec \
  -Dphylospec.validation.runName=testBirthDeathAsYule-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testBirthDeathAsYule/beastx-direct/testBirthDeathAsYule-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testBirthDeathAsYule/beastx-direct/testBirthDeathAsYule-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testBirthDeathAsYule/beastx-direct/testBirthDeathAsYule-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testBirthDeathAsYule/beastx-direct/testBirthDeathAsYule-beastx-direct.run.log
```

### 5. PhyloSpec to BEAST X XML and external BEAST X

Generate the XML:

```bash
mkdir -p validation/target/testBirthDeathAsYule/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testBirthDeathAsYule/testBirthDeathAsYule.phylospec \
  -Dphylospec.validation.runName=testBirthDeathAsYule-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testBirthDeathAsYule/beastx-xml/testBirthDeathAsYule-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testBirthDeathAsYule/beastx-xml/testBirthDeathAsYule-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testBirthDeathAsYule/beastx-xml/testBirthDeathAsYule-beastx-xml.operators.txt \
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
    "$PWD/validation/target/testBirthDeathAsYule/beastx-xml/testBirthDeathAsYule-beastx.xml"
} 2>&1 | tee \
  validation/target/testBirthDeathAsYule/beastx-xml/testBirthDeathAsYule-beastx-xml.run.log

```
