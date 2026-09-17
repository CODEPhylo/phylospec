package org.phylospec.beast3.processor;

import java.util.List;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.phylospec.typeresolver.Stochasticity;

record InputSpec(
        String argument,
        String semanticType,
        TypeMirror valueType,
        boolean required,
        Set<Stochasticity> acceptedStochasticities,
        List<InputBindingSpec> bindings) {

    InputSpec {
        acceptedStochasticities = Set.copyOf(acceptedStochasticities);
        bindings = List.copyOf(bindings);
    }
}
