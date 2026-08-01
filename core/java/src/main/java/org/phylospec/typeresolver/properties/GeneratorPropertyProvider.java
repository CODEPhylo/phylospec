package org.phylospec.typeresolver.properties;

import java.util.Map;
import java.util.Set;
import org.phylospec.typeresolver.ResolvedType;

/**
 * Provides generator-specific type properties that cannot be inferred from component metadata.
 * Implementations are discovered through {@link java.util.ServiceLoader} and selected by their
 * fully qualified generator name.
 */
public interface GeneratorPropertyProvider {

    String getGenerator();

    void resolveGenerator(
            ResolvedType generatedType, Map<String, Set<ResolvedType>> resolvedArguments);
}
