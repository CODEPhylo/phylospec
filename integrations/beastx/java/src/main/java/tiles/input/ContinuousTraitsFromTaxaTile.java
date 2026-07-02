package tiles.input;

import dr.evolution.alignment.Alignment;
import dr.evolution.alignment.SimpleAlignment;
import dr.evolution.datatype.ContinuousDataType;
import dr.evolution.sequence.Sequence;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;
import tiles.input.ParserTile.Parser;

import java.util.IdentityHashMap;
import java.util.List;

public class ContinuousTraitsFromTaxaTile extends GeneratorTile<Alignment, BeastXState> {

    public static final String TRAIT_ATTRIBUTE = "continuousTrait";

    @Override
    public String getPhyloSpecGeneratorName() {
        return "continuousTraitsFromTaxa";
    }

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    GeneratorTileInput<Parser, BeastXState> traitInput =
            new GeneratorTileInput<>("trait");

    @Override
    public Alignment applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        Parser traitParser =
                this.traitInput.apply(beastState, indexVariables);

        SimpleAlignment alignment =
                new SimpleAlignment();

        alignment.setDataType(ContinuousDataType.INSTANCE);

        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            Taxon taxon =
                    taxa.getTaxon(i);

            String taxonName =
                    taxon.getId();

            String parsedTrait =
                    traitParser.parse(taxonName);

            double traitValue =
                    parseTraitValue(taxonName, parsedTrait);

            taxon.setAttribute(TRAIT_ATTRIBUTE, traitValue);

            Sequence sequence =
                    new Sequence(taxon, "A");

            sequence.setId(taxon.getId());
            sequence.setDataType(ContinuousDataType.INSTANCE);
            sequence.setAttribute(TRAIT_ATTRIBUTE, traitValue);

            alignment.addSequence(sequence);
        }

        alignment.updateSiteCount();

        return alignment;
    }

    private double parseTraitValue(String taxonName, String parsedTrait) {
        if (parsedTrait == null || parsedTrait.isBlank()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "continuousTraitsFromTaxa parsed an empty trait for taxon '" + taxonName + "'.",
                    "Check the parser used for the trait argument.",
                    List.of("continuousTraitsFromTaxa(taxa=taxa, trait=parse(regex=\".*_([0-9]+(?:\\\\.[0-9]+)?)$\"))")
            );
        }

        try {
            return Double.parseDouble(parsedTrait);
        } catch (NumberFormatException exception) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "continuousTraitsFromTaxa parsed a non-numeric trait for taxon '" + taxonName + "'.",
                    "Use a parser that extracts a numeric trait value.",
                    List.of("continuousTraitsFromTaxa(taxa=taxa, trait=parse(regex=\".*_([0-9]+(?:\\\\.[0-9]+)?)$\"))")
            );
        }
    }
}