package org.phylospec.typeresolver.properties;

import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.util.Map;
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
        GeneratorPropertyProvider.resolveFile(resolvedArguments, "file")
                .flatMap(LightweightFileParsers::parseCsv)
                .ifPresent(numRows -> generatedType.properties().attach(NUM, numRows));
    }
}
