package org.phylospec.typeresolver.properties;

import java.util.Map;
import java.util.Set;
import org.phylospec.components.ParsedType;
import org.phylospec.typeresolver.ResolvedType;

public class FromNewickHook implements TypePropertyResolverHook {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromNewick";
    }

    @Override
    public void attemptResolution(
            ParsedType parsedType,
            ResolvedType generatedType,
            Map<String, Set<ResolvedType>> resolvedArguments) {
        Set<ResolvedType> newickTypeSet = resolvedArguments.get("newickString");
        if (newickTypeSet == null || newickTypeSet.isEmpty()) return;
        Object newickObj = TypePropertyUtils.getPropertyOnAgreement(newickTypeSet, "literal");

        if (!(newickObj instanceof String newickString)) {
            return;
        }

        LightweightFileParsers.parseNewick(newickString)
                .ifPresent(
                        properties -> {
                            generatedType.attachProperty("numBranches", properties.numBranches());
                            generatedType.attachProperty("numTaxa", properties.numTaxa());
                        });
    }
}
