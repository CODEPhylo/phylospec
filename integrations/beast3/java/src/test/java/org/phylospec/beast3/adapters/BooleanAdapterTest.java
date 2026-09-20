package org.phylospec.beast3.adapters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.inference.parameter.BoolScalarParam;
import beastconfig.BEASTState;
import org.junit.jupiter.api.Test;

public class BooleanAdapterTest {

    @Test
    public void extractsJavaBoolean() {
        BooleanAdapter adapter = new BooleanAdapter();
        BEASTState state = new BEASTState("boolean-adapter");

        assertTrue(adapter.adapt(new BoolScalarParam(true), state));
        assertFalse(adapter.adapt(new BoolScalarParam(false), state));
    }
}
