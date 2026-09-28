package tiles.input;

import beast.base.spec.evolution.alignment.FilteredAlignment;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class SubsetTile extends GeneratorTile<DecoratedAlignment, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "subset";
    }

    GeneratorTileInput<DecoratedAlignment, BEASTState> alignmentInput = new GeneratorTileInput<>("alignment");
    GeneratorTileInput<Integer, BEASTState> startInput =
            new GeneratorTileInput<>("start", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<Integer, BEASTState> endInput =
            new GeneratorTileInput<>("end", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<Integer, BEASTState> codonPositionInput = new GeneratorTileInput<>(
            "codonPosition", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        DecoratedAlignment alignment = this.alignmentInput.apply(beastState, indexVariables);
        Integer start = this.startInput.apply(beastState, indexVariables);
        Integer end = this.endInput.apply(beastState, indexVariables);
        Integer codonPosition = this.codonPositionInput.apply(beastState, indexVariables);

        if (start != null && end != null && end < start) {
            throw new TileApplicationError(
                    "Your start index is bigger than your end index.",
                    "Choose a start index which is smaller than the end index.");
        }
        if (start != null && alignment.alignment().getSiteCount() < start) {
            throw new TileApplicationError(
                    "Your start index is bigger than the total number of sites.",
                    "Choose a start index which is smaller than the total number of sites "
                            + alignment.alignment().getSiteCount() + ".");
        }
        if (end != null && alignment.alignment().getSiteCount() < end) {
            throw new TileApplicationError(
                    "Your end index is bigger than the total number of sites.",
                    "Choose a end index which is smaller than the total number of sites ("
                            + alignment.alignment().getSiteCount() + ").");
        }

        if (codonPosition != null && (codonPosition < 1 || 3 < codonPosition)) {
            throw new TileApplicationError(
                    "Codon position " + codonPosition + " is not 1, 2 or 3.", "Use codonPosition=1, 2 or 3.");
        }

        String filterString = filterString(start, end, codonPosition);

        FilteredAlignment filteredAlignment = new FilteredAlignment();
        beastState.setInput(filteredAlignment, filteredAlignment.alignmentInput, alignment.alignment());
        beastState.setInput(filteredAlignment, filteredAlignment.filterInput, filterString);

        return new DecoratedAlignment(filteredAlignment, alignment.taxonSet(), alignment.ages());
    }

    /**
     * The BEAST {@code FilteredAlignment} filter for sites {@code start..end} (1-based,
     * inclusive, either open), keeping only one codon position if one is given.
     *
     * <p>Codon position {@code p} of a range starting at site {@code s} is every third site from
     * {@code s + p - 1}, which BEAST writes as the iterator {@code from:to:step}. A range is
     * {@code from-to}. BEAST's other step syntax is {@code from-to\\step} with a backslash; a
     * slash is not read as a step at all, and the filter silently keeps every site.
     */
    static String filterString(Integer start, Integer end, Integer codonPosition) {
        int from = start == null ? 1 : start;
        String to = end == null ? "" : end.toString();
        if (codonPosition == null) {
            return from + "-" + to;
        }
        return (from + codonPosition - 1) + ":" + to + ":3";
    }
}
