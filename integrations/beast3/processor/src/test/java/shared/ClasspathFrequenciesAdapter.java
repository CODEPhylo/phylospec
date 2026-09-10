package shared;

import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import org.phylospec.tiling.TypeAdapter;

/** Precompiled adapter used to model an adapter supplied by a dependency JAR. */
public final class ClasspathFrequenciesAdapter
        implements TypeAdapter<Simplex, Frequencies, BEASTState> {

    @Override
    public Frequencies adapt(Simplex value, BEASTState state) {
        return null;
    }
}
