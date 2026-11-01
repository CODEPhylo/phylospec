package tiles.input;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.tree.TraitSet;
import beastconfig.BEASTState;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class FromFastaTile extends GeneratorTile<DecoratedAlignment, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromFasta";
    }

    GeneratorTileInput<String, BEASTState> fileInput =
            new GeneratorTileInput<>("file", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<ParserTile.Parser, BEASTState> ageInput = new GeneratorTileInput<>("age", false);
    GeneratorTileInput<ParserTile.Parser, BEASTState> dateInput = new GeneratorTileInput<>("date", false);

    @Override
    public DecoratedAlignment applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String path = fileInput.apply(state, indexVariables);
        Path resolved = state.resolvePath(path);

        Alignment alignment;
        try {
            alignment = readAlignment(resolved);
        } catch (IOException e) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Could not read input file.",
                    "'" + path + "' resolves to '" + resolved + "'. " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Invalid nucleotide FASTA alignment.",
                    "'" + path + "' resolves to '" + resolved + "'. " + e.getMessage());
        }

        ParserTile.Parser ageParser = ageInput.apply(state, indexVariables);
        ParserTile.Parser dateParser = dateInput.apply(state, indexVariables);

        validateMetadata(resolved, alignment, ageParser, dateParser);
        return DecoratedAlignment.create(state, alignment, ageParser, dateParser);
    }

    private static Alignment readAlignment(Path path) throws IOException {
        Map<String, StringBuilder> records = new LinkedHashMap<>();
        StringBuilder currentSequence = null;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String text = line.strip();
                if (text.isEmpty()) {
                    continue;
                }

                if (text.startsWith(">")) {
                    String header = text.substring(1).strip();
                    if (header.isEmpty()) {
                        throw new IllegalArgumentException("Empty FASTA header at line " + lineNumber + ".");
                    }

                    String taxon = header.split("\\s+", 2)[0];
                    if (records.containsKey(taxon)) {
                        throw new IllegalArgumentException(
                                "Duplicate taxon '" + taxon + "' at line " + lineNumber + ".");
                    }

                    currentSequence = new StringBuilder();
                    records.put(taxon, currentSequence);
                } else {
                    if (currentSequence == null) {
                        throw new IllegalArgumentException(
                                "Sequence data before the first FASTA header at line " + lineNumber + ".");
                    }
                    currentSequence.append(text.replaceAll("\\s+", ""));
                }
            }
        }

        if (records.isEmpty()) {
            throw new IllegalArgumentException("The FASTA file contains no sequences.");
        }

        List<Sequence> sequences = new ArrayList<>();
        int expectedLength = -1;

        for (Map.Entry<String, StringBuilder> record : records.entrySet()) {
            String taxon = record.getKey();
            String sequence = record.getValue().toString().toUpperCase(Locale.ROOT);

            if (sequence.isEmpty()) {
                throw new IllegalArgumentException("Taxon '" + taxon + "' has an empty sequence.");
            }

            if (expectedLength < 0) {
                expectedLength = sequence.length();
            } else if (sequence.length() != expectedLength) {
                throw new IllegalArgumentException("Taxon '" + taxon + "' has " + sequence.length()
                        + " sites; expected " + expectedLength
                        + ". FASTA input must already be aligned.");
            }

            sequences.add(new Sequence(taxon, sequence));
        }

        // Let BEAST validate nucleotide symbols and construct its native alignment.
        try {
            return new Alignment(sequences, "nucleotide");
        } catch (RuntimeException e) {
            // Alignment wraps sequence-validation errors in RuntimeException.
            // Recover only that validation failure so applyTile can report the FASTA path.
            if (e.getCause() instanceof IllegalArgumentException invalidSequence) {
                throw new IllegalArgumentException(invalidSequence.getMessage(), e);
            }
            throw e;
        }
    }

    private void validateMetadata(
            Path path, Alignment alignment, ParserTile.Parser ageParser, ParserTile.Parser dateParser) {
        ParserTile.Parser parser = ageParser != null ? ageParser : dateParser;
        if (parser == null) {
            return;
        }

        // Validate before TraitSet.initAndValidate(), whose native error handling
        // can terminate the JVM for an unparseable date.
        TraitSet validator = new TraitSet();
        validator.traitNameInput.setValue(ageParser != null ? "age" : "date", validator);

        for (String taxon : alignment.getTaxaNames()) {
            try {
                String raw = parser.parse(taxon);
                if (raw == null || raw.isBlank()) {
                    throw new IllegalArgumentException("The parser returned an empty value.");
                }

                // TraitSet encodes metadata as comma-separated taxon=value pairs.
                if (taxon.contains(",") || taxon.contains("=") || raw.contains(",") || raw.contains("=")) {
                    throw new IllegalArgumentException("Taxon names and parsed metadata must not contain ',' or '=' "
                            + "when age/date metadata is used.");
                }

                String normalized = raw.replaceAll("\\s+", " ").strip();
                double value = validator.convertValueToDouble(normalized);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("The parsed value must be finite.");
                }
                if (ageParser != null && value < 0.0) {
                    throw new IllegalArgumentException("A sampling age must not be negative.");
                }
            } catch (RuntimeException e) {
                throw new TileApplicationError(
                        getRootNode(),
                        "Invalid sampling metadata.",
                        "'" + path + "', taxon '" + taxon + "': " + e.getMessage());
            }
        }
    }
}
