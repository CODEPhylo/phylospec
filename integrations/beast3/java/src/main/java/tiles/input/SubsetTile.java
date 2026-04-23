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

        if (start != null && end != null && end < start) {
            throw new TileApplicationError(
                    "Your start index is bigger than your end index.",
                    "Choose a start index which is smaller than the end index."
            );
        }
        if (start != null && alignment.alignment().getSiteCount() < start) {
            throw new TileApplicationError(
                    "Your start index is bigger than the total number of sites.",
                    "Choose a start index which is smaller than the total number of sites " + alignment.alignment().getSiteCount() + "."
            );
        }
        if (end != null && alignment.alignment().getSiteCount() < end) {
            throw new TileApplicationError(
                    "Your end index is bigger than the total number of sites.",
                    "Choose a end index which is smaller than the total number of sites (" + alignment.alignment().getSiteCount() + ")."
            );
        }

        String filterString  = "";
        filterString += start == null ? "1" : start;
        filterString += "-";
        filterString += end == null ? "" : end;
        filterString += codonPosition == null ? "" : ("/" + codonPosition);

        FilteredAlignment filteredAlignment = new FilteredAlignment();
        beastState.setInput(filteredAlignment, filteredAlignment.alignmentInput, alignment.alignment());
        beastState.setInput(filteredAlignment, filteredAlignment.filterInput, filterString);

        return new DecoratedAlignment(filteredAlignment, alignment.taxonSet(), alignment.ages());
    }

}
