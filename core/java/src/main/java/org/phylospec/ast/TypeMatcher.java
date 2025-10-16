package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * This class helps to determine the type of the result of an operation on one or more operands.
 */
public class TypeMatcher {
    private ComponentResolver componentResolver;

    public TypeMatcher(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
    }

    public static final String ANY = "ANY";

    /// Determines the result type of an operation on one or more operands.
    ///
    /// Two arguments are required:
    /// -   The typeMap is a list containing different input combinations and
    ///     the resulting type:
    ///     [["PositiveReal", "PositiveReal", "Real"], ["Real", "Real", "Real"]]
    ///     Here, both input combinations produce a Real.
    /// -   The query specifies the given input:
    ///     ["Real", "PositiveReal"]
    ///     In that case, there is no exact match. However, PositiveReal can be widened
    ///     to a Real and get a match in the typeMap.
    ///
    /// Input types are widened automatically using the "extends" field in the component
    /// definitions.
    public String findMatch(List<List<Object>> typeMap, List<Object> query) {
        String exactMatch = findExactMatch(typeMap, query);
        if (exactMatch != null) {
            return exactMatch;
        }

        // we couldn't find a direct match
        // let's try to go up the type hierarchy of the query to see if there is
        // a match for widened types
        while (true) {
            boolean reachedMostGeneralType = true;

            for (int i = 0; i < query.size() - 1; i++) {  // the last entry contains the result
                if (!componentResolver.canResolveType(query.get(i).toString())) continue;

                Type type = componentResolver.resolveType(query.get(i).toString());
                if (type.getExtends() != null) {
                    // we replace this type with its direct parent
                    query = new ArrayList<>(query);
                    query.set(i, type.getExtends());

                    String match = findExactMatch(typeMap, query);
                    if (match != null) {
                        return match;
                    }

                    reachedMostGeneralType = false;
                }
            }

            if (reachedMostGeneralType) {
                return null;
            }
        }
    }

    /** Checks if there is an exact match in the typeMap for the given query and returns it.
     * Returns null if not match is found. */
    private String findExactMatch(List<List<Object>> typeMap, List<Object> query) {
        for (List<Object> candidate : typeMap) {
            assert candidate.size() == query.size() + 1; // the last query element it the resulting type

            boolean matches = true;
            for (int i = 0; i < query.size(); i++) {
                if (candidate.get(i) != ANY && !(candidate.get(i).equals(query.get(i)))) {
                    matches =  false;
                    break;
                }
            }
            if (matches) {
                return candidate.getLast().toString();
            }
        }

        return null;
    }

}
