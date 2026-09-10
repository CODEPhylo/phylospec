package org.phylospec.beast3.processor;

import javax.lang.model.type.TypeMirror;

record InputBindingSpec(
        String input,
        TypeMirror inputType,
        TypeMirror adapterType,
        boolean usesAdapter) {}
