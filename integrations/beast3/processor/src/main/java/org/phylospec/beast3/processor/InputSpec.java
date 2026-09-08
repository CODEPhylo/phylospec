package org.phylospec.beast3.processor;

import javax.lang.model.type.TypeMirror;

record InputSpec(
        String argument,
        String semanticType,
        String input,
        TypeMirror valueType,
        TypeMirror inputType,
        TypeMirror adapterType,
        boolean usesAdapter,
        boolean required) {}
