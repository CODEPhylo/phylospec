# Miscellaneous Open Points

This document describes miscellaneous open points.

## JSON Representation of PhyloSpec

Do we want a JSON representation of a PhyloSpec model, potentially as input for the engines?

Pros:

- This would simplify the process of automatically generating PhyloSpec models.
- It would lower the barrier to entry for new engines.
- It would allow general graph-level optimizations.

Cons:

- Using a JSON representation as an input to all engines would require shipping compilation tools with each engine and make the process slightly more brittle.

Another option is to have libraries allowing the modification of a PhyloSpec script to enable easy modification of scripts. This would require libraries in multiple languages (Java, Python, R?).

## LSP

A language server protocol (LSP) implementation for PhyloSpec would be useful for editors and IDEs.
