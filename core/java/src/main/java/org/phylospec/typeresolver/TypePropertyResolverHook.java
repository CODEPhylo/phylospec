package org.phylospec.typeresolver;

import java.util.Map;
import java.util.Set;
import org.phylospec.components.ParsedType;

public interface TypePropertyResolverHook {

    String getGenerator();

    void attemptResolution(
            ParsedType parsedType,
            ResolvedType generatedType,
            Map<String, Set<ResolvedType>> resolvedArguments);
}
