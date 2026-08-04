package org.phylospec.typeresolver.properties;

import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.util.Map;
import java.util.Set;
import org.phylospec.typeresolver.ResolvedType;

public class FromTreeProvider implements GeneratorPropertyProvider {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromTree";
    }

    @Override
    public void resolveGenerator(
            ResolvedType generatedType, Map<String, Set<ResolvedType>> resolvedArguments) {
        GeneratorPropertyProvider.resolveFile(resolvedArguments, "file")
                .flatMap(LightweightFileParsers::parseTree)
                .ifPresent(
                        properties -> {
                            generatedType
                                    .properties()
                                    .attach(NUM_BRANCHES, properties.numBranches());
                            generatedType.properties().attach(NUM_TAXA, properties.numTaxa());
                        });
    }
}
