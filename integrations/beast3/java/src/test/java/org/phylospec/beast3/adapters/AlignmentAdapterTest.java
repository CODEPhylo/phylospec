package org.phylospec.beast3.adapters;

import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;
import beastconfig.BEASTState;
import org.junit.jupiter.api.Test;
import tiles.input.DecoratedAlignment;

public class AlignmentAdapterTest {

    @Test
    public void extractsTheBeastAlignment() {
        Alignment alignment = new Alignment();
        DecoratedAlignment decorated = new DecoratedAlignment(alignment, new TaxonSet(), new TraitSet());

        Alignment result = new AlignmentAdapter().adapt(decorated, new BEASTState("alignment-adapter"));

        assertSame(alignment, result);
    }
}
