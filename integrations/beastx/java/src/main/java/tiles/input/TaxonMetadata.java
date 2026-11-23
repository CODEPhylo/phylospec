package tiles.input;

import dr.evolution.alignment.Alignment;
import dr.evolution.util.Date;
import dr.evolution.util.Taxon;
import dr.evolution.util.Units;
import org.phylospec.tiling.errors.TileApplicationError;

/**
 * Taxon metadata parsed from the taxon names of an alignment, shared by fromNexus and fromFasta:
 * sampling ages (age or date) and species names.
 */
final class TaxonMetadata {

    private TaxonMetadata() {
    }

    static void apply(
            Alignment alignment,
            ParserTile.Parser ageParser,
            ParserTile.Parser dateParser,
            ParserTile.Parser speciesNameParser,
            String generatorName
    ) {
        if (ageParser != null && dateParser != null) {
            throw new TileApplicationError(
                    generatorName + " cannot use both age and date parsers.",
                    "Use either age=parse(...) or date=parse(...), not both."
            );
        }

        if (ageParser != null) {
            applyTaxonAges(alignment, ageParser);
        }

        if (dateParser != null) {
            applyTaxonDates(alignment, dateParser);
        }

        if (speciesNameParser != null) {
            applySpeciesNames(alignment, speciesNameParser);
        }
    }

    // Stored like the species of taxon(name, species=...), so species(taxon) reads both.
    private static void applySpeciesNames(
            Alignment alignment,
            ParserTile.Parser speciesNameParser
    ) {
        for (int i = 0; i < alignment.getTaxonCount(); i++) {
            Taxon taxon =
                    alignment.getTaxon(i);

            String species =
                    speciesNameParser.parse(taxon.getId());

            if (species == null || species.isBlank()) {
                throw new TileApplicationError(
                        "Parsed species name is empty for taxon '" + taxon.getId() + "'.",
                        "Check the speciesName parser."
                );
            }

            taxon.setAttribute("species", species);
        }
    }

    /**
     * Ages are shifted so that the youngest taxon has age 0, as BEAST 3 does for an age trait
     * and as the date path below already does.
     *
     * <p>BEAST X measures tip heights from the most recent date it has seen anywhere in the JVM
     * ({@code Taxon}'s static time scale). Without a taxon at age 0, the same alignment would get
     * different tip heights depending on what was built before.</p>
     */
    private static void applyTaxonAges(
            Alignment alignment,
            ParserTile.Parser ageParser
    ) {
        double[] ages =
                new double[alignment.getTaxonCount()];

        double youngestAge =
                Double.POSITIVE_INFINITY;

        for (int i = 0; i < alignment.getTaxonCount(); i++) {
            Taxon taxon =
                    alignment.getTaxon(i);

            ages[i] =
                    parseAge(taxon.getId(), ageParser.parse(taxon.getId()));

            youngestAge =
                    Math.min(youngestAge, ages[i]);
        }

        for (int i = 0; i < alignment.getTaxonCount(); i++) {
            alignment.getTaxon(i).setDate(
                    Date.createRelativeAge(
                            ages[i] - youngestAge,
                            Units.Type.YEARS
                    )
            );
        }
    }

    private static void applyTaxonDates(
            Alignment alignment,
            ParserTile.Parser dateParser
    ) {
        double[] dates =
                new double[alignment.getTaxonCount()];

        double mostRecentDate =
                Double.NEGATIVE_INFINITY;

        for (int i = 0; i < alignment.getTaxonCount(); i++) {
            Taxon taxon =
                    alignment.getTaxon(i);

            String parsedDate =
                    dateParser.parse(taxon.getId());

            double date =
                    parseDate(taxon.getId(), parsedDate);

            dates[i] =
                    date;

            mostRecentDate =
                    Math.max(mostRecentDate, date);
        }

        for (int i = 0; i < alignment.getTaxonCount(); i++) {
            Taxon taxon =
                    alignment.getTaxon(i);

            double age =
                    mostRecentDate - dates[i];

            taxon.setDate(
                    Date.createRelativeAge(
                            age,
                            Units.Type.YEARS
                    )
            );
        }
    }

    private static double parseAge(
            String taxonName,
            String parsedAge
    ) {
        double age =
                parseFiniteDouble(
                        taxonName,
                        parsedAge,
                        "age"
                );

        if (age < 0.0) {
            throw new TileApplicationError(
                    "Parsed taxon age is negative for taxon '" + taxonName + "'.",
                    "Use an age parser that returns non-negative numeric ages."
            );
        }

        return age;
    }

    private static double parseDate(
            String taxonName,
            String parsedDate
    ) {
        return parseFiniteDouble(
                taxonName,
                parsedDate,
                "date"
        );
    }

    private static double parseFiniteDouble(
            String taxonName,
            String parsedValue,
            String valueName
    ) {
        try {
            double value =
                    Double.parseDouble(parsedValue);

            if (!Double.isFinite(value)) {
                throw new TileApplicationError(
                        "Parsed taxon " + valueName + " is not finite for taxon '" + taxonName + "'.",
                        "Check the " + valueName + " parser. It returned '" + parsedValue + "'."
                );
            }

            return value;
        } catch (NumberFormatException e) {
            throw new TileApplicationError(
                    "Parsed taxon " + valueName + " is not numeric for taxon '" + taxonName + "'.",
                    "Check the " + valueName + " parser. It returned '" + parsedValue + "'."
            );
        }
    }
}
