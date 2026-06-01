package tiles.input;

import dr.evolution.util.Date;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evolution.util.Units;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaxaWithAgesFromTableTile extends GeneratorTile<Taxa, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxaWithAgesFromTable";
    }

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    GeneratorTileInput<List<?>, BeastXState> tableInput =
            new GeneratorTileInput<>("table");

    GeneratorTileInput<String, BeastXState> taxonColumnInput =
            new GeneratorTileInput<>("taxonColumn", false);

    GeneratorTileInput<String, BeastXState> ageColumnInput =
            new GeneratorTileInput<>("ageColumn", false);

    @Override
    public Taxa applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        List<?> table =
                this.tableInput.apply(beastState, indexVariables);

        String taxonColumn =
                this.taxonColumnInput.apply(beastState, indexVariables);

        String ageColumn =
                this.ageColumnInput.apply(beastState, indexVariables);

        if (taxonColumn == null) {
            taxonColumn = "taxon";
        }

        if (ageColumn == null) {
            ageColumn = "age";
        }

        Map<String, Double> ageByTaxon =
                buildAgeLookup(table, taxonColumn, ageColumn);

        Taxa datedTaxa =
                new Taxa();

        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            String taxonName =
                    taxa.getTaxonId(i);

            Double age =
                    ageByTaxon.get(taxonName);

            if (age == null) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "No age was found for taxon '" + taxonName + "'.",
                        "Make sure the metadata table has one row for every taxon and that taxonColumn matches the taxon ids.",
                        List.of("taxaWithAgesFromTable(taxa=taxa, table=metadata, taxonColumn=\"taxon\", ageColumn=\"age\")")
                );
            }

            Taxon datedTaxon =
                    new Taxon(taxonName);

            datedTaxon.setDate(
                    Date.createRelativeAge(
                            age,
                            Units.Type.YEARS
                    )
            );

            datedTaxa.addTaxon(datedTaxon);
        }

        return datedTaxa;
    }

    private static Map<String, Double> buildAgeLookup(
            List<?> table,
            String taxonColumn,
            String ageColumn
    ) {
        if (table == null || table.isEmpty()) {
            throw new TileApplicationError(
                    "Metadata table is empty.",
                    "Use fromCSV(...) with at least one data row."
            );
        }

        Map<String, Double> ageByTaxon =
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

            String rawAge =
                    getRequiredString(row, ageColumn);

            double age =
                    parseAge(taxon, rawAge);

            if (ageByTaxon.containsKey(taxon)) {
                throw new TileApplicationError(
                        "Duplicate taxon in metadata table.",
                        "The taxon '" + taxon + "' appears more than once."
                );
            }

            ageByTaxon.put(taxon, age);
        }

        return ageByTaxon;
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

    private static double parseAge(String taxon, String rawAge) {
        try {
            double age =
                    Double.parseDouble(rawAge);

            if (age < 0.0) {
                throw new TileApplicationError(
                        "Taxon age must be non-negative.",
                        "The age for taxon '" + taxon + "' is " + rawAge + "."
                );
            }

            return age;
        } catch (NumberFormatException exception) {
            throw new TileApplicationError(
                    "Taxon age must be numeric.",
                    "The age for taxon '" + taxon + "' is '" + rawAge + "'."
            );
        }
    }
}