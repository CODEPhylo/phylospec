# testMultipleAlignments

## Purpose

This validation example is derived from the BEAST 2.7.7
`testMultipleAlignments_randomTaxaOrder.xml` example. It checks that
PhyloSpec preserves model linkage when three alignments contain the same taxa
in different orders.

The original BEAST 2 XML is preserved verbatim as:

- `reference/beast2-original.xml`

The controlled comparison inputs are:

- `reference/beast2-aligned.xml`;
- `testMultipleAlignments.phylospec`;
- `data/gene1.nex`, `data/gene2.nex`, and `data/gene3.nex`.

The PhyloSpec source is maintained manually. The three NEXUS files are a
mechanical extraction of the three alignment blocks in the original XML.
An `__ageN` suffix was added to each taxon id so both backends can recover
the explicit tip ages through the same `fromNexus(..., age=parse(...))`
interface. Taxon names are labels and this suffix does not change the
alignment or probabilistic model.

## Controlled model

All five paths represent:

- three 6-taxon, 10,000-site nucleotide alignments;
- identical taxon sets presented in three different orders;
- three independent trees;
- three constant-population coalescent tree priors;
- one population-size parameter shared by all three tree priors;
- one HKY substitution model shared by all three likelihoods;
- one kappa parameter shared by all three likelihoods;
- fixed equal base frequencies;
- one four-category discrete-gamma site model shared by all likelihoods;
- one gamma-shape parameter shared by all likelihoods;
- one strict-clock rate shared across all three trees;
- the original relative tip ages;
- a chain length of 10,000,000 and trace logging every 1,000 states.

The controlled priors are:

- `populationSize ~ LogNormal(logMean=11.831743, logSd=1.0)`;
- `kappa ~ LogNormal(logMean=1.0, logSd=1.25)`;
- `gammaShape ~ Gamma(shape=1.0, rate=1.0)`, which is distributionally
  identical to the `Exponential(rate=1.0)` prior in the aligned BEAST 2 XML
  while preserving the positive-real domain required by the BEAST 3
  `DiscreteGammaInv` tile;
- `clockRate ~ LogNormal(logMean=-18.420681, logSd=0.5)`.

## Why the aligned XML differs from the original

The distributed example uses improper `OneOnX` priors for population size
and clock rate, and empirical frequencies from the first alignment.
PhyloSpec does not currently expose those exact nucleotide-frequency and
improper-prior declarations symmetrically in both backends.

The aligned XML therefore keeps the defining multi-alignment structure,
dated taxa, three trees, three coalescent priors, shared HKY/gamma/clock
model, and random taxon ordering, while replacing:

- the two `OneOnX` priors with the proper log-normal priors above;
- empirical base frequencies with fixed equal frequencies;
- the 100,000,000-state chain with a controlled 10,000,000-state chain.

The aligned XML also includes an explicit population-size scale operator.
The distributed example places population size in the state and prior but
does not provide an operator for it, so that parameter otherwise remains
fixed at its initial value. A fixed population size is not equivalent to the
PhyloSpec model, in which the shared population-size parameter is sampled.

## Output layout

```text
validation/target/testMultipleAlignments/
├── beast2-reference/
├── beast3-direct/
├── beast3-xml/
├── beastx-direct/
└── beastx-xml/
```

Because this model contains three independent trees, the BEAST X validation
paths create one tree log per tree:

- `*.tree_1.trees`;
- `*.tree_2.trees`;
- `*.tree_3.trees`.

BEAST X requires exactly one tree in each `<logTree>` element. The validation
wrapper keeps the files separate until the generic BEAST X XML logger builder
is updated to expand a multi-tree logger specification in the same way.

The indexed declarations in `testMultipleAlignments.phylospec` are also
intentional. An earlier manually expanded form caused the BEAST 3 backend to
materialize every named tree three times, producing nine state trees and nine
coalescent priors while only three trees were connected to likelihoods. That
old output is not semantically comparable. Both backends now export exactly
three trees, three coalescent priors, and three tree likelihoods from the
indexed form.

Run all commands from the repository root:

```bash
cd /Users/adm-hhua361/Desktop/phylospec
set -o pipefail
```

## 1. Aligned BEAST 2 reference

```bash
mkdir -p validation/target/testMultipleAlignments/beast2-reference

{
  /usr/bin/time -p "/Applications/BEAST 2.7.7/bin/beast" \
    -beagle \
    -beagle_CPU \
    -beagle_double \
    -overwrite \
    -seed 1234 \
    -prefix "$PWD/validation/target/testMultipleAlignments/beast2-reference/" \
    "$PWD/validation/examples/testMultipleAlignments/reference/beast2-aligned.xml"
} 2>&1 | tee \
  validation/target/testMultipleAlignments/beast2-reference/testMultipleAlignments-beast2.run.log
```

## 2. PhyloSpec to BEAST 3 direct

```bash
mkdir -p validation/target/testMultipleAlignments/beast3-direct

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testMultipleAlignments/testMultipleAlignments.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testMultipleAlignments/beast3-direct/testMultipleAlignments-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.expectedLog=validation/target/testMultipleAlignments/beast3-direct/testMultipleAlignments-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testMultipleAlignments/beast3-direct/testMultipleAlignments-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testMultipleAlignments/beast3-direct/testMultipleAlignments-direct.run.log
```

## 3. PhyloSpec to BEAST 3 XML

Generate the XML:

```bash
mkdir -p validation/target/testMultipleAlignments/beast3-xml

mvn -pl validation/beast3/java -am \
  -Pvalidation-beast3-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testMultipleAlignments/testMultipleAlignments.phylospec \
  -Dphylospec.validation.outputPrefix=validation/target/testMultipleAlignments/beast3-xml/testMultipleAlignments-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.xml=validation/target/testMultipleAlignments/beast3-xml/testMultipleAlignments-beast3.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testMultipleAlignments/beast3-xml/testMultipleAlignments-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testMultipleAlignments/beast3-xml/testMultipleAlignments-xml.run.log
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
    "$PWD/validation/target/testMultipleAlignments/beast3-xml/testMultipleAlignments-beast3.xml"
} 2>&1 | tee -a \
  validation/target/testMultipleAlignments/beast3-xml/testMultipleAlignments-xml.run.log
```

## 4. PhyloSpec to BEAST X direct

```bash
mkdir -p validation/target/testMultipleAlignments/beastx-direct

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-direct \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testMultipleAlignments/testMultipleAlignments.phylospec \
  -Dphylospec.validation.runName=testMultipleAlignments-beastx-direct \
  -Dphylospec.validation.outputPrefix=validation/target/testMultipleAlignments/beastx-direct/testMultipleAlignments-beastx-direct \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.expectedLog=validation/target/testMultipleAlignments/beastx-direct/testMultipleAlignments-beastx-direct.log \
  -Dphylospec.validation.operatorSummary=validation/target/testMultipleAlignments/beastx-direct/testMultipleAlignments-beastx-direct.operators.txt \
  verify 2>&1 | tee \
  validation/target/testMultipleAlignments/beastx-direct/testMultipleAlignments-beastx-direct.run.log
```

## 5. PhyloSpec to BEAST X XML

Generate the XML:

```bash
mkdir -p validation/target/testMultipleAlignments/beastx-xml

mvn -pl validation/beastx/java -am \
  -Pvalidation-beastx-export \
  -DskipTests \
  -Dphylospec.validation.source=validation/examples/testMultipleAlignments/testMultipleAlignments.phylospec \
  -Dphylospec.validation.runName=testMultipleAlignments-beastx \
  -Dphylospec.validation.outputPrefix=validation/target/testMultipleAlignments/beastx-xml/testMultipleAlignments-beastx-xml \
  -Dphylospec.validation.seed=1234 \
  -Dphylospec.validation.logEvery=1000 \
  -Dphylospec.validation.xml=validation/target/testMultipleAlignments/beastx-xml/testMultipleAlignments-beastx.xml \
  -Dphylospec.validation.operatorSummary=validation/target/testMultipleAlignments/beastx-xml/testMultipleAlignments-beastx-xml.operators.txt \
  verify 2>&1 | tee \
  validation/target/testMultipleAlignments/beastx-xml/testMultipleAlignments-beastx-xml.run.log
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
    "$PWD/validation/target/testMultipleAlignments/beastx-xml/testMultipleAlignments-beastx.xml"
} 2>&1 | tee -a \
  validation/target/testMultipleAlignments/beastx-xml/testMultipleAlignments-beastx-xml.run.log
```

The direct and external-engine commands run the complete MCMC chain. The two
export commands only generate XML.

## Comparison targets

The shared comparison targets are:

- `posterior`, `prior`, and total `likelihood`;
- the three individual alignment likelihoods;
- `populationSize`;
- `kappa`;
- `gammaShape`;
- `clockRate`;
- the three tree heights and tree lengths.

The most important structural check is that every path contains exactly
three tree likelihoods and three coalescent tree priors, while
`populationSize`, HKY, gamma shape, and clock rate remain shared rather than
being duplicated per alignment.
