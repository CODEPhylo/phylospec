# testRestrictedGTR

## Purpose

This example starts from the `testRestrictedGTR.xml` file distributed with
BEAST 2.7.7. It tests whether a parameter shared by several inputs of one
substitution model retains that identity in both PhyloSpec backends and in
their exported XML.

The source and controlled comparison files are:

- `reference/beast2-original.xml`: unmodified BEAST 2 example;
- `reference/beast2-aligned.xml`: controlled BEAST 2 reference;
- `testRestrictedGTR.phylospec`: manually maintained PhyloSpec model.

## Controlled model

All five paths represent:

- the 12-taxon, 898-site primate mitochondrial alignment;
- five alignment fragments sharing one GTR model and one tree;
- `rateAC`, `rateAT`, `rateCG`, and `rateGT` bound to the same estimated
  `rateTransversion` parameter;
- a separately estimated `rateAG`;
- `rateCT` fixed to `1.0`;
- `rateTransversion ~ LogNormal(logMean=0, logSd=1.25)`;
- `rateAG ~ LogNormal(logMean=0, logSd=1.25)`;
- estimated base frequencies with a `Dirichlet(1,1,1,1)` prior;
- a shared Yule tree with
  `birthRate ~ Gamma(shape=1, rate=0.001)`;
- one strict clock with rate fixed to `1.0`;
- a chain length of 5,000,000 and a logging interval of 1,000.

The original BEAST 2 XML uses two filtered likelihood components, whereas
PhyloSpec uses five contiguous fragments. Because all fragments share the same
tree, clock, site model, and GTR matrix, the two representations have the same
likelihood semantics.

## Why the aligned XML differs from the original

The aligned BEAST 2 XML preserves the restricted-GTR parameter sharing and
data model. It changes only comparison controls:

- the original uniform birth-rate prior is replaced by the explicit Gamma
  prior used in PhyloSpec;
- an explicit `Dirichlet(1,1,1,1)` frequency prior is added;
- the logging interval and output names are standardized.

## Output layout

```text
validation/target/testRestrictedGTR/
├── beast2-reference/
├── beast3-direct/
├── beast3-xml/
├── beastx-direct/
└── beastx-xml/
```

Run every command from the repository root:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

## 1. Aligned BEAST 2 reference

```bash
mkdir -p validation/target/testRestrictedGTR/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testRestrictedGTR/beast2-reference/" \
    "$PWD/validation/examples/testRestrictedGTR/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testRestrictedGTR/beast2-reference/testRestrictedGTR-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testRestrictedGTR/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testRestrictedGTR/testRestrictedGTR.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testRestrictedGTR/beast3-direct/testRestrictedGTR-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testRestrictedGTR/beast3-direct/testRestrictedGTR-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testRestrictedGTR/beast3-direct/testRestrictedGTR-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testRestrictedGTR/beast3-direct/testRestrictedGTR-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testRestrictedGTR/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testRestrictedGTR/testRestrictedGTR.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testRestrictedGTR/beast3-xml/testRestrictedGTR-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testRestrictedGTR/beast3-xml/testRestrictedGTR-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testRestrictedGTR/beast3-xml/testRestrictedGTR-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testRestrictedGTR/beast3-xml/testRestrictedGTR-xml.run.log
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
    "$PWD/validation/target/testRestrictedGTR/beast3-xml/testRestrictedGTR-beast3.xml"
} 2>&1 | tee -a \
  validation/target/testRestrictedGTR/beast3-xml/testRestrictedGTR-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testRestrictedGTR/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testRestrictedGTR/testRestrictedGTR.phylospec \
  -Dphylospec.validation.runName=testRestrictedGTR-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testRestrictedGTR/beastx-direct/testRestrictedGTR-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testRestrictedGTR/beastx-direct/testRestrictedGTR-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testRestrictedGTR/beastx-direct/testRestrictedGTR-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testRestrictedGTR/beastx-direct/testRestrictedGTR-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testRestrictedGTR/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testRestrictedGTR/testRestrictedGTR.phylospec \
  -Dphylospec.validation.runName=testRestrictedGTR-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testRestrictedGTR/beastx-xml/testRestrictedGTR-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testRestrictedGTR/beastx-xml/testRestrictedGTR-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testRestrictedGTR/beastx-xml/testRestrictedGTR-beastx-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testRestrictedGTR/beastx-xml/testRestrictedGTR-beastx-xml.run.log
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
    "$PWD/validation/target/testRestrictedGTR/beastx-xml/testRestrictedGTR-beastx.xml"
} 2>&1 | tee -a \
  validation/target/testRestrictedGTR/beastx-xml/testRestrictedGTR-beastx-xml.run.log
```

The direct and external-engine commands run the full chain. The two export
commands only build and write XML.

## Comparison targets

The primary shared traces are:

- `posterior`, `prior`, and `likelihood`;
- `rateTransversion` and `rateAG`;
- the base-frequency vector;
- `birthRate`;
- tree height and tree length.

The generated direct model and XML must contain one stochastic
`rateTransversion` object referenced by four GTR rate inputs, and only one
operator for that shared parameter. Four independently created transversion
parameters would be a semantic failure even if their names or priors looked
similar.

## BEAST X shared-rate export regression found by this example

The first export attempt exposed a BEAST X XML-builder limitation. The
in-memory BEAST X `GTR` object registers each distinct variable once, so
iterating over `gtr.getVariableCount()` returns fewer than six variables when
one parameter is shared across several rate slots. The exporter incorrectly
treated this deduplicated variable list as the positional
`AC, AG, AT, CG, CT, GT` list and rejected the model.

`SubstitutionModelXmlBuilder` now reads the six named GTR rate slots in their
fixed positional order. Repeated slots remain repeated references to the same
parameter. The focused regression test verifies that:

- `rateAC`, `rateAT`, `rateCG`, and `rateGT` all reference
  `rateTransversion`;
- `rateCT=1.0` remains the omitted BEAST X reference rate;
- `rateTransversion` is defined once;
- only one scale operator is generated for it;
- the resulting XML is accepted by the BEAST X parser.
