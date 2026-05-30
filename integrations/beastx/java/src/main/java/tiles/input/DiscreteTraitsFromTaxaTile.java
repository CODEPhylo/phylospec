package tiles.input;

import dr.evolution.alignment.Alignment;
import dr.evolution.alignment.SimpleAlignment;
import dr.evolution.datatype.DataType;
import dr.evolution.datatype.GeneralDataType;
import dr.evolution.datatype.TwoStates;
import dr.evolution.sequence.Sequence;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;
import tiles.input.ParserTile.Parser;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiscreteTraitsFromTaxaTile extends GeneratorTile<Alignment, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "discreteTraitsFromTaxa";
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

        Map<String, Integer> stateIndexByTrait =
                new LinkedHashMap<>();

        String[] parsedTraits =
                new String[taxa.getTaxonCount()];

        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            String taxonName =
                    taxa.getTaxonId(i);

            String trait =
                    traitParser.parse(taxonName);

            if (trait == null || trait.isBlank()) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "discreteTraitsFromTaxa parsed an empty trait for taxon '" + taxonName + "'.",
                        "Check the parser used for the trait argument.",
                        List.of("discreteTraitsFromTaxa(taxa=taxa, trait=parse(regex=\".*_([01])$\"))")
                );
            }

            parsedTraits[i] = trait;
            stateIndexByTrait.computeIfAbsent(trait, ignored -> stateIndexByTrait.size());
        }

        DataType dataType =
                buildDataType(stateIndexByTrait);

        SimpleAlignment alignment =
                new SimpleAlignment();

        alignment.setDataType(dataType);

        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            Taxon taxon =
                    taxa.getTaxon(i);

            int state =
                    stateIndexByTrait.get(parsedTraits[i]);

            Sequence sequence =
                    new Sequence(
                            taxon,
                            dataType,
                            new int[]{state}
                    );

            sequence.setId(taxon.getId());
            alignment.addSequence(sequence);
        }

        alignment.updateSiteCount();

        return alignment;
    }

    private static DataType buildDataType(Map<String, Integer> stateIndexByTrait) {
        if (stateIndexByTrait.size() == 2
                && stateIndexByTrait.containsKey("0")
                && stateIndexByTrait.containsKey("1")) {
            return TwoStates.INSTANCE;
        }

        return new GeneralDataType(
                stateIndexByTrait.keySet()
        );
    }
}
