# testHKY

## Source

This validation example starts from the `testHKY.xml` file distributed with
BEAST 2.7.7. The original file is preserved without modification:

- `reference/beast2-original.xml`

The original is useful as a feature-coverage source, but it is not used
directly for posterior comparison because its statistical model differs from
the manually written PhyloSpec model.

## Controlled model

The comparison model is defined by:

- `testHKY.phylospec`
- `reference/beast2-aligned.xml`

Both represent:

- the six-taxon, 768-site mitochondrial alignment from the BEAST example;
- one HKY substitution model;
- `kappa ~ LogNormal(logMean=1.0, logSd=0.5)`;
- estimated base frequencies with a `Dirichlet(1,1,1,1)` prior;
- an estimated tree with a Yule prior and birth rate fixed to `1.0`;
- a strict molecular clock with rate fixed to `1.0`;
- no gamma-distributed rate variation and no invariant-site category;
- a chain length of 10,000,000 and a logging interval of 1,000.

The BEAST 2 aligned XML contains an explicit strict-clock model. PhyloSpec does
not declare a clock variable, so both backends use their fixed unit-rate clock
semantics.

## Why the original XML is not the comparison reference

The distributed BEAST 2 example:

- uses empirical alignment frequencies rather than estimated frequencies;
- assigns a OneOnX prior to kappa;
- has no tree-prior distribution in the posterior;
- uses the random coalescent tree only as an initializer;
- runs for 5,000,000 states with 50,000 pre-burnin states.

Those differences change the target distribution. The aligned XML changes
these model components explicitly while retaining the original alignment and
the general HKY estimated-tree purpose of the example.

## Validation paths

The five paths are:

1. aligned BEAST 2 XML run;
2. PhyloSpec to BEAST 3 direct run;
3. PhyloSpec to BEAST 3 XML export, followed by external BEAST 3 execution;
4. PhyloSpec to BEAST X direct run;
5. PhyloSpec to BEAST X XML export, followed by external BEAST X execution.

The PhyloSpec translation is maintained manually. The validation runners only
replace the `${REPOSITORY_ROOT}` placeholder and do not infer a model from the
BEAST 2 XML.

## Output layout

Generated results belong under:

```text
validation/target/testHKY/
├── beast2-reference/
├── beast3-direct/
├── beast3-xml/
├── beastx-direct/
└── beastx-xml/
```

The validation entry points create their own output directories. The BEAST 2
reference directory is created by the shell command because that engine is
executed externally.

## Commands

Run every command from the repository root. Before a command that uses `tee`,
enable pipeline failure propagation:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

### 1. Aligned BEAST 2 reference

```bash
mkdir -p validation/target/testHKY/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testHKY/beast2-reference/" \
    "$PWD/validation/examples/testHKY/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testHKY/beast2-reference/testHKY-beast2.run.log
```

### 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testHKY/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testHKY/testHKY.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testHKY/beast3-direct/testHKY-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testHKY/beast3-direct/testHKY-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testHKY/beast3-direct/testHKY-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testHKY/beast3-direct/testHKY-direct.run.log
```

### 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testHKY/testHKY.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testHKY/beast3-xml/testHKY-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testHKY/beast3-xml/testHKY-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testHKY/beast3-xml/testHKY-xml.operators.txt \
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
    "$PWD/validation/target/testHKY/beast3-xml/testHKY-beast3.xml"
} 2>&1 | tee \
  validation/target/testHKY/beast3-xml/testHKY-xml.run.log
```

### 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testHKY/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testHKY/testHKY.phylospec \
  -Dphylospec.validation.runName=testHKY-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testHKY/beastx-direct/testHKY-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testHKY/beastx-direct/testHKY-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testHKY/beastx-direct/testHKY-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testHKY/beastx-direct/testHKY-beastx-direct.run.log
```

### 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testHKY/testHKY.phylospec \
  -Dphylospec.validation.runName=testHKY-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testHKY/beastx-xml/testHKY-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testHKY/beastx-xml/testHKY-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testHKY/beastx-xml/testHKY-beastx-xml.operators.txt \
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
    "$PWD/validation/target/testHKY/beastx-xml/testHKY-beastx.xml"
} 2>&1 | tee \
  validation/target/testHKY/beastx-xml/testHKY-beastx-xml.run.log
```

The direct and external-engine commands run the full 10,000,000-state chain.
XML export and BEAST 2 `-validate` checks do not run the chain.
