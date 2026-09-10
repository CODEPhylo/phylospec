package org.phylospec.beast3.processor;

import java.util.List;
import javax.lang.model.type.TypeMirror;

record InputSpec(
        String argument,
        String semanticType,
        TypeMirror valueType,
        boolean required,
        List<InputBindingSpec> bindings) {

    InputSpec {
        bindings = List.copyOf(bindings);
    }
}
