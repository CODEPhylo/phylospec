package org.phylospec.typeresolver.properties;

import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.util.Map;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.ResolvedTypeSet;
import org.phylospec.workspace.Workspace;

public class FromTreeProvider implements GeneratorPropertyProvider {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromTree";
    }

    @Override
    public void resolveGenerator(
            ResolvedType generatedType, Map<String, ResolvedTypeSet> resolvedArguments, Workspace workspace) {
        GeneratorPropertyProvider.resolveFile(resolvedArguments, "file", workspace)
                .flatMap(LightweightFileParsers::parseTree)
                .ifPresent(properties -> {
                    generatedType.properties().attach(NUM_BRANCHES, properties.numBranches());
                    generatedType.properties().attach(NUM_TAXA, properties.numTaxa());
                });
    }
}
