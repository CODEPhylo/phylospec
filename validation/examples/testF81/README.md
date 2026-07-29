# testF81

This validation case isolates the `f81` substitution-model tile.

- data: the six-taxon nucleotide alignment shared with `testHKY`
- substitution model: F81 with estimated base frequencies
- base-frequency prior: `Dirichlet(1, 1, 1, 1)`
- tree prior: Yule with fixed birth rate `1.0`
- clock: the default unit branch-rate vector

The checked BEAST example sets do not contain a complete standalone F81
analysis. The frozen native BEAST 3 XML therefore records the same explicitly
documented semantic specification and provides the external parse/run path;
it is not claimed as an independent official example.
