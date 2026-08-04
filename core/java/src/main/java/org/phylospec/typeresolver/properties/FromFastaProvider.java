package org.phylospec.typeresolver.properties;

import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.util.Map;
import java.util.Set;
import org.phylospec.typeresolver.ResolvedType;

public class FromFastaProvider implements GeneratorPropertyProvider {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromFasta";
    }

    @Override
    public void resolveGenerator(ResolvedType generatedType, Map<String, Set<ResolvedType>> resolvedArguments) {
        GeneratorPropertyProvider.resolveFile(resolvedArguments, "file")
                .flatMap(LightweightFileParsers::parseFasta)
                .ifPresent(properties -> {
                    generatedType.properties().attach(NUM_SITES, properties.numSites());
                    generatedType.properties().attach(NUM_TAXA, properties.numTaxa());
                });
    }
}
