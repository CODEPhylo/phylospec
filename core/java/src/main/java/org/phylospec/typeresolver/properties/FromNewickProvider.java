package org.phylospec.typeresolver.properties;

import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.util.Map;
import java.util.Set;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.workspace.Workspace;

public class FromNewickProvider implements GeneratorPropertyProvider {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromNewick";
    }

    @Override
    public void resolveGenerator(
            ResolvedType generatedType, Map<String, Set<ResolvedType>> resolvedArguments, Workspace workspace) {
        GeneratorPropertyProvider.resolveLiteral(resolvedArguments, "newickString")
                .flatMap(LightweightFileParsers::parseNewick)
                .ifPresent(properties -> {
                    generatedType.properties().attach(NUM_BRANCHES, properties.numBranches());
                    generatedType.properties().attach(NUM_TAXA, properties.numTaxa());
                });
    }
}
