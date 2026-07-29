# testK80

This validation case isolates the `k80` substitution-model tile.

- data: the six-taxon nucleotide alignment shared with `testHKY`
- substitution model: K80 with estimated transition/transversion ratio
- kappa prior: `LogNormal(logMean=1.0, logSd=0.5)`
- nucleotide frequencies: fixed at `(0.25, 0.25, 0.25, 0.25)`
- tree prior: Yule with fixed birth rate `1.0`
- clock: the default unit branch-rate vector

The checked BEAST example sets do not contain a complete standalone K80
analysis. The frozen native BEAST 3 XML therefore records the same explicitly
documented semantic specification and provides the external parse/run path;
it is not claimed as an independent official example.
