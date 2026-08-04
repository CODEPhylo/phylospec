package org.phylospec.typeresolver.properties;

import java.nio.file.Path;
import java.util.*;
import org.phylospec.typeresolver.ResolvedType;

/**
 * Provides generator-specific type properties that cannot be inferred from component metadata.
 * Implementations are discovered through {@link java.util.ServiceLoader} and selected by their
 * fully qualified generator name.
 */
public interface GeneratorPropertyProvider {

    /**
     * Returns the name of the generator including the namespace for which this provider is applicable.
     */
    String getGenerator();

    /**
     * Resolves the properties for the generatedType based on the resolved arguments.
     */
    void resolveGenerator(
            ResolvedType generatedType, Map<String, Set<ResolvedType>> resolvedArguments);

    /**
     * Returns all providers that can be discovered through {@link java.util.ServiceLoader}.
     */
    static List<GeneratorPropertyProvider> loadProviders() {
        List<GeneratorPropertyProvider> providers = new ArrayList<>();
        for (GeneratorPropertyProvider hook : ServiceLoader.load(GeneratorPropertyProvider.class)) {
            providers.add(hook);
        }
        return providers;
    }

    /**
     * Resolves a generator argument to its literal string value, if all of its candidate types
     * agree on one.
     */
    static Optional<String> resolveLiteral(
            Map<String, Set<ResolvedType>> resolvedArguments, String argumentName) {
        Set<ResolvedType> argumentTypeSet = resolvedArguments.get(argumentName);
        if (argumentTypeSet == null || argumentTypeSet.isEmpty()) return Optional.empty();

        Object literal =
                TypePropertyEngine.getPropertyOnAgreement(
                        argumentTypeSet, TypePropertyNames.LITERAL);
        return literal instanceof String stringValue ? Optional.of(stringValue) : Optional.empty();
    }

    /** Resolves a generator argument to a small on-disk file, if it names one. */
    static Optional<Path> resolveFile(
            Map<String, Set<ResolvedType>> resolvedArguments, String argumentName) {
        return resolveLiteral(resolvedArguments, argumentName)
                .flatMap(LightweightFileParsers::resolveSmallFile);
    }
}
