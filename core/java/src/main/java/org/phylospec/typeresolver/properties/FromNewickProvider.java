package org.phylospec.typeresolver.properties;

import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.util.Map;
import java.util.Set;
import org.phylospec.typeresolver.ResolvedType;

public class FromNewickProvider implements GeneratorPropertyProvider {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromNewick";
    }

    @Override
    public void resolveGenerator(
            ResolvedType generatedType, Map<String, Set<ResolvedType>> resolvedArguments) {
        Set<ResolvedType> newickTypeSet = resolvedArguments.get("newickString");
        if (newickTypeSet == null || newickTypeSet.isEmpty()) return;
        Object newickObj = TypePropertyEngine.getPropertyOnAgreement(newickTypeSet, LITERAL);

        if (!(newickObj instanceof String newickString)) {
            return;
        }

        LightweightFileParsers.parseNewick(newickString)
                .ifPresent(
                        properties -> {
                            generatedType
                                    .properties()
                                    .attach(NUM_BRANCHES, properties.numBranches());
                            generatedType.properties().attach(NUM_TAXA, properties.numTaxa());
                        });
    }
}
