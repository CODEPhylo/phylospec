package org.phylospec.types;

import org.phylospec.primitives.Bool;

public interface BoolScalar extends Scalar<Bool, Boolean> {

    boolean get();

}