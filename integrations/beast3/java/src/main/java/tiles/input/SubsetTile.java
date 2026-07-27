package tiles.input;

import beast.base.spec.evolution.alignment.FilteredAlignment;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.TileApplicationError;

import java.util.IdentityHashMap;
import java.util.Set;

public class SubsetTile extends GeneratorTile<DecoratedAlignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "subset";
    }

    GeneratorTileInput<DecoratedAlignment> alignmentInput = new GeneratorTileInput<>("alignment");
    GeneratorTileInput<Integer> startInput = new GeneratorTileInput<>(
            "start", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<Integer> endInput = new GeneratorTileInput<>(
            "end", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<Integer> codonPositionInput = new GeneratorTileInput<>(
            "codonPosition", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        DecoratedAlignment alignment = this.alignmentInput.apply(beastState, indexVariables);
        Integer start = this.startInput.apply(beastState, indexVariables);
        Integer end = this.endInput.apply(beastState, indexVariables);
        Integer codonPosition = this.codonPositionInput.apply(beastState, indexVariables);
        int siteCount = alignment.alignment().getSiteCount();

        if (start != null && end != null && end < start) {
            throw new TileApplicationError(
                    "Your start index is bigger than your end index.",
                    "Choose a start index which is smaller than the end index."
            );
        }
        if (start != null && start < 1) {
            throw new TileApplicationError(
                    "Your start index is smaller than one.",
                    "Choose a one-based start index."
            );
        }
        if (end != null && end < 1) {
            throw new TileApplicationError(
                    "Your end index is smaller than one.",
                    "Choose a one-based end index."
            );
        }
        if (start != null && siteCount < start) {
            throw new TileApplicationError(
                    "Your start index is bigger than the total number of sites.",
                    "Choose a start index which is smaller than the total number of sites " + siteCount + "."
            );
        }
        if (end != null && siteCount < end) {
            throw new TileApplicationError(
                    "Your end index is bigger than the total number of sites.",
                    "Choose a end index which is smaller than the total number of sites (" + siteCount + ")."
            );
        }
        if (codonPosition != null && (codonPosition < 1 || codonPosition > 3)) {
            throw new TileApplicationError(
                    "Your codon position is outside the range from one to three.",
                    "Choose codonPosition=1, codonPosition=2, or codonPosition=3."
            );
        }

        String filterString = buildFilterString(start, end, codonPosition);

        FilteredAlignment filteredAlignment = new FilteredAlignment();
        beastState.setInput(filteredAlignment, filteredAlignment.alignmentInput, alignment.alignment());
        beastState.setInput(filteredAlignment, filteredAlignment.filterInput, filterString);

        // FilteredAlignment builds its site-to-pattern index during initialization.
        // Downstream deterministic functions such as numSites() may inspect the
        // filtered alignment while tiles are still being applied, before the final
        // BEAST object initialization pass in PhyloSpecRunner.
        beastState.initBEASTObject(filteredAlignment);

        return new DecoratedAlignment(filteredAlignment, alignment.taxonSet(), alignment.ages());
    }

    static String buildFilterString(Integer start, Integer end, Integer codonPosition) {
        int firstSite = start == null ? 1 : start;

        if (codonPosition == null) {
            return firstSite + "-" + (end == null ? "" : end);
        }

        int firstSiteCodonPosition = Math.floorMod(firstSite - 1, 3) + 1;
        int offset = Math.floorMod(codonPosition - firstSiteCodonPosition, 3);
        int firstSelectedSite = firstSite + offset;

        if (end != null && firstSelectedSite > end) {
            throw new TileApplicationError(
                    "The selected range contains no sites at codon position " + codonPosition + ".",
                    "Choose a wider range or a different codon position."
            );
        }

        return firstSelectedSite + ":" + (end == null ? "" : end) + ":3";
    }

}
