# testSliceHKY

## Purpose

This example is derived from `testSliceHKY.xml`, distributed with BEAST
2.7.7. It tests whether different valid operator schedules for the HKY kappa
parameter sample the same controlled posterior.

The example uses:

- `testSliceHKY.phylospec`: the manually maintained PhyloSpec model;
- `reference/beast2-aligned.xml`: the controlled BEAST 2 reference;
- `../testHKY/testHKY.nex`: the canonical copy of the original six-taxon,
  768-site alignment shared with the earlier HKY comparison.

The PhyloSpec source is not generated from the XML.

## Controlled model

All five paths represent:

- the original six-taxon, 768-site nucleotide alignment;
- an HKY substitution model;
- `kappa ~ LogNormal(logMean=1.0, logSd=0.5)`;
- fixed empirical A/C/G/T frequencies:
  `[0.3390942900896959, 0.2465543644716692,
  0.1531393568147014, 0.2612119886239335]`;
- an estimated tree with a Yule prior and birth rate fixed to `1.0`;
- a strict molecular clock fixed to `1.0`;
- one site-rate category, with no gamma-distributed variation and no
  invariant-site component;
- a chain length of 10,000,000 and random seed `1234`.

The empirical frequencies were calculated from the 4,571 unambiguous
nucleotides in the shared alignment.

## Operator comparison

The aligned BEAST 2 XML deliberately retains both kappa proposals that define
the purpose of the source example:

- `ScaleOperator`;
- `SliceOperator`.

PhyloSpec currently delegates operator selection to each backend, so BEAST 3
and BEAST X are not required to generate a `SliceOperator`. Operator names,
weights, and tuning affect efficiency but not the target density when the
proposals are valid.

The test therefore has two separate outcomes:

1. posterior equality checks model semantics;
2. ESS, acceptance, tuning, and runtime compare sampling efficiency.

## Why the aligned XML differs from the original

The distributed XML is not suitable as a cross-engine posterior reference:

- it has no tree prior;
- its optional gamma-shape and invariant-proportion state are commented out;
- its kappa `OneOnX` prior is commented out;
- it uses empirical frequencies indirectly through the alignment;
- its posterior contains only the likelihood;
- it combines a scale proposal and slice proposal without defining a proper
  kappa prior.

The controlled comparison makes the target proper and explicit. It adds the
Yule and log-normal densities, fixes the empirical frequencies numerically,
and uses the same strict-clock semantics in every path. It does not activate
the source XML sections that are commented out.

## Output layout

```text
validation/target/testSliceHKY/
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
mkdir -p validation/target/testSliceHKY/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testSliceHKY/beast2-reference/" \
    "$PWD/validation/examples/testSliceHKY/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testSliceHKY/beast2-reference/testSliceHKY-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testSliceHKY/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSliceHKY/testSliceHKY.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testSliceHKY/beast3-direct/testSliceHKY-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testSliceHKY/beast3-direct/testSliceHKY-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testSliceHKY/beast3-direct/testSliceHKY-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSliceHKY/beast3-direct/testSliceHKY-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML to external BEAST 3

Generate the XML:

```bash
mkdir -p validation/target/testSliceHKY/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSliceHKY/testSliceHKY.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testSliceHKY/beast3-xml/testSliceHKY-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testSliceHKY/beast3-xml/testSliceHKY-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testSliceHKY/beast3-xml/testSliceHKY-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSliceHKY/beast3-xml/testSliceHKY-xml.run.log
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
    "$PWD/validation/target/testSliceHKY/beast3-xml/testSliceHKY-beast3.xml"
} 2>&1 | tee -a \
  validation/target/testSliceHKY/beast3-xml/testSliceHKY-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testSliceHKY/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSliceHKY/testSliceHKY.phylospec \
  -Dphylospec.validation.runName=testSliceHKY-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testSliceHKY/beastx-direct/testSliceHKY-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testSliceHKY/beastx-direct/testSliceHKY-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testSliceHKY/beastx-direct/testSliceHKY-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSliceHKY/beastx-direct/testSliceHKY-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML to external BEAST X

Generate the XML:

```bash
mkdir -p validation/target/testSliceHKY/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSliceHKY/testSliceHKY.phylospec \
  -Dphylospec.validation.runName=testSliceHKY-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testSliceHKY/beastx-xml/testSliceHKY-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testSliceHKY/beastx-xml/testSliceHKY-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testSliceHKY/beastx-xml/testSliceHKY-beastx-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSliceHKY/beastx-xml/testSliceHKY-beastx-xml.run.log
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
    "$PWD/validation/target/testSliceHKY/beastx-xml/testSliceHKY-beastx.xml"
} 2>&1 | tee -a \
  validation/target/testSliceHKY/beastx-xml/testSliceHKY-beastx-xml.run.log
```

The direct and external-engine commands run the complete chain. The two
export commands generate XML and operator summaries before the external
runs.

## Comparison targets

Compare the following shared model quantities:

- `posterior`, `prior`, and `likelihood`;
- `kappa`;
- the kappa log-normal density;
- the Yule tree-prior density;
- tree height and tree length.

Compare operator performance separately:

- operator type and weight;
- final tuning value;
- acceptance probability;
- kappa ESS;
- MCMC wall-clock runtime.

Before comparing posterior marginals, confirm:

1. all paths use the same six taxa and 768 sites;
2. base frequencies are fixed and absent from the MCMC state;
3. only `kappa` and the tree are sampled;
4. the same log-normal kappa prior and Yule prior are connected;
5. strict-clock rate and Yule birth rate are fixed to `1.0`;
6. BEAST 2 contains both the scale and slice kappa operators;
7. backend operator differences are treated as efficiency differences, not
   model-semantic differences.

This controlled example should be marked complete only after all five paths
finish, the target distributions agree, and the operator diagnostics have
been recorded.
