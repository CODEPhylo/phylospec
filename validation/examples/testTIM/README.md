# testTIM

## Purpose

This example starts from the `testTIM.xml` file distributed with BEAST 2.7.7.
It validates a TIM substitution model represented in PhyloSpec as a
constrained GTR matrix. In particular, it checks that two different stochastic
parameters can each be shared by a different pair of GTR rate slots.

The controlled inputs are:

- `reference/beast2-original.xml`: the unmodified BEAST 2 example;
- `reference/beast2-aligned.xml`: the controlled BEAST 2 reference;
- `testTIM.phylospec`: the manually maintained PhyloSpec model.

## Controlled model

All five paths represent:

- the 12-taxon, 898-site primate mitochondrial alignment;
- coding and noncoding alignment fragments sharing one tree, clock, site
  model, and substitution model;
- a TIM rate constraint represented as GTR:
  - `rateAC = rateGT = rateTransversions1`;
  - `rateAT = rateCG = rateTransversions2`;
  - `rateAG` estimated independently;
  - `rateCT = 1.0` as the fixed reference rate;
- independent
  `LogNormal(logMean=0, logSd=1.25)` priors on `rateAG`,
  `rateTransversions1`, and `rateTransversions2`;
- estimated base frequencies with a `Dirichlet(1,1,1,1)` prior;
- a shared Yule tree with
  `birthRate ~ Gamma(shape=1, rate=0.001)`;
- a strict clock with its rate fixed to `1.0`;
- a chain length of 5,000,000 and a logging interval of 1,000.

The TIM-to-GTR slot mapping is taken directly from the BEAST 2 `TIM`
implementation. The original BEAST 2 XML uses two disjoint filtered
alignments. PhyloSpec expresses the same sites as five contiguous fragments
because its current `subset` operation accepts one interval at a time. All
five fragments share the same evolutionary model, so the product of their
likelihoods is semantically equivalent to the two filtered BEAST 2
likelihoods.

## Why the aligned XML differs from the original

The aligned BEAST 2 XML preserves the original TIM likelihood and parameter
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
mkdir -p validation/target/testTIM/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testTIM/beast2-reference/" \
    "$PWD/validation/examples/testTIM/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testTIM/beast2-reference/testTIM-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testTIM/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTIM/testTIM.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testTIM/beast3-direct/testTIM-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testTIM/beast3-direct/testTIM-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testTIM/beast3-direct/testTIM-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTIM/beast3-direct/testTIM-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testTIM/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTIM/testTIM.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testTIM/beast3-xml/testTIM-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testTIM/beast3-xml/testTIM-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testTIM/beast3-xml/testTIM-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTIM/beast3-xml/testTIM-xml.run.log
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
    "$PWD/validation/target/testTIM/beast3-xml/testTIM-beast3.xml"
} 2>&1 | tee -a \
  validation/target/testTIM/beast3-xml/testTIM-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testTIM/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTIM/testTIM.phylospec \
  -Dphylospec.validation.runName=testTIM-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testTIM/beastx-direct/testTIM-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testTIM/beastx-direct/testTIM-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testTIM/beastx-direct/testTIM-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTIM/beastx-direct/testTIM-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testTIM/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTIM/testTIM.phylospec \
  -Dphylospec.validation.runName=testTIM-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testTIM/beastx-xml/testTIM-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testTIM/beastx-xml/testTIM-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testTIM/beastx-xml/testTIM-beastx-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTIM/beastx-xml/testTIM-beastx-xml.run.log
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
    "$PWD/validation/target/testTIM/beastx-xml/testTIM-beastx.xml"
} 2>&1 | tee -a \
  validation/target/testTIM/beastx-xml/testTIM-beastx-xml.run.log
```

The direct and external-engine commands run the complete chain. The two export
commands only generate XML.

## Comparison targets

Compare:

- `posterior`, `prior`, and `likelihood`;
- `rateAG`, `rateTransversions1`, and `rateTransversions2`;
- the base-frequency vector;
- `birthRate`;
- tree height and tree length.

The generated models must preserve both identities:

- one `rateTransversions1` object referenced by `rateAC` and `rateGT`;
- one `rateTransversions2` object referenced by `rateAT` and `rateCG`.

Each shared parameter must be defined once and receive one parameter operator.
