package tiles.input;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;
import beastconfig.BEASTState;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import org.phylospec.tiling.errors.TileApplicationError;

/**
 * Represents an alignment together with its taxa, tip ages and optional species names.
 */
public record DecoratedAlignment(
        Alignment alignment, TaxonSet taxonSet, TraitSet ages, Map<String, String> speciesNames) {

    public DecoratedAlignment {
        speciesNames = Map.copyOf(speciesNames);
    }

    public DecoratedAlignment(Alignment alignment, TaxonSet taxonSet, TraitSet ages) {
        this(alignment, taxonSet, ages, Map.of());
    }

    static DecoratedAlignment create(
            BEASTState state,
            Alignment alignment,
            ParserTile.Parser ageParser,
            ParserTile.Parser dateParser,
            ParserTile.Parser speciesParser,
            Path path) {
        TaxonSet taxonSet = new TaxonSet(alignment);
        state.setInput(taxonSet, taxonSet.alignmentInput, alignment);

        Map<String, String> speciesNames = new LinkedHashMap<>();
        if (speciesParser != null) {
            for (String taxon : alignment.getTaxaNames()) {
                try {
                    String species = speciesParser.parse(taxon);
                    if (species == null || species.isBlank()) {
                        throw new IllegalArgumentException("The species parser returned an empty value.");
                    }
                    speciesNames.put(taxon, species);
                } catch (RuntimeException e) {
                    throw new TileApplicationError(
                            "Invalid species metadata.", "'" + path + "', taxon '" + taxon + "': " + e.getMessage());
                }
            }
        }

        ParserTile.Parser parser = ageParser != null ? ageParser : dateParser;
        if (parser == null) {
            return new DecoratedAlignment(alignment, taxonSet, null, speciesNames);
        }

        TraitSet traits = new TraitSet();
        traits.traitNameInput.setValue(ageParser != null ? "age" : "date", traits);
        StringJoiner values = new StringJoiner(",");
        for (String taxon : alignment.getTaxaNames()) {
            try {
                String raw = parser.parse(taxon);
                if (raw == null || raw.isBlank()) {
                    throw new IllegalArgumentException("The parser returned an empty value.");
                }

                // Validate before native initialization, which can terminate the JVM
                // on an unparseable date. Keep BEAST's existing age/date convention.
                if (taxon.contains(",") || taxon.contains("=") || raw.contains(",") || raw.contains("=")) {
                    throw new IllegalArgumentException("Taxon names and parsed metadata must not contain ',' or '=' "
                            + "when age/date metadata is used.");
                }
                if (!taxon.equals(taxon.replaceAll("\\s+", " ").strip())) {
                    throw new IllegalArgumentException("BEAST's TraitSet cannot preserve this taxon name.");
                }
                String normalized = raw.replaceAll("\\s+", " ").strip();
                double value = traits.convertValueToDouble(normalized);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("The parsed value must be finite.");
                }
                if (ageParser != null && value < 0.0) {
                    throw new IllegalArgumentException("A sampling age must not be negative.");
                }
                // Serialize exactly what was validated, including whitespace normalization.
                values.add(taxon + "=" + normalized);
            } catch (RuntimeException e) {
                throw new TileApplicationError(
                        "Invalid sampling metadata.", "'" + path + "', taxon '" + taxon + "': " + e.getMessage());
            }
        }

        state.setInput(traits, traits.traitNameInput, ageParser != null ? "age" : "date");
        state.setInput(traits, traits.taxaInput, taxonSet);
        state.setInput(traits, traits.traitsInput, values.toString());
        traits.initAndValidate();

        return new DecoratedAlignment(alignment, taxonSet, traits, speciesNames);
    }
}
