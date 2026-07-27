# testClassicRootCalibrationPrior

## Purpose

This validation example is derived from the BEAST 2.7.7
`testClassicRootCalibrationPrior.xml` example. It extends the current
cross-engine suite with a calibrated tree model and checks that a root-age
constraint is connected to the same Yule tree in all five paths.

The unmodified source XML is preserved as:

- `reference/beast2-original.xml`.

The manually maintained controlled comparison inputs are:

- `reference/beast2-aligned.xml`;
- `testClassicRootCalibrationPrior.phylospec`;
- `testClassicRootCalibrationPrior.nex`.

## Controlled model

All five paths represent:

- four one-site nucleotide sequences;
- `birthRate ~ LogNormal(logMean=0.0, logSd=1.0)`;
- an estimated Yule tree;
- `rootAge(tree) ~ Uniform(0.0, 10.0)`;
- a JC69 likelihood;
- a strict molecular clock fixed to `1.0`;
- a chain length of 5,000,000;
- trace and tree logging every 500 states.

## Why the aligned XML differs from the original

The distributed BEAST 2 example is a prior-focused analytical example. It
uses a LogNormal root calibration and an improper `OneOnX` birth-rate prior,
and does not include its alignment in a tree likelihood.

The current PhyloSpec calibration syntax supported symmetrically by both
backends is a bounded observation:

```text
rootAge(tree=tree) observed between [lower, upper]
```

The aligned model therefore uses a `Uniform(0.0, 10.0)` root calibration,
replaces `OneOnX` with a proper log-normal birth-rate prior, and adds the same
JC69 likelihood to every path. It preserves the defining purpose of the
source example—sampling a Yule tree under a root calibration—while making
the target distribution explicit and comparable.

The zero lower bound is intentional. PhyloSpec currently leaves construction
of the initial Yule tree to each backend. A positive lower calibration bound
can therefore reject an otherwise valid automatically generated starting
tree before MCMC begins. The finite upper bound still exercises root
calibration semantics without making the comparison depend on backend-specific
initial-tree generation.

BEAST 2 and BEAST X conventionally classify an MRCA/root calibration as a
prior. The BEAST 3 observation tile now uses the same classification, while
allowing a tree to retain both its Yule prior and its root-calibration prior.
The primary semantic targets for this example are:

- `posterior`;
- `birthRate`;
- calibrated root age / tree height;
- tree length;
- the root-calibration contribution;
- the JC69 tree likelihood.

The five traces should therefore agree on the reported prior/likelihood
decomposition as well as on the posterior.

## Output layout

```text
validation/target/testClassicRootCalibrationPrior/
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
mkdir -p validation/target/testClassicRootCalibrationPrior/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testClassicRootCalibrationPrior/beast2-reference/" \
    "$PWD/validation/examples/testClassicRootCalibrationPrior/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testClassicRootCalibrationPrior/beast2-reference/testClassicRootCalibrationPrior-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testClassicRootCalibrationPrior/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testClassicRootCalibrationPrior/testClassicRootCalibrationPrior.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testClassicRootCalibrationPrior/beast3-direct/testClassicRootCalibrationPrior-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testClassicRootCalibrationPrior/beast3-direct/testClassicRootCalibrationPrior-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testClassicRootCalibrationPrior/beast3-direct/testClassicRootCalibrationPrior-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testClassicRootCalibrationPrior/beast3-direct/testClassicRootCalibrationPrior-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testClassicRootCalibrationPrior/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testClassicRootCalibrationPrior/testClassicRootCalibrationPrior.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testClassicRootCalibrationPrior/beast3-xml/testClassicRootCalibrationPrior-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testClassicRootCalibrationPrior/beast3-xml/testClassicRootCalibrationPrior-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testClassicRootCalibrationPrior/beast3-xml/testClassicRootCalibrationPrior-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testClassicRootCalibrationPrior/beast3-xml/testClassicRootCalibrationPrior-xml.run.log
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
    "$PWD/validation/target/testClassicRootCalibrationPrior/beast3-xml/testClassicRootCalibrationPrior-beast3.xml"
} 2>&1 | tee -a \
  validation/target/testClassicRootCalibrationPrior/beast3-xml/testClassicRootCalibrationPrior-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testClassicRootCalibrationPrior/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testClassicRootCalibrationPrior/testClassicRootCalibrationPrior.phylospec \
  -Dphylospec.validation.runName=testClassicRootCalibrationPrior-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testClassicRootCalibrationPrior/beastx-direct/testClassicRootCalibrationPrior-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=500 \
  -Dphylospec.validation.expectedLog=validation/target/testClassicRootCalibrationPrior/beastx-direct/testClassicRootCalibrationPrior-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testClassicRootCalibrationPrior/beastx-direct/testClassicRootCalibrationPrior-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testClassicRootCalibrationPrior/beastx-direct/testClassicRootCalibrationPrior-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testClassicRootCalibrationPrior/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testClassicRootCalibrationPrior/testClassicRootCalibrationPrior.phylospec \
  -Dphylospec.validation.runName=testClassicRootCalibrationPrior-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testClassicRootCalibrationPrior/beastx-xml/testClassicRootCalibrationPrior-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=500 \
  -Dphylospec.validation.xml=validation/target/testClassicRootCalibrationPrior/beastx-xml/testClassicRootCalibrationPrior-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testClassicRootCalibrationPrior/beastx-xml/testClassicRootCalibrationPrior-beastx-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testClassicRootCalibrationPrior/beastx-xml/testClassicRootCalibrationPrior-beastx-xml.run.log
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
    "$PWD/validation/target/testClassicRootCalibrationPrior/beastx-xml/testClassicRootCalibrationPrior-beastx.xml"
} 2>&1 | tee -a \
  validation/target/testClassicRootCalibrationPrior/beastx-xml/testClassicRootCalibrationPrior-beastx-xml.run.log
```

## Completion criteria

Before marking this example complete, verify:

1. all five chains contain 5,000,000 states;
2. all five paths use one Yule tree and one root calibration;
3. root ages remain within `[0.0, 10.0]`;
4. birth-rate and root-height posterior intervals overlap;
5. BEAST 3 direct/XML agree;
6. BEAST X direct/XML agree;
7. the root-calibration contribution is included in `prior`, not
   `likelihood`, in every path.
