package tiles.input;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;

/**
 * Represents an alignment together with the corresponding taxon set and tip ages.
 */
public record DecoratedAlignment(Alignment alignment, TaxonSet taxonSet, TraitSet ages) {
}
