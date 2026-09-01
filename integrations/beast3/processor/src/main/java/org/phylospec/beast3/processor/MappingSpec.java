package org.phylospec.beast3.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

record MappingSpec(
        TypeElement declaration,
        String qualifiedComponentName,
        String namespace,
        String componentName,
        TypeMirror implementationType,
        String generatedPackageName,
        String generatedTileName) {}
