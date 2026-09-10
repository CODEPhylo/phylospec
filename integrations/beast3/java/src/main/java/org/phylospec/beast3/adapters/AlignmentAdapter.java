package org.phylospec.beast3.adapters;

import beast.base.evolution.alignment.Alignment;
import beastconfig.BEASTState;
import org.phylospec.annotations.AdapterMapping;
import org.phylospec.tiling.TypeAdapter;
import tiles.input.DecoratedAlignment;

/** Extracts the BEAST alignment from the engine value representing a PhyloSpec alignment. */
@AdapterMapping
public final class AlignmentAdapter implements TypeAdapter<DecoratedAlignment, Alignment, BEASTState> {

    @Override
    public Alignment adapt(DecoratedAlignment value, BEASTState state) {
        return value.alignment();
    }
}
