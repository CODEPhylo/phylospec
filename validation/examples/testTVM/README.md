# testTVM

## Purpose

This example starts from the `testTVM.xml` file distributed with BEAST 2.7.7.
It validates a TVM substitution model represented in PhyloSpec as a
constrained GTR matrix. In particular, it checks that one stochastic
transition-rate parameter can be shared by the `AG` and `CT` slots while the
transversion slots remain fixed or independently estimated.

The controlled inputs are:

- `reference/beast2-original.xml`: the unmodified BEAST 2 example;
- `reference/beast2-aligned.xml`: the controlled BEAST 2 reference;
- `testTVM.phylospec`: the manually maintained PhyloSpec model.

## Controlled model

All five paths represent:

- the 12-taxon, 898-site primate mitochondrial alignment;
- coding and noncoding alignment fragments sharing one tree, clock, site
  model, and substitution model;
- a TVM rate constraint represented as GTR:
  - `rateAC = 1.0` as the fixed reference rate;
  - `rateAG = rateCT = rateTransitions`;
  - `rateAT`, `rateCG`, and `rateGT` estimated independently;
- independent
  `LogNormal(logMean=0, logSd=1.25)` priors on `rateTransitions`, `rateAT`,
  `rateCG`, and `rateGT`;
- estimated base frequencies with a `Dirichlet(1,1,1,1)` prior;
- a shared Yule tree with
  `birthRate ~ Gamma(shape=1, rate=0.001)`;
- a strict clock with its rate fixed to `1.0`;
- a chain length of 5,000,000 and a logging interval of 1,000.

The TVM-to-GTR slot mapping is taken directly from the BEAST 2 `TVM`
implementation. The original BEAST 2 XML uses two disjoint filtered
alignments. PhyloSpec expresses the same sites as five contiguous fragments
because its current `subset` operation accepts one interval at a time. All
five fragments share the same evolutionary model, so the product of their
likelihoods is semantically equivalent to the two filtered BEAST 2
likelihoods.

## Why the aligned XML differs from the original

The aligned BEAST 2 XML preserves the original TVM likelihood and parameter
sharing. It changes only comparison controls:

- the uniform birth-rate prior is replaced by
  `Gamma(shape=1, rate=0.001)`;
- an explicit `Dirichlet(1,1,1,1)` base-frequency prior is added;
- trace and tree output names and the logging interval are standardized.

Run all commands from the repository root:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

## 1. Aligned BEAST 2 reference

```bash
mkdir -p validation/target/testTVM/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testTVM/beast2-reference/" \
    "$PWD/validation/examples/testTVM/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testTVM/beast2-reference/testTVM-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testTVM/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTVM/testTVM.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testTVM/beast3-direct/testTVM-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testTVM/beast3-direct/testTVM-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testTVM/beast3-direct/testTVM-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTVM/beast3-direct/testTVM-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testTVM/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTVM/testTVM.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testTVM/beast3-xml/testTVM-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testTVM/beast3-xml/testTVM-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testTVM/beast3-xml/testTVM-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTVM/beast3-xml/testTVM-xml.run.log
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
    "$PWD/validation/target/testTVM/beast3-xml/testTVM-beast3.xml"
} 2>&1 | tee -a \
  validation/target/testTVM/beast3-xml/testTVM-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testTVM/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTVM/testTVM.phylospec \
  -Dphylospec.validation.runName=testTVM-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testTVM/beastx-direct/testTVM-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testTVM/beastx-direct/testTVM-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testTVM/beastx-direct/testTVM-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTVM/beastx-direct/testTVM-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testTVM/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTVM/testTVM.phylospec \
  -Dphylospec.validation.runName=testTVM-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testTVM/beastx-xml/testTVM-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testTVM/beastx-xml/testTVM-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testTVM/beastx-xml/testTVM-beastx-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTVM/beastx-xml/testTVM-beastx-xml.run.log
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
    "$PWD/validation/target/testTVM/beastx-xml/testTVM-beastx.xml"
} 2>&1 | tee -a \
  validation/target/testTVM/beastx-xml/testTVM-beastx-xml.run.log
```

The direct and external-engine commands run the complete chain. The two export
commands only generate XML.

## Comparison targets

Compare:

- `posterior`, `prior`, and `likelihood`;
- `rateTransitions`, `rateAT`, `rateCG`, and `rateGT`;
- the base-frequency vector;
- `birthRate`;
- tree height and tree length.

The generated models must preserve one `rateTransitions` object referenced by
both `rateAG` and `rateCT`. It must be defined once and receive one parameter
operator. The three estimated transversion rates must remain distinct, while
`rateAC` must remain fixed at `1.0`.
