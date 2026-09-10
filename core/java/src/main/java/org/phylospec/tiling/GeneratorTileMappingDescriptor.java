package org.phylospec.tiling;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.phylospec.annotations.PhyloSpec;
import org.phylospec.typeresolver.Stochasticity;

/**
 * Immutable description of how one generator tile maps a PhyloSpec generator to an engine.
 *
 * <p>This is the shared representation used after metadata has been obtained from annotations or
 * from a conventional tile. It deliberately describes the mapping without containing the
 * engine-specific construction performed by {@code applyTile()}.
 */
public record GeneratorTileMappingDescriptor(
        Class<?> implementationClass,
        String componentName,
        Optional<String> namespace,
        Optional<PhyloSpec.Category> category,
        Optional<PhyloSpec.Role> role,
        TypeToken<?> outputType,
        List<Input> inputs) {

    public GeneratorTileMappingDescriptor {
        Objects.requireNonNull(implementationClass, "implementationClass");
        requireNonBlank(componentName, "componentName");
        namespace = Objects.requireNonNull(namespace, "namespace");
        category = Objects.requireNonNull(category, "category");
        role = Objects.requireNonNull(role, "role");
        Objects.requireNonNull(outputType, "outputType");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));

        namespace.ifPresent(value -> requireNonBlank(value, "namespace"));

        Set<String> inputNames = new HashSet<>();
        for (Input input : inputs) {
            if (!inputNames.add(input.name())) {
                throw new IllegalArgumentException("Generator tile mapping for '"
                        + componentName
                        + "' declares the input '"
                        + input.name()
                        + "' more than once.");
            }
        }
    }

    /**
     * Returns the engine-independent contract implemented by this mapping. The concrete Tile class
     * is deliberately excluded, allowing equivalent mappings supplied by different packages to be
     * recognised.
     */
    public Signature signature() {
        return new Signature(componentName, namespace, outputType, inputs);
    }

    /** Identifies one semantic generator mapping independently of its Tile implementation class. */
    public record Signature(
            String componentName, Optional<String> namespace, TypeToken<?> outputType, List<Input> inputs) {

        public Signature {
            requireNonBlank(componentName, "componentName");
            namespace = Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(outputType, "outputType");
            inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        }

        /** Returns the qualified generator name used in diagnostics. */
        public String qualifiedComponentName() {
            return namespace
                    .filter(value -> !componentName.startsWith(value + "."))
                    .map(value -> value + "." + componentName)
                    .orElse(componentName);
        }
    }

    /** Describes one PhyloSpec argument accepted by the generator tile. */
    public record Input(
            String name,
            boolean required,
            Optional<String> defaultValue,
            TypeToken<?> type,
            Set<Stochasticity> acceptedStochasticities) {

        public Input {
            requireNonBlank(name, "input name");
            defaultValue = Objects.requireNonNull(defaultValue, "defaultValue").filter(value -> !value.isBlank());
            Objects.requireNonNull(type, "type");
            acceptedStochasticities =
                    Set.copyOf(Objects.requireNonNull(acceptedStochasticities, "acceptedStochasticities"));
            if (acceptedStochasticities.isEmpty()) {
                throw new IllegalArgumentException(
                        "Generator tile mapping input '" + name + "' must accept at least one stochasticity.");
            }
        }
    }

    private static void requireNonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
    }
}
