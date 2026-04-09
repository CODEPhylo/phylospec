package tiles.input;

import beast.base.spec.evolution.alignment.FilteredAlignment;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class SubsetTile extends GeneratorTile<DecoratedAlignment> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "subset";
    }

    TileInput<DecoratedAlignment> alignmentInput = new TileInput<>("alignment");
    TileInput<Integer> startInput = new TileInput<>("start", false);
    TileInput<Integer> endInput = new TileInput<>("end", false);
    TileInput<Integer> codonPositionInput = new TileInput<>("codonPosition", false);

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState) {
        DecoratedAlignment alignment = this.alignmentInput.apply(beastState);
        Integer start = this.startInput.apply(beastState);
        Integer end = this.endInput.apply(beastState);
        Integer codonPosition = this.codonPositionInput.apply(beastState);

        String filterString  = "";
        filterString += start == null ? "1" : start;
        filterString += ":";
        filterString += end == null ? "" : end;
        filterString += codonPosition == null ? "" : (":" + codonPosition);

        FilteredAlignment filteredAlignment = new FilteredAlignment();
        beastState.setInput(filteredAlignment, filteredAlignment.alignmentInput, alignment.alignment());
        beastState.setInput(filteredAlignment, filteredAlignment.filterInput, filterString);

        return new DecoratedAlignment(filteredAlignment, alignment.taxonSet(), alignment.ages());
    }

}
