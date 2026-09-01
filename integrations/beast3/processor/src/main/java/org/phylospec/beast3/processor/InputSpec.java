package org.phylospec.beast3.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

record InputSpec(
        ExecutableElement declaration,
        String argument,
        String input,
        TypeMirror valueType,
        boolean required) {}