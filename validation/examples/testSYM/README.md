# testSYM

## Purpose

This example starts from the `testSYM.xml` file distributed with BEAST 2.7.7.
It validates a SYM substitution model represented in PhyloSpec as a
GTR matrix with one fixed reference rate. In particular, it checks that all
five non-reference exchangeability rates remain distinct stochastic
parameters in both backends and both XML exporters.

The controlled inputs are:

- `reference/beast2-original.xml`: the unmodified BEAST 2 example;
- `reference/beast2-aligned.xml`: the controlled BEAST 2 reference;
- `testSYM.phylospec`: the manually maintained PhyloSpec model.

## Controlled model

All five paths represent:

- the 12-taxon, 898-site primate mitochondrial alignment;
- coding and noncoding alignment fragments sharing one tree, clock, site
  model, and substitution model;
- a SYM rate constraint represented as GTR:
  - `rateCT = 1.0` as the fixed reference rate;
  - `rateAC`, `rateAG`, `rateAT`, `rateCG`, and `rateGT` estimated
    independently;
- independent
  `LogNormal(logMean=0, logSd=1.25)` priors on all five stochastic rates;
- estimated base frequencies with a `Dirichlet(1,1,1,1)` prior;
- a shared Yule tree with
  `birthRate ~ Gamma(shape=1, rate=0.001)`;
- a strict clock with its rate fixed to `1.0`;
- a chain length of 5,000,000 and a logging interval of 1,000.

The distributed BEAST 2 example expresses SYM directly as a GTR model with
`rateCT=1.0`. The original XML uses two disjoint filtered alignments.
PhyloSpec expresses the same sites as five contiguous fragments because its
current `subset` operation accepts one interval at a time. All five fragments
share the same evolutionary model, so the product of their likelihoods is
semantically equivalent to the two filtered BEAST 2 likelihoods.

## Why the aligned XML differs from the original

The aligned BEAST 2 XML preserves the original SYM/GTR likelihood and five
independent stochastic rate parameters. It changes only comparison controls:

- the uniform birth-rate prior is replaced by
  `Gamma(shape=1, rate=0.001)`;
- an explicit `Dirichlet(1,1,1,1)` base-frequency prior is added;
- a base-frequency delta-exchange operator is added for the estimated
  simplex;
- trace and tree output names and the logging interval are standardized.

Run all commands from the repository root:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

## 1. Aligned BEAST 2 reference

```bash
mkdir -p validation/target/testSYM/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testSYM/beast2-reference/" \
    "$PWD/validation/examples/testSYM/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testSYM/beast2-reference/testSYM-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testSYM/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSYM/testSYM.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testSYM/beast3-direct/testSYM-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testSYM/beast3-direct/testSYM-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testSYM/beast3-direct/testSYM-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSYM/beast3-direct/testSYM-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testSYM/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSYM/testSYM.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testSYM/beast3-xml/testSYM-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testSYM/beast3-xml/testSYM-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testSYM/beast3-xml/testSYM-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSYM/beast3-xml/testSYM-xml.run.log
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
    "$PWD/validation/target/testSYM/beast3-xml/testSYM-beast3.xml"
} 2>&1 | tee -a \
  validation/target/testSYM/beast3-xml/testSYM-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testSYM/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSYM/testSYM.phylospec \
  -Dphylospec.validation.runName=testSYM-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testSYM/beastx-direct/testSYM-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testSYM/beastx-direct/testSYM-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testSYM/beastx-direct/testSYM-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSYM/beastx-direct/testSYM-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testSYM/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testSYM/testSYM.phylospec \
  -Dphylospec.validation.runName=testSYM-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testSYM/beastx-xml/testSYM-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testSYM/beastx-xml/testSYM-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testSYM/beastx-xml/testSYM-beastx-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testSYM/beastx-xml/testSYM-beastx-xml.run.log
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
    "$PWD/validation/target/testSYM/beastx-xml/testSYM-beastx.xml"
} 2>&1 | tee -a \
  validation/target/testSYM/beastx-xml/testSYM-beastx-xml.run.log
```

The direct and external-engine commands run the complete chain. The two export
commands only generate XML.

## Comparison targets

Compare:

- `posterior`, `prior`, and `likelihood`;
- `rateAC`, `rateAG`, `rateAT`, `rateCG`, and `rateGT`;
- the base-frequency vector;
- `birthRate`;
- tree height and tree length.

The generated models must preserve five distinct stochastic rate parameters,
each defined once and each receiving one parameter operator. `rateCT` must
remain fixed at `1.0` and serve as the omitted reference rate in BEAST X XML.

## Five-path result (2026-07-26)

All five paths completed the 5,000,000-state chain and wrote 5,001 trace
rows, including state zero. With a 10% burn-in, each comparison contains
4,501 samples.

The five paths agree on the shared target quantities:

- posterior means range from `-5948.411` to `-5948.103`;
- likelihood means range from `-5949.619` to `-5949.336`;
- tree-height means range from `0.24484` to `0.24538`;
- birth-rate means range from `7.825` to `7.967`;
- `rateAC` means range from `0.3203` to `0.3227`;
- `rateAG` means range from `0.7345` to `0.7395`;
- `rateAT` means range from `0.15105` to `0.15187`;
- `rateCG` means range from `0.08105` to `0.08385`;
- `rateGT` means range from `0.03549` to `0.03595`;
- all four base-frequency components show the same agreement.

The small between-run differences are minor relative to the marginal
posterior standard deviations and do not separate by engine or by direct
versus exported-XML execution. This supports five-path semantic parity for
the controlled SYM model. It is evidence for the specific tiles and model
features exercised here, rather than a claim that every PhyloSpec model is
already covered.
