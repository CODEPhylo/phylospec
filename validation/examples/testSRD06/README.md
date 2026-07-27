# testSRD06

## Purpose

This example extends the validated `testCoalescent` model by changing one
major component only: one nucleotide alignment is divided into two SRD06
model partitions.

The source example is the `testSRD06.xml` file distributed with BEAST 2.7.7.
Its unmodified contents are preserved as:

- `reference/beast2-original.xml`

The manually written PhyloSpec model and the controlled BEAST 2 comparison
reference are:

- `testSRD06.phylospec`
- `reference/beast2-aligned.xml`

The validation workflow does not translate the original BEAST 2 XML into
PhyloSpec. The PhyloSpec source is maintained manually.

## Controlled model

All five comparison paths represent:

- the 12-taxon, 898-site primate mitochondrial alignment from the example;
- codon positions 1 and 2 sharing one HKY substitution model;
- codon position 3 using a second HKY substitution model;
- one independently estimated `kappa` and one independently estimated base
  frequency vector for each model partition;
- `firstSecondKappa ~ LogNormal(logMean=1.0, logSd=0.5)`;
- `thirdKappa ~ LogNormal(logMean=1.0, logSd=0.5)`;
- both base frequency vectors having a `Dirichlet(1,1,1,1)` prior;
- `populationSize ~ LogNormal(logMean=0.0, logSd=1.0)`;
- one estimated tree shared by every likelihood component;
- a constant-population coalescent tree prior;
- one strict molecular clock with rate fixed to `1.0`;
- no gamma-distributed rate variation and no invariant-site category;
- a chain length of 10,000,000 and a logging interval of 1,000.

PhyloSpec's `subset` function selects one codon position at a time. Positions
1 and 2 are therefore represented by two likelihood components that share the
same HKY matrix. Position 3 has a third likelihood component and its own HKY
matrix. The aligned BEAST 2 XML uses the same three-component representation.
This is likelihood-equivalent to concatenating positions 1 and 2 into one
filtered alignment because sites are conditionally independent under the
shared model.

## Why the original XML is not the comparison reference

The distributed BEAST 2 example uses `OneOnX` priors for both kappa
parameters, does not place explicit priors on the two estimated frequency
vectors, fixes its initial coalescent population size, and contains two
additional state parameters that are not connected to either likelihood.
It also uses a different logging interval and combines codon positions 1 and
2 into one filtered alignment.

Those differences prevent a controlled comparison with the current PhyloSpec
backends. The aligned XML retains the original alignment, SRD06 partitioning,
two HKY models, shared tree, and fixed unit clock while making every
stochastic parameter and prior explicit. It should therefore be described as
an aligned validation model derived from the BEAST 2 `testSRD06.xml` example,
not as a verbatim reproduction.

## Output layout

Generated files belong under:

```text
validation/target/testSRD06/
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
mkdir -p validation/target/testSRD06/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testSRD06/beast2-reference/" \
    "$PWD/validation/examples/testSRD06/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testSRD06/beast2-reference/testSRD06-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testSRD06/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSRD06/testSRD06.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testSRD06/beast3-direct/testSRD06-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testSRD06/beast3-direct/testSRD06-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testSRD06/beast3-direct/testSRD06-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSRD06/beast3-direct/testSRD06-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testSRD06/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSRD06/testSRD06.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testSRD06/beast3-xml/testSRD06-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testSRD06/beast3-xml/testSRD06-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testSRD06/beast3-xml/testSRD06-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSRD06/beast3-xml/testSRD06-xml.run.log
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
    "$PWD/validation/target/testSRD06/beast3-xml/testSRD06-beast3.xml"
} 2>&1 | tee -a \
  validation/target/testSRD06/beast3-xml/testSRD06-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testSRD06/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSRD06/testSRD06.phylospec \
  -Dphylospec.validation.runName=testSRD06-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testSRD06/beastx-direct/testSRD06-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testSRD06/beastx-direct/testSRD06-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testSRD06/beastx-direct/testSRD06-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSRD06/beastx-direct/testSRD06-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testSRD06/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSRD06/testSRD06.phylospec \
  -Dphylospec.validation.runName=testSRD06-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testSRD06/beastx-xml/testSRD06-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testSRD06/beastx-xml/testSRD06-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testSRD06/beastx-xml/testSRD06-beastx-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSRD06/beastx-xml/testSRD06-beastx-xml.run.log
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
    "$PWD/validation/target/testSRD06/beastx-xml/testSRD06-beastx.xml"
} 2>&1 | tee -a \
  validation/target/testSRD06/beastx-xml/testSRD06-beastx-xml.run.log
```

The direct and external-engine commands run the full 10,000,000-state chain.
XML export and static validation checks do not run the chain.

## Comparison traces

The primary shared traces are:

- `posterior`;
- `prior`;
- `likelihood`;
- the three partition likelihood components;
- `firstSecondKappa`;
- `firstSecondBaseFrequencies`;
- `thirdKappa`;
- `thirdBaseFrequencies`;
- `populationSize`;
- tree height;
- tree length.

The three component likelihood values may be logged under engine-specific
column names. The total likelihood and shared stochastic parameters are the
primary cross-engine comparison targets. Operator schedules and starting
trees may differ across engines; they affect sampling efficiency but not the
controlled target distribution.

## BEAST 3 codon-subset regression found by this example

The first five-path run exposed a BEAST 3 backend bug in `SubsetTile`. For
`codonPosition=1`, `2`, and `3`, the backend generated the filters `1-/1`,
`1-/2`, and `1-/3`. BEAST 3 does not interpret the slash suffix as an iterator
step, so every filter selected all 898 sites. The direct and XML paths
therefore reported 413 unique patterns for each likelihood component, while
BEAST 2 and BEAST X correctly reported 207, 163, and 109 patterns.

This made the incorrect BEAST 3 likelihood equivalent to evaluating the full
alignment three times, rather than evaluating the three codon partitions. It
explains why the BEAST 3 total log likelihood was approximately three times
larger in magnitude than the BEAST 2 and BEAST X values. This was a model
semantics error, not an operator, random-seed, or MCMC mixing difference.

The BEAST 3 tile now emits BEAST iterator syntax:

```text
codonPosition=1 -> 1::3
codonPosition=2 -> 2::3
codonPosition=3 -> 3::3
```

When `start` or `end` is also provided, the first selected site is adjusted so
that codon positions remain relative to the original alignment, matching the
BEAST X backend. `SubsetTileCodonPositionTest` protects the filter syntax and
the expected 300/299/299 site counts for an 898-site alignment.

After rebuilding, regenerate the BEAST 3 XML before rerunning it. The generated
XML and the BEAST 3 direct run should report partition site counts of
300/299/299 (and, for this alignment, pattern counts of 207/163/109).
