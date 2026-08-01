package org.phylospec.typeresolver.properties;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.phylospec.components.ParsedType;
import org.phylospec.typeresolver.ResolvedType;

public class FromCsvHook implements TypePropertyResolverHook {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromCSV";
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
        LightweightFileParsers.parseCsv(path.get())
                .ifPresent(numRows -> generatedType.attachProperty("num", numRows));
    }
}
