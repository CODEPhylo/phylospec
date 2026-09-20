package org.phylospec.beast3.adapters;

import beast.base.spec.type.BoolScalar;
import beastconfig.BEASTState;
import org.phylospec.annotations.AdapterMapping;
import org.phylospec.tiling.TypeAdapter;

/** Extracts a Java Boolean for legacy BEAST inputs from a PhyloSpec boolean scalar. */
@AdapterMapping
public final class BooleanAdapter implements TypeAdapter<BoolScalar, Boolean, BEASTState> {

    @Override
    public Boolean adapt(BoolScalar value, BEASTState state) {
        return value.get();
    }
}
