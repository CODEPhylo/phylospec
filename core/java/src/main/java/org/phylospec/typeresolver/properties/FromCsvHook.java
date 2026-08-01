package org.phylospec.typeresolver.properties;

import java.util.Map;
import java.util.Set;
import org.phylospec.components.ParsedType;
import org.phylospec.typeresolver.ResolvedType;

public class FromCsvHook implements TypePropertyResolverHook {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromCsv";
    }

    @Override
    public void attemptResolution(
            ParsedType parsedType,
            ResolvedType generatedType,
            Map<String, Set<ResolvedType>> resolvedArguments) {
        Set<ResolvedType> fileNameTypeSet = resolvedArguments.get("file");
        Object fileNameObj = TypePropertyUtils.getPropertyOnAgreement(fileNameTypeSet, "literal");

        if (!(fileNameObj instanceof String fileName)) {
            // we don't know the file name, let's skip
            return;
        }

        // read the file and get the properties

        // TODO

        // attach the properties

        generatedType.attachProperty("num", 100); // num rows without header
    }
}
