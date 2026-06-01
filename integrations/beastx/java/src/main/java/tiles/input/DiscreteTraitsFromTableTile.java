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

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiscreteTraitsFromTableTile extends GeneratorTile<Alignment, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "discreteTraitsFromTable";
    }

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    GeneratorTileInput<List<?>, BeastXState> tableInput =
            new GeneratorTileInput<>("table");

    GeneratorTileInput<String, BeastXState> taxonColumnInput =
            new GeneratorTileInput<>("taxonColumn", false);

    GeneratorTileInput<String, BeastXState> traitColumnInput =
            new GeneratorTileInput<>("traitColumn", false);

    @Override
    public Alignment applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        List<?> table =
                this.tableInput.apply(beastState, indexVariables);

        String taxonColumn =
                this.taxonColumnInput.apply(beastState, indexVariables);

        String traitColumn =
                this.traitColumnInput.apply(beastState, indexVariables);

        if (taxonColumn == null) {
            taxonColumn = "taxon";
        }

        if (traitColumn == null) {
            traitColumn = "trait";
        }

        Map<String, String> traitByTaxon =
                buildTraitLookup(table, taxonColumn, traitColumn);

        Map<String, Integer> stateIndexByTrait =
                new LinkedHashMap<>();

        String[] traits =
                new String[taxa.getTaxonCount()];

        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            String taxonName =
                    taxa.getTaxonId(i);

            String trait =
                    traitByTaxon.get(taxonName);

            if (trait == null || trait.isBlank()) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "No discrete trait was found for taxon '" + taxonName + "'.",
                        "Make sure the metadata table has one row for every taxon and that taxonColumn matches the taxon ids.",
                        List.of("discreteTraitsFromTable(taxa=taxa, table=metadata, taxonColumn=\"taxon\", traitColumn=\"trait\")")
                );
            }

            traits[i] = trait;
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
                    stateIndexByTrait.get(traits[i]);

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

    private static Map<String, String> buildTraitLookup(
            List<?> table,
            String taxonColumn,
            String traitColumn
    ) {
        if (table == null || table.isEmpty()) {
            throw new TileApplicationError(
                    "Metadata table is empty.",
                    "Use fromCSV(...) with at least one data row."
            );
        }

        Map<String, String> traitByTaxon =
                new LinkedHashMap<>();

        for (Object rowObject : table) {
            if (!(rowObject instanceof Map<?, ?> row)) {
                throw new TileApplicationError(
                        "Metadata table rows must be maps.",
                        "Use a table produced by fromCSV(...)."
                );
            }

            String taxon =
                    getRequiredString(row, taxonColumn);

            String trait =
                    getRequiredString(row, traitColumn);

            if (traitByTaxon.containsKey(taxon)) {
                throw new TileApplicationError(
                        "Duplicate taxon in metadata table.",
                        "The taxon '" + taxon + "' appears more than once."
                );
            }

            traitByTaxon.put(taxon, trait);
        }

        return traitByTaxon;
    }

    private static String getRequiredString(Map<?, ?> row, String column) {
        Object value =
                row.get(column);

        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new TileApplicationError(
                    "Metadata table is missing required column value.",
                    "Column '" + column + "' must exist and contain a non-empty string."
            );
        }

        return stringValue;
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