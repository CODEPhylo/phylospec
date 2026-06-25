package tiles.input;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;
import beast.base.parser.NexusParser;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.TileApplicationError;

import java.io.File;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Set;

public class FromNexusTile extends GeneratorTile<DecoratedAlignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromNexus";
    }

    GeneratorTileInput<String> fileInput = new GeneratorTileInput<>(
            "file", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<ParserTile.Parser> ageInput = new GeneratorTileInput<>("age", false);
    GeneratorTileInput<ParserTile.Parser> dateInput = new GeneratorTileInput<>("date", false);

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String path = this.fileInput.apply(beastState, indexVariables);
        File file = new File(path);

        ParserTile.Parser ageParser = this.ageInput.apply(beastState, indexVariables);
        ParserTile.Parser dateParser = this.dateInput.apply(beastState, indexVariables);

        if (ageParser != null && dateParser != null) {
            throw new TileApplicationError(
                    "fromNexus cannot use both age and date parsers.",
                    "Use either age=parse(...) or date=parse(...), not both."
            );
        }

        NexusParser nexusParser = new NexusParser();
        try {
            nexusParser.parseFile(file);
        } catch (IOException e) {
            throw new TileApplicationError(
                    "File not found.",
                    "'" + path + "' could not be found. Does it exist? Select a valid file path."
            );
        }

        Alignment alignment = nexusParser.m_alignment;
        TaxonSet taxonSet = new TaxonSet(alignment);
        beastState.setInput(taxonSet, taxonSet.alignmentInput, alignment);

        TraitSet ageTraitSet = null;
        if (ageParser != null) {
            ageTraitSet = createAgeTraitSet(beastState, taxonSet, alignment, ageParser);
        } else if (dateParser != null) {
            ageTraitSet = createRelativeAgeTraitSetFromDates(beastState, taxonSet, alignment, dateParser);
        }

        return new DecoratedAlignment(alignment, taxonSet, ageTraitSet);
    }

    private static TraitSet createAgeTraitSet(
            BEASTState beastState,
            TaxonSet taxonSet,
            Alignment alignment,
            ParserTile.Parser ageParser
    ) {
        StringBuilder traits = new StringBuilder();
        for (String taxon : alignment.getTaxaNames()) {
            double age = parseAge(taxon, ageParser.parse(taxon));
            traits.append(taxon).append("=").append(age).append(",");
        }

        return createAgeTraitSet(beastState, taxonSet, traits.toString());
    }

    private static TraitSet createRelativeAgeTraitSetFromDates(
            BEASTState beastState,
            TaxonSet taxonSet,
            Alignment alignment,
            ParserTile.Parser dateParser
    ) {
        double[] dates = new double[alignment.getTaxonCount()];
        double mostRecentDate = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < alignment.getTaxonCount(); i++) {
            String taxon = alignment.getTaxaNames().get(i);
            double date = parseDate(taxon, dateParser.parse(taxon));

            dates[i] = date;
            mostRecentDate = Math.max(mostRecentDate, date);
        }

        StringBuilder traits = new StringBuilder();
        for (int i = 0; i < alignment.getTaxonCount(); i++) {
            String taxon = alignment.getTaxaNames().get(i);
            double age = mostRecentDate - dates[i];

            traits.append(taxon).append("=").append(age).append(",");
        }

        return createAgeTraitSet(beastState, taxonSet, traits.toString());
    }

    private static TraitSet createAgeTraitSet(
            BEASTState beastState,
            TaxonSet taxonSet,
            String traits
    ) {
        TraitSet ageTraitSet = new TraitSet();

        beastState.setInput(ageTraitSet, ageTraitSet.traitNameInput, "age");
        beastState.setInput(ageTraitSet, ageTraitSet.taxaInput, taxonSet);
        beastState.setInput(ageTraitSet, ageTraitSet.traitsInput, traits);

        ageTraitSet.initAndValidate();

        return ageTraitSet;
    }

    private static double parseAge(
            String taxonName,
            String parsedAge
    ) {
        double age = parseFiniteDouble(taxonName, parsedAge, "age");

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
        return parseFiniteDouble(taxonName, parsedDate, "date");
    }

    private static double parseFiniteDouble(
            String taxonName,
            String parsedValue,
            String valueName
    ) {
        try {
            double value = Double.parseDouble(parsedValue);

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
