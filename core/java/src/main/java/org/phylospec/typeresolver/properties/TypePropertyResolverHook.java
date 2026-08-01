package org.phylospec.typeresolver.properties;

import java.util.Map;
import java.util.Set;
import org.phylospec.components.ParsedType;
import org.phylospec.typeresolver.ResolvedType;

public interface TypePropertyResolverHook {

    String getGenerator();

    void attemptResolution(
            ParsedType parsedType,
            ResolvedType generatedType,
            Map<String, Set<ResolvedType>> resolvedArguments);
}
