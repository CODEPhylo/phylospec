package org.phylospec.typeresolver.properties;

import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.phylospec.typeresolver.ResolvedType;

public class FromCsvProvider implements GeneratorPropertyProvider {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromCSV";
    }

    @Override
    public void resolveGenerator(
            ResolvedType generatedType, Map<String, Set<ResolvedType>> resolvedArguments) {
        Set<ResolvedType> fileNameTypeSet = resolvedArguments.get("file");
        if (fileNameTypeSet == null || fileNameTypeSet.isEmpty()) return;
        Object fileNameObj = TypePropertyEngine.getPropertyOnAgreement(fileNameTypeSet, LITERAL);

        if (!(fileNameObj instanceof String fileName)) {
            return;
        }

        Optional<Path> path = LightweightFileParsers.resolveSmallFile(fileName);
        if (path.isEmpty()) return;
        LightweightFileParsers.parseCsv(path.get())
                .ifPresent(numRows -> generatedType.properties().attach(NUM, numRows));
    }
}
