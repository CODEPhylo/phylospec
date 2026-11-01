package tiles.input;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;
import beastconfig.BEASTState;

/**
 * Represents an alignment together with the corresponding taxon set and tip ages.
 */
public record DecoratedAlignment(Alignment alignment, TaxonSet taxonSet, TraitSet ages) {

    static DecoratedAlignment create(
            BEASTState state, Alignment alignment, ParserTile.Parser ageParser, ParserTile.Parser dateParser) {
        TaxonSet taxonSet = new TaxonSet(alignment);
        state.setInput(taxonSet, taxonSet.alignmentInput, alignment);

        ParserTile.Parser parser = ageParser != null ? ageParser : dateParser;
        if (parser == null) {
            return new DecoratedAlignment(alignment, taxonSet, null);
        }

        TraitSet traits = new TraitSet();
        StringBuilder values = new StringBuilder();
        for (String taxon : alignment.getTaxaNames()) {
            values.append(taxon).append("=").append(parser.parse(taxon)).append(",");
        }

        state.setInput(traits, traits.traitNameInput, ageParser != null ? "age" : "date");
        state.setInput(traits, traits.taxaInput, taxonSet);
        state.setInput(traits, traits.traitsInput, values.toString());
        traits.initAndValidate();

        return new DecoratedAlignment(alignment, taxonSet, traits);
    }
}
