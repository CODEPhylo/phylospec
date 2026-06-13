package tiles.input;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;
import beast.base.parser.NexusParser;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.TileApplicationError;

import java.io.File;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Set;

public class FromNexusTile extends GeneratorTile<DecoratedAlignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromNexus";
    }

    GeneratorTileInput<String> fileInput = new GeneratorTileInput<>(
            "file", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<ParserTile.Parser> ageInput = new GeneratorTileInput<>("age", false);
    GeneratorTileInput<ParserTile.Parser> dateInput = new GeneratorTileInput<>("date", false);

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String path = this.fileInput.apply(beastState, indexVariables);
        File file = new File(path);

        NexusParser nexusParser = new NexusParser();
        try {
            nexusParser.parseFile(file);
        } catch (IOException e) {
            throw new TileApplicationError(
                    "File not found.",
                    "'" + path + "' could not be found. Does it exist? Select a valid file path."
            );
        }

        Alignment alignment = nexusParser.m_alignment;
        TaxonSet taxonSet = new TaxonSet(alignment);
        beastState.setInput(taxonSet, taxonSet.alignmentInput, alignment);

        // build age and date trait set if needed

        ParserTile.Parser ageParser = this.ageInput.apply(beastState, indexVariables);
        ParserTile.Parser dateParser = this.dateInput.apply(beastState, indexVariables);

        TraitSet ageTraitSet = null;
        if (ageParser != null) {
            ageTraitSet = new TraitSet();

            StringBuilder traits = new StringBuilder();
            for (String taxon : alignment.getTaxaNames()) {
                traits.append(taxon).append("=").append(ageParser.parse(taxon)).append(",");
            }

            beastState.setInput(ageTraitSet, ageTraitSet.traitNameInput, "age");
            beastState.setInput(ageTraitSet, ageTraitSet.taxaInput, taxonSet);
            beastState.setInput(ageTraitSet, ageTraitSet.traitsInput, traits.toString());

            ageTraitSet.initAndValidate();
        } else if (dateParser != null) {
            ageTraitSet = new TraitSet();

            StringBuilder traits = new StringBuilder();
            for (String taxon : alignment.getTaxaNames()) {
                traits.append(taxon).append("=").append(dateParser.parse(taxon)).append(",");
            }

            beastState.setInput(ageTraitSet, ageTraitSet.traitNameInput, "date");
            beastState.setInput(ageTraitSet, ageTraitSet.taxaInput, taxonSet);
            beastState.setInput(ageTraitSet, ageTraitSet.traitsInput, traits.toString());

            ageTraitSet.initAndValidate();
        }

        return new DecoratedAlignment(alignment, taxonSet, ageTraitSet);
    }

}
