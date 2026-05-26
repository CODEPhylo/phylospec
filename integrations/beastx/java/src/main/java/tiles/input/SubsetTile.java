package tiles.input;

import dr.evolution.alignment.Alignment;
import dr.evolution.alignment.SimpleAlignment;
import dr.evolution.sequence.Sequence;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class SubsetTile extends GeneratorTile<Alignment, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "subset";
    }

    GeneratorTileInput<Alignment, BeastXState> alignmentInput =
            new GeneratorTileInput<>("alignment");

    GeneratorTileInput<Integer, BeastXState> startInput =
            new GeneratorTileInput<>(
                    "start",
                    false,
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<Integer, BeastXState> endInput =
            new GeneratorTileInput<>(
                    "end",
                    false,
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<Integer, BeastXState> codonPositionInput =
            new GeneratorTileInput<>(
                    "codonPosition",
                    false,
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public Alignment applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Alignment alignment =
                this.alignmentInput.apply(beastState, indexVariables);

        Integer start =
                this.startInput.apply(beastState, indexVariables);

        Integer end =
                this.endInput.apply(beastState, indexVariables);

        Integer codonPosition =
                this.codonPositionInput.apply(beastState, indexVariables);

        int siteCount =
                alignment.getSiteCount();

        int firstSite =
                start == null ? 1 : start;

        int lastSite =
                end == null ? siteCount : end;

        validateBounds(siteCount, firstSite, lastSite, codonPosition);

        SimpleAlignment subset =
                new SimpleAlignment();

        subset.setDataType(alignment.getDataType());

        for (int sequenceIndex = 0; sequenceIndex < alignment.getSequenceCount(); sequenceIndex++) {
            String sourceSequence =
                    alignment.getAlignedSequenceString(sequenceIndex);

            StringBuilder selectedSites =
                    new StringBuilder();

            for (int site = firstSite; site <= lastSite; site++) {
                if (codonPosition != null && ((site - 1) % 3) + 1 != codonPosition) {
                    continue;
                }

                selectedSites.append(sourceSequence.charAt(site - 1));
            }

            Sequence sequence =
                    new Sequence(
                            alignment.getTaxon(sequenceIndex),
                            selectedSites.toString()
                    );

            sequence.setDataType(alignment.getDataType());

            subset.addSequence(sequence);
        }

        subset.updateSiteCount();

        return subset;
    }

    private static void validateBounds(
            int siteCount,
            int start,
            int end,
            Integer codonPosition
    ) {
        if (start < 1) {
            throw new TileApplicationError(
                    "Subset start must be at least 1.",
                    "PhyloSpec subset site positions are one-based."
            );
        }

        if (end < start) {
            throw new TileApplicationError(
                    "Subset end must not be smaller than start.",
                    "Use start <= end. Example: subset(alignment=data, start=1, end=100)"
            );
        }

        if (start > siteCount) {
            throw new TileApplicationError(
                    "Subset start is larger than the number of sites.",
                    "Use a start position within the alignment."
            );
        }

        if (end > siteCount) {
            throw new TileApplicationError(
                    "Subset end is larger than the number of sites.",
                    "Use an end position within the alignment."
            );
        }

        if (codonPosition != null && (codonPosition < 1 || codonPosition > 3)) {
            throw new TileApplicationError(
                    "Codon position must be 1, 2, or 3.",
                    "Use codonPosition=1, codonPosition=2, or codonPosition=3."
            );
        }
    }
}
