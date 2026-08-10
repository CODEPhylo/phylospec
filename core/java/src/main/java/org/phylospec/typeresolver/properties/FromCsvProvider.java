package org.phylospec.typeresolver.properties;

import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.util.Map;
import java.util.Set;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.workspace.Workspace;

public class FromCsvProvider implements GeneratorPropertyProvider {

    @Override
    public String getGenerator() {
        return "phylospec.functions.io.fromCSV";
    }

    @Override
    public void resolveGenerator(
            ResolvedType generatedType,
            Map<String, Set<ResolvedType>> resolvedArguments,
            Workspace workspace) {
        GeneratorPropertyProvider.resolveFile(resolvedArguments, "file", workspace)
                .flatMap(LightweightFileParsers::parseCsv)
                .ifPresent(numRows -> generatedType.properties().attach(NUM, numRows));
    }
}
