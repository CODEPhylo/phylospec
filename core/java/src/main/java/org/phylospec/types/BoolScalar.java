package org.phylospec.types;

import org.phylospec.domain.Bool;

public interface BoolScalar extends Scalar<Bool, Boolean> {

    boolean get();

}