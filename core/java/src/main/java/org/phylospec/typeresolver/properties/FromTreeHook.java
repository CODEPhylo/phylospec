package org.phylospec.typeresolver.properties;

import java.util.Map;
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
        Object fileNameObj = TypePropertyUtils.getPropertyOnAgreement(fileNameTypeSet, "literal");

        if (!(fileNameObj instanceof String fileName)) {
            // we don't know the file name, let's skip
            return;
        }

        // the file could be a newick or nexus file, in both cases with a single tree
        // read the file and get the properties

        // TODO

        // attach the properties

        generatedType.attachProperty("numBranches", 100);
        generatedType.attachProperty("numTaxa", 100);
    }
}
