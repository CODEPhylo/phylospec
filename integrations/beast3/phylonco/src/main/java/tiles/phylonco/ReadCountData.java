package tiles.phylonco;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.alignment.TaxonSet;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.List;
import phylonco.beast.evolution.datatype.ReadCount;
import tiles.input.DecoratedAlignment;

/**
 * Read counts as PhyloSpec sees them: the phylonco {@link ReadCount} plus a one-site placeholder
 * alignment carrying the cell names, so tree tiles (which take their taxa from an alignment) work
 * unchanged. The placeholder never enters a likelihood.
 */
public record ReadCountData(ReadCount readCount, DecoratedAlignment taxa, int numSites) {

    static ReadCountData of(MpileupReader.Counts counts, BEASTState state) {
        ReadCount readCount = new ReadCount();
        state.setInput(readCount, readCount.readCountStrInput, counts.toReadCountString());
        state.setInput(readCount, readCount.taxaNamesInput, String.join(" ", counts.cells()));

        List<Sequence> sequences = new ArrayList<>();
        List<Taxon> taxa = new ArrayList<>();
        for (String cell : counts.cells()) {
            sequences.add(new Sequence(cell, "?"));
            taxa.add(new Taxon(cell));
        }
        Alignment placeholder = new Alignment(sequences, "nucleotide");
        TaxonSet taxonSet = new TaxonSet(taxa);

        return new ReadCountData(readCount, new DecoratedAlignment(placeholder, taxonSet, null), counts.numSites());
    }
}
