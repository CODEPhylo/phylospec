# testTN93

## Purpose

This example starts from the `testTN93.xml` file distributed with BEAST 2.7.7.
It validates a TN93 substitution model represented in PhyloSpec as a
constrained GTR matrix. It checks whether the native TN93 `kappa1` and
`kappa2` parameters remain equivalent to two independently estimated GTR
transition rates while all four transversion rates are fixed to `1.0`.

The controlled inputs are:

- `reference/beast2-original.xml`: the unmodified BEAST 2 example;
- `reference/beast2-aligned.xml`: the controlled BEAST 2 reference;
- `testTN93.phylospec`: the manually maintained PhyloSpec model.

## Controlled model

All five paths represent:

- the 12-taxon, 898-site primate mitochondrial alignment;
- coding and noncoding alignment fragments sharing one tree, clock, site
  model, and substitution model;
- a TN93 rate constraint represented as GTR:
  - `rateAC = rateAT = rateCG = rateGT = 1.0`;
  - `rateAG` and `rateCT` estimated independently;
- independent `LogNormal(logMean=0, logSd=1.25)` priors on `rateAG` and
  `rateCT`;
- estimated base frequencies with a `Dirichlet(1,1,1,1)` prior;
- a shared Yule tree with
  `birthRate ~ Gamma(shape=1, rate=0.001)`;
- a strict clock with its rate fixed to `1.0`;
- a chain length of 5,000,000 and a logging interval of 1,000.

The TN93-to-GTR slot mapping is taken directly from the BEAST 2 `TN93`
implementation. The original BEAST 2 XML uses two disjoint filtered
alignments. PhyloSpec expresses the same sites as five contiguous fragments
because its current `subset` operation accepts one interval at a time. All
five fragments share the same evolutionary model, so the product of their
likelihoods is semantically equivalent to the two filtered BEAST 2
likelihoods.

## Why the aligned XML differs from the original

The aligned BEAST 2 XML preserves the original TN93 likelihood and parameter
mapping. It changes only comparison controls:

- the uniform birth-rate prior is replaced by
  `Gamma(shape=1, rate=0.001)`;
- explicit `LogNormal(logMean=0, logSd=1.25)` priors are added to the two
  transition-rate parameters, which were unprioritized in the original XML;
- an explicit `Dirichlet(1,1,1,1)` base-frequency prior is added;
- trace and tree output names and the logging interval are standardized.

Run all commands from the repository root:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

## 1. Aligned BEAST 2 reference

```bash
mkdir -p validation/target/testTN93/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testTN93/beast2-reference/" \
    "$PWD/validation/examples/testTN93/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testTN93/beast2-reference/testTN93-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testTN93/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTN93/testTN93.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testTN93/beast3-direct/testTN93-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testTN93/beast3-direct/testTN93-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testTN93/beast3-direct/testTN93-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTN93/beast3-direct/testTN93-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testTN93/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTN93/testTN93.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testTN93/beast3-xml/testTN93-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testTN93/beast3-xml/testTN93-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testTN93/beast3-xml/testTN93-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTN93/beast3-xml/testTN93-xml.run.log
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
    "$PWD/validation/target/testTN93/beast3-xml/testTN93-beast3.xml"
} 2>&1 | tee -a \
  validation/target/testTN93/beast3-xml/testTN93-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testTN93/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTN93/testTN93.phylospec \
  -Dphylospec.validation.runName=testTN93-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testTN93/beastx-direct/testTN93-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testTN93/beastx-direct/testTN93-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testTN93/beastx-direct/testTN93-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTN93/beastx-direct/testTN93-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testTN93/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testTN93/testTN93.phylospec \
  -Dphylospec.validation.runName=testTN93-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testTN93/beastx-xml/testTN93-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testTN93/beastx-xml/testTN93-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testTN93/beastx-xml/testTN93-beastx-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testTN93/beastx-xml/testTN93-beastx-xml.run.log
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
    "$PWD/validation/target/testTN93/beastx-xml/testTN93-beastx.xml"
} 2>&1 | tee -a \
  validation/target/testTN93/beastx-xml/testTN93-beastx-xml.run.log
```

The direct and external-engine commands run the complete chain. The two export
commands only generate XML.

## Comparison targets

Compare:

- `posterior`, `prior`, and `likelihood`;
- `rateAG` and `rateCT`;
- the base-frequency vector;
- `birthRate`;
- tree height and tree length.

The generated models must preserve two distinct stochastic transition
parameters and four fixed transversion slots. Each stochastic parameter must
be defined once and receive one parameter operator.

## BEAST X XML reference-rate handling

BEAST X represents a stochastic GTR model with five named rates and treats
the omitted sixth rate as the fixed reference value `1.0`. TN93 has four
rates fixed to `1.0`, so the exporter deterministically omits the first fixed
slot (`rateAC`) and writes the other three fixed slots explicitly. The two
stochastic transition rates (`rateAG` and `rateCT`) remain separate parameter
references and each receives one operator.

This example exposed an exporter limitation that previously rejected models
with more than one fixed rate. `BeastXXmlTN93Test` now covers the corrected
mapping. Path 5 verifies that the external BEAST X parser accepts the
generated XML before running the chain.

## Five-path result (2026-07-26)

All five paths completed 5,000,000 states and produced 5,001 trace rows,
including state zero. After discarding the first 10% of each chain, every
comparison used 4,501 samples.

The common posterior summaries agree:

- posterior mean: `-5995.467` to `-5995.301`;
- likelihood mean: `-5993.510` to `-5993.318`;
- tree-height mean: `0.25035` to `0.25065`;
- birth-rate mean: `7.669` to `7.804`;
- `rateAG` mean: `4.142` to `4.180`;
- `rateCT` mean: `5.445` to `5.468`.

The four base-frequency means also agree across all paths. No engine-specific
grouping or direct/XML separation remains. This supports semantic parity for
the controlled TN93 example; it is not a claim that every model supported by
the backends is already covered.
