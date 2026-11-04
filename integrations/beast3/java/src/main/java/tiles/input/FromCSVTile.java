package tiles.input;

import beastconfig.BEASTState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.parser.CsvParser;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

/** Reads a fixed table without converting or trimming its string values. */
public class FromCSVTile extends GeneratorTile<List<Map<String, String>>, BEASTState> {
    private static final Set<Stochasticity> FIXED = Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC);

    GeneratorTileInput<String, BEASTState> fileInput = new GeneratorTileInput<>("file", FIXED);
    GeneratorTileInput<String, BEASTState> delimiterInput = new GeneratorTileInput<>("delimiter", false, FIXED);
    GeneratorTileInput<List<String>, BEASTState> headersInput = new GeneratorTileInput<>("headers", false, FIXED);

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromCSV";
    }

    @Override
    public List<Map<String, String>> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String file = fileInput.apply(state, indexVariables);
        String delimiter = delimiterInput.apply(state, indexVariables);
        List<String> headers = headersInput.apply(state, indexVariables);
        Path path = state.resolvePath(file);

        try {
            List<List<String>> records = CsvParser.parse(
                    Files.readString(path, StandardCharsets.UTF_8), delimiter == null ? "," : delimiter);
            int firstDataRecord = 0;
            if (headers == null) {
                if (records.isEmpty()) throw new IllegalArgumentException("The CSV file has no header record.");
                headers = records.getFirst();
                firstDataRecord = 1;
            }
            validateHeaders(headers);

            List<Map<String, String>> rows = new ArrayList<>();
            for (int index = firstDataRecord; index < records.size(); index++) {
                List<String> values = records.get(index);
                if (values.size() != headers.size()) {
                    throw new IllegalArgumentException("CSV record " + (index + 1) + " has " + values.size()
                            + " columns; expected " + headers.size() + ".");
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int column = 0; column < headers.size(); column++) {
                    row.put(headers.get(column), values.get(column));
                }
                rows.add(Collections.unmodifiableMap(row));
            }
            return List.copyOf(rows);
        } catch (IOException exception) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Could not read CSV file.",
                    "'" + file + "' resolves to '" + path + "'. " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Invalid CSV input.",
                    "'" + file + "' resolves to '" + path + "'. " + exception.getMessage());
        }
    }

    private static void validateHeaders(List<String> headers) {
        if (headers.isEmpty()) throw new IllegalArgumentException("Provide at least one CSV header.");
        Set<String> names = new HashSet<>();
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                throw new IllegalArgumentException("CSV headers must not be blank.");
            }
            if (!names.add(header)) throw new IllegalArgumentException("Duplicate CSV header '" + header + "'.");
        }
    }
}
