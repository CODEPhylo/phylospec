package tiles.input;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

        List<String> lines =
                readLines(file);

        if (lines.isEmpty()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "CSV file is empty.",
                    "Use a CSV/TSV file with at least one header row and one data row.",
                    List.of("fromCSV(file=\"src/test/java/resources/metadata.csv\")")
            );
        }

        List<String> headers;
        int firstDataLineIndex;

        if (providedHeaders == null) {
            headers =
                    parseLine(lines.getFirst(), delimiter);

            firstDataLineIndex =
                    1;
        } else {
            headers =
                    providedHeaders;

            firstDataLineIndex =
                    0;
        }

        validateHeaders(headers, file);

        List<Map<String, String>> rows =
                new ArrayList<>();

        for (int i = firstDataLineIndex; i < lines.size(); i++) {
            String line =
                    lines.get(i);

            if (line.isBlank()) {
                continue;
            }

            List<String> values =
                    parseLine(line, delimiter);

            if (values.size() != headers.size()) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "CSV row has the wrong number of columns.",
                        "Expected " + headers.size() + " columns but found " + values.size() + " on line " + (i + 1) + ".",
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

    private static List<String> readLines(String file) {
        try {
            return Files.readAllLines(Path.of(file));
        } catch (IOException e) {
            throw new TileApplicationError(
                    "Could not read CSV file.",
                    "'" + file + "' could not be read."
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

        for (String header : headers) {
            if (header == null || header.isBlank()) {
                throw new TileApplicationError(
                        "CSV header is empty.",
                        "Use non-empty column names."
                );
            }
        }
    }

    private static List<String> parseLine(String line, String delimiter) {
        String[] parts =
                line.split(java.util.regex.Pattern.quote(delimiter), -1);

        List<String> values =
                new ArrayList<>();

        for (String part : parts) {
            values.add(part.trim());
        }

        return values;
    }
}