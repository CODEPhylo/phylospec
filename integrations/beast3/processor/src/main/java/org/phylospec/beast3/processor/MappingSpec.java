package org.phylospec.beast3.processor;

import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

record MappingSpec(
        TypeElement declaration,
        String qualifiedComponentName,
        String namespace,
        String componentName,
        TypeMirror implementationType,
        TypeMirror outputType,
        List<InputSpec> inputs,
        String generatedPackageName,
        String generatedTileName) {

    MappingSpec {
        inputs = List.copyOf(inputs);
    }
}
