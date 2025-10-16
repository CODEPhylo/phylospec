package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Type;
import org.phylospec.lexer.TokenType;

import java.util.*;

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
    /// -   The rules is a list containing different input combinations and
    ///     the resulting type:
    ///     [["PositiveReal", "PositiveReal", "Real"], ["Real", "Real", "Real"]]
    ///     Here, both input combinations produce a Real.
    /// -   The query specifies the given input:
    ///     ["Real", "PositiveReal"]
    ///     In that case, there is no exact match. However, PositiveReal can be widened
    ///     to a Real and get a match in the rules.
    ///
    /// Input types are widened automatically using the "extends" field in the component
    /// definitions.
    ///
    /// Returns null if no match is found.
    public Set<ResolvedType> findMatch(List<Rule> rules, Query query) {
        Set<ResolvedType> exactMatch = findExactMatch(rules, query);
        if (!exactMatch.isEmpty()) {
            return exactMatch;
        }

        // we couldn't find a direct match
        // let's try to go up the type hierarchy of the query to see if there is
        // a match for widened types
        while (true) {
            for (int i = 0; i < query.inputTypes.length; i++) {
                Set<Type> widenedInputType = new HashSet<>();
                for (ResolvedType type : query.inputTypes[i]) {
                    if (type.getExtends() != null) {
                        // we replace this type with its direct parent
                        Type widenedType = componentResolver.resolveType(type.getExtends());
                        if (widenedType != null) widenedInputType.add(widenedType);
                    }
                }

                if (widenedInputType.isEmpty()) return Set.of();

                Set<ResolvedType> match = findExactMatch(rules, query);
                if (!match.isEmpty()) return match;
            }
        }
    }

    /** Checks if there is a perfectly matching rule the given query and returns it.
     * Returns null if not match is found. */
    private Set<ResolvedType> findExactMatch(List<Rule> rules, Query query) {
        for (Rule rule : rules) {
            if (rule.operation != query.operation) continue;
            if (!(componentResolver.canResolveType(rule.resultType))) continue;

            boolean matches = true;
            for (int i = 0; i < query.inputTypes.length; i++) {
                if (rule.inputTypes[i].equals(ANY)) continue;

                // we have to check if there is an overlap
                final int j = i;
                if (query.inputTypes[i].stream().noneMatch(x -> x.getName().equals(rule.inputTypes[j]))) {
                    matches =  false;
                    break;
                }
            }

            if (matches) {
                return Set.of(
                        ResolvedType.fromString(rule.resultType, componentResolver)
                );
            }
        }

        return Set.of();
    }

    static class Rule {
        public Rule(TokenType operation, String... types) {
            this.operation = operation;
            this.inputTypes = Arrays.stream(types).limit(types.length - 1).toArray(String[]::new);
            this.resultType = types[types.length - 1];
        }

        TokenType operation;
        String[] inputTypes;
        String resultType;
    }

    static class Query {
        public Query(TokenType operation, Set<ResolvedType>... inputTypes) {
            this.operation = operation;
            this.inputTypes = inputTypes;
        }

        TokenType operation;
        Set<ResolvedType>[] inputTypes;
    }

}
