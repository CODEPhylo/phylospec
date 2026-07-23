# BEAST testGTR coverage example

## Reference

- Example: `testGTR.xml`
- Reference engine: BEAST 2
- Target backend: PhyloSpec BEAST 3
- Alignment: primate mtDNA
- Taxa: 12
- Sites: 898
- Chain length in reference: 5,000,000
- Chain length in smoke test: 10,000

## Model

- Substitution model: GTR
- Estimated rates:
    - rateAC
    - rateAG
    - rateAT
    - rateCG
    - rateGT
- Fixed rate:
    - rateCT = 1.0
- Estimated base frequencies
- Fixed site-model parameters
- Fixed strict-clock rate = 1.0
- Yule tree prior

## Reference partitions

- coding: `2-457,660-896`
- noncoding: `1,458-659,897-898`

## PhyloSpec representation

PhyloSpec currently supports continuous `subset()` intervals but does not
support comma-separated unions of intervals. The two reference partitions
are therefore represented as five likelihoods sharing the same tree,
substitution model, site model and clock.

## Known parity differences

### Yule birth rate

The BEAST 2 XML estimates the Yule birth rate with an implicit/improper
`Uniform(0, Infinity)` prior.

PhyloSpec currently cannot express this exact prior as a
`Distribution<PositiveReal>`. The initial coverage example fixes the birth
rate to 1.0.

Status: coverage gap.

### Partition representation

The reference uses two filtered alignments with non-contiguous filters.
The PhyloSpec model uses five continuous subsets.

The likelihood semantics are equivalent because all subsets share the same
tree, GTR model, site model and clock, but the generated XML structure is
not identical.

Status: semantically supported, structurally different.

## Validation stages

- [ ] PhyloSpec parses
- [ ] BEAST 3 tiling succeeds
- [ ] GTR object is generated
- [ ] Five estimated rates are present
- [ ] rateCT is fixed
- [ ] Base frequencies are estimated
- [ ] Operators cover all estimated state nodes
- [ ] BEAST 3 XML is generated
- [ ] Generated XML contains all likelihood partitions
- [ ] Short MCMC completes
- [ ] Parameter log is generated
- [ ] Tree log is generated
- [ ] Generated XML can be read back by BEAST 3