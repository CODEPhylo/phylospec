package tiles.phylonco;

import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.alignment.TaxonSet;
import beastconfig.BEASTState;
import java.util.List;
import phylonco.beast.evolution.datatype.ReadCount;
import tiles.input.TaxaData;

/**
 * Read counts as PhyloSpec sees them: the phylonco {@link ReadCount} plus the cells as a taxon list
 * in input order. {@code taxa(readCounts)} returns this list, so tree tiles take the cells through
 * the same interface as {@code taxa(alignment)}.
 */
public record ReadCountData(ReadCount readCount, TaxaData taxa, int numSites) {

    static ReadCountData of(MpileupReader.Counts counts, BEASTState state) {
        ReadCount readCount = new ReadCount();
        state.setInput(readCount, readCount.readCountStrInput, counts.toReadCountString());
        state.setInput(readCount, readCount.taxaNamesInput, String.join(" ", counts.cells()));

        List<Taxon> cells = counts.cells().stream().map(Taxon::new).toList();
        TaxaData taxa = new TaxaData(new TaxonSet(cells), null);

        return new ReadCountData(readCount, taxa, counts.numSites());
    }

    /** The number of cells. */
    public int numCells() {
        return this.taxa.size();
    }
}
