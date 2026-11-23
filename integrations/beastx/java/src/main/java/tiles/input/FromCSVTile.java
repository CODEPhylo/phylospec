package tiles.input;

import org.phylospec.ast.Expr;
import org.phylospec.parser.CsvParser;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FromCSVTile extends GeneratorTile<List<Map<String, String>>, BeastXState> {

    private static final Set<Stochasticity> NON_STOCHASTIC =
            Set.of(
                    Stochasticity.CONSTANT,
                    Stochasticity.DETERMINISTIC
            );

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromCSV";
    }

    GeneratorTileInput<String, BeastXState> fileInput =
            new GeneratorTileInput<>(
                    "file",
                    NON_STOCHASTIC
            );

    GeneratorTileInput<String, BeastXState> delimiterInput =
            new GeneratorTileInput<>(
                    "delimiter",
                    false,
                    NON_STOCHASTIC
            );

    GeneratorTileInput<List<String>, BeastXState> headersInput =
            new GeneratorTileInput<>(
                    "headers",
                    false,
                    NON_STOCHASTIC
            );

    @Override
    public List<Map<String, String>> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        String file =
                this.fileInput.apply(beastState, indexVariables);

        String delimiter =
                this.delimiterInput.apply(beastState, indexVariables);

        if (delimiter == null) {
            delimiter =
                    ",";
        }

        List<String> providedHeaders =
                this.headersInput.apply(beastState, indexVariables);

        // Core's CSV parser, as in BEAST 3: quoted fields may contain the delimiter, doubled
        // quotes or line breaks, and values are kept untrimmed.
        List<List<String>> records =
                readRecords(beastState.resolveInput(file), delimiter);

        List<String> headers;
        int firstDataRecord;

        if (providedHeaders == null) {
            if (records.isEmpty()) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "CSV file is empty.",
                        "Use a CSV/TSV file with a header row, or give the headers argument.",
                        List.of("fromCSV(file=\"src/test/java/resources/metadata.csv\")")
                );
            }

            headers =
                    records.getFirst();

            firstDataRecord =
                    1;
        } else {
            headers =
                    providedHeaders;

            firstDataRecord =
                    0;
        }

        validateHeaders(headers, file);

        List<Map<String, String>> rows =
                new ArrayList<>();

        for (int i = firstDataRecord; i < records.size(); i++) {
            List<String> values =
                    records.get(i);

            if (values.size() != headers.size()) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "CSV row has the wrong number of columns.",
                        "Expected " + headers.size() + " columns but found " + values.size() + " in record " + (i + 1) + ".",
                        List.of("Check that the delimiter argument matches the file format.")
                );
            }

            Map<String, String> row =
                    new LinkedHashMap<>();

            for (int column = 0; column < headers.size(); column++) {
                row.put(
                        headers.get(column),
                        values.get(column)
                );
            }

            rows.add(row);
        }

        return rows;
    }

    private static List<List<String>> readRecords(Path file, String delimiter) {
        try {
            return CsvParser.parse(Files.readString(file, StandardCharsets.UTF_8), delimiter);
        } catch (IOException e) {
            throw new TileApplicationError(
                    "Could not read CSV file.",
                    "'" + file + "' could not be read."
            );
        } catch (IllegalArgumentException e) {
            throw new TileApplicationError(
                    "Invalid CSV file.",
                    "'" + file + "': " + e.getMessage()
            );
        }
    }

    private static void validateHeaders(List<String> headers, String file) {
        if (headers.isEmpty()) {
            throw new TileApplicationError(
                    "CSV headers are empty.",
                    "'" + file + "' must provide at least one column."
            );
        }

        Set<String> names =
                new HashSet<>();

        for (String header : headers) {
            if (header == null || header.isBlank()) {
                throw new TileApplicationError(
                        "CSV header is empty.",
                        "Use non-empty column names."
                );
            }

            if (!names.add(header)) {
                throw new TileApplicationError(
                        "CSV header '" + header + "' appears twice in '" + file + "'.",
                        "Use distinct column names."
                );
            }
        }
    }
}