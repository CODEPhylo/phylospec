package org.phylospec.typeresolver.properties;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.phylospec.components.ParsedType;
import org.phylospec.typeresolver.ResolvedType;

public class FromTreeHook implements TypePropertyResolverHook {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromTree";
    }

    @Override
    public void attemptResolution(
            ParsedType parsedType,
            ResolvedType generatedType,
            Map<String, Set<ResolvedType>> resolvedArguments) {
        Set<ResolvedType> fileNameTypeSet = resolvedArguments.get("file");
        if (fileNameTypeSet == null || fileNameTypeSet.isEmpty()) return;
        Object fileNameObj = TypePropertyUtils.getPropertyOnAgreement(fileNameTypeSet, "literal");

        if (!(fileNameObj instanceof String fileName)) {
            return;
        }

        Optional<Path> path = LightweightFileParsers.resolveSmallFile(fileName);
        if (path.isEmpty()) return;
        LightweightFileParsers.parseTree(path.get())
                .ifPresent(
                        properties -> {
                            generatedType.attachProperty("numBranches", properties.numBranches());
                            generatedType.attachProperty("numTaxa", properties.numTaxa());
                        });
    }
}
