package tiles.input;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;

public record DecoratedAlignment(Alignment alignment, TaxonSet taxonSet, TraitSet ages) {
}
