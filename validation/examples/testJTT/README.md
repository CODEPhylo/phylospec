# testJTT

This validation case isolates the `jtt` empirical amino-acid model tile.

- data: the shared ten-taxon, 234-site protein alignment
- substitution model: JTT with its empirical equilibrium frequencies
- tree prior: Yule with fixed birth rate `1.0`
- clock: the default unit branch-rate vector

The alignment is copied from the BEAST 3 `beast-base` test resources. The
frozen native reference XML records the same explicitly documented model
and provides the external parse/run path because that resource set does not
contain a complete standalone JTT run. It is not claimed as an independent
official example.
