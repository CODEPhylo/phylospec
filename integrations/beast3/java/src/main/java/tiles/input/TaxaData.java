package tiles.input;

import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;

/** Taxa and optional sampling ages, independent of sequence data. */
public record TaxaData(TaxonSet taxonSet, TraitSet ages) {}
