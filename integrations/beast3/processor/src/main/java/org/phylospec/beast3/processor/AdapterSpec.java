package org.phylospec.beast3.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

record AdapterSpec(
        TypeElement declaration,
        TypeMirror adapterType,
        TypeMirror sourceType,
        TypeMirror targetType) {}
